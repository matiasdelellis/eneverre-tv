package ar.com.delellis.eneverretv.api.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

public class CameraJsonTest {
    private static final Gson GSON = new Gson();

    @Test
    public void parsesTheCurrentCameraShape() {
        String json = "{"
                + "\"id\":\"front\","
                + "\"name\":\"Front door\","
                + "\"comment\":\"\","
                + "\"location\":\"House\","
                + "\"rtsp\":\"rtsp://10.0.0.5:8554/front/1893456000.deadbeef\","
                + "\"live_mse\":\"/api/camera/front/live/stream\","
                + "\"width\":1920,"
                + "\"height\":1080,"
                + "\"privacy\":false,"
                + "\"schedule_off\":false,"
                + "\"enabled\":true,"
                + "\"schedule_id\":\"night\","
                + "\"capabilities\":{"
                + "  \"privacy\":true,"
                + "  \"thumbnail\":true,"
                + "  \"playback\":true,"
                + "  \"ptz\":true,"
                + "  \"talk\":true,"
                + "  \"settings\":false"
                + "},"
                + "\"ptz\":{"
                + "  \"fov_h\":113,"
                + "  \"fov_v\":63.5,"
                + "  \"pan_range\":360,"
                + "  \"tilt_range\":180"
                + "}"
                + "}";

        Camera camera = GSON.fromJson(json, Camera.class);

        assertEquals("front", camera.getId());
        assertEquals("rtsp://10.0.0.5:8554/front/1893456000.deadbeef", camera.getRtsp());
        assertTrue(camera.getPtz());
        assertTrue(camera.hasPlayback());
        assertTrue(camera.hasThumbnail());
        assertTrue(camera.hasTalk());
        assertTrue(camera.isEnabled());
        assertFalse(camera.isScheduleOff());
        assertEquals("night", camera.getScheduleId());
        assertNotNull(camera.getPtzMetadata());
        assertEquals(113f, camera.getPtzMetadata().getFovH(), 0.001f);
    }

    @Test
    public void toleratesOlderServersWithoutCapabilitiesOrEnabled() {
        String json = "{"
                + "\"id\":\"back\","
                + "\"name\":\"Back yard\","
                + "\"location\":\"House\","
                + "\"rtsp\":\"\","
                + "\"width\":16,"
                + "\"height\":9"
                + "}";

        Camera camera = GSON.fromJson(json, Camera.class);

        assertFalse(camera.getPtz());
        assertFalse(camera.hasPlayback());
        assertTrue(camera.isEnabled());
        assertFalse(camera.getPrivacy());
        assertEquals("", camera.getRtsp());
    }
}
