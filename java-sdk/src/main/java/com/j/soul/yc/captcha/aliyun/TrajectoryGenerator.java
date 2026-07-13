package com.j.soul.yc.captcha.aliyun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.j.soul.yc.config.YcClientConfig;
import com.j.soul.yc.exception.YcException;
import com.j.soul.yc.exception.YcStep;
import com.j.soul.yc.http.HttpTransport;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.zip.Deflater;

/**
 * Builds VerifyCaptchaV3 trajectory {@code data}.
 * Primary path: GraalJS full slider solve (fresh per session, no static sample default).
 * Secondary: synthetic TrackList plaintext helpers for tests / offline inspection.
 */
public final class TrajectoryGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RND = new SecureRandom();

    private final CaptchaJsRuntime sharedRuntime;
    private final YcClientConfig config;
    private final boolean closeRuntime;
    private final int startX;
    private final int startY;
    private final int endX;
    private final int endY;
    private final int screenW;
    private final int screenH;
    private final int innerW;
    private final int innerH;

    public TrajectoryGenerator() {
        this(null, null);
    }

    public TrajectoryGenerator(YcClientConfig config) {
        this(config, null);
    }

    public TrajectoryGenerator(CaptchaJsRuntime sharedRuntime) {
        this(null, sharedRuntime);
    }

    public TrajectoryGenerator(YcClientConfig config, CaptchaJsRuntime sharedRuntime) {
        this(config, sharedRuntime, 120, 120, 440, 120, 1728, 1117, 1728, 997);
    }

    public TrajectoryGenerator(YcClientConfig config,
                               CaptchaJsRuntime sharedRuntime,
                               int startX, int startY, int endX, int endY,
                               int screenW, int screenH, int innerW, int innerH) {
        this.config = config;
        this.sharedRuntime = sharedRuntime;
        this.closeRuntime = sharedRuntime == null;
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.screenW = screenW;
        this.screenH = screenH;
        this.innerW = innerW;
        this.innerH = innerH;
    }

    public String generate(AliyunSession session) {
        if (session == null) {
            throw new YcException(YcStep.CAPTCHA, "AliyunSession required");
        }
        try {
            CaptchaSolveResult solved = solve(null);
            session.setTrajectoryData(solved.data());
            if (session.deviceToken() == null && solved.deviceToken() != null) {
                session.setDeviceToken(solved.deviceToken());
            }
            if (session.securityToken() == null && solved.securityToken() != null) {
                session.setSecurityToken(solved.securityToken());
            }
            return solved.data();
        } catch (YcException e) {
            throw e;
        } catch (Exception e) {
            throw new YcException(YcStep.CAPTCHA, "trajectory generate failed: " + e.getMessage(), e);
        }
    }

    /**
     * Full solve returning data + deviceToken (+ optional securityToken).
     */
    public CaptchaSolveResult solve(HttpTransport transport) {
        CaptchaJsRuntime runtime = null;
        try {
            if (sharedRuntime != null) {
                runtime = sharedRuntime;
            } else {
                YcClientConfig cfg = config != null ? config : YcClientConfig.builder().build();
                runtime = CaptchaJsRuntime.open(cfg, transport);
            }
            return runtime.solveSlider();
        } catch (YcException e) {
            throw e;
        } catch (Exception e) {
            throw new YcException(YcStep.CAPTCHA, "trajectory solve failed: " + e.getMessage(), e);
        } finally {
            if (closeRuntime && runtime != null) {
                runtime.close();
            }
        }
    }

    /** Pure: zlib(UTF-8 bytes) → standard base64 (JSVMP input). */
    public static String deflateToBase64(byte[] utf8) {
        Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, false);
        def.setInput(utf8);
        def.finish();
        byte[] buf = new byte[4096];
        ByteArrayOutputStream baos = new ByteArrayOutputStream(utf8.length);
        while (!def.finished()) {
            int n = def.deflate(buf);
            if (n > 0) {
                baos.write(buf, 0, n);
            }
        }
        def.end();
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    /** Build TrackList plaintext as observed offline: {@code <32hex>}{@code {json}}. */
    String buildPlaintext() {
        long trackStart = System.currentTimeMillis();
        int steps = 70 + RND.nextInt(20);
        List<int[]> path = synthesizePath(steps);

        int downRel = 5000 + RND.nextInt(80);
        int firstMove = downRel + 90 + RND.nextInt(40);
        int lastMoveRel = firstMove;
        StringBuilder mp = new StringBuilder();
        StringBuilder mm = new StringBuilder();
        mm.append(startX).append(',').append(startY).append(',').append(downRel).append(",0");
        for (int i = 0; i < path.size(); i++) {
            int[] p = path.get(i);
            int t = firstMove + p[2];
            lastMoveRel = t;
            if (i > 0) {
                mp.append('|');
            }
            mp.append(p[0]).append(',').append(p[1]).append(',').append(t).append(",0");
            mm.append('|').append(p[0]).append(',').append(p[1]).append(',').append(t).append(",0");
        }
        int upRel = lastMoveRel + 10 + RND.nextInt(30);
        mm.append('|').append(endX + 60 + RND.nextInt(20)).append(',')
                .append(startY + 10 + RND.nextInt(20)).append(',')
                .append(upRel + 15).append(",1");

        String mc = startX + "," + startY + "," + downRel + ", ,0";
        String mu = endX + "," + endY + "," + upRel + ", ,0";
        String si = screenW + "," + innerW + "," + innerH + ",-1,-1,768," + screenH
                + "," + (100.0 * innerH / screenH) + ",1024";

        long verifyTime = trackStart + upRel + 80 + RND.nextInt(200);
        String arg = randomArg();
        String prefix = randomHex32();

        try {
            ObjectNode track = MAPPER.createObjectNode();
            track.put("mc", mc);
            track.put("tc", "");
            track.put("mu", mu);
            track.put("te", "");
            track.put("mp", mp.toString());
            track.put("tmv", "");
            track.put("mm", mm.toString());
            track.put("ks", "");
            track.put("fi", "");
            track.put("startTime", trackStart);
            track.put("si", si);

            ObjectNode root = MAPPER.createObjectNode();
            root.set("TrackList", track);
            root.put("TrackStartTime", trackStart);
            root.put("VerifyTime", verifyTime);
            root.put("arg", arg);
            String json = MAPPER.writeValueAsString(root);
            return prefix + json;
        } catch (Exception e) {
            throw new YcException(YcStep.CAPTCHA, "TrackList json failed", e);
        }
    }

    private List<int[]> synthesizePath(int steps) {
        List<int[]> pts = new ArrayList<>(steps);
        int distance = endX - startX;
        int t = 0;
        for (int i = 1; i <= steps; i++) {
            double p = (double) i / steps;
            double eased = p < 0.5 ? 2 * p * p : 1 - Math.pow(-2 * p + 2, 2) / 2;
            int x = (int) Math.round(startX + distance * eased + (RND.nextDouble() - 0.5) * 2);
            int y = (int) Math.round(startY + (RND.nextDouble() - 0.5) * 3 + Math.sin(p * Math.PI * 2) * 2);
            t += 10 + RND.nextInt(6);
            if (i == steps) {
                x = endX;
                y = endY;
            }
            pts.add(new int[]{x, y, t});
        }
        return pts;
    }

    private static String randomHex32() {
        byte[] b = new byte[16];
        RND.nextBytes(b);
        StringBuilder sb = new StringBuilder(32);
        for (byte v : b) {
            sb.append(String.format("%02x", v));
        }
        return sb.toString();
    }

    private static String randomArg() {
        byte[] b = new byte[10];
        RND.nextBytes(b);
        b[8] = 0x7c;
        return Base64.getEncoder().encodeToString(b);
    }
}
