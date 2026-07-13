package com.j.soul.yc.http;

import com.j.soul.yc.config.YcClientConfig;
import com.j.soul.yc.exception.YcException;
import com.j.soul.yc.exception.YcStep;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class OkHttpTransport implements HttpTransport {
    private final OkHttpClient client;

    public OkHttpTransport(YcClientConfig config) {
        Objects.requireNonNull(config, "config");
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(config.connectTimeout())
                .readTimeout(config.readTimeout());
        if (hasProxy(config)) {
            builder.proxy(new Proxy(
                    Proxy.Type.HTTP,
                    new InetSocketAddress(config.proxyHost(), config.proxyPort())));
        }
        this.client = builder.build();
    }

    @Override
    public HttpResponse execute(HttpRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            RequestBody body = buildBody(request);
            Request.Builder rb = new Request.Builder()
                    .url(request.url())
                    .method(request.method(), body);
            request.headers().forEach(rb::header);
            try (Response resp = client.newCall(rb.build()).execute()) {
                byte[] bytes = resp.body() != null ? resp.body().bytes() : new byte[0];
                Map<String, String> headers = new LinkedHashMap<>();
                for (String name : resp.headers().names()) {
                    headers.put(name, resp.header(name));
                }
                return new HttpResponse(resp.code(), headers, bytes);
            }
        } catch (IOException e) {
            throw new YcException(YcStep.HTTP, e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    private static RequestBody buildBody(HttpRequest request) {
        MediaType mediaType = request.contentType() == null
                ? null
                : MediaType.parse(request.contentType());
        if (request.body() != null) {
            return RequestBody.create(request.body(), mediaType);
        }
        String method = request.method();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            return null;
        }
        return RequestBody.create(new byte[0], mediaType);
    }

    private static boolean hasProxy(YcClientConfig config) {
        String host = config.proxyHost();
        Integer port = config.proxyPort();
        return host != null && !host.isBlank() && port != null && port > 0;
    }
}
