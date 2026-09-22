package com.example.bunnycare;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Maps extends Fragment {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 101;

    private static final double DEFAULT_LAT = 14.6760;
    private static final double DEFAULT_LNG = 120.5360;

    private MapView mapView;
    private IMapController mapController;
    private FusedLocationProviderClient fusedLocationClient;

    public static final OnlineTileSourceBase MYMAPNIK = new XYTileSource("Mapnik",
            0, 19, 256, ".png", new String[]{
            "https://tile.openstreetmap.org/"}, "© OpenStreetMap contributors",
            new TileSourcePolicy(1,
                    TileSourcePolicy.FLAG_NO_BULK
                            | TileSourcePolicy.FLAG_NO_PREVENTIVE
                            | TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL
                            | TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED
            ));

    private Marker userMarker;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;

    private boolean isVerifiedVet = false;
    private boolean isVerifiedFeedSupplier = false;

    private FolderOverlay vetLayer;
    private FolderOverlay feedLayer;
    private FolderOverlay approvedPinLayer;
    private ListenerRegistration pinsListener;

    private double lastKnownLat, lastKnownLng;

    private static final String TYPE_VET = "vet";
    private static final String TYPE_FEED = "feed";

    class Vet {
        double lat, lng;
        String name;
        double distance;

        Vet(double lat, double lng, String name) {
            this.lat = lat;
            this.lng = lng;
            this.name = name;
        }
    }

    class FeedSeller {
        double lat, lng;
        String name;
        double distance;

        FeedSeller(double lat, double lng, String name) {
            this.lat = lat;
            this.lng = lng;
            this.name = name;
        }
    }

    public Maps() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_maps, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        Configuration.getInstance().load(
                requireContext(),
                PreferenceManager.getDefaultSharedPreferences(requireContext())
        );

        Configuration.getInstance().setUserAgentValue(requireContext().getPackageName());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        mapView = view.findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.OpenTopo);
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(true);

        mapController = mapView.getController();
        mapController.setZoom(10.0);

        MapEventsOverlay longPressOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                return false;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                onMapLongPress(p);
                return true;
            }
        });

        mapView.getOverlays().add(longPressOverlay);

        checkVerificationStatus();
        getCurrentLocation();

        mapView.setOnClickListener(v -> {
            mapController.animateTo(mapView.getMapCenter());
            mapController.setZoom(15.0);
        });
    }

    private void checkVerificationStatus() {

        isVerifiedVet = false;
        isVerifiedFeedSupplier = false;

        if (currentUser == null) return;

        db.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(doc -> {

                    if (!doc.exists()) return;

                    Boolean verifiedVet = doc.getBoolean("verifiedVet");
                    Boolean verifiedFeedSupplier = doc.getBoolean("verifiedFeedSupplier");

                    isVerifiedVet = verifiedVet != null && verifiedVet;
                    isVerifiedFeedSupplier = verifiedFeedSupplier != null && verifiedFeedSupplier;

                    if (!isVerifiedVet && !isVerifiedFeedSupplier) {
                        Boolean oldVerified = doc.getBoolean("verified");
                        isVerifiedVet = oldVerified != null && oldVerified;
                    }
                });
    }

    private void onMapLongPress(GeoPoint point) {

        if (getContext() == null) return;

        if (currentUser == null) {
            Toast.makeText(
                    getContext(),
                    "Please log in first.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (!isVerifiedVet && !isVerifiedFeedSupplier) {
            Toast.makeText(
                    getContext(),
                    "Only registered vets or feed suppliers can add a pin.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (isVerifiedVet && isVerifiedFeedSupplier) {
            showPinTypeSelection(point);
        } else if (isVerifiedVet) {
            promptAddPin(point, TYPE_VET);
        } else {
            promptAddPin(point, TYPE_FEED);
        }
    }

    private void showPinTypeSelection(GeoPoint point) {

        String[] options = {
                "Veterinary Clinic",
                "Feed Supplier"
        };

        new AlertDialog.Builder(requireContext())
                .setTitle("Select Pin Type")
                .setItems(options, (dialog, which) -> {

                    if (which == 0) {
                        promptAddPin(point, TYPE_VET);
                    } else {
                        promptAddPin(point, TYPE_FEED);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptAddPin(GeoPoint point, String type) {

        if (TYPE_VET.equals(type) && !isVerifiedVet) {
            Toast.makeText(
                    requireContext(),
                    "Only registered veterinarians can add veterinary clinic pins.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (TYPE_FEED.equals(type) && !isVerifiedFeedSupplier) {
            Toast.makeText(
                    requireContext(),
                    "Only registered feed suppliers can add feed supplier pins.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        int paddingPx = (int) (16 * getResources().getDisplayMetrics().density);

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(
                paddingPx,
                paddingPx,
                paddingPx,
                paddingPx
        );

        EditText nameInput = new EditText(requireContext());

        if (TYPE_VET.equals(type)) {
            nameInput.setHint("Clinic name");
        } else {
            nameInput.setHint("Feed store name");
        }

        layout.addView(nameInput);

        String title;

        if (TYPE_VET.equals(type)) {
            title = "Add Veterinary Clinic Pin";
        } else {
            title = "Add Feed Supplier Pin";
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage("Your pin will appear on the map immediately.")
                .setView(layout)
                .setPositiveButton("Submit", (dialog, which) -> {

                    String name = nameInput.getText().toString().trim();

                    if (name.isEmpty()) {
                        Toast.makeText(
                                requireContext(),
                                "Please enter a name",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    submitPin(point, name, type);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void submitPin(GeoPoint point, String name, String type) {

        if (currentUser == null) {
            Toast.makeText(
                    requireContext(),
                    "Please log in first.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (TYPE_VET.equals(type) && !isVerifiedVet) {
            Toast.makeText(
                    requireContext(),
                    "Only registered veterinarians can add veterinary clinic pins.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (TYPE_FEED.equals(type) && !isVerifiedFeedSupplier) {
            Toast.makeText(
                    requireContext(),
                    "Only registered feed suppliers can add feed supplier pins.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        Map<String, Object> pin = new HashMap<>();

        pin.put("userId", currentUser.getUid());
        pin.put("ownerId", currentUser.getUid());
        pin.put("vetId", currentUser.getUid());
        pin.put("vetName", currentUser.getDisplayName());
        pin.put("userEmail", currentUser.getEmail());
        pin.put("userName", currentUser.getDisplayName());
        pin.put("clinicName", name);
        pin.put("type", type);
        pin.put("latitude", point.getLatitude());
        pin.put("longitude", point.getLongitude());
        pin.put("status", "approved");
        pin.put("submittedAt", FieldValue.serverTimestamp());

        if (TYPE_VET.equals(type)) {
            pin.put("verificationType", "veterinarian");
        } else {
            pin.put("verificationType", "feed_supplier");
        }

        db.collection("vetPins")
                .add(pin)
                .addOnSuccessListener(docRef -> {

                    if (getContext() == null) return;

                    if (TYPE_VET.equals(type)) {
                        Toast.makeText(
                                getContext(),
                                "Veterinary clinic pin added to the map.",
                                Toast.LENGTH_SHORT
                        ).show();
                    } else {
                        Toast.makeText(
                                getContext(),
                                "Feed supplier pin added to the map.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                })
                .addOnFailureListener(e -> {

                    if (getContext() == null) return;

                    Toast.makeText(
                            getContext(),
                            "Couldn't submit pin: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void loadApprovedPins() {

        if (pinsListener != null) return;

        pinsListener = db.collection("vetPins")
                .whereEqualTo("status", "approved")
                .addSnapshotListener((value, error) -> {

                    if (error != null ||
                            value == null ||
                            getContext() == null ||
                            mapView == null) {
                        return;
                    }

                    if (approvedPinLayer != null) {
                        mapView.getOverlays().remove(approvedPinLayer);
                    }

                    approvedPinLayer = new FolderOverlay();
                    mapView.getOverlays().add(approvedPinLayer);

                    Drawable vetIcon = makeScaledIcon(
                            R.drawable.pin_vet,
                            96,
                            130
                    );

                    Drawable feedIcon = makeScaledIcon(
                            R.drawable.pin_feed_seller,
                            96,
                            130
                    );

                    for (DocumentSnapshot doc : value.getDocuments()) {

                        Double lat = doc.getDouble("latitude");
                        Double lng = doc.getDouble("longitude");
                        String clinicName = doc.getString("clinicName");
                        String type = doc.getString("type");

                        boolean isFeed = TYPE_FEED.equals(type);

                        if (lat == null || lng == null) continue;

                        Marker marker = new Marker(mapView);

                        marker.setPosition(
                                new GeoPoint(lat, lng)
                        );

                        if (isFeed) {

                            marker.setTitle(
                                    "🌾 Feed Seller: " +
                                            (clinicName != null
                                                    ? clinicName
                                                    : "Verified Feed Store")
                            );

                            marker.setSnippet(
                                    "Verified Feed Supplier"
                            );

                            marker.setIcon(feedIcon);

                        } else {

                            marker.setTitle(
                                    "🐾 Vet: " +
                                            (clinicName != null
                                                    ? clinicName
                                                    : "Verified Clinic")
                            );

                            marker.setSnippet(
                                    "Verified Veterinary Clinic"
                            );

                            marker.setIcon(vetIcon);
                        }

                        marker.setAnchor(
                                Marker.ANCHOR_CENTER,
                                Marker.ANCHOR_BOTTOM
                        );

                        approvedPinLayer.add(marker);
                    }

                    mapView.invalidate();
                });
    }

    private double distanceMeters(
            double lat1,
            double lon1,
            double lat2,
            double lon2) {

        double R = 6371000;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a =
                Math.sin(dLat / 2) *
                        Math.sin(dLat / 2) +
                        Math.cos(Math.toRadians(lat1)) *
                                Math.cos(Math.toRadians(lat2)) *
                                Math.sin(dLon / 2) *
                                Math.sin(dLon / 2);

        double c = 2 * Math.atan2(
                Math.sqrt(a),
                Math.sqrt(1 - a)
        );

        return R * c;
    }

    private void sortVetsByNearest(
            List<Vet> vets,
            double userLat,
            double userLng) {

        for (Vet v : vets) {
            v.distance = distanceMeters(
                    userLat,
                    userLng,
                    v.lat,
                    v.lng
            );
        }

        vets.sort(
                (a, b) -> Double.compare(
                        a.distance,
                        b.distance
                )
        );
    }

    private void sortFeedsByNearest(
            List<FeedSeller> feeds,
            double userLat,
            double userLng) {

        for (FeedSeller f : feeds) {
            f.distance = distanceMeters(
                    userLat,
                    userLng,
                    f.lat,
                    f.lng
            );
        }

        feeds.sort(
                (a, b) -> Double.compare(
                        a.distance,
                        b.distance
                )
        );
    }

    private boolean hasLocationPermission() {

        return ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void getCurrentLocation() {

        if (!hasLocationPermission()) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST_CODE
            );

            return;
        }

        fusedLocationClient
                .getLastLocation()
                .addOnSuccessListener(
                        requireActivity(),
                        location -> {

                            if (location != null) {
                                onLocationReady(
                                        location.getLatitude(),
                                        location.getLongitude()
                                );
                            } else {
                                requestFreshLocation();
                            }
                        }
                )
                .addOnFailureListener(
                        requireActivity(),
                        e -> requestFreshLocation()
                );
    }

    @SuppressLint("MissingPermission")
    private void requestFreshLocation() {

        if (!isAdded()) return;

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {

            useFallbackLocation();
            return;
        }

        try {

            CancellationTokenSource cancellationTokenSource =
                    new CancellationTokenSource();

            fusedLocationClient
                    .getCurrentLocation(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            cancellationTokenSource.getToken()
                    )
                    .addOnSuccessListener(
                            requireActivity(),
                            location -> {

                                if (location != null) {

                                    onLocationReady(
                                            location.getLatitude(),
                                            location.getLongitude()
                                    );

                                } else {
                                    useFallbackLocation();
                                }
                            }
                    )
                    .addOnFailureListener(
                            requireActivity(),
                            e -> useFallbackLocation()
                    );

        } catch (SecurityException e) {
            useFallbackLocation();
        }
    }

    private void useFallbackLocation() {

        if (!isAdded()) return;

        Toast.makeText(
                getContext(),
                "Couldn't get your location. Showing Balanga instead.",
                Toast.LENGTH_SHORT
        ).show();

        onLocationReady(
                DEFAULT_LAT,
                DEFAULT_LNG
        );
    }

    private void onLocationReady(
            double userLat,
            double userLng) {

        if (!isAdded() || mapView == null) return;

        lastKnownLat = userLat;
        lastKnownLng = userLng;

        GeoPoint userPoint =
                new GeoPoint(userLat, userLng);

        mapController.setCenter(userPoint);
        mapController.setZoom(14.0);

        showUserMarker(userPoint);

        loadVets(userLat, userLng);
        loadFeedSellers(userLat, userLng);
        loadApprovedPins();

        mapView.invalidate();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {

            if (grantResults.length > 0 &&
                    grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED) {

                getCurrentLocation();

            } else {

                useFallbackLocation();
            }
        }
    }

    private Drawable makeUserLocationIcon() {

        int size = 64;

        Bitmap bmp = Bitmap.createBitmap(
                size,
                size,
                Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(bmp);

        Paint paint = new Paint();
        paint.setAntiAlias(true);

        paint.setColor(Color.WHITE);

        canvas.drawCircle(
                size / 2f,
                size / 2f,
                size / 2f,
                paint
        );

        paint.setColor(
                Color.parseColor("#1A73E8")
        );

        canvas.drawCircle(
                size / 2f,
                size / 2f,
                size / 2f - 8f,
                paint
        );

        return new BitmapDrawable(
                getResources(),
                bmp
        );
    }

    private void showUserMarker(GeoPoint userPoint) {

        if (userMarker != null) {
            mapView.getOverlays().remove(userMarker);
        }

        userMarker = new Marker(mapView);

        userMarker.setPosition(userPoint);
        userMarker.setTitle("You are here");
        userMarker.setIcon(makeUserLocationIcon());

        userMarker.setAnchor(
                Marker.ANCHOR_CENTER,
                Marker.ANCHOR_CENTER
        );

        mapView.getOverlays().add(userMarker);
    }

    private void loadVets(
            double userLat,
            double userLng) {

        List<Vet> vets = new ArrayList<>();

        vets.add(new Vet(
                14.66864878815696,
                120.54425441716226,
                "Easyvet Cupang Branch - Pet Supplies & Veterinary Services"
        ));

        vets.add(new Vet(
                14.674436487056353,
                120.54718269535796,
                "PENINSULA VETERINARY CLINIC"
        ));

        vets.add(new Vet(
                14.678788762953449,
                120.54162734120344,
                "Man´s Best Friend Veterinary Clinics"
        ));

        vets.add(new Vet(
                14.681817404737464,
                120.54355199074746,
                "Pet Hub Veterinary Hospital - Bataan"
        ));

        vets.add(new Vet(
                14.684225208214851,
                120.53775841911424,
                "Veterinary Clinic at PE SM Bataan"
        ));

        vets.add(new Vet(
                14.686466932444512,
                120.53951794827692,
                "ABC Animal Bite Center"
        ));

        vets.add(new Vet(
                14.704648957282709,
                120.5375867578363,
                "Bfc Animal Clinic"
        ));

        vets.add(new Vet(
                14.677043233369728,
                120.5359559748471,
                "Pet Needs Veterinary Care"
        ));

        vets.add(new Vet(
                14.678579282525043,
                120.52784497456055,
                "PETSTOP Animal Clinic"
        ));

        vets.add(new Vet(
                14.672808663549786,
                120.527501651784,
                "Easyvet Balanga Main Branch"
        ));

        vets.add(new Vet(
                14.619125,
                120.563875,
                "Salubrious Toptails Animal Clinic and Grooming Center"
        ));

        vets.add(new Vet(
                14.620938,
                120.579188,
                "BFC Animal Clinic"
        ));

        vets.add(new Vet(
                14.6657,
                120.5593,
                "Peninsula Veterinary Clinic"
        ));

        sortVetsByNearest(
                vets,
                userLat,
                userLng
        );

        Drawable icon = makeScaledIcon(
                R.drawable.pin_vet,
                96,
                130
        );

        Drawable nearestIcon = makeScaledIcon(
                R.drawable.pin_vet,
                96,
                130,
                true
        );

        if (vetLayer != null) {
            mapView.getOverlays().remove(vetLayer);
        }

        vetLayer = new FolderOverlay();

        mapView.getOverlays().add(vetLayer);

        for (int i = 0; i < vets.size(); i++) {

            Vet v = vets.get(i);

            Marker marker = new Marker(mapView);

            marker.setPosition(
                    new GeoPoint(
                            v.lat,
                            v.lng
                    )
            );

            marker.setTitle(
                    "🐾 Vet: " + v.name
            );

            if (i == 0) {

                marker.setSnippet(
                        "NEAREST VETERINARY CLINIC"
                );

                marker.setIcon(nearestIcon);

            } else {

                marker.setSnippet(
                        "Veterinary Clinic"
                );

                marker.setIcon(icon);
            }

            marker.setAnchor(
                    Marker.ANCHOR_CENTER,
                    Marker.ANCHOR_BOTTOM
            );

            vetLayer.add(marker);
        }
    }

    private void loadFeedSellers(
            double userLat,
            double userLng) {

        List<FeedSeller> feeds =
                new ArrayList<>();

        feeds.add(new FeedSeller(
                14.672139308142011,
                120.54996505969378,
                "G4R Poultry Supply"
        ));

        feeds.add(new FeedSeller(
                14.678108937105124,
                120.54397455803918,
                "JF Agrivet Supply"
        ));

        feeds.add(new FeedSeller(
                14.679714774476277,
                120.54249497636003,
                "Bataan Farmer's Center"
        ));

        feeds.add(new FeedSeller(
                14.68285659598325,
                120.54090713257439,
                "Vinshe Pet Store"
        ));

        feeds.add(new FeedSeller(
                14.680866780968083,
                120.53545794134182,
                "Botchikit’s Poultry Feeds"
        ));

        feeds.add(new FeedSeller(
                14.671615648815195,
                120.53675708621026,
                "Poultry Hub"
        ));

        feeds.add(new FeedSeller(
                14.665331637873322,
                120.53361748589477,
                "Ava's Pet Station"
        ));

        feeds.add(new FeedSeller(
                14.676540701734748,
                120.52366234271304,
                "MBCom Feeds Outlet"
        ));

        feeds.add(new FeedSeller(
                14.662287396654012,
                120.56529244192595,
                "RC's Animal Feeds Trading"
        ));

        feeds.add(new FeedSeller(
                14.592451964805823,
                120.5878109546487,
                "BFF PET AND POULTRY SUPPLIES"
        ));

        sortFeedsByNearest(
                feeds,
                userLat,
                userLng
        );

        Drawable feedIcon = makeScaledIcon(
                R.drawable.pin_feed_seller,
                96,
                130
        );

        Drawable nearestFeedIcon = makeScaledIcon(
                R.drawable.pin_feed_seller,
                96,
                130,
                true
        );

        if (feedLayer != null) {
            mapView.getOverlays().remove(feedLayer);
        }

        feedLayer = new FolderOverlay();

        mapView.getOverlays().add(feedLayer);

        for (int i = 0; i < feeds.size(); i++) {

            FeedSeller f = feeds.get(i);

            Marker marker = new Marker(mapView);

            marker.setPosition(
                    new GeoPoint(
                            f.lat,
                            f.lng
                    )
            );

            marker.setTitle(
                    "🌾 Feed Seller: " + f.name
            );

            if (i == 0) {

                marker.setSnippet(
                        "NEAREST FEED STORE"
                );

                marker.setIcon(
                        nearestFeedIcon
                );

            } else {

                marker.setSnippet(
                        "Agrivet / Feed Store"
                );

                marker.setIcon(
                        feedIcon
                );
            }

            marker.setAnchor(
                    Marker.ANCHOR_CENTER,
                    Marker.ANCHOR_BOTTOM
            );

            feedLayer.add(marker);
        }
    }

    private Drawable makeScaledIcon(
            int drawableRes,
            int widthPx,
            int heightPx) {

        return makeScaledIcon(
                drawableRes,
                widthPx,
                heightPx,
                false
        );
    }

    private Drawable makeScaledIcon(
            int drawableRes,
            int widthPx,
            int heightPx,
            boolean highlightNearest) {

        Bitmap bitmap =
                BitmapFactory.decodeResource(
                        getResources(),
                        drawableRes
                );

        Bitmap scaled =
                Bitmap.createScaledBitmap(
                        bitmap,
                        widthPx,
                        heightPx,
                        true
                );

        if (highlightNearest) {
            scaled = addRedBorder(scaled);
        }

        return new BitmapDrawable(
                getResources(),
                scaled
        );
    }

    private Bitmap addRedBorder(Bitmap src) {

        float borderWidth = 8f;

        Bitmap bordered =
                Bitmap.createBitmap(
                        src.getWidth(),
                        src.getHeight(),
                        Bitmap.Config.ARGB_8888
                );

        Canvas canvas =
                new Canvas(bordered);

        canvas.drawBitmap(
                src,
                0,
                0,
                null
        );

        Paint paint = new Paint();

        paint.setAntiAlias(true);
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(borderWidth);

        canvas.drawRect(
                borderWidth / 2f,
                borderWidth / 2f,
                src.getWidth() -
                        borderWidth / 2f,
                src.getHeight() -
                        borderWidth / 2f,
                paint
        );

        return bordered;
    }

    @Override
    public void onResume() {

        super.onResume();

        if (mapView != null) {
            mapView.onResume();
        }

        if (currentUser != null) {
            checkVerificationStatus();
        }
    }

    @Override
    public void onPause() {

        super.onPause();

        if (mapView != null) {
            mapView.onPause();
        }
    }

    @Override
    public void onDestroyView() {

        super.onDestroyView();

        if (pinsListener != null) {
            pinsListener.remove();
            pinsListener = null;
        }

        if (mapView != null) {
            mapView.onDetach();
        }

        vetLayer = null;
        feedLayer = null;
        approvedPinLayer = null;
        userMarker = null;
        mapView = null;
    }
}