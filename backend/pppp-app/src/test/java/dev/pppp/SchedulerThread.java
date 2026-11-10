package dev.pppp;

import java.util.Arrays;
import java.util.stream.Collectors;

/** For timeout messages: where the input scheduler thread is, so a hang in CI shows its cause. */
public final class SchedulerThread {
    private SchedulerThread() {}

    public static String stack() {
        return Thread.getAllStackTraces().entrySet().stream()
                .filter(e -> e.getKey().getName().equals("input-scheduler"))
                .map(e -> e.getKey().getState() + "\n  at " + Arrays.stream(e.getValue())
                        .map(String::valueOf).collect(Collectors.joining("\n  at ")))
                .collect(Collectors.joining("\n---\n", "input-scheduler: ", ""));
    }
}
