package ar.com.delellis.eneverretv.player;

import java.net.URI;

/**
 * Helpers for the RTSP relay URL minted by {@code GET /api/cameras}:
 *
 * <pre>rtsp://&lt;host&gt;:&lt;port&gt;/&lt;camera-id&gt;/&lt;exp&gt;.&lt;sig&gt;</pre>
 *
 * <p>The last path segment is a bearer credential. Nothing in this class logs
 * it; {@link #redact(String)} exists so error messages coming from the RTSP
 * library can be logged or shown without leaking it.</p>
 */
public final class RtspUrl {
    /** The server signs live relay URLs for 6 hours. */
    public static final long TOKEN_TTL_MS = 6L * 60L * 60L * 1000L;

    /** Refresh a URL this long before its expiry. */
    public static final long REFRESH_MARGIN_MS = 30L * 60L * 1000L;

    private RtspUrl() {
    }

    /**
     * Token expiry as Unix seconds, or 0 when the URL is empty or does not
     * carry an {@code <exp>.<sig>} last segment.
     */
    public static long tokenExpiresAt(String url) {
        if (url == null || url.isEmpty()) {
            return 0L;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (!"rtsp".equalsIgnoreCase(scheme) && !"rtsps".equalsIgnoreCase(scheme)) {
                return 0L;
            }
            String path = uri.getPath();
            if (path == null) {
                return 0L;
            }
            int slash = path.lastIndexOf('/');
            String last = slash >= 0 ? path.substring(slash + 1) : path;
            int dot = last.lastIndexOf('.');
            if (dot <= 0 || dot == last.length() - 1) {
                return 0L;
            }
            return Long.parseLong(last.substring(0, dot));
        } catch (Exception e) {
            return 0L;
        }
    }

    /** True when the URL has a token that is still outside the safety margin. */
    public static boolean isUsable(String url, long marginMs) {
        long expiresAt = tokenExpiresAt(url);
        if (expiresAt <= 0L) {
            return false;
        }
        long expiresAtMs = expiresAt * 1000L;
        return expiresAtMs > System.currentTimeMillis() + Math.max(0L, marginMs);
    }

    /** True when the URL is present and would need a refresh before use. */
    public static boolean shouldRefresh(String url, long marginMs) {
        return url != null && !url.isEmpty() && !isUsable(url, marginMs);
    }

    /** Redacts every RTSP URL embedded in free text (e.g. a library error). */
    public static String redact(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        StringBuilder out = new StringBuilder(message.length());
        int start = 0;
        while (start < message.length()) {
            int rtsp = message.indexOf("rtsp://", start);
            int rtsps = message.indexOf("rtsps://", start);
            int found;
            if (rtsp < 0) {
                found = rtsps;
            } else if (rtsps < 0) {
                found = rtsp;
            } else {
                found = Math.min(rtsp, rtsps);
            }
            if (found < 0) {
                out.append(message, start, message.length());
                break;
            }

            out.append(message, start, found);
            int end = found;
            while (end < message.length() && !isUrlDelimiter(message.charAt(end))) {
                end++;
            }
            out.append(redactUrl(message.substring(found, end)));
            start = end;
        }
        return out.toString();
    }

    /** Redacts the token segment (and any userinfo) of one RTSP URL. */
    public static String redactUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return url;
        }
        int authorityStart = scheme + 3;

        // Drop a query/fragment; the relay URL never has one, but a library
        // message might.
        int cut = url.length();
        int query = url.indexOf('?');
        if (query >= 0) {
            cut = Math.min(cut, query);
        }
        int fragment = url.indexOf('#');
        if (fragment >= 0) {
            cut = Math.min(cut, fragment);
        }
        String base = url.substring(0, cut);

        int firstSlash = base.indexOf('/', authorityStart);
        if (firstSlash < 0) {
            return base;
        }

        // Old servers embedded user:pass@ in the URL. Keep the host but hide
        // the credentials as well.
        int at = base.indexOf('@', authorityStart);
        if (at >= 0 && at < firstSlash) {
            base = base.substring(0, authorityStart) + "<redacted>" + base.substring(at);
            firstSlash = base.indexOf('/', authorityStart);
        }

        int lastSlash = base.lastIndexOf('/');
        if (lastSlash < 0) {
            return base;
        }
        return base.substring(0, lastSlash + 1) + "<redacted>";
    }

    private static boolean isUrlDelimiter(char c) {
        return Character.isWhitespace(c)
                || c == '\''
                || c == '"'
                || c == '<'
                || c == '>'
                || c == ')'
                || c == ']'
                || c == '}'
                || c == ',';
    }
}
