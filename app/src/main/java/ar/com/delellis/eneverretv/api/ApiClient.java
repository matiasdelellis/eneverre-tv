package ar.com.delellis.eneverretv.api;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static ApiClient instance;

    private final ApiService apiService;

    private ApiClient(String baseUrl) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ensureTrailingSlash(baseUrl))
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.apiService = retrofit.create(ApiService.class);
    }

    public static void init(String baseUrl) {
        if (instance != null) {
            return;
        }
        instance = new ApiClient(baseUrl);
    }

    public static ApiClient get() {
        if (instance == null) {
            throw new IllegalStateException("ApiClient not initialized");
        }
        return instance;
    }

    public ApiService api() {
        return apiService;
    }

    private String ensureTrailingSlash(String url) {
        return url.endsWith("/") ? url : url + "/";
    }
}