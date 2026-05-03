package ar.com.delellis.eneverretv.api;

import android.util.Base64;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static ApiClient instance;

    private final ApiService apiService;

    private ApiClient(String baseUrl, String username, String password) {
        String authHeader = createAuth(username, password);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new AuthInterceptor(authHeader))
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ensureTrailingSlash(baseUrl))
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.apiService = retrofit.create(ApiService.class);
    }

    public static void init(String baseUrl, String username, String password) {
        if (instance != null) {
            throw new IllegalStateException("ApiClient already initialized");
        }
        instance = new ApiClient(baseUrl, username, password);
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

    private String createAuth(String user, String pass) {
        String credentials = user + ":" + pass;
        return "Basic " + Base64.encodeToString(
                credentials.getBytes(),
                Base64.NO_WRAP
        );
    }

    private String ensureTrailingSlash(String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    private static class AuthInterceptor implements Interceptor {
        private final String authHeader;

        AuthInterceptor(String authHeader) {
            this.authHeader = authHeader;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request().newBuilder()
                    .addHeader("Authorization", authHeader)
                    .build();

            return chain.proceed(request);
        }
    }
}