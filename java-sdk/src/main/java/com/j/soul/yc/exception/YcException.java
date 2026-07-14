package com.j.soul.yc.exception;

/**
 * SDK 基础设施异常（网络、加密、滑块、解析等）。
 * <p>
 * 上游业务拒绝（HTTP 成功但 JSON {@code code!=200}）不会抛本异常，而是返回 {@code ApiResult}。
 */
public class YcException extends RuntimeException {
    private final YcStep step;

    public YcException(YcStep step, String message, Throwable cause) {
        super(message, cause);
        this.step = step;
    }

    public YcException(YcStep step, String message) {
        this(step, message, null);
    }

    /** 失败阶段，便于调用方区分来源。 */
    public YcStep getStep() {
        return step;
    }
}
