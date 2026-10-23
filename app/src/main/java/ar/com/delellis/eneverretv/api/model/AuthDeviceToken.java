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
    private long expiresAt;

    @Expose
    @SerializedName("refresh_token")
    private String refreshToken;

    @Expose
    @SerializedName("refresh_expires_at")
    private long refreshExpiresAt;

    public String getToken() {
        return token;
    }

    public String getStatus() {
        return status;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public long getRefreshExpiresAt() {
        return refreshExpiresAt;
    }
}
