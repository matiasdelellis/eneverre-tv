package ar.com.delellis.eneverretv;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ar.com.delellis.eneverretv.api.model.Camera;

public class LocationsActivity extends FragmentActivity {

    public static final String RAW_CAMERAS_LIST_DATA = "RAW_CAMERA_LIST";

    private List<Camera> cameraList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_locations);

        cameraList = (List<Camera>) getIntent().getSerializableExtra(RAW_CAMERAS_LIST_DATA);
        if (cameraList == null) {
            cameraList = new ArrayList<>();
        }

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

                listRowAdapter.add(new ViewGridAction(locationName, cameras));
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
        public final List<Camera> cameras;

        public ViewGridAction(String locationName, List<Camera> cameras) {
            this.locationName = locationName;
            this.cameras = cameras;
        }
    }

    public static class CardPresenter extends Presenter {
        private static final int CARD_WIDTH = 313;
        private static final int CARD_HEIGHT = 176;

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent) {
            Context context = parent.getContext();
            ImageCardView card = new ImageCardView(context);
            card.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT);
            card.setMainImageScaleType(ImageView.ScaleType.CENTER_CROP);
            return new ViewHolder(card);
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, Object item) {
            ImageCardView card = (ImageCardView) viewHolder.view;
            if (item instanceof Camera) {
                Camera cam = (Camera) item;
                card.setTitleText(cam.getName());
                String sub = cam.getComment();
                if (sub == null || sub.isEmpty()) {
                    sub = cam.getLocation();
                }
                card.setContentText(sub);
                card.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT);
                card.setBadgeImage(null);
                card.setInfoAreaBackgroundColor(ContextCompat.getColor(card.getContext(), R.color.card_info_bg));
            } else if (item instanceof ViewGridAction) {
                ViewGridAction action = (ViewGridAction) item;
                card.setTitleText(action.locationName);
                card.setContentText(card.getContext().getString(R.string.view_grid_action));
                card.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT);
                card.setBadgeImage(ContextCompat.getDrawable(card.getContext(), R.drawable.ic_grid_badge));
                card.setInfoAreaBackgroundColor(ContextCompat.getColor(card.getContext(), R.color.card_info_bg));
            }

            final String bannerUrl = (item instanceof ViewGridAction)
                    ? ChannelImageLoader.BANNER_GRID
                    : ChannelImageLoader.BANNER_CAMERA;

            Drawable cached = ChannelImageLoader.getDrawable(card.getContext(), bannerUrl);
            if (cached != null) {
                card.setMainImage(cached);
            } else {
                card.getMainImageView().setTag(bannerUrl);
                ChannelImageLoader.loadInto(card.getContext(), bannerUrl, card.getMainImageView(), drawable -> {
                    if (bannerUrl.equals(card.getMainImageView().getTag())) {
                        card.setMainImage(drawable);
                    }
                });
            }
        }

        @Override
        public void onUnbindViewHolder(ViewHolder viewHolder) {
            ImageCardView card = (ImageCardView) viewHolder.view;
            card.setMainImage(null);
        }
    }
}
