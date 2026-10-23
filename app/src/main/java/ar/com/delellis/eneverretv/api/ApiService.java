package ar.com.delellis.eneverretv.api;

import java.util.List;

import ar.com.delellis.eneverretv.api.model.AuthDevice;
import ar.com.delellis.eneverretv.api.model.AuthDeviceToken;
import ar.com.delellis.eneverretv.api.model.Camera;
import ar.com.delellis.eneverretv.api.model.PtzPosition;
import ar.com.delellis.eneverretv.api.model.RefreshRequest;
import ar.com.delellis.eneverretv.api.model.RefreshResponse;
import ar.com.delellis.eneverretv.api.model.UpdateManifest;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    @GET("auth/device")
    Call<AuthDevice> authDevice(@Query("device_name") String deviceName);

    @GET("auth/device/{device_id}")
    Call<AuthDeviceToken> authDeviceToken(@Path("device_id") String device_id);

    @POST("auth/refresh")
    Call<RefreshResponse> refresh(@Body RefreshRequest refreshToken);

    @GET("cameras")
    Call<List<Camera>> cameras(@Header("Authorization") String authorization);

    @POST("camera/{camera_id}/ptz/move")
    Call<PtzPosition> ptzMove(@Header("Authorization") String authorization,
                              @Path("camera_id") String cameraId,
                              @Query("pan") double pan,
                              @Query("tilt") double tilt);

    @POST("camera/{camera_id}/ptz/home")
    Call<PtzPosition> ptzHome(@Header("Authorization") String authorization,
                              @Path("camera_id") String cameraId);

    @GET("app/tv/update")
    Call<UpdateManifest> tvUpdate();
}
