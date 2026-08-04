package com.example.bunnycare;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {
    FirebaseAuth auth;
    Button btnGoLogin, btnGoRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_opening);

        FirebaseApp.initializeApp( this);
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance());
        btnGoLogin = findViewById(R.id.btnGoLogin);
        btnGoRegister = findViewById(R.id.btnGoRegister);

        if (btnGoLogin != null) {
            btnGoLogin.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, Login.class))
            );
        }

        if (btnGoRegister != null) {
            btnGoRegister.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, Register.class))
            );
        }
    }
}