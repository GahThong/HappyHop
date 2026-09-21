package com.example.bunnycare;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

public class QRActivity extends AppCompatActivity {

    private ImageView qr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String data = getIntent().getStringExtra("data");
        String title = getIntent().getStringExtra("title");

        if (data == null || data.trim().isEmpty()) {
            finish();
            return;
        }

        if (title == null || title.trim().isEmpty()) {
            title = "QR Code";
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(40, 40, 40, 40);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.BLACK);
        titleView.setTextSize(20);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        titleParams.setMargins(0, 0, 0, 30);

        titleView.setLayoutParams(titleParams);

        qr = new ImageView(this);

        LinearLayout.LayoutParams qrParams =
                new LinearLayout.LayoutParams(
                        800,
                        800
                );

        qrParams.gravity = Gravity.CENTER;

        qr.setLayoutParams(qrParams);

        TextView info = new TextView(this);
        info.setText("Scan this QR code to access the medical record.");
        info.setTextColor(Color.DKGRAY);
        info.setTextSize(13);
        info.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams infoParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        infoParams.setMargins(0, 30, 0, 0);

        info.setLayoutParams(infoParams);

        root.addView(titleView);
        root.addView(qr);
        root.addView(info);

        setContentView(root);

        generateQRCode(data);
    }

    private void generateQRCode(String data) {

        try {

            BitMatrix bitMatrix =
                    new MultiFormatWriter().encode(
                            data,
                            BarcodeFormat.QR_CODE,
                            800,
                            800
                    );

            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();

            Bitmap bitmap =
                    Bitmap.createBitmap(
                            width,
                            height,
                            Bitmap.Config.RGB_565
                    );

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(
                            x,
                            y,
                            bitMatrix.get(x, y)
                                    ? Color.BLACK
                                    : Color.WHITE
                    );
                }
            }

            qr.setImageBitmap(bitmap);

        } catch (Exception e) {
            finish();
        }
    }
}