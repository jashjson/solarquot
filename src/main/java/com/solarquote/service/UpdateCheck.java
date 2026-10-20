package com.solarquote.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks GitHub for the latest published release and compares it with the installed version.
 *
 * Uses HttpURLConnection (java.base) so the packaged runtime needs no extra modules.
 */
public final class UpdateCheck {

    public static final String RELEASES_PAGE = "https://github.com/jashjson/solarquot/releases/latest";
    private static final String LATEST_API = "https://api.github.com/repos/jashjson/solarquot/releases/latest";
    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");

    private UpdateCheck() {}

    /** Version of the installed app, set by the jpackage launcher; null when run from an IDE or plain jar. */
    public static String currentVersion() {
        return System.getProperty("jpackage.app-version");
    }

    /** Returns the newer release's version (e.g. "1.2.0"), or null if up to date, not installed, or offline. */
    public static String newerVersion() {
        String current = currentVersion();
        if (current == null) return null;
        try {
            String latest = parseTag(fetch(LATEST_API));
            return latest != null && isNewer(latest, current) ? latest : null;
        } catch (IOException | RuntimeException e) {
            System.err.println("Update check failed: " + e);
            return null;
        }
    }

    private static String fetch(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "SolarQuote");
        try {
            // 404 means no release has been published yet
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    /** Pulls "1.2.0" out of the release JSON's "tag_name": "v1.2.0". */
    static String parseTag(String json) {
        if (json == null) return null;
        Matcher m = TAG.matcher(json);
        return m.find() ? m.group(1).replaceFirst("^[vV]", "") : null;
    }

    /** Numeric, part-by-part comparison: 1.10.0 is newer than 1.9.0, and 1.2 equals 1.2.0. */
    static boolean isNewer(String latest, String current) {
        String[] a = latest.split("\\.");
        String[] b = current.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? Integer.parseInt(a[i].trim()) : 0;
            int y = i < b.length ? Integer.parseInt(b[i].trim()) : 0;
            if (x != y) return x > y;
        }
        return false;
    }
}
