package ar.com.delellis.eneverretv.api.model;

import com.google.gson.annotations.SerializedName;

/**
 * Position returned by the PTZ endpoints, in degrees. The server omits the
 * fields when the firmware did not echo a position; the zero values are then
 * "unknown" rather than a real position at 0,0.
 */
public class PtzPosition {
    @SerializedName("pan")
    private double pan;

    @SerializedName("tilt")
    private double tilt;

    public double getPan() {
        return pan;
    }

    public double getTilt() {
        return tilt;
    }
}
