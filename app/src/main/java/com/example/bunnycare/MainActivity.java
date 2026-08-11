package com.example.bunnycare;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    FirebaseAuth auth;
    View btnGoLogin;
    View btnGoRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_opening);

        Window window = getWindow();

        window.setStatusBarColor(Color.rgb(245, 235, 208));
        window.setNavigationBarColor(Color.rgb(245, 235, 208));

        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );

        FirebaseApp.initializeApp(this);

        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();

        firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
        );

        btnGoLogin = findViewById(R.id.btnGoLogin);
        btnGoRegister = findViewById(R.id.btnGoRegister);

        if (btnGoLogin != null) {
            btnGoLogin.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, Login.class);
                startActivity(intent);
            });
        }

        if (btnGoRegister != null) {
            btnGoRegister.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, Register.class);
                startActivity(intent);
            });
        }
    }
}