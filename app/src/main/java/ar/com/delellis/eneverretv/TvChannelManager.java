package ar.com.delellis.eneverretv;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.tvprovider.media.tv.PreviewChannel;
import androidx.tvprovider.media.tv.PreviewProgram;
import androidx.tvprovider.media.tv.TvContractCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ar.com.delellis.eneverretv.api.model.Camera;

public class TvChannelManager {
    private static final String PREFS = "tv_channel";
    private static final String KEY_CHANNEL_ID_PREFIX = "channel_id_";
    private static final String LEGACY_KEY_CHANNEL_ID = "channel_id";

    public static void publish(Context context, List<Camera> cameras) {
        Map<String, List<Camera>> grouped = groupByLocation(context, cameras);

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Map<String, Long> existing = readChannelMap(prefs);
        Map<String, Long> next = new HashMap<>();

        for (Map.Entry<String, List<Camera>> entry : grouped.entrySet()) {
            String location = entry.getKey();
            List<Camera> cams = entry.getValue();
            long channelId = existing.containsKey(location)
                    ? existing.get(location)
                    : createChannel(context, location);
            if (channelId == -1) continue;

            next.put(location, channelId);
            clearPrograms(context, channelId);
            for (Camera cam : cams) {
                addProgram(context, channelId, cam);
            }
            requestBrowsable(context, channelId);
        }

        for (Map.Entry<String, Long> stale : existing.entrySet()) {
            if (!next.containsKey(stale.getKey())) {
                deleteChannel(context, stale.getValue());
            }
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        for (Map.Entry<String, Long> e : next.entrySet()) {
            editor.putLong(KEY_CHANNEL_ID_PREFIX + e.getKey(), e.getValue());
        }
        editor.apply();
    }

    private static Map<String, List<Camera>> groupByLocation(Context context, List<Camera> cameras) {
        Map<String, List<Camera>> grouped = new LinkedHashMap<>();
        String unnamed = context.getString(R.string.location_unnamed);
        for (Camera cam : cameras) {
            String key = cam.getLocation();
            if (key == null || key.isEmpty()) {
                key = unnamed;
            }
            List<Camera> list = grouped.get(key);
            if (list == null) {
                list = new ArrayList<>();
                grouped.put(key, list);
            }
            list.add(cam);
        }
        return grouped;
    }

    private static Map<String, Long> readChannelMap(SharedPreferences prefs) {
        Map<String, Long> map = new HashMap<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(KEY_CHANNEL_ID_PREFIX) && entry.getValue() instanceof Long) {
                map.put(key.substring(KEY_CHANNEL_ID_PREFIX.length()), (Long) entry.getValue());
            } else if (LEGACY_KEY_CHANNEL_ID.equals(key) && entry.getValue() instanceof Long) {
                map.put("__legacy__", (Long) entry.getValue());
            }
        }
        return map;
    }

    private static long createChannel(Context context, String locationName) {
        Uri appLink = Uri.parse("eneverre://home");

        PreviewChannel channel = new PreviewChannel.Builder()
                .setDisplayName(locationName)
                .setDescription("Live cameras in " + locationName)
                .setAppLinkIntentUri(appLink)
                .build();

        Uri uri = context.getContentResolver().insert(
                TvContractCompat.Channels.CONTENT_URI,
                channel.toContentValues()
        );

        if (uri == null) return -1;
        return ContentUris.parseId(uri);
    }

    private static void deleteChannel(Context context, long channelId) {
        if (channelId == -1) return;
        Uri uri = TvContractCompat.buildChannelUri(channelId);
        context.getContentResolver().delete(uri, null, null);
    }

    private static void clearPrograms(Context context, long channelId) {
        Uri uri = TvContractCompat.buildPreviewProgramsUriForChannel(channelId);
        context.getContentResolver().delete(uri, null, null);
    }

    private static void addProgram(Context context, long channelId, Camera camera) {
        Intent intent = new Intent(context, LaunchActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra("camera_id", camera.getId());
        Uri intentUri = Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME));

        String bannerBase = ChannelImageLoader.bannerBaseUrl();
        Uri posterUri = Uri.parse(bannerBase + "img/camera-banner.png");

        PreviewProgram program = new PreviewProgram.Builder()
                .setChannelId(channelId)
                .setTitle(camera.getName())
                .setDescription(camera.getComment())
                .setIntentUri(intentUri)
                .setType(TvContractCompat.PreviewPrograms.TYPE_CLIP)
                .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                .setPosterArtUri(posterUri)
                .setThumbnailUri(posterUri)
                .setLive(true)
                .build();

        context.getContentResolver().insert(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                program.toContentValues()
        );
    }

    private static void requestBrowsable(Context context, long channelId) {
        Intent intent = new Intent(TvContractCompat.ACTION_REQUEST_CHANNEL_BROWSABLE);
        intent.putExtra(TvContractCompat.EXTRA_CHANNEL_ID, channelId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(intent);
    }
}
