package com.j.soul.yc.batch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate report for {@link YcSmsBatchExecutor#sendSms(java.util.Collection)}.
 */
public final class SmsBatchReport {
    private final List<SmsTaskResult> results;
    private final long elapsedMs;

    public SmsBatchReport(List<SmsTaskResult> results, long elapsedMs) {
        this.results = Collections.unmodifiableList(new ArrayList<>(results));
        this.elapsedMs = elapsedMs;
    }

    public List<SmsTaskResult> results() {
        return results;
    }

    public long elapsedMs() {
        return elapsedMs;
    }

    public int total() {
        return results.size();
    }

    public int successCount() {
        int n = 0;
        for (SmsTaskResult r : results) {
            if (r.success()) {
                n++;
            }
        }
        return n;
    }

    public int failureCount() {
        return total() - successCount();
    }

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
