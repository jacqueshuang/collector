package com.j.soul.yc.batch;

import com.j.soul.yc.model.ApiResult;

/**
 * 批量发码中单个手机号的结果。
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

    /**
     * 已拿到业务响应时构造。{@code success} 仅当 {@code code==200} 为 true。
     */
    public static SmsTaskResult ok(String mobile, ApiResult apiResult, long elapsedMs) {
        boolean bizOk = apiResult != null && apiResult.getCode() != null && apiResult.getCode() == 200;
        return new SmsTaskResult(mobile, bizOk, apiResult, null, elapsedMs);
    }

    /** 抛异常或中断时的失败结果。 */
    public static SmsTaskResult fail(String mobile, String error, long elapsedMs) {
        return new SmsTaskResult(mobile, false, null, error, elapsedMs);
    }

    /** 手机号。 */
    public String mobile() {
        return mobile;
    }

    /** 是否业务成功（{@code code==200}）。 */
    public boolean success() {
        return success;
    }

    /** 业务响应；异常失败时可能为 null。 */
    public ApiResult apiResult() {
        return apiResult;
    }

    /** 异常信息；成功时为 null。 */
    public String error() {
        return error;
    }

    /** 单号耗时（毫秒）。 */
    public long elapsedMs() {
        return elapsedMs;
    }
}
