package ar.com.delellis.eneverretv.api;

import java.util.List;

import ar.com.delellis.eneverretv.api.model.Camera;
import retrofit2.Call;
import retrofit2.http.GET;

public interface ApiService {
    @GET("cameras")
    Call<List<Camera>> cameras();
}
