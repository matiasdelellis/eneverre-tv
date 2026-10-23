package ar.com.delellis.eneverretv.api.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

public class Camera implements Serializable {
    @Expose
    @SerializedName("id")
    private String id;

    @Expose
    @SerializedName("name")
    private String name;

    @Expose
    @SerializedName("location")
    private String location;

    @Expose
    @SerializedName("comment")
    private String comment;

    // Embedded RTSP relay URL with a short-lived per-camera signed path token:
    // rtsp://<host>:<port>/<camera-id>/<exp>.<sig>. Opaque to the client.
    @Expose
    @SerializedName("rtsp")
    private String rtsp;

    // Same-origin MSE path used by the web UI. The TV plays RTSP, so this is
    // only kept for completeness / future use.
    @Expose
    @SerializedName("live_mse")
    private String liveMse;

    @Expose
    @SerializedName("width")
    private int width;

    @Expose
    @SerializedName("height")
    private int height;

    // Current privacy state (mutable at runtime), not a capability: whether
    // the camera supports privacy is capabilities.privacy. While privacy is
    // on, the server withholds both live_mse and rtsp.
    @Expose
    @SerializedName("privacy")
    private boolean privacy;

    // Current "outside the recording schedule" state. Also withholds the live
    // URLs while true (like privacy, but driven by the scheduler).
    @Expose
    @SerializedName("schedule_off")
    private boolean scheduleOff;

    // Nullable so a response from an older server (no enabled field) is still
    // considered enabled.
    @Expose
    @SerializedName("enabled")
    private Boolean enabled;

    @Expose
    @SerializedName("schedule_id")
    private String scheduleId;

    @Expose
    @SerializedName("capabilities")
    private Capabilities capabilities;

    // Public PTZ metadata block, present only when capabilities.ptz is true.
    @Expose
    @SerializedName("ptz")
    private PtzMetadata ptz;

    public static class PtzMetadata implements Serializable {
        @Expose
        @SerializedName("fov_h")
        private float fovH;

        @Expose
        @SerializedName("fov_v")
        private float fovV;

        @Expose
        @SerializedName("pan_range")
        private float panRange;

        @Expose
        @SerializedName("tilt_range")
        private float tiltRange;

        public float getFovH() {
            return fovH;
        }

        public float getFovV() {
            return fovV;
        }

        public float getPanRange() {
            return panRange;
        }

        public float getTiltRange() {
            return tiltRange;
        }
    }

    /** Whether the camera supports each feature (capabilities), not live state. */
    public static class Capabilities implements Serializable {
        @Expose
        @SerializedName("privacy")
        private boolean privacy;

        @Expose
        @SerializedName("thumbnail")
        private boolean thumbnail;

        @Expose
        @SerializedName("playback")
        private boolean playback;

        @Expose
        @SerializedName("ptz")
        private boolean ptz;

        @Expose
        @SerializedName("talk")
        private boolean talk;

        @Expose
        @SerializedName("settings")
        private boolean settings;

        @Expose
        @SerializedName("talk_codecs")
        private List<String> talkCodecs;

        public boolean hasPrivacy() {
            return privacy;
        }

        public boolean hasThumbnail() {
            return thumbnail;
        }

        public boolean hasPlayback() {
            return playback;
        }

        public boolean hasPtz() {
            return ptz;
        }

        public boolean hasTalk() {
            return talk;
        }

        public boolean hasSettings() {
            return settings;
        }

        public List<String> getTalkCodecs() {
            return talkCodecs;
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }

    public String getComment() {
        return comment;
    }

    public String getRtsp() {
        return rtsp;
    }

    public String getLiveMse() {
        return liveMse;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean hasPlayback() {
        return capabilities != null && capabilities.hasPlayback();
    }

    public boolean hasThumbnail() {
        return capabilities != null && capabilities.hasThumbnail();
    }

    public boolean getPtz() {
        return capabilities != null && capabilities.hasPtz();
    }

    public PtzMetadata getPtzMetadata() {
        return ptz;
    }

    public boolean hasPrivacy() {
        return capabilities != null && capabilities.hasPrivacy();
    }

    /** Current privacy state, not the privacy capability. */
    public boolean getPrivacy() {
        return privacy;
    }

    public boolean hasTalk() {
        return capabilities != null && capabilities.hasTalk();
    }

    public boolean hasSettings() {
        return capabilities != null && capabilities.hasSettings();
    }

    /** Whether the camera is in service. Missing field (older servers) means true. */
    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public boolean isScheduleOff() {
        return scheduleOff;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public void setPrivacy(boolean enable) {
        privacy = enable;
    }
}
