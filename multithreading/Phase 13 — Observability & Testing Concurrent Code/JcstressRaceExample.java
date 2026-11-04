package com.orderengine.phase13;

/*
 * EXAMPLE jcstress TEST — NOT RUNNABLE WITH PLAIN javac/java.
 *
 * jcstress (the Java Concurrency Stress testing framework, part of the
 * OpenJDK Code Tools project) is a SEPARATE artifact from the JDK
 * itself — it needs its own Maven/Gradle project setup (an archetype:
 * `mvn archetype:generate -DinteractiveMode=false
 *   -DarchetypeGroupId=org.openjdk.jcstress -DarchetypeArtifactId=jcstress-java-test-archetype`)
 * and a build step that packages an executable "shaded" jar jcstress
 * runs against. This file is commented out as a demonstration of what a
 * real jcstress test for a bug class covered in this project would look
 * like — it's included for the code shape and the explanation, not as
 * something the run commands in this phase's other classes can execute.
 *
 * WHY jcstress EXISTS, AND WHY ORDINARY UNIT TESTS CAN'T DO ITS JOB:
 *
 * Every "broken, then fixed" demo throughout this project (Phase 1's
 * VisibilityBugDemo, Phase 3's AbaProblemDemo, Phase 5's
 * CheckThenActRaceDemo, Phase 8's ParallelStreamPitfallsDemo's racy
 * accumulation) reproduces its race through EITHER: (a) sheer scale —
 * running enough iterations/threads that the bad interleaving shows up
 * often enough to observe, which is exactly what makes these bugs
 * SO DANGEROUS in production (they can pass thousands of test runs and
 * still occur once in a blue moon at real scale), or (b) deterministic
 * forced coordination via CountDownLatch, which only works when you
 * already know EXACTLY which interleaving to force — useless for
 * discovering a race you don't already suspect exists.
 *
 * jcstress solves this differently: it runs a given test method many
 * millions of times, using JVM-internal instrumentation and varying
 * actual OS-level thread scheduling/JIT compilation states across runs,
 * SPECIFICALLY trying to explore as many different real interleavings as
 * possible — far more thoroughly than relying on natural OS scheduling
 * variance the way this project's scale-based demos do. It then
 * categorizes every OBSERVED outcome against a declared set of
 * ACCEPTABLE and FORBIDDEN outcomes, and reports a hard, unambiguous
 * FAILURE if a forbidden outcome is ever observed even once across
 * however many runs it performed — turning "this bug is timing-
 * dependent and might not show up in your test run" into "this
 * framework will actively hunt for exactly the interleaving that
 * reveals it."
 *
 * Below: the SHAPE a jcstress test for Phase 1's OrderIngestionBuffer
 * visibility bug would take (real annotations, real API surface — this
 * is what a genuine jcstress test file looks like, just not wired into
 * a buildable module in this project).
 *
 *
 * @JCStressTest
 * @Outcome(id = "true", expect = Expect.ACCEPTABLE, desc = "drain thread observed the shutdown signal")
 * @Outcome(id = "false", expect = Expect.FORBIDDEN, desc = "drain thread never observed shutdown — visibility bug")
 * @State
 * public class OrderIngestionBufferVisibilityTest {
 *
 *     // Deliberately the BROKEN, non-volatile version from Phase 1.
 *     boolean running = true;
 *
 *     @Actor
 *     public void shutdownActor() {
 *         running = false;
 *     }
 *
 *     @Actor
 *     public void drainActor(BooleanResult1 result) {
 *         // In a real test this would need to actually spin/observe
 *         // over some bounded window rather than a single read, since
 *         // jcstress controls interleaving timing, not wall-clock waits
 *         // — omitted here since this file is illustrative, not a
 *         // complete, runnable test.
 *         result.r1 = !running;
 *     }
 * }
 *
 *
 * @Actor methods are jcstress's way of declaring "this method runs
 * concurrently with the other @Actor methods in this @State class, on
 * a real separate thread, for every one of the millions of forced
 * interleavings jcstress explores." @Outcome declares what result
 * combinations are ACCEPTABLE vs FORBIDDEN — jcstress fails the test
 * the moment it ever observes a FORBIDDEN outcome, with a full report
 * of exactly how often each outcome occurred across all explored
 * interleavings, which is itself valuable diagnostic data (a bug that
 * occurs 1-in-10-million interleavings vs 1-in-10 tells you very
 * different things about real-world risk).
 *
 * To actually run a real jcstress project: generate it via the Maven
 * archetype above, write real tests using this shape, then:
 *   mvn clean package
 *   java -jar target/jcstress.jar
 */
public final class JcstressRaceExample {
    private JcstressRaceExample() {}
}
