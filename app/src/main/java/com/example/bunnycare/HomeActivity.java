package com.example.bunnycare;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.os.Bundle;
import android.widget.ImageButton;

public class HomeActivity extends AppCompatActivity {

    ImageButton btnNotification, btnCamera, btnCommunity, btnMaps, btnAccount;
    ImageButton[] navButtons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        btnCommunity = findViewById(R.id.btnCommunity);
        btnNotification = findViewById(R.id.btnNotification);
        btnCamera = findViewById(R.id.btnCamera);
        btnMaps = findViewById(R.id.btnMaps);
        btnAccount = findViewById(R.id.btnAccount);

        navButtons = new ImageButton[]{
                btnCommunity, btnNotification, btnCamera, btnMaps, btnAccount
        };

        if (savedInstanceState == null) {
            changeFragment(new Community());
            setActiveButton(btnCommunity);
        }

        btnCommunity.setOnClickListener(v -> {
            changeFragment(new Community());
            setActiveButton(btnCommunity);
        });

        btnNotification.setOnClickListener(v -> {
            changeFragment(new Monitoring());
            setActiveButton(btnNotification);
        });

        btnCamera.setOnClickListener(v -> {
            changeFragment(new Camera());
            setActiveButton(btnCamera);
        });

        btnMaps.setOnClickListener(v -> {
            changeFragment(new Maps());
            setActiveButton(btnMaps);
        });

        btnAccount.setOnClickListener(v -> {
            changeFragment(new Account());
            setActiveButton(btnAccount);
        });
    }

    /**
     * Marks the tapped nav button as selected (keeping its gray circle
     * background visible) and clears the selected state on the other four,
     * so only one tab is ever highlighted at a time.
     */
    private void setActiveButton(ImageButton active) {

        for (ImageButton button : navButtons) {
            button.setSelected(button == active);
        }
    }

    private void changeFragment(Fragment fragment) {

        if (fragment == null || isFinishing()) return;
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
    }

}