package com.j.soul.yc.http;

public interface HttpTransport extends AutoCloseable {
    HttpResponse execute(HttpRequest request);

    @Override
    void close();
}
