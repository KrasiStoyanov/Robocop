package a2.mobile.mobileapp.handlers;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.mapbox.geojson.Feature;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.offline.OfflineManager;
import com.mapbox.mapboxsdk.offline.OfflineRegion;
import com.mapbox.mapboxsdk.offline.OfflineRegionError;
import com.mapbox.mapboxsdk.offline.OfflineRegionStatus;
import com.mapbox.mapboxsdk.offline.OfflineTilePyramidRegionDefinition;

import org.json.JSONObject;

import java.util.Objects;

import a2.mobile.mobileapp.activities.MainActivity;
import a2.mobile.mobileapp.constants.MapConstants;
import a2.mobile.mobileapp.data.Data;
import a2.mobile.mobileapp.data.classes.Point;
import a2.mobile.mobileapp.data.classes.Route;
import a2.mobile.mobileapp.utils.MapUtils;
import timber.log.Timber;

public class MapHandler {
    static String currentRouteDistance;
    private static boolean isOfflineRegionDownloaded;

    /**
     * Download an offline region of the map - Nijmegen.
     *
     * @param context The MainActivity context
     */
    public static void downloadOfflineRegion(Context context) {
        // Create a bounding box for the offline region
        LatLngBounds latLngBounds = new LatLngBounds.Builder()
                .include(new com.mapbox.mapboxsdk.geometry.LatLng(
                        MapConstants.MAP_REGION_MIN_LAT,
                        MapConstants.MAP_REGION_MIN_LON)
                )
                .include(new com.mapbox.mapboxsdk.geometry.LatLng(
                        MapConstants.MAP_REGION_MAX_LAT,
                        MapConstants.MAP_REGION_MAX_LON)
                )
                .build();

        OfflineTilePyramidRegionDefinition definition = new OfflineTilePyramidRegionDefinition(
                Style.MAPBOX_STREETS,
                latLngBounds,
                MapConstants.MIN_ZOOM,
                MapConstants.MAX_ZOOM,
                context.getResources().getDisplayMetrics().density
        );

        // Set the metadata
        byte[] metadata;
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put(MapConstants.MAP_JSON_FIELD_REGION_NAME, MapConstants.MAP_NAME);

            String json = jsonObject.toString();
            metadata = json.getBytes(MapConstants.MAP_JSON_CHARSET);
        } catch (Exception exception) {
            Timber.e(Objects.requireNonNull(exception.getMessage()));
            metadata = null;
        }

        // Create the region asynchronously
        if (metadata != null) {
            MainActivity.offlineMapManager.createOfflineRegion(
                    definition,
                    metadata,
                    new OfflineManager.CreateOfflineRegionCallback() {

                        @Override
                        public void onCreate(OfflineRegion offlineRegion) {
                            offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE);

                            // Display the download progress bar
                            MapHandler.startProgress();

                            observeDownloadingProcess(context, offlineRegion);
                        }

                        @Override
                        public void onError(String error) {
                            Timber.e(error);
                        }
                    });
        }
    }

    /**
     * Visualize a progress bar on download start.
     */
    private static void startProgress() {

        // Start and show the progress bar
        isOfflineRegionDownloaded = false;

        MainActivity.progressBar.setIndeterminate(true);
        MainActivity.progressBar.setVisibility(View.VISIBLE);
        MainActivity.loadingBackground.setVisibility(View.VISIBLE);
    }

    /**
     * Update the download state using percentages.
     *
     * @param percentage The current percentage
     */
    private static void setPercentage(final int percentage) {
        MainActivity.progressBar.setIndeterminate(false);
        MainActivity.progressBar.setProgress(percentage);
    }

    /**
     * Hide the progress bard on download end and notify the user of it.
     *
     * @param context The MainActivity context
     */
    private static void endProgress(Context context) {
        // Don't notify more than once
        if (isOfflineRegionDownloaded) {
            return;
        }

        // Stop and hide the progress bar
        isOfflineRegionDownloaded = true;
        MainActivity.progressBar.setIndeterminate(false);
        MainActivity.progressBar.setVisibility(View.GONE);
        MainActivity.loadingBackground.setVisibility(View.GONE);

        // Show a toast
        Toast.makeText(context, "Region downloaded successfully!", Toast.LENGTH_LONG).show();
    }

    /**
     * Monitor the downloading process using an observer.
     *
     * @param offlineRegion The region to observe while downloading.
     */
    private static void observeDownloadingProcess(Context context, OfflineRegion offlineRegion) {
        offlineRegion.setObserver(new OfflineRegion.OfflineRegionObserver() {

            @Override
            public void onStatusChanged(OfflineRegionStatus status) {

                // Calculate the download percentage and update the progress bar
                double percentage = status.getRequiredResourceCount() >= 0
                        ? (100.0 * status.getCompletedResourceCount() / status.getRequiredResourceCount()) :
                        0.0;

                if (status.isComplete()) {
                    // Download complete
                    MapHandler.endProgress(context);
                } else if (status.isRequiredResourceCountPrecise()) {
                    // Switch to determinate state
                    setPercentage((int) Math.round(percentage));
                }
            }

            @Override
            public void onError(OfflineRegionError error) {
                // If an error occurs, print to logcat
                Timber.e(error.getReason());
                Timber.e(error.getMessage());
                MapHandler.endProgress(context);
            }

            @Override
            public void mapboxTileCountLimitExceeded(long limit) {
                // Notify if offline region exceeds maximum tile count
                Timber.e(String.valueOf(limit));
            }
        });
    }

    /**
     * Focus the Google Map on the route's start and end point area.
     */
    public static void focusMapOnRoute(Context context) {
        Route route = Data.selectedRoute;
        Log.e("Interest Points", route.pointsOfInterest.toString());
        LatLng startPoint = new LatLng(
                route.startPoint.coordinates.get(0),
                route.startPoint.coordinates.get(1)
        );

        LatLng endPoint = new LatLng(
                route.endPoint.coordinates.get(0),
                route.endPoint.coordinates.get(1)
        );

        ((Activity) context).runOnUiThread(() -> {

            // Add the start and end markers to the map.
            MapUtils.addRouteMarker(generateRouteMarker(
                    route.startPoint,
                    startPoint.getLongitude(),
                    startPoint.getLatitude()
            ));

            MapUtils.addRouteMarker(generateRouteMarker(
                    route.endPoint,
                    endPoint.getLongitude(),
                    endPoint.getLatitude()
            ));

            // Render the markers and route layers on the map.
            MapUtils.updateRoute(
                    context,
                    com.mapbox.geojson.Point.fromLngLat(
                            startPoint.getLongitude(),
                            startPoint.getLatitude()
                    ),
                    com.mapbox.geojson.Point.fromLngLat(
                            endPoint.getLongitude(),
                            endPoint.getLatitude()
                    ),
                    Data.selectedRoute.pointsOfInterest
            );

            // Position the camera in the bounds of the route.
            LatLngBounds latLngBounds = new LatLngBounds.Builder()
                    .include(startPoint)
                    .include(endPoint)
                    .build();

            MapUtils.map.easeCamera(CameraUpdateFactory.newLatLngBounds(
                    latLngBounds,
                    MapConstants.MAP_FOCUS_PADDING
            ));
        });
    }

    /**
     * Generate a route marker and add it to the Mapbox map.
     *
     * @param point the current point that holds the coordinates
     */
    private static Feature generateRouteMarker(Point point, double longitude, double latitude) {
        String title = point.title;
        String interest = point.interest;

        // Create a new instance of a marker based on the coordinates from the point of interest.
        com.mapbox.geojson.Point coordinatesPoint = com.mapbox.geojson.Point.fromLngLat(
                longitude,
                latitude
        );

        Feature feature = Feature.fromGeometry(coordinatesPoint);

        // Settings for the marker.
        feature.addStringProperty("title", title);
        feature.addStringProperty("interest", interest);

        return feature;
    }
}