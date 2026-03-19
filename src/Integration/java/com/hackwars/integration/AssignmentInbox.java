package com.hackwars.integration;

import com.plink.dolphinnet.DataHandler;

import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class AssignmentInbox implements DataHandler {
    private final LinkedBlockingQueue<Object> events = new LinkedBlockingQueue<Object>();

    @Override
    public void addData(Object o) {
        if (o != null) {
            events.offer(o);
        }
    }

    @Override
    public void addFinishedAssignment(com.plink.dolphinnet.Assignment assignment) {
    }

    @Override
    public Object getData(int i) {
        return null;
    }

    @Override
    public void resetData() {
        events.clear();
    }

    public <T> T await(Class<T> type, Duration timeout) throws InterruptedException {
        return await(type, timeout, value -> true);
    }

    public <T> T await(Class<T> type, Duration timeout, Predicate<T> predicate) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            long remainingNanos = deadline - System.nanoTime();
            Object next = events.poll(Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos)), TimeUnit.MILLISECONDS);
            if (next == null || !type.isInstance(next)) {
                continue;
            }
            T cast = type.cast(next);
            if (predicate.test(cast)) {
                return cast;
            }
        }
        throw new AssertionError("Timed out waiting for " + type.getSimpleName());
    }
}
