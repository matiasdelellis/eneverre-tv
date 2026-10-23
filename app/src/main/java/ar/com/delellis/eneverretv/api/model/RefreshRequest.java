package ar.com.delellis.eneverretv.api.model;

import com.google.gson.annotations.SerializedName;

/** Body of {@code POST /api/auth/refresh}: the refresh token to exchange. */
public class RefreshRequest {
    @SerializedName("refresh_token")
    private final String refreshToken;

    public RefreshRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
