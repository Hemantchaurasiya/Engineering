# Cache Eviction Policies
- A cache eviction policy/ algorithm is a way of deciding which data to evict from the cache storage to ensure the memory is not OOM . 

## There are different Eviction Policies
- LRU (Least Recently Used)
- LFU (Least Frequently Used)
- FIFO (First In, First Out)
- Random Replacement

LRU :
Discards the least recently accessed data.
Efficient for time-sensitive applications.
Example: Web browsers evict least used tabs.
LFU :
Discards data that is accessed the least times.
More memory-intensive than LRU as it requires tracking usage frequency.
Example: Leaderboard Cache.
FIFO :
Evicts the oldest data first, regardless of access frequency
But not ideal as the old data can be frequently used
Example: Simple queue-based caching.
Random Replacement :
Evicts random entries.
Example: Not sure about usecase tho :).