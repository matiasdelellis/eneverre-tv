package ar.com.delellis.eneverretv;

import android.os.Build;
import android.text.TextUtils;

/**
 * Human-readable name of the device the app is installed on, for display in the backend
 * (e.g. the list of authorized devices). Based on {@link Build} fields: always available and
 * permission-free, but NOT a unique identifier (devices of the same model share the same name).
 */
public final class DeviceInfo {

    private DeviceInfo() {
    }

    public static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        String model = Build.MODEL == null ? "" : Build.MODEL;
        if (!TextUtils.isEmpty(model) && model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
            return model;
        }
        return (manufacturer + " " + model).trim();
    }
}
