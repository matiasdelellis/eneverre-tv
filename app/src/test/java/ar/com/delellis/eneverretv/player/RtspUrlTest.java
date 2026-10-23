package ar.com.delellis.eneverretv.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RtspUrlTest {

    @Test
    public void parsesTheExpiryFromTheLastPathSegment() {
        assertEquals(1893456000L,
                RtspUrl.tokenExpiresAt("rtsp://10.0.0.5:8554/front/1893456000.deadbeef"));
        assertEquals(1893456000L,
                RtspUrl.tokenExpiresAt("rtsp://relay.example/front/1893456000.a1b2c3"));
    }

    @Test
    public void rejectsEmptyAndMalformedUrls() {
        assertEquals(0L, RtspUrl.tokenExpiresAt(null));
        assertEquals(0L, RtspUrl.tokenExpiresAt(""));
        assertEquals(0L, RtspUrl.tokenExpiresAt("rtsp://10.0.0.5:8554/front"));
        assertEquals(0L, RtspUrl.tokenExpiresAt("rtsp://10.0.0.5:8554/front/not-a-number.sig"));
        assertEquals(0L, RtspUrl.tokenExpiresAt("rtsp://10.0.0.5:8554/front/1893456000."));
        assertEquals(0L, RtspUrl.tokenExpiresAt("/front/1893456000.sig"));
    }

    @Test
    public void usabilityHonoursTheSafetyMargin() {
        long now = System.currentTimeMillis() / 1000L;
        String fresh = "rtsp://host/cam/" + (now + 3600) + ".sig";
        String nearExpiry = "rtsp://host/cam/" + (now + 60) + ".sig";

        assertTrue(RtspUrl.isUsable(fresh, RtspUrl.REFRESH_MARGIN_MS));
        assertFalse(RtspUrl.isUsable(nearExpiry, RtspUrl.REFRESH_MARGIN_MS));
        assertTrue(RtspUrl.isUsable(nearExpiry, 0L));
        assertTrue(RtspUrl.shouldRefresh(nearExpiry, RtspUrl.REFRESH_MARGIN_MS));
        assertFalse(RtspUrl.shouldRefresh("", RtspUrl.REFRESH_MARGIN_MS));
    }

    @Test
    public void redactsOnlyTheTokenSegment() {
        String redacted = RtspUrl.redactUrl("rtsp://10.0.0.5:8554/front/1893456000.deadbeef");
        assertEquals("rtsp://10.0.0.5:8554/front/<redacted>", redacted);
        assertFalse(redacted.contains("deadbeef"));
    }

    @Test
    public void redactsUserInfoFromLegacyUrls() {
        String redacted = RtspUrl.redactUrl("rtsp://user:pass@10.0.0.5:8554/front/token");
        assertEquals("rtsp://<redacted>@10.0.0.5:8554/front/<redacted>", redacted);
        assertFalse(redacted.contains("pass"));
        assertFalse(redacted.contains("token"));
    }

    @Test
    public void redactsEveryUrlInsideAnErrorMessage() {
        String message = "RTSP URI changed to 'rtsp://host:8554/front/1893456000.deadbeef' "
                + "and then failed on rtsp://host:8554/back/1893456001.cafebabe";
        String safe = RtspUrl.redact(message);

        assertFalse(safe.contains("deadbeef"));
        assertFalse(safe.contains("cafebabe"));
        assertTrue(safe.contains("front/<redacted>"));
        assertTrue(safe.contains("back/<redacted>"));
    }

    @Test
    public void redactLeavesNonRtspTextUntouched() {
        assertEquals("plain failure", RtspUrl.redact("plain failure"));
        assertEquals(null, RtspUrl.redact(null));
    }
}
