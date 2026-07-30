package com.example.bunnycare;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.os.Bundle;
import android.widget.ImageButton;

public class HomeActivity extends AppCompatActivity {

    ImageButton btnNotification, btnCamera, btnCommunity, btnMaps, btnAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        btnCommunity = findViewById(R.id.btnCommunity);
        btnNotification = findViewById(R.id.btnNotification);
        btnCamera = findViewById(R.id.btnCamera);
        btnMaps = findViewById(R.id.btnMaps);
        btnAccount = findViewById(R.id.btnAccount);


        if (savedInstanceState == null) {
            changeFragment(new Community());
        }

        btnCommunity.setOnClickListener(v -> changeFragment(new Community()));
        btnNotification.setOnClickListener(v -> changeFragment(new Monitoring()));
        btnCamera.setOnClickListener(v -> changeFragment(new Camera()));
        btnMaps.setOnClickListener(v -> changeFragment(new Maps()));
        btnAccount.setOnClickListener(v -> changeFragment(new Account()));
    }

    private void changeFragment(Fragment fragment) {


        if (fragment == null || isFinishing()) return;
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
    }

    }