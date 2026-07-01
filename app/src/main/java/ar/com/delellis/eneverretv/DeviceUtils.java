package ar.com.delellis.eneverretv;

import android.os.Build;

public final class DeviceUtils {

    private DeviceUtils() {
    }

    /**
     * Best-effort detection of the Android emulator. Used to force software video decoding, since
     * the emulator's goldfish hardware decoder accepts input but never renders frames for
     * High-profile H264 streams.
     */
    public static boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.FINGERPRINT.contains("emulator")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator");
    }
}
