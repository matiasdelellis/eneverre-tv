package ar.com.delellis.eneverretv.api.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class AuthDeviceToken implements Serializable {
    @Expose
    @SerializedName("token")
    private String token;

    @Expose
    @SerializedName("status")
    private String status;

    @Expose
    @SerializedName("expires_at")
    private int expires_at;

    public String getToken() {
        return token;
    }

    public String getStatus() {
        return status;
    }

    public int getExpiresAt() {
        return expires_at;
    }
}
