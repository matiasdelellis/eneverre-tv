package ar.com.delellis.eneverretv;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;

import com.google.android.material.card.MaterialCardView;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ar.com.delellis.eneverretv.api.CameraRepository;
import ar.com.delellis.eneverretv.api.model.Camera;

public class LocationsActivity extends FragmentActivity {

    public static final String RAW_CAMERAS_LIST_DATA = "RAW_CAMERA_LIST";

    private List<Camera> cameraList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_locations);

        List<Camera> intentList = (List<Camera>) getIntent().getSerializableExtra(RAW_CAMERAS_LIST_DATA);
        if (intentList == null) {
            intentList = new ArrayList<>();
        }
        CameraRepository.seedIfEmpty(intentList);
        cameraList = CameraRepository.isEmpty() ? intentList : CameraRepository.getCameras();

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.main_browse_fragment, new LocationsBrowseFragment())
                    .commitNow();
        }
    }

    public List<Camera> getCameraList() {
        return cameraList;
    }

    public static class LocationsBrowseFragment extends BrowseSupportFragment {
        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            setTitle(getString(R.string.locations_title));
            setHeadersState(HEADERS_ENABLED);
            setHeadersTransitionOnBackEnabled(true);
            setBrandColor(ContextCompat.getColor(requireContext(), R.color.brand_color));
            setSearchAffordanceColor(Color.TRANSPARENT);

            setupRows();
        }

        private void setupRows() {
            ArrayObjectAdapter rowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());

            LocationsActivity activity = (LocationsActivity) getActivity();
            if (activity == null) return;

            Map<String, List<Camera>> grouped = groupByLocation(activity.getCameraList());
            if (grouped.isEmpty()) {
                TextView empty = requireActivity().findViewById(R.id.empty_message);
                if (empty != null) empty.setVisibility(View.VISIBLE);
                return;
            }

            CardPresenter cardPresenter = new CardPresenter();
            long headerId = 0L;
            for (Map.Entry<String, List<Camera>> entry : grouped.entrySet()) {
                String locationName = entry.getKey();
                List<Camera> cameras = entry.getValue();

                HeaderItem header = new HeaderItem(headerId++, locationName);
                ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(cardPresenter);

                String rawLocation = cameras.isEmpty() || cameras.get(0).getLocation() == null
                        ? ""
                        : cameras.get(0).getLocation();
                listRowAdapter.add(new ViewGridAction(locationName, rawLocation, cameras));
                for (Camera cam : cameras) {
                    listRowAdapter.add(cam);
                }

                rowsAdapter.add(new ListRow(header, listRowAdapter));
            }

            setAdapter(rowsAdapter);

            setOnItemViewClickedListener(new OnItemViewClickedListener() {
                @Override
                public void onItemClicked(Presenter.ViewHolder itemViewHolder,
                                          Object item,
                                          RowPresenter.ViewHolder rowViewHolder,
                                          Row row) {
                    if (item instanceof Camera) {
                        openCameraFullscreen((Camera) item);
                    } else if (item instanceof ViewGridAction) {
                        openLocationGrid((ViewGridAction) item);
                    }
                }
            });
        }

        private void openCameraFullscreen(Camera camera) {
            LocationsActivity activity = (LocationsActivity) getActivity();
            if (activity == null) return;
            Intent intent = new Intent(activity, LiveActivity.class);
            intent.putExtra(LiveActivity.RAW_CAMERAS_LIST_DATA, (Serializable) activity.getCameraList());
            intent.putExtra(LiveActivity.CURRENT_CAMERA_ID, camera.getId());
            startActivity(intent);
        }

        private void openLocationGrid(ViewGridAction action) {
            LocationsActivity activity = (LocationsActivity) getActivity();
            if (activity == null) return;
            Intent intent = new Intent(activity, LocationGridActivity.class);
            intent.putExtra(LocationGridActivity.RAW_CAMERAS_LIST_DATA, (Serializable) action.cameras);
            intent.putExtra(LocationGridActivity.LOCATION_NAME, action.locationName);
            intent.putExtra(LocationGridActivity.LOCATION_KEY, action.locationKey);
            startActivity(intent);
        }

        private Map<String, List<Camera>> groupByLocation(List<Camera> cameras) {
            Map<String, List<Camera>> grouped = new LinkedHashMap<>();
            for (Camera cam : cameras) {
                String key = cam.getLocation();
                if (key == null || key.isEmpty()) {
                    key = getString(R.string.location_unnamed);
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
    }

    public static class ViewGridAction {
        public final String locationName;
        public final String locationKey;
        public final List<Camera> cameras;

        public ViewGridAction(String locationName, String locationKey, List<Camera> cameras) {
            this.locationName = locationName;
            this.locationKey = locationKey;
            this.cameras = cameras;
        }
    }

    public static class CardPresenter extends Presenter {
        private static final float FOCUS_SCALE = 1.04f;

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent) {
            MaterialCardView card = (MaterialCardView) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.card_camera, parent, false);
            card.setOnFocusChangeListener((view, hasFocus) -> {
                float scale = hasFocus ? FOCUS_SCALE : 1f;
                view.animate()
                        .scaleX(scale)
                        .scaleY(scale)
                        .setDuration(hasFocus ? 160 : 120)
                        .start();
                card.setStrokeWidth(hasFocus ? dp(view.getContext(), 3) : 0);
                card.setStrokeColor(ContextCompat.getColor(view.getContext(), R.color.focus));
                card.setCardElevation(dp(view.getContext(), hasFocus ? 14 : 4));
            });
            return new ViewHolder(card);
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, Object item) {
            MaterialCardView card = (MaterialCardView) viewHolder.view;
            ImageView image = card.findViewById(R.id.card_image);
            TextView title = card.findViewById(R.id.card_title);
            TextView subtitle = card.findViewById(R.id.card_subtitle);
            TextView badge = card.findViewById(R.id.card_badge);

            if (item instanceof Camera) {
                Camera cam = (Camera) item;
                title.setText(cam.getName());
                String sub = cam.getComment();
                if (sub == null || sub.isEmpty()) {
                    sub = cam.getLocation();
                }
                showSubtitle(subtitle, sub);
                badge.setText(badgeText(cam));
                tintBadge(badge, badgeTint(cam));
                badge.setVisibility(View.VISIBLE);
                card.setContentDescription(cam.getName());
            } else if (item instanceof ViewGridAction) {
                ViewGridAction action = (ViewGridAction) item;
                title.setText(action.locationName);
                showSubtitle(subtitle, card.getContext().getString(R.string.view_grid_action));
                badge.setText(R.string.grid_badge);
                tintBadge(badge, R.color.card_badge_bg);
                badge.setVisibility(View.VISIBLE);
                card.setContentDescription(action.locationName);
            }

            card.setStrokeWidth(card.isFocused() ? dp(card.getContext(), 3) : 0);
            card.setStrokeColor(ContextCompat.getColor(card.getContext(), R.color.focus));
            float scale = card.isFocused() ? FOCUS_SCALE : 1f;
            card.setScaleX(scale);
            card.setScaleY(scale);

            final String bannerUrl = (item instanceof ViewGridAction)
                    ? ChannelImageLoader.BANNER_GRID
                    : ChannelImageLoader.BANNER_CAMERA;

            Drawable cached = ChannelImageLoader.getDrawable(card.getContext(), bannerUrl);
            if (cached != null) {
                image.setImageDrawable(cached);
                loadThumbnailIfAvailable(card, item, image);
            } else {
                image.setTag(bannerUrl);
                ChannelImageLoader.loadInto(card.getContext(), bannerUrl, image, drawable -> {
                    if (bannerUrl.equals(image.getTag())) {
                        image.setImageDrawable(drawable);
                        loadThumbnailIfAvailable(card, item, image);
                    }
                });
            }
        }

        private static int badgeText(Camera camera) {
            if (!camera.isEnabled()) {
                return R.string.offline_badge;
            }
            if (camera.getPrivacy()) {
                return R.string.paused_badge;
            }
            if (camera.isScheduleOff()) {
                return R.string.schedule_badge;
            }
            return R.string.live_badge;
        }

        private static int badgeTint(Camera camera) {
            if (camera.isEnabled() && !camera.getPrivacy() && !camera.isScheduleOff()) {
                return R.color.card_badge_bg;
            }
            return R.color.surface_highest;
        }

        private static void tintBadge(TextView badge, int colorRes) {
            Drawable background = badge.getBackground();
            if (background != null) {
                background.mutate().setTint(ContextCompat.getColor(badge.getContext(), colorRes));
            }
        }

        private static void showSubtitle(TextView subtitle, String text) {
            if (text == null || text.isEmpty()) {
                subtitle.setVisibility(View.GONE);
            } else {
                subtitle.setText(text);
                subtitle.setVisibility(View.VISIBLE);
            }
        }

        /**
         * Replaces the static card banner with the camera's current still when
         * the server advertises the thumbnail capability. The banner remains
         * as the fallback if the snapshot request fails.
         */
        private void loadThumbnailIfAvailable(MaterialCardView card, Object item, ImageView image) {
            if (!(item instanceof Camera)) {
                return;
            }
            Camera camera = (Camera) item;
            if (!camera.hasThumbnail()) {
                return;
            }
            ChannelImageLoader.loadThumbnail(card.getContext(), camera,
                    image, image::setImageDrawable);
        }

        @Override
        public void onUnbindViewHolder(ViewHolder viewHolder) {
            MaterialCardView card = (MaterialCardView) viewHolder.view;
            ImageView image = card.findViewById(R.id.card_image);
            image.setImageDrawable(null);
            image.setTag(null);
            card.setStrokeWidth(0);
            card.setScaleX(1f);
            card.setScaleY(1f);
        }

        private static int dp(Context context, int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }
    }
}
