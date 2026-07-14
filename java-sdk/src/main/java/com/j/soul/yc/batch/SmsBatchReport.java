package com.j.soul.yc.batch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 批量发码汇总报告（顺序与输入非空号码一致）。
 */
public final class SmsBatchReport {
    private final List<SmsTaskResult> results;
    private final long elapsedMs;

    public SmsBatchReport(List<SmsTaskResult> results, long elapsedMs) {
        this.results = Collections.unmodifiableList(new ArrayList<>(results));
        this.elapsedMs = elapsedMs;
    }

    /** 全部单号结果（与输入顺序一致）。 */
    public List<SmsTaskResult> results() {
        return results;
    }

    /** 整批耗时（毫秒）。 */
    public long elapsedMs() {
        return elapsedMs;
    }

    /** 处理的号码总数。 */
    public int total() {
        return results.size();
    }

    /** 业务成功数（{@link SmsTaskResult#success()} 为 true）。 */
    public int successCount() {
        int n = 0;
        for (SmsTaskResult r : results) {
            if (r.success()) {
                n++;
            }
        }
        return n;
    }

    /** 失败数。 */
    public int failureCount() {
        return total() - successCount();
    }

    /** 仅失败项。 */
    public List<SmsTaskResult> failures() {
        List<SmsTaskResult> out = new ArrayList<>();
        for (SmsTaskResult r : results) {
            if (!r.success()) {
                out.add(r);
            }
        }
        return out;
    }
}
