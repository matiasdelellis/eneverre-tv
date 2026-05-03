package ar.com.delellis.eneverretv;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.tvprovider.media.tv.PreviewChannel;
import androidx.tvprovider.media.tv.PreviewProgram;
import androidx.tvprovider.media.tv.TvContractCompat;

import java.util.List;

import ar.com.delellis.eneverretv.api.model.Camera;

public class TvChannelManager {
    private static final String PREFS = "tv_channel";
    private static final String KEY_CHANNEL_ID = "channel_id";

    private static final String CHANNEL_NAME = "Cameras";
    private static final String CHANNEL_DESC = "Live cameras";

    public static void publish(Context context, List<Camera> cameras) {
        long channelId = getOrCreateChannel(context);

        if (channelId == -1) return;

        clearPrograms(context, channelId);

        for (Camera cam : cameras) {
            addProgram(context, channelId, cam);
        }

        requestBrowsable(context, channelId);
    }

    private static long getOrCreateChannel(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long channelId = prefs.getLong(KEY_CHANNEL_ID, -1);

        if (channelId != -1) {
            return channelId;
        }

        Uri appLink = Uri.parse("eneverre://home");

        PreviewChannel channel = new PreviewChannel.Builder()
                .setDisplayName(CHANNEL_NAME)
                .setDescription(CHANNEL_DESC)
                .setAppLinkIntentUri(appLink)
                .build();

        Uri uri = context.getContentResolver().insert(
                TvContractCompat.Channels.CONTENT_URI,
                channel.toContentValues()
        );

        if (uri == null) return -1;

        channelId = ContentUris.parseId(uri);

        prefs.edit().putLong(KEY_CHANNEL_ID, channelId).apply();

        return channelId;
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

        PreviewProgram program = new PreviewProgram.Builder()
                .setChannelId(channelId)
                .setTitle(camera.getName())
                .setDescription(camera.getComment())
                .setIntentUri(intentUri)
                .setType(TvContractCompat.PreviewPrograms.TYPE_CLIP)
                .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                .setPosterArtUri(Uri.parse("https://services.delellis.com.ar/data/public/camera-banner.png"))
                .setThumbnailUri(Uri.parse("https://services.delellis.com.ar/data/public/camera-banner.png"))
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