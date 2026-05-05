package ar.com.delellis.eneverretv.api.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class AuthDevice implements Serializable {
    @Expose
    @SerializedName("device_code")
    private String device_code;

    @Expose
    @SerializedName("user_code")
    private String user_code;

    @Expose
    @SerializedName("expires_in")
    private int expires_in;

    public String getDeviceCode() {
        return device_code;
    }

    public String getUserCode() {
        return user_code;
    }

    public int getExpiresIn() {
        return expires_in;
    }
}
