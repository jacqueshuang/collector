package com.j.soul.yc.captcha.aliyun;

import com.j.soul.yc.http.HttpRequest;
import com.j.soul.yc.http.HttpResponse;
import com.j.soul.yc.http.HttpTransport;
import org.graalvm.polyglot.Value;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;

/**
 * Java-backed XHR/fetch for GraalJS captcha scripts.
 * HTTP runs on a worker pool; JS callbacks are queued and must be drained on the
 * GraalJS Context owner thread ({@link #drainCallbacks()}).
 */
public final class JsHttpBridge {

    private final HttpTransport transport;
    private final ExecutorService executor;
    private final String userAgent;
    private final String origin;
    private final String referer;
    private final ConcurrentLinkedQueue<Pending> pending = new ConcurrentLinkedQueue<>();

    public JsHttpBridge(HttpTransport transport, ExecutorService executor,
                        String userAgent, String origin, String referer) {
        this.transport = transport;
        this.executor = executor;
        this.userAgent = userAgent;
        this.origin = origin;
        this.referer = referer;
    }

    /**
     * @param method   HTTP method
     * @param url      absolute URL
     * @param body     request body or null/empty
     * @param headers  plain JS object of request headers (may be null)
     * @param callback function(status:int, responseText:string, headersJson:string)
     */
    public void request(String method, String url, String body, Value headers, Value callback) {
        Map<String, String> hdr = new LinkedHashMap<>();
        if (headers != null && headers.hasMembers()) {
            for (String key : headers.getMemberKeys()) {
                Value v = headers.getMember(key);
                if (v != null && !v.isNull()) {
                    hdr.put(key, v.asString());
                }
            }
        }
        hdr.putIfAbsent("User-Agent", userAgent);
        hdr.putIfAbsent("Origin", origin);
        hdr.putIfAbsent("Referer", referer);

        final String m = method == null ? "GET" : method.toUpperCase();
        final String u = url;
        final byte[] payload = (body == null || body.isEmpty())
                ? null
                : body.getBytes(StandardCharsets.UTF_8);
        final String contentType = hdr.getOrDefault("Content-Type",
                hdr.getOrDefault("content-type", "application/x-www-form-urlencoded"));
        final Value cb = callback;

        executor.execute(() -> {
            try {
                HttpRequest req = new HttpRequest(m, u, hdr, payload, contentType);
                HttpResponse resp = transport.execute(req);
                String text = new String(resp.body(), StandardCharsets.UTF_8);
                String headersJson = toHeadersJson(resp.headers());
                pending.offer(new Pending(cb, resp.status(), text, headersJson));
            } catch (Exception e) {
                pending.offer(new Pending(cb, 0, "", "{}"));
            }
        });
    }

    /**
     * Invoke queued JS callbacks on the Context owner thread.
     * Must not be called from HTTP worker threads.
     *
     * @return number of callbacks executed
     */
    public int drainCallbacks() {
        int n = 0;
        Pending p;
        while ((p = pending.poll()) != null) {
            if (p.callback != null && p.callback.canExecute()) {
                try {
                    p.callback.execute(p.status, p.text, p.headersJson);
                } catch (Exception ignored) {
                    // context may already be closed
                }
            }
            n++;
        }
        return n;
    }

    /** Test/support: pending completions not yet drained. */
    int pendingCount() {
        return pending.size();
    }

    private static String toHeadersJson(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder(64);
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey().toLowerCase())).append('"')
                    .append(':')
                    .append('"').append(escape(e.getValue())).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static final class Pending {
        final Value callback;
        final int status;
        final String text;
        final String headersJson;

        Pending(Value callback, int status, String text, String headersJson) {
            this.callback = callback;
            this.status = status;
            this.text = text == null ? "" : text;
            this.headersJson = headersJson == null ? "{}" : headersJson;
        }
    }
}
