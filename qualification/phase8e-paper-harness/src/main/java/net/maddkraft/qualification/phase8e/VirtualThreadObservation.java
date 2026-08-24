package net.maddkraft.qualification.phase8e;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingStream;

/** Qualification-only JFR observation for production virtual-thread families. */
final class VirtualThreadObservation implements AutoCloseable {
    private static final String OPERATION_PREFIX = "maddprestige-operation-";
    private static final String PLACEHOLDER_PREFIX = "maddprestige-placeholder-publisher-";
    private static final String EVENT_THREAD = "eventThread";

    private final RecordingStream recording = new RecordingStream();
    private final Map<Long, Family> active = new ConcurrentHashMap<>();
    private final FamilyCounters operation = new FamilyCounters();
    private final FamilyCounters placeholder = new FamilyCounters();
    private final AtomicLong submitFailures = new AtomicLong();
    private final AtomicLong pinned = new AtomicLong();

    VirtualThreadObservation() {
        recording.enable("jdk.VirtualThreadStart").withoutStackTrace();
        recording.enable("jdk.VirtualThreadEnd").withoutStackTrace();
        recording.enable("jdk.VirtualThreadPinned").withThreshold(Duration.ZERO);
        recording.enable("jdk.VirtualThreadSubmitFailed").withoutStackTrace();
        recording.setOrdered(true);
        recording.onEvent("jdk.VirtualThreadStart", this::started);
        recording.onEvent("jdk.VirtualThreadEnd", this::ended);
        recording.onEvent("jdk.VirtualThreadPinned", this::pinned);
        recording.onEvent("jdk.VirtualThreadSubmitFailed", ignored -> submitFailures.incrementAndGet());
        recording.startAsync();
    }

    Snapshot snapshot() {
        return new Snapshot(operation.snapshot(), placeholder.snapshot(), submitFailures.get(), pinned.get());
    }

    @Override
    public void close() {
        recording.close();
    }

    private void started(RecordedEvent event) {
        RecordedThread thread = event.getThread(EVENT_THREAD);
        Family family = family(thread == null ? null : thread.getJavaName());
        if (family == null) return;
        long id = thread.getJavaThreadId();
        if (active.putIfAbsent(id, family) == null) counters(family).started();
    }

    private void ended(RecordedEvent event) {
        RecordedThread thread = event.getThread(EVENT_THREAD);
        if (thread == null) return;
        Family family = active.remove(thread.getJavaThreadId());
        if (family != null) counters(family).ended();
    }

    private void pinned(RecordedEvent event) {
        RecordedThread thread = event.getThread(EVENT_THREAD);
        if (family(thread == null ? null : thread.getJavaName()) != null) pinned.incrementAndGet();
    }

    private FamilyCounters counters(Family family) {
        return family == Family.OPERATION ? operation : placeholder;
    }

    private static Family family(String name) {
        if (name == null) return null;
        if (name.startsWith(OPERATION_PREFIX)) return Family.OPERATION;
        if (name.startsWith(PLACEHOLDER_PREFIX)) return Family.PLACEHOLDER;
        return null;
    }

    record Snapshot(Counters operation, Counters placeholder, long submitFailures, long pinned) {
        String logFields() {
            return " operationVtActive=" + operation.active()
                    + " operationVtHighWater=" + operation.highWater()
                    + " operationVtStarted=" + operation.started()
                    + " operationVtEnded=" + operation.ended()
                    + " placeholderVtActive=" + placeholder.active()
                    + " placeholderVtHighWater=" + placeholder.highWater()
                    + " placeholderVtStarted=" + placeholder.started()
                    + " placeholderVtEnded=" + placeholder.ended()
                    + " vtSubmitFailures=" + submitFailures + " vtPinned=" + pinned;
        }

        boolean converged() {
            return operation.active() == 0 && operation.started() == operation.ended()
                    && placeholder.active() == 0 && placeholder.started() == placeholder.ended();
        }

        @Override
        public String toString() {
            return logFields().trim().toLowerCase(Locale.ROOT);
        }
    }

    record Counters(int active, int highWater, long started, long ended) {
    }

    private static final class FamilyCounters {
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger highWater = new AtomicInteger();
        private final AtomicLong started = new AtomicLong();
        private final AtomicLong ended = new AtomicLong();

        private void started() {
            started.incrementAndGet();
            int current = active.incrementAndGet();
            highWater.accumulateAndGet(current, Math::max);
        }

        private void ended() {
            ended.incrementAndGet();
            active.decrementAndGet();
        }

        private Counters snapshot() {
            return new Counters(active.get(), highWater.get(), started.get(), ended.get());
        }
    }

    private enum Family {
        OPERATION,
        PLACEHOLDER
    }
}
