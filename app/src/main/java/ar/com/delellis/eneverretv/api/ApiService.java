package ar.com.delellis.eneverretv.api;

import java.util.List;

import ar.com.delellis.eneverretv.api.model.AuthDevice;
import ar.com.delellis.eneverretv.api.model.AuthDeviceToken;
import ar.com.delellis.eneverretv.api.model.Camera;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;

public interface ApiService {
    @GET("auth/device")
    Call<AuthDevice> authDevice();

    @GET("auth/device/{device_id}")
    Call<AuthDeviceToken> authDeviceToken(@Path("device_id") String device_id);

    @GET("cameras")
    Call<List<Camera>> cameras(@Header("Authorization") String authorization);
}
