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


    class Vet {
        double lat;
        double lng;
        String name;

        Vet(double lat, double lng, String name) {
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

        loadVets();
        getCurrentLocation();
    }


    private void loadVets() {

        List<Vet> vets = new ArrayList<>();


        vets.add(new Vet(
                14.66864878815696, 120.54425441716226,
                "Easyvet Cupang Branch - Pet Supplies & Veterinary Services"
        ));

        vets.add(new Vet(
                14.674436487056353, 120.54718269535796,
                "PENINSULA VETERINARY CLINIC"
        ));
        vets.add(new Vet(
                14.678788762953449, 120.54162734120344,
                "Man´s Best Friend Veterinary Clinics"
        ));
        vets.add(new Vet(
                14.681817404737464, 120.54355199074746,
                "Pet Hub Veterinary Hospital - Bataan"
        ));
        vets.add(new Vet(
                14.684225208214851, 120.53775841911424,
                "Veterinary Clinic at PE SM Bataan"
        ));
        vets.add(new Vet(
                14.686466932444512, 120.53951794827692,
                "ABC Animal Bite Center - Balanga, Bataan"
        ));
        vets.add(new Vet(
                14.704648957282709, 120.5375867578363,
                "Bfc Animal Clinic & Grooming Center"
        ));
        vets.add(new Vet(
                14.678788762953449, 120.54162734120344,
                "PENINSULA VETERINARY CLINIC"
        ));
        vets.add(new Vet(
                14.677043233369728, 120.5359559748471,
                "Pet Needs Veterinary Care and Grooming Center"
        ));
        vets.add(new Vet(
                14.678579282525043, 120.52784497456055,
                "PETSTOP ANIMAL CLINIC AND GROOMING CENTER"
        ));
        vets.add(new Vet(
                14.672808663549786, 120.527501651784,
                "Easyvet Balanga Main Branch - Pet Supplies, Grooming & Veterinary Services"
        ));

        Drawable icon = ContextCompat.getDrawable(
                getActivity(),
                org.osmdroid.library.R.drawable.marker_default
        );

        FolderOverlay vetLayer = new FolderOverlay(getActivity());
        mapView.getOverlays().add(vetLayer);

        for (Vet v : vets) {

            GeoPoint point = new GeoPoint(v.lat, v.lng);

            Marker marker = new Marker(mapView);
            marker.setPosition(point);
            marker.setTitle(v.name);
            marker.setIcon(icon);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

            vetLayer.add(marker);
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

                        GeoPoint current = new GeoPoint(
                                location.getLatitude(),
                                location.getLongitude()
                        );

                        mapController.setCenter(current);
                        mapController.setZoom(13.0);
                    }
                });
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