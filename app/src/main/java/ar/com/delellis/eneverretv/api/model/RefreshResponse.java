package ar.com.delellis.eneverretv.api.model;

import com.google.gson.annotations.SerializedName;

/**
 * Response of {@code POST /api/auth/refresh}: a freshly rotated token pair.
 * Both secrets change on every refresh, so the stored pair must be replaced
 * or the next refresh will be treated as a replay.
 */
public class RefreshResponse {
    @SerializedName("token")
    private String token;

    @SerializedName("expires_at")
    private long expiresAt;

    @SerializedName("refresh_token")
    private String refreshToken;

    @SerializedName("refresh_expires_at")
    private long refreshExpiresAt;

    public String getToken() {
        return token;
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
