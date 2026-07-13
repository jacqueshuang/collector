package com.j.soul.yc.exception;

public class YcException extends RuntimeException {
    private final YcStep step;

    public YcException(YcStep step, String message, Throwable cause) {
        super(message, cause);
        this.step = step;
    }

    public YcException(YcStep step, String message) {
        this(step, message, null);
    }

    public YcStep getStep() {
        return step;
    }
}
