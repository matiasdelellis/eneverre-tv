package ar.com.delellis.eneverretv.api.model;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

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

    @SerializedName("versionName")
    private String versionName;

    @SerializedName("versionCode")
    private int versionCode;

    @SerializedName("mandatory")
    private Boolean mandatory;

    @SerializedName("releaseNotes")
    private String releaseNotes;

    @SerializedName("builds")
    private List<Build> builds;

    public String getVersionName() {
        return versionName;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public boolean isMandatory() {
        return mandatory != null && mandatory;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }

    public List<Build> getBuilds() {
        return builds;
    }

    public static class Build implements Serializable {
        @SerializedName("abi")
        private String abi;

        @SerializedName("apkFilename")
        private String apkFilename;

        @SerializedName("size")
        private long size;

        @SerializedName("sha256")
        private String sha256;

        @SerializedName("url")
        private String url;

        public String getAbi() {
            return abi;
        }

        public String getApkFilename() {
            return apkFilename;
        }

        public long getSize() {
            return size;
        }

        public String getSha256() {
            return sha256;
        }

        public String getUrl() {
            return url;
        }

        public String toJson() {
            return GSON.toJson(this);
        }

        public static Build fromJson(String json) {
            if (json == null || json.isEmpty()) {
                return null;
            }
            try {
                return GSON.fromJson(json, Build.class);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
