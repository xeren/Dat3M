package com.dat3m.dartagnan.utils;

import java.util.function.Supplier;

public class Timer {

    private int currentTime = 0;
    private long currentStartTime;

    public Timer() {
    }

    private Timer(int time) {
        this.currentTime = time;
    }

    public void start() {
        currentStartTime = System.currentTimeMillis();
    }

    public void stop() {
        long stopTime = System.currentTimeMillis();
        currentTime += (int) (stopTime - currentStartTime);
    }

    public void reset() {
        currentTime = 0;
    }

    public int getTime() {
        return currentTime;
    }

    public String getFormattedTime() {
        return formatTime(getTime());
    }

    public static String formatTime(int time) {
        return Utils.toTimeString(time);
    }

    public void measure(Runnable runner) {
        start();
        runner.run();
        stop();
    }

    public <T> T measure(Supplier<T> runner) {
        start();
        T result = runner.get();
        stop();
        return result;
    }

    public static Timer add(Timer first, Timer second) {
        return new Timer(first.getTime() + second.getTime());
    }
}
