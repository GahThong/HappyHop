package com.example.bunnycare;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.api.IMapController;

import java.util.ArrayList;
import java.util.List;

public class Maps extends Fragment {

    private MapView mapView;
    private IMapController mapController;
    private FusedLocationProviderClient fusedLocationClient;

    private final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;

    private Marker userMarker;

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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StrictMode.ThreadPolicy policy =
                new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_maps, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {

        Configuration.getInstance().load(
                getActivity(),
                PreferenceManager.getDefaultSharedPreferences(getActivity())
        );


        Configuration.getInstance().setUserAgentValue(getActivity().getPackageName());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(getActivity());

        mapView = view.findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(true);

        mapController = mapView.getController();

        requestPermissionsIfNecessary(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION
        });

        mapController.setZoom(10.0);

        getCurrentLocation();

        mapView.setOnClickListener(v -> {
            mapController.animateTo(mapView.getMapCenter());
            mapController.setZoom(15.0);
        });
    }

    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                        Math.cos(Math.toRadians(lat1)) *
                                Math.cos(Math.toRadians(lat2)) *
                                Math.sin(dLon / 2) *
                                Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private void sortVetsByNearest(List<Vet> vets, double userLat, double userLng) {
        for (Vet v : vets) {
            v.distance = distanceMeters(userLat, userLng, v.lat, v.lng);
        }
        vets.sort((a, b) -> Double.compare(a.distance, b.distance));
    }

    private void computeFeedDistances(List<FeedSeller> feeds, double userLat, double userLng) {
        for (FeedSeller f : feeds) {
            f.distance = distanceMeters(userLat, userLng, f.lat, f.lng);
        }
    }

    private void getCurrentLocation() {

        if (ActivityCompat.checkSelfPermission(getActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    getActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    101
            );
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(getActivity(), location -> {

                    if (location != null) {

                        double userLat = location.getLatitude();
                        double userLng = location.getLongitude();

                        GeoPoint userPoint = new GeoPoint(userLat, userLng);

                        mapController.setCenter(userPoint);
                        mapController.setZoom(14.0);

                        showUserMarker(userPoint);
                        loadVets(userLat, userLng);
                        loadFeedSellers(userLat, userLng);
                    }
                });
    }

    private void showUserMarker(GeoPoint userPoint) {

        if (userMarker != null) {
            mapView.getOverlays().remove(userMarker);
        }

        userMarker = new Marker(mapView);
        userMarker.setPosition(userPoint);
        userMarker.setTitle("You are here");

        Drawable icon = ContextCompat.getDrawable(
                getActivity(),
                org.osmdroid.library.R.drawable.person
        );

        userMarker.setIcon(icon);
        userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);

        mapView.getOverlays().add(userMarker);
    }

    private void loadVets(double userLat, double userLng) {

        List<Vet> vets = new ArrayList<>();

        vets.add(new Vet(14.66864878815696, 120.54425441716226,
                "Easyvet Cupang Branch - Pet Supplies & Veterinary Services"));
        vets.add(new Vet(14.674436487056353, 120.54718269535796,
                "PENINSULA VETERINARY CLINIC"));
        vets.add(new Vet(14.678788762953449, 120.54162734120344,
                "Man´s Best Friend Veterinary Clinics"));
        vets.add(new Vet(14.681817404737464, 120.54355199074746,
                "Pet Hub Veterinary Hospital - Bataan"));
        vets.add(new Vet(14.684225208214851, 120.53775841911424,
                "Veterinary Clinic at PE SM Bataan"));
        vets.add(new Vet(14.686466932444512, 120.53951794827692,
                "ABC Animal Bite Center"));
        vets.add(new Vet(14.704648957282709, 120.5375867578363,
                "Bfc Animal Clinic"));
        vets.add(new Vet(14.677043233369728, 120.5359559748471,
                "Pet Needs Veterinary Care"));
        vets.add(new Vet(14.678579282525043, 120.52784497456055,
                "PETSTOP Animal Clinic"));
        vets.add(new Vet(14.672808663549786, 120.527501651784,
                "Easyvet Balanga Main Branch"));
        vets.add(new Vet(14.619125, 120.563875,
                "Salubrious Toptails Animal Clinic and Grooming Center"));
        vets.add(new Vet(14.620938, 120.579188,
                "BFC Animal Clinic"));
        vets.add(new Vet(14.6657, 120.5593,
                "Peninsula Veterinary Clinic"));

        sortVetsByNearest(vets, userLat, userLng);

        Drawable icon = ContextCompat.getDrawable(
                getActivity(),
                org.osmdroid.library.R.drawable.marker_default
        );

        FolderOverlay vetLayer = new FolderOverlay(getActivity());
        mapView.getOverlays().add(vetLayer);

        for (int i = 0; i < vets.size(); i++) {

            Vet v = vets.get(i);

            Marker marker = new Marker(mapView);
            marker.setPosition(new GeoPoint(v.lat, v.lng));

            marker.setTitle("🐾 Vet: " + v.name);

            if (i == 0) {
                marker.setSnippet("NEAREST VETERINARY CLINIC");
            } else {
                marker.setSnippet("Veterinary Clinic");
            }

            marker.setIcon(icon);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

            vetLayer.add(marker);
        }
    }

    private void loadFeedSellers(double userLat, double userLng) {

        List<FeedSeller> feeds = new ArrayList<>();

        feeds.add(new FeedSeller(14.672139308142011, 120.54996505969378, "G4R Poultry Supply"));
        feeds.add(new FeedSeller(14.678108937105124, 120.54397455803918, "JF Agrivet Supply"));
        feeds.add(new FeedSeller(14.679714774476277, 120.54249497636003, "Bataan Farmer's Center"));
        feeds.add(new FeedSeller(14.68285659598325, 120.54090713257439, "Vinshe Pet Store"));
        feeds.add(new FeedSeller(14.680866780968083, 120.53545794134182, "Botchikit’s Poultry Feeds"));
        feeds.add(new FeedSeller(14.671615648815195, 120.53675708621026, "Poultry Hub"));
        feeds.add(new FeedSeller(14.665331637873322, 120.53361748589477, "Ava's Pet Station"));
        feeds.add(new FeedSeller(14.676540701734748, 120.52366234271304, "MBCom Feeds Outlet"));
        feeds.add(new FeedSeller(14.662287396654012, 120.56529244192595, "RC's Animal Feeds Trading"));
        feeds.add(new FeedSeller(14.592451964805823, 120.5878109546487, "BFF PET AND POULTRY SUPPLIES"));

        computeFeedDistances(feeds, userLat, userLng);

        Drawable feedIcon = ContextCompat.getDrawable(
                getActivity(),
                org.osmdroid.library.R.drawable.marker_default
        );

        FolderOverlay feedLayer = new FolderOverlay(getActivity());
        mapView.getOverlays().add(feedLayer);

        for (int i = 0; i < feeds.size(); i++) {

            FeedSeller f = feeds.get(i);

            Marker marker = new Marker(mapView);
            marker.setPosition(new GeoPoint(f.lat, f.lng));

            marker.setTitle("🌾 Feed Seller: " + f.name);

            if (i == 0) {
                marker.setSnippet("NEAREST FEED STORE");
            } else {
                marker.setSnippet("Agrivet / Feed Store");
            }

            marker.setIcon(feedIcon);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

            feedLayer.add(marker);
        }
    }

    private void requestPermissionsIfNecessary(String[] permissions) {

        ArrayList<String> toRequest = new ArrayList<>();

        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(getActivity(), p)
                    != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(p);
            }
        }

        if (!toRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    getActivity(),
                    toRequest.toArray(new String[0]),
                    REQUEST_PERMISSIONS_REQUEST_CODE
            );
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }
}