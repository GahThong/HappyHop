package com.example.bunnycare;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

public class QRActivity extends AppCompatActivity {

    ImageView qr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr);

        qr = findViewById(R.id.qrImage);

        String data = getIntent().getStringExtra("data");

        try {
            BitMatrix m = new MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, 600, 600);

            Bitmap bmp = Bitmap.createBitmap(600, 600, Bitmap.Config.RGB_565);

            for (int x = 0; x < 600; x++) {
                for (int y = 0; y < 600; y++) {
                    bmp.setPixel(x, y, m.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }

            qr.setImageBitmap(bmp);

        } catch (Exception e) {}
    }
}