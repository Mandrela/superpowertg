package org.telegram.messenger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.util.Consumer;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;

public class OpenStreetMapProvider implements IMapsProvider {

    @Override
    public void initializeMaps(Context context) {

    }

    @Override
    public IMapView onCreateMapView(Context context) {
        return new OpenStreetMapView(context);
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLng(LatLng latLng) {
        return new OpenStreetMapCameraUpdate(new GeoPoint(latLng.latitude, latLng.longitude));
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLngZoom(LatLng latLng, float zoom) {
        return new OpenStreetMapCameraUpdate(new GeoPoint(latLng.latitude, latLng.longitude), zoom);
    }

    @Override
    public ICameraUpdate newCameraUpdateLatLngBounds(ILatLngBounds bounds, int padding) {
        return new OpenStreetMapCameraUpdate(((OpenStreetMapLatLngBounds) bounds).boundingBox);
    }

    @Override
    public ILatLngBoundsBuilder onCreateLatLngBoundsBuilder() {
        return new OpenStreetMapLatLngBoundsBuilder();
    }

    @Override
    public IMapStyleOptions loadRawResourceStyle(Context context, int resId) {
        return new OpenStreetMapStyleOptions();
    }

    @Override
    public String getMapsAppPackageName() {
        return "com.google.android.apps.maps";
    }

    @Override
    public int getInstallMapsString() {
        return R.string.InstallGoogleMaps;
    }

    @Override
    public IMarkerOptions onCreateMarkerOptions() {
        return new OpenStreetMapMarkerOptions();
    }

    @Override
    public ICircleOptions onCreateCircleOptions() {
        return new OpenStreetMapCircleOptions();
    }

    public final static class OpenStreetMap implements IMap {
        private final MapView mapView;
        private final IMapController controller;
        private MyLocationNewOverlay myLocationOverlay = null;
        private OnMarkerClickListener onMarkerClickListener = null;
        private Consumer<Location> onMyLocationChangeListener = null;
        private OnCameraMoveStartedListener onCameraMoveStartedListener = null;
        private Runnable onCameraIdleListener = null;
        private Runnable onCameraMoveListener = null;

        private OpenStreetMap(IMapController controller, MapView mapView) {
            this.controller = controller;
            this.mapView = mapView;
        }

        @Override
        public void setMapType(int mapType) {
        }

        @Override
        public void animateCamera(ICameraUpdate update) {
            this.animateCamera(update, null, null);
        }

        @Override
        public void animateCamera(ICameraUpdate update, ICancelableCallback callback) {
            this.animateCamera(update, null, callback);
        }

        @Override
        public void animateCamera(ICameraUpdate update, int duration, ICancelableCallback callback) {
            this.animateCamera(update, Integer.valueOf(duration), callback);
        }

        private void animateCamera(ICameraUpdate update, Integer duration, ICancelableCallback callback) {
            if(myLocationOverlay != null) {
                myLocationOverlay.disableFollowLocation();
            }

            OpenStreetMapCameraUpdate osmUpdate = (OpenStreetMapCameraUpdate) update;
            Long longDuration = null;

            if(duration != null) {
                longDuration = Long.valueOf(duration);
            }

            if(onCameraMoveStartedListener != null) {
                onCameraMoveStartedListener.onCameraMoveStarted(OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION);
            }

            if(onCameraMoveListener != null) {
                onCameraMoveListener.run();
            }

            if(osmUpdate.boundingBox != null) {
                mapView.zoomToBoundingBox(osmUpdate.boundingBox, true, 0, mapView.getMaxZoomLevel(), longDuration);
            } else {
                controller.animateTo(osmUpdate.center, osmUpdate.zoom, longDuration);
            }

            Runnable callCallbacks = () -> {
                if(callback != null) {
                    callback.onFinish();
                }

                if(onCameraIdleListener != null) {
                    onCameraIdleListener.run();
                }
            };

            if(longDuration != null) {
                new Handler(Looper.getMainLooper()).postDelayed(callCallbacks, longDuration);
            } else {
                callCallbacks.run();
            }
        }

        @Override
        public void moveCamera(ICameraUpdate update) {
            if(myLocationOverlay != null) {
                myLocationOverlay.disableFollowLocation();
            }

            OpenStreetMapCameraUpdate osmUpdate = (OpenStreetMapCameraUpdate) update;

            if(onCameraMoveStartedListener != null) {
                onCameraMoveStartedListener.onCameraMoveStarted(OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION);
            }

            if(onCameraMoveListener != null) {
                onCameraMoveListener.run();
            }

            if(osmUpdate.boundingBox != null) {
                mapView.zoomToBoundingBox(osmUpdate.boundingBox, false);
            } else {
                if (osmUpdate.center != null) {
                    controller.setCenter(osmUpdate.center);
                }

                if (osmUpdate.zoom != null) {
                    controller.setZoom(osmUpdate.zoom);
                }
            }

            if(onCameraIdleListener != null) {
                onCameraIdleListener.run();
            }
        }

        @Override
        public float getMaxZoomLevel() {
            return (float) mapView.getMaxZoomLevel() - 7.0F;
        }

        @Override
        public float getMinZoomLevel() {
            return (float) mapView.getMinZoomLevel();
        }

        @Override
        public void setMyLocationEnabled(boolean enabled) {
            if(myLocationOverlay == null && enabled) {
                myLocationOverlay = new MyLocationNewOverlay(mapView) {
                    @Override
                    public void onLocationChanged(Location location, IMyLocationProvider source) {
                        super.onLocationChanged(location, source);

                        if(onMyLocationChangeListener != null) {
                            onMyLocationChangeListener.accept(location);
                        }
                    }
                };

                myLocationOverlay.enableMyLocation();
                myLocationOverlay.enableFollowLocation();

                mapView.getOverlays().add(myLocationOverlay);
            } else if(myLocationOverlay != null && !enabled) {
                mapView.getOverlays().remove(myLocationOverlay);

                myLocationOverlay = null;
            }
        }

        @Override
        public IUISettings getUiSettings() {
            return new OpenStreetMapUISettings();
        }

        @Override
        public void setOnCameraMoveStartedListener(OnCameraMoveStartedListener onCameraMoveStartedListener) {
            this.onCameraMoveStartedListener = onCameraMoveStartedListener;
        }

        @Override
        public void setOnCameraIdleListener(Runnable callback) {
            onCameraIdleListener = callback;
        }

        @Override
        public CameraPosition getCameraPosition() {
            IGeoPoint center = mapView.getMapCenter();
            return new CameraPosition(new LatLng(center.getLatitude(), center.getLongitude()), (float) mapView.getZoomLevelDouble());
        }

        @Override
        public void setOnMapLoadedCallback(Runnable callback) {
            callback.run();
        }

        @Override
        public IProjection getProjection() {
            return new OpenStreetMapProjection(mapView.getProjection());
        }

        @Override
        public void setPadding(int left, int top, int right, int bottom) {

        }

        @Override
        public void setMapStyle(IMapStyleOptions style) {

        }

        @Override
        public IMarker addMarker(IMarkerOptions markerOptions) {
            OpenStreetMapMarkerOptions osmMarkerOptions = (OpenStreetMapMarkerOptions) markerOptions;

            org.osmdroid.views.overlay.Marker marker = new org.osmdroid.views.overlay.Marker(mapView);
            marker.setPosition(osmMarkerOptions.position);
            marker.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM);

            mapView.getOverlays().add(marker);

            IMarker markerWrapper = new IMarker() {
                private Object tag;

                @Override
                public Object getTag() {
                    return tag;
                }

                @Override
                public void setTag(Object tag) {
                    this.tag = tag;
                }

                @Override
                public LatLng getPosition() {
                    return new LatLng(osmMarkerOptions.position.getLatitude(), osmMarkerOptions.position.getLongitude());
                }

                @Override
                public void setPosition(LatLng latLng) {
                    marker.setPosition(new GeoPoint(latLng.latitude, latLng.longitude));
                }

                @Override
                public void setRotation(int rotation) {
                    marker.setRotation((float) rotation);
                }

                @Override
                public void setIcon(Bitmap bitmap) {
                    marker.setIcon(new BitmapDrawable(bitmap));
                }

                @Override
                public void setIcon(int resId) {
                    marker.setIcon(AppCompatResources.getDrawable(mapView.getContext(), resId));
                }

                @Override
                public void remove() {
                    mapView.getOverlays().remove(marker);
                }
            };

            marker.setOnMarkerClickListener((marker1, mapView) -> {
                if(onMarkerClickListener != null) {
                    return onMarkerClickListener.onClick(markerWrapper);
                }

                return false;
            });

            return markerWrapper;
        }


        @Override
        public ICircle addCircle(ICircleOptions circleOptions) {
            OpenStreetMapCircleOptions osmCircleOptions = (OpenStreetMapCircleOptions) circleOptions;

            Polygon circle = new Polygon(mapView);

            Runnable updateCircle = () -> {
                ArrayList<GeoPoint> circlePoints = new ArrayList<>();

                for(float f = 0; f < 360; f+= 1) {
                    circlePoints.add(osmCircleOptions.center.destinationPoint(osmCircleOptions.radius, f));
                }

                circle.setPoints(circlePoints);
                circle.getFillPaint().setColor(osmCircleOptions.fillColor);
                circle.getOutlinePaint().setColor(osmCircleOptions.strokeColor);
            };

            updateCircle.run();

            mapView.getOverlays().add(circle);

            return new ICircle() {
                @Override
                public void setStrokeColor(int color) {
                    osmCircleOptions.strokeColor = color;
                    updateCircle.run();
                }

                @Override
                public void setFillColor(int color) {
                    osmCircleOptions.fillColor = color;
                    updateCircle.run();
                }

                @Override
                public void setRadius(double newRadius) {
                    osmCircleOptions.radius = newRadius;
                    updateCircle.run();
                }

                @Override
                public double getRadius() {
                    return osmCircleOptions.radius;
                }

                @Override
                public void setCenter(LatLng latLng) {
                    osmCircleOptions.center = new GeoPoint(latLng.latitude, latLng.longitude);
                    updateCircle.run();
                }

                @Override
                public void remove() {
                    mapView.getOverlays().remove(circle);
                }
            };
        }

        @Override
        public void setOnMyLocationChangeListener(Consumer<Location> callback) {
            onMyLocationChangeListener = callback;
        }

        @Override
        public void setOnMarkerClickListener(OnMarkerClickListener markerClickListener) {
            onMarkerClickListener = markerClickListener;
        }

        @Override
        public void setOnCameraMoveListener(Runnable callback)
        {
            onCameraMoveListener = callback;
        }

        public static final class OpenStreetMapCircleOptions implements ICircleOptions {
            private double radius = 1;
            private GeoPoint center;
            private int strokeColor = -1;
            private int fillColor = -1;

            @Override
            public ICircleOptions center(LatLng latLng) {
                center = new GeoPoint(latLng.latitude, latLng.longitude);
                return this;
            }

            @Override
            public ICircleOptions radius(double radius) {
                this.radius = 1;
                return this;
            }

            @Override
            public ICircleOptions strokeColor(int color) {
                strokeColor = color;
                return this;
            }

            @Override
            public ICircleOptions fillColor(int color) {
                fillColor = color;
                return this;
            }

            @Override
            public ICircleOptions strokePattern(List<PatternItem> patternItems) {
                return this;
            }

            @Override
            public ICircleOptions strokeWidth(int width) {
                return this;
            }
        }

    }

    public final static class OpenStreetMapProjection implements IProjection {
        private final Projection projection;

        OpenStreetMapProjection(Projection projection) {
            this.projection = projection;
        }

        @Override
        public Point toScreenLocation(LatLng latLng) {
            return new Point((int) projection.getLongPixelXFromLongitude(latLng.longitude), (int) projection.getLongPixelYFromLatitude(latLng.latitude));
        }
    }

    public final static class OpenStreetMapUISettings implements IUISettings {
        @Override
        public void setMyLocationButtonEnabled(boolean enabled) {

        }

        @Override
        public void setZoomControlsEnabled(boolean enabled) {

        }

        @Override
        public void setCompassEnabled(boolean enabled) {

        }
    }

    public static final class OpenStreetMapCircleOptions implements ICircleOptions {
        @Override
        public ICircleOptions center(LatLng latLng) {
            return this;
        }

        @Override
        public ICircleOptions radius(double radius) {
            return this;
        }

        @Override
        public ICircleOptions strokeColor(int color) {
            return this;
        }

        @Override
        public ICircleOptions fillColor(int color) {
            return this;
        }

        @Override
        public ICircleOptions strokePattern(List<PatternItem> patternItems) {
            return this;
        }

        @Override
        public ICircleOptions strokeWidth(int width) {
            return this;
        }
    }

    public final static class OpenStreetMapMarkerOptions implements IMarkerOptions {
        public GeoPoint position = null;
        public String title = null;

        @Override
        public IMarkerOptions position(LatLng latLng) {
            position = new GeoPoint(latLng.latitude, latLng.longitude);
            return this;
        }

        @Override
        public IMarkerOptions icon(Bitmap bitmap) {
            return this;
        }

        @Override
        public IMarkerOptions icon(int resId) {
            return this;
        }

        @Override
        public IMarkerOptions anchor(float lat, float lng) {
            return this;
        }

        @Override
        public IMarkerOptions title(String title) {
            this.title = title;
            return this;
        }

        @Override
        public IMarkerOptions snippet(String snippet) {
            return this;
        }

        @Override
        public IMarkerOptions flat(boolean flat) {
            return this;
        }
    }

    public static final class OpenStreetMapLatLngBoundsBuilder implements ILatLngBoundsBuilder {
        private final List<GeoPoint> points = new ArrayList<>();

        @Override
        public ILatLngBoundsBuilder include(LatLng latLng) {
            this.points.add(new GeoPoint(latLng.latitude, latLng.longitude));
            return this;
        }

        @Override
        public ILatLngBounds build() {
            return new OpenStreetMapLatLngBounds(points);
        }
    }

    public static final class OpenStreetMapLatLngBounds implements ILatLngBounds {
        private final BoundingBox boundingBox;

        public OpenStreetMapLatLngBounds(List<GeoPoint> points) {
            Double minLatitude = null;
            Double maxLatitude = null;
            Double minLongitude = null;
            Double maxLongitude = null;

            for(int i = 0; i < points.size(); i++) {
                GeoPoint point = points.get(i);

                if(minLatitude == null || point.getLatitude() < minLatitude) {
                    minLatitude = point.getLatitude();
                }

                if(maxLatitude == null || point.getLatitude() > maxLatitude) {
                    maxLatitude = point.getLatitude();
                }

                if(minLongitude == null || point.getLongitude() < minLongitude) {
                    minLongitude = point.getLongitude();
                }

                if(maxLongitude == null || point.getLongitude() > maxLongitude) {
                    maxLongitude = point.getLongitude();
                }
            }

            if(minLatitude == null) {
                throw new IllegalStateException();
            }

            boundingBox = new BoundingBox(minLatitude, minLongitude, maxLatitude, maxLongitude);
        }

        @Override
        public LatLng getCenter() {
            return new LatLng(boundingBox.getCenterLatitude(), boundingBox.getCenterLongitude());
        }
    }

    public final static class OpenStreetMapView implements IMapView {
        private final org.osmdroid.views.MapView mapView;
        private GLSurfaceView glSurfaceView;

        private ITouchInterceptor dispatchInterceptor;
        private ITouchInterceptor interceptInterceptor;
        private Runnable onLayoutListener;

        public OpenStreetMapView(Context context) {
            IConfigurationProvider config = Configuration.getInstance();
            config.load(context, PreferenceManager.getDefaultSharedPreferences(context));

            mapView = new MapView(context) {
                @Override
                public boolean dispatchTouchEvent(MotionEvent event) {
                    if(dispatchInterceptor != null) {
                        return dispatchInterceptor.onInterceptTouchEvent(event, super::dispatchTouchEvent);
                    }

                    return super.dispatchTouchEvent(event);
                }

                @Override
                public boolean onInterceptTouchEvent(MotionEvent event) {
                    if(interceptInterceptor != null) {
                        return interceptInterceptor.onInterceptTouchEvent(event, super::onInterceptTouchEvent);
                    }

                    return super.onInterceptTouchEvent(event);
                }

                @Override
                protected void onLayout(boolean changed, int l, int t, int r, int b) {
                    super.onLayout(changed, l, t, r, b);

                    if(onLayoutListener != null) {
                        onLayoutListener.run();
                    }
                }
            };

            mapView.setMultiTouchControls(true);
        }

        @Override
        public View getView() {
            return mapView;
        }

        @Override
        public void getMapAsync(Consumer<IMap> callback) {
            findGlSurfaceView(mapView);
            callback.accept(new OpenStreetMap(mapView.getController(), mapView));
        }

        @Override
        public GLSurfaceView getGlSurfaceView() {
            return glSurfaceView;
        }

        private void findGlSurfaceView(View v) {
            if (v instanceof GLSurfaceView) {
                glSurfaceView = (GLSurfaceView) v;
            }

            if (v instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) v;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    findGlSurfaceView(vg.getChildAt(i));
                }
            }
        }

        @Override
        public void onResume() {
            mapView.onResume();
        }

        @Override
        public void onPause() {
            mapView.onPause();
        }

        @Override
        public void onCreate(Bundle savedInstance) {

        }

        @Override
        public void onDestroy() {

        }

        @Override
        public void onLowMemory() {

        }

        @Override
        public void setOnDispatchTouchEventInterceptor(ITouchInterceptor touchInterceptor) {
            dispatchInterceptor = touchInterceptor;
        }

        @Override
        public void setOnInterceptTouchEventInterceptor(ITouchInterceptor touchInterceptor) {
            interceptInterceptor = touchInterceptor;
        }

        @Override
        public void setOnLayoutListener(Runnable callback) {
            onLayoutListener = callback;
        }
    }

    public static final class OpenStreetMapCameraUpdate implements ICameraUpdate {
        public GeoPoint center = null;
        public Double zoom = null;
        public BoundingBox boundingBox = null;

        public OpenStreetMapCameraUpdate(GeoPoint center) {
            this.center = center;
        }

        public OpenStreetMapCameraUpdate(GeoPoint center, float zoom) {
            this.center = center;
            this.zoom = (double) zoom;
        }

        public OpenStreetMapCameraUpdate(BoundingBox boundingBox) {
            this.boundingBox = boundingBox;
        }
    }


    public final static class OpenStreetMapStyleOptions implements IMapStyleOptions {
    }
}