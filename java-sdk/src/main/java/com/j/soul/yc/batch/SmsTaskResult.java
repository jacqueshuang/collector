package com.j.soul.yc.batch;

import com.j.soul.yc.model.ApiResult;

/**
 * Per-mobile outcome of a batch sendSms run.
 */
public final class SmsTaskResult {
    private final String mobile;
    private final boolean success;
    private final ApiResult apiResult;
    private final String error;
    private final long elapsedMs;

    public SmsTaskResult(String mobile, boolean success, ApiResult apiResult, String error, long elapsedMs) {
        this.mobile = mobile;
        this.success = success;
        this.apiResult = apiResult;
        this.error = error;
        this.elapsedMs = elapsedMs;
    }

    public static SmsTaskResult ok(String mobile, ApiResult apiResult, long elapsedMs) {
        boolean bizOk = apiResult != null && apiResult.getCode() != null && apiResult.getCode() == 200;
        return new SmsTaskResult(mobile, bizOk, apiResult, null, elapsedMs);
    }

    public static SmsTaskResult fail(String mobile, String error, long elapsedMs) {
        return new SmsTaskResult(mobile, false, null, error, elapsedMs);
    }

    public String mobile() {
        return mobile;
    }

    public boolean success() {
        return success;
    }

    public ApiResult apiResult() {
        return apiResult;
    }

    public String error() {
        return error;
    }

    public long elapsedMs() {
        return elapsedMs;
    }
}
