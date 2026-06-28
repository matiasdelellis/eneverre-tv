package ar.com.delellis.eneverretv.api.model;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class UpdateManifest implements Serializable {
    private static final Gson GSON = new Gson();

    public static UpdateManifest fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return GSON.fromJson(json, UpdateManifest.class);
        } catch (Exception e) {
            return null;
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    @Expose
    @SerializedName("versionName")
    private String versionName;

    @Expose
    @SerializedName("versionCode")
    private int versionCode;

    @Expose
    @SerializedName("apkFilename")
    private String apkFilename;

    @Expose
    @SerializedName("url")
    private String url;

    @Expose
    @SerializedName("sha256")
    private String sha256;

    @Expose
    @SerializedName("mandatory")
    private Boolean mandatory;

    @Expose
    @SerializedName("releaseNotes")
    private String releaseNotes;

    public String getVersionName() {
        return versionName;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public String getApkFilename() {
        return apkFilename;
    }

    public String getUrl() {
        return url;
    }

    public String getSha256() {
        return sha256;
    }

    public boolean isMandatory() {
        return mandatory != null && mandatory;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }
}
