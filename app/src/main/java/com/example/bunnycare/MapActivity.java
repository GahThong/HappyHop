package com.example.bunnycare;

import static org.mapsforge.map.rendertheme.rule.RenderThemeHandler.getRenderTheme;

import android.app.Activity;
import android.content.res.AssetManager;
import android.graphics.BitmapFactory;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.core.model.Rotation;
import org.mapsforge.core.model.Tag;
import org.mapsforge.map.android.graphics.AndroidBitmap;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.layers.MyLocationOverlay;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.GroupLayer;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.model.MapViewPosition;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;
import org.mapsforge.poi.android.storage.AndroidPoiPersistenceManagerFactory;
import org.mapsforge.poi.storage.ExactMatchPoiCategoryFilter;
import org.mapsforge.poi.storage.PoiCategoryFilter;
import org.mapsforge.poi.storage.PoiCategoryManager;
import org.mapsforge.poi.storage.PoiPersistenceManager;
import org.mapsforge.poi.storage.PointOfInterest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;



public class MapActivity extends AppCompatActivity {
    private static final String MAPFILE = "world.map";
    private static final String POI_FILE = "philippines.poi";
    private GroupLayer groupLayer;
    private static final String POI_CATEGORY = "Veterinary";
    private MapView mapView;
    private TileCache tileCache;
    private TileRendererLayer tileRendererLayer;
    private MyLocationOverlay myLocationOverlay;
    private MapViewPosition mapViewPosition;
    private LocationManager locationManager;
    private ExecutorService poiExecutor;
    private Handler poiHandler;
    private PoiSearchTask poiTask;
    private PoiPersistenceManager persistenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AndroidGraphicFactory.createInstance(this.getApplication());

        setContentView(R.layout.activity_map);

        mapView = findViewById(R.id.mapView);

        this.mapView.setClickable(true);
        this.mapView.getMapScaleBar().setVisible(true);
        this.mapView.setBuiltInZoomControls(true);
        this.mapView.getMapZoomControls().setZoomLevelMin((byte) 10);
        this.mapView.getMapZoomControls().setZoomLevelMax((byte) 20);

        // create a tile cache of suitable size
        this.tileCache = AndroidUtil.createTileCache(this, "mapcache",
                mapView.getModel().displayModel.getTileSize(), 1,
                this.mapView.getModel().frameBufferModel.getOverdrawFactor());

        persistenceManager = AndroidPoiPersistenceManagerFactory.getPoiPersistenceManager(getCacheDir() + "/" + POI_FILE);
        poiExecutor = Executors.newSingleThreadExecutor();
        poiHandler = new Handler(Looper.getMainLooper());
        poiTask = new PoiSearchTask(this, POI_CATEGORY, mapView.getBoundingBox());
    }

    @Override
    protected void onStart() {
        super.onStart();

        this.mapView.getModel().mapViewPosition.setCenter(new LatLong(13.000, 122.000));
        this.mapView.getModel().mapViewPosition.setZoomLevel((byte) 12);

        // tile renderer layer using internal render theme
        copyAssets();
        MapDataStore mapDataStore = new MapFile(getCacheDir() + "/" + MAPFILE);
        this.tileRendererLayer = new TileRendererLayer(tileCache, mapDataStore,
                this.mapView.getModel().mapViewPosition, AndroidGraphicFactory.INSTANCE);

        tileRendererLayer.setXmlRenderTheme(MapsforgeThemes.MOTORIDER);

        // only once a layer is associated with a mapView the rendering starts
        this.mapView.getLayerManager().getLayers().add(tileRendererLayer);
        poiExecutor.execute(poiTask);
    }
    private void copyAssets() {
        AssetManager assetManager = getAssets();
        String[] files = null;
        try {
            files = assetManager.list("");
        } catch (IOException e) {
            Log.e("tag", "Failed to get asset file list.", e);
        }
        for (String filename : files) {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = assetManager.open(filename);

                String outDir = String.valueOf(getApplicationContext().getCacheDir());

                File outFile = new File(outDir, filename);

                out = new FileOutputStream(outFile);
                copyFile(in, out);
                in.close();
                in = null;
                out.flush();
                out.close();
                out = null;
            } catch (IOException e) {
                Log.e("tag", "Failed to copy asset file: " + filename, e);
            }
        }
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.mapView.destroy();
    }

    private class PoiSearchTask implements Runnable {
        private final WeakReference< MapActivity > weakActivity;
        private final String category;
        private BoundingBox boundingBox;
        private Collection<PointOfInterest> pointOfInterests;

        private PoiSearchTask(MapActivity activity, String category, BoundingBox boundingBox) {
            this.weakActivity = new WeakReference < > (activity);
            this.category = category;
            this.boundingBox = boundingBox;
        }

        public void setBoundingBox(BoundingBox newBoundingBox) {
            this.boundingBox = newBoundingBox;
        }

        @Override
        public void run() {
            try {
                PoiCategoryManager categoryManager = persistenceManager.getCategoryManager();
                PoiCategoryFilter categoryFilter = new ExactMatchPoiCategoryFilter();
                categoryFilter.addCategory(categoryManager.getPoiCategoryByTitle("Health"));
                categoryFilter.addCategory(categoryManager.getPoiCategoryByTitle("Dog Map"));
                categoryFilter.addCategory(categoryManager.getPoiCategoryByTitle("Shopping"));
                ArrayList<Tag> tagArray = new ArrayList<>();
                tagArray.add(new Tag("shop:pet", "*"));
                pointOfInterests = persistenceManager.findInRect(this.boundingBox, categoryFilter, null, null, Integer.MAX_VALUE, true);
                poiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        final MapActivity activity = weakActivity.get();
                        if (activity == null) {
                            return;
                        }
                        Toast.makeText(activity, category + ": " + (pointOfInterests != null ? pointOfInterests.size() : 0), Toast.LENGTH_SHORT).show();
                        if (pointOfInterests == null) {
                            return;
                        }

                        // Overlay POI
                        groupLayer = new GroupLayer();
                        Bitmap bitmap = new AndroidBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.maps_icon));
                        for (final PointOfInterest pointOfInterest: pointOfInterests) {
                            Marker marker = new MarkerImpl(pointOfInterest.getLatLong(), bitmap, 0, -bitmap.getHeight() / 2, pointOfInterest);
                            groupLayer.layers.add(marker);
                        }
                        mapView.getLayerManager().getLayers().add(groupLayer);
                        mapView.getLayerManager().redrawLayers();
                    }
                });
            } catch (Exception e) {
                Log.e("MAP ERROR", e.toString(), e);
            }
        }
    }

    private class MarkerImpl extends Marker {
        private final PointOfInterest pointOfInterest;

        private MarkerImpl(LatLong latLong, Bitmap bitmap, int horizontalOffset, int verticalOffset, PointOfInterest pointOfInterest) {
            super(latLong, bitmap, horizontalOffset, verticalOffset);
            this.pointOfInterest = pointOfInterest;
        }

        @Override
        public boolean onTap(LatLong tapLatLong, Point layerXY, Point tapXY) {
            // GroupLayer does not have a position, layerXY is null
            layerXY = mapView.getMapViewProjection().toPixels(getPosition());
            if (!Rotation.noRotation(mapView.getMapRotation()) && layerXY != null) {
                layerXY = mapView.getMapRotation().rotate(layerXY, true);
            }
            if (contains(layerXY, tapXY, mapView)) {
                Toast.makeText(MapActivity.this, pointOfInterest.getName(), Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        }
    }

}