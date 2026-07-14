package com.j.soul.yc.batch;

import com.j.soul.yc.YcClient;
import com.j.soul.yc.exception.YcException;
import com.j.soul.yc.model.ApiResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于共享 {@link YcClient} 的批量短信发送器。
 * <p>
 * 并发受本类 {@code workers} 与客户端 {@code captchaConcurrency} 双重限制。
 * 真·数百路并发建议提高两者，或拆多机/多进程。
 * <p>
 * 500 路参考：
 * <ul>
 *   <li>单机：{@code captchaConcurrency=500} + {@code workers=500}（内存需求大，通常不现实）</li>
 *   <li>集群：10 实例 × {@code captchaConcurrency=50} = 500 路滑块</li>
 * </ul>
 */
public final class YcSmsBatchExecutor implements AutoCloseable {

    private final YcClient client;
    private final ExecutorService pool;
    private final boolean ownPool;
    private final int workers;
    private final int maxBatchSize;

    private YcSmsBatchExecutor(
            YcClient client,
            ExecutorService pool,
            boolean ownPool,
            int workers,
            int maxBatchSize) {
        this.client = Objects.requireNonNull(client, "client");
        this.pool = Objects.requireNonNull(pool, "pool");
        this.ownPool = ownPool;
        this.workers = workers;
        this.maxBatchSize = maxBatchSize;
    }

    /** 创建批量发送器构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 批量发送「重置手机号」短信（pathType=11，含检查手机号）。
     * <p>
     * {@link SmsBatchReport#results()} 顺序与输入非空号码顺序一致。
     *
     * @param mobiles 手机号集合；null/空白会被跳过
     * @return 批量报告
     * @throws YcException 非空号码数 &gt; {@link #maxBatchSize()} 时立即失败（不做 captcha/HTTP）
     */
    public SmsBatchReport sendSms(Collection<String> mobiles) {
        return sendAll(mobiles, false);
    }

    /**
     * 批量发送「登录页」短信（pathType=5，不检查手机号）。
     *
     * @param mobiles 手机号集合；null/空白会被跳过
     * @return 批量报告
     * @throws YcException 非空号码数 &gt; {@link #maxBatchSize()} 时立即失败
     */
    public SmsBatchReport sendLoginSms(Collection<String> mobiles) {
        return sendAll(mobiles, true);
    }

    private SmsBatchReport sendAll(Collection<String> mobiles, boolean login) {
        Objects.requireNonNull(mobiles, "mobiles");
        long t0 = System.nanoTime();
        List<String> list = new ArrayList<>(mobiles.size());
        for (String m : mobiles) {
            if (m != null && !m.isBlank()) {
                list.add(m.trim());
            }
        }
        if (list.isEmpty()) {
            return new SmsBatchReport(List.of(), 0L);
        }
        if (list.size() > maxBatchSize) {
            throw new YcException(
                    com.j.soul.yc.exception.YcStep.SMS,
                    "batch size " + list.size() + " exceeds maxBatchSize " + maxBatchSize);
        }

        List<Callable<SmsTaskResult>> tasks = new ArrayList<>(list.size());
        for (String mobile : list) {
            tasks.add(() -> runOne(mobile, login));
        }

        List<SmsTaskResult> results = new ArrayList<>(list.size());
        try {
            List<Future<SmsTaskResult>> futures = pool.invokeAll(tasks);
            for (int i = 0; i < futures.size(); i++) {
                String mobile = list.get(i);
                Future<SmsTaskResult> f = futures.get(i);
                try {
                    results.add(f.get());
                } catch (ExecutionException e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    results.add(SmsTaskResult.fail(mobile, c.getMessage(), 0L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    results.add(SmsTaskResult.fail(mobile, "interrupted", 0L));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new YcException(
                    com.j.soul.yc.exception.YcStep.SMS,
                    "batch sendSms interrupted",
                    e);
        }
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        return new SmsBatchReport(results, elapsed);
    }

    private SmsTaskResult runOne(String mobile, boolean login) {
        long t0 = System.nanoTime();
        try {
            ApiResult r = login ? client.sendLoginSms(mobile) : client.sendSms(mobile);
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            return SmsTaskResult.ok(mobile, r, ms);
        } catch (Exception e) {
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            return SmsTaskResult.fail(mobile, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), ms);
        }
    }

    /** 工作线程数。 */
    public int workers() {
        return workers;
    }

    /** 单次批量允许的非空手机号上限。 */
    public int maxBatchSize() {
        return maxBatchSize;
    }

    /** 关闭自有线程池（注入外部 executor 时不关闭）。 */
    @Override
    public void close() {
        if (ownPool) {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public static final class Builder {
        private YcClient client;
        private int workers = Math.max(4, Math.min(64, Runtime.getRuntime().availableProcessors() * 4));
        private int maxBatchSize = 1000;
        private ExecutorService executor;

        public Builder client(YcClient client) {
            this.client = Objects.requireNonNull(client, "client");
            return this;
        }

        /**
         * Parallel worker threads submitting {@code sendSms}.
         * Should be &gt;= {@code captchaConcurrency} to keep captcha slots busy;
         * excess workers only queue on the captcha semaphore.
         */
        public Builder workers(int workers) {
            if (workers < 1) {
                throw new IllegalArgumentException("workers must be >= 1");
            }
            this.workers = workers;
            return this;
        }

        /**
         * Reject {@link #sendSms(Collection)} immediately when non-blank mobile count exceeds this.
         * Default {@code 1000}.
         */
        public Builder maxBatchSize(int maxBatchSize) {
            if (maxBatchSize < 1) {
                throw new IllegalArgumentException("maxBatchSize must be >= 1");
            }
            this.maxBatchSize = maxBatchSize;
            return this;
        }

        /** Optional external pool; caller owns lifecycle if set. */
        public Builder executor(ExecutorService executor) {
            this.executor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        public YcSmsBatchExecutor build() {
            Objects.requireNonNull(client, "client");
            if (executor != null) {
                return new YcSmsBatchExecutor(client, executor, false, workers, maxBatchSize);
            }
            AtomicInteger seq = new AtomicInteger();
            ThreadFactory tf = r -> {
                Thread t = new Thread(r, "yc-sms-batch-" + seq.incrementAndGet());
                t.setDaemon(true);
                return t;
            };
            ExecutorService pool = Executors.newFixedThreadPool(workers, tf);
            return new YcSmsBatchExecutor(client, pool, true, workers, maxBatchSize);
        }
    }
}
