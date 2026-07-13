package com.j.soul.yc.http;

import com.j.soul.yc.config.YcClientConfig;
import com.j.soul.yc.exception.YcException;
import com.j.soul.yc.exception.YcStep;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Phase-1 curl4j backend: system {@code curl} CLI via {@link ProcessBuilder}.
 * Requires {@code curl} on PATH. Not a Maven libcurl binding.
 */
public final class Curl4jTransport implements HttpTransport {
    private final YcClientConfig config;

    public Curl4jTransport(YcClientConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public HttpResponse execute(HttpRequest request) {
        Objects.requireNonNull(request, "request");
        Path bodyFile = null;
        try {
            bodyFile = Files.createTempFile("yc-curl-body-", ".bin");
            List<String> cmd = buildCommand(request, bodyFile);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process process = pb.start();
            writeRequestBody(process, request.body());
            String statusText = readUtf8(process.getInputStream()).trim();
            String stderr = readUtf8(process.getErrorStream());
            int exit = process.waitFor();
            if (exit != 0) {
                throw new YcException(
                        YcStep.HTTP,
                        "curl exit " + exit + (stderr.isBlank() ? "" : ": " + stderr.trim()));
            }
            int status;
            try {
                status = Integer.parseInt(statusText);
            } catch (NumberFormatException e) {
                throw new YcException(YcStep.HTTP, "invalid curl http_code: " + statusText, e);
            }
            return new HttpResponse(status, Map.of(), Files.readAllBytes(bodyFile));
        } catch (YcException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new YcException(YcStep.HTTP, "curl interrupted", e);
        } catch (Exception e) {
            throw new YcException(YcStep.HTTP, e.getMessage(), e);
        } finally {
            if (bodyFile != null) {
                try {
                    Files.deleteIfExists(bodyFile);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
        }
    }

    @Override
    public void close() {
        // no process pool to release
    }

    private List<String> buildCommand(HttpRequest request, Path bodyFile) {
        List<String> cmd = new ArrayList<>();
        cmd.add("curl");
        cmd.add("-sS");
        cmd.add("-X");
        cmd.add(request.method());
        cmd.add("--connect-timeout");
        cmd.add(String.valueOf(Math.max(1L, config.connectTimeout().toSeconds())));
        long maxTime = Math.max(1L, config.connectTimeout().toSeconds() + config.readTimeout().toSeconds());
        cmd.add("--max-time");
        cmd.add(String.valueOf(maxTime));
        if (hasProxy(config)) {
            cmd.add("-x");
            cmd.add(config.proxyHost() + ":" + config.proxyPort());
        }
        boolean hasContentType = false;
        for (Map.Entry<String, String> e : request.headers().entrySet()) {
            if ("content-type".equals(e.getKey().toLowerCase(Locale.ROOT))) {
                hasContentType = true;
            }
            cmd.add("-H");
            cmd.add(e.getKey() + ": " + e.getValue());
        }
        if (!hasContentType && request.contentType() != null && !request.contentType().isBlank()) {
            cmd.add("-H");
            cmd.add("Content-Type: " + request.contentType());
        }
        if (request.body() != null) {
            cmd.add("--data-binary");
            cmd.add("@-");
        }
        cmd.add("-o");
        cmd.add(bodyFile.toAbsolutePath().toString());
        cmd.add("-w");
        cmd.add("%{http_code}");
        cmd.add(request.url());
        return cmd;
    }

    private static void writeRequestBody(Process process, byte[] body) throws IOException {
        try (OutputStream os = process.getOutputStream()) {
            if (body != null) {
                os.write(body);
            }
        }
    }

    private static String readUtf8(InputStream in) throws IOException {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean hasProxy(YcClientConfig config) {
        String host = config.proxyHost();
        Integer port = config.proxyPort();
        return host != null && !host.isBlank() && port != null && port > 0;
    }
}
