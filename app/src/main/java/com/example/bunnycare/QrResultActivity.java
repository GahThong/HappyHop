package com.example.bunnycare;

import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

public class QrResultActivity extends AppCompatActivity {

    ImageView qrImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_result);

        qrImage = findViewById(R.id.qrImage);

        String imageUrl = getIntent().getStringExtra("image");

        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(this)
                    .load(imageUrl)
                    .into(qrImage);
        }
    }
}