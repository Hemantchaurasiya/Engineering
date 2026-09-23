package com.orderengine.phase5;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry of active notification channels (email, SMS, push, webhook —
 * one implementation per delivery mechanism). Every single order that
 * completes iterates this ENTIRE list to notify every registered channel.
 * Channels themselves are registered/deregistered extremely rarely — at
 * startup, or occasionally via an admin action — nowhere close to the
 * frequency of iteration. This read:write ratio is the textbook case for
 * CopyOnWriteArrayList, for exactly the same reason Phase 2's
 * InventoryLedger cared about read:write skew when choosing a lock
 * strategy — the right concurrent collection choice, like the right lock
 * choice, follows from the actual traffic shape, not habit.
 *
 * How CopyOnWriteArrayList actually works: every mutating operation
 * (add/remove/set) takes a lock, copies the ENTIRE underlying array, and
 * atomically swaps in the new array reference. Iteration NEVER takes any
 * lock at all and NEVER throws ConcurrentModificationException — an
 * iterator created via iterator() simply holds a reference to whatever
 * array snapshot existed at the moment the iterator was created, and
 * iterates that snapshot to the end, completely unaffected by any
 * mutation that happens on other threads during the iteration. This is
 * "snapshot" or "weakly consistent" iteration: an iterator started before
 * a channel is added will NOT see that new channel, even if the addition
 * completes while the iteration is still in progress — neither behavior
 * (seeing it or not) is a bug, both are valid outcomes of the contract,
 * but code must not assume either one specifically.
 *
 * The cost this trades away: every single mutation is an O(n) full-array
 * copy. For a list that changes rarely (this one) and is read constantly
 * (every order notification), that's an excellent trade. For a list
 * that's mutated frequently, CopyOnWriteArrayList would be a serious
 * anti-pattern — O(n) copies on every write turns into real, measurable
 * throughput loss and GC churn from constantly discarding old array
 * snapshots, which is exactly why VipAwareOrderQueue and RetryScheduler
 * in this same phase do NOT use it for their much more frequently
 * mutated structures.
 */
public class NotificationChannelRegistry {

    public interface NotificationChannel {
        String name();
        void notify(long orderId, String message);
    }

    private final CopyOnWriteArrayList<NotificationChannel> channels = new CopyOnWriteArrayList<>();

    public void register(NotificationChannel channel) {
        channels.addIfAbsent(channel); // atomic "add only if not already present" — avoids double registration
    }

    public void deregister(NotificationChannel channel) {
        channels.remove(channel);
    }

    /**
     * Called on every completed order. No lock is taken here at all —
     * this is the entire performance point of CopyOnWriteArrayList: the
     * hot, frequent operation (iteration) is essentially free, and the
     * cost is pushed entirely onto the rare operation (mutation).
     */
    public void notifyAll(long orderId, String message) {
        for (NotificationChannel channel : channels) {
            channel.notify(orderId, message);
        }
    }

    public List<NotificationChannel> currentChannels() {
        return List.copyOf(channels);
    }

    public int channelCount() {
        return channels.size();
    }
}
