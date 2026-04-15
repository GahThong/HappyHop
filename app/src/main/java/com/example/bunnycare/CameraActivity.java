package com.example.bunnycare;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.renderscript.Element;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.classifier.Classifications;
import org.tensorflow.lite.task.vision.classifier.ImageClassifier;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;


public class CameraActivity extends AppCompatActivity {
    final int CAMERA_REQUEST_CODE = 1808;
    int imageSize = 224;
    AlertDialog.Builder breedResultDialogBuilder;
    AlertDialog breedResultDialog;

    ActivityResultLauncher<Void> takePictureActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.TakePicturePreview(),
            new ActivityResultCallback<Bitmap>() {
                @Override
                public void onActivityResult(Bitmap image) {

                    Bitmap processedImage = image;
                    int dimension = Math.min(processedImage .getWidth(), processedImage .getHeight());
                    processedImage = ThumbnailUtils.extractThumbnail(processedImage, dimension, dimension);
                    //imageView.setImageBitmap(processedImage);

                    processedImage = Bitmap.createScaledBitmap(processedImage, imageSize, imageSize, false);
                    // String result = classifyImage(processedImage);
                   try {
                       ImageClassifier.ImageClassifierOptions options =
                               ImageClassifier.ImageClassifierOptions.builder()
                                       .setBaseOptions(BaseOptions.builder().useGpu().build())
                                       .setMaxResults(1)
                                       .setScoreThreshold(0.99f)
                                       .build();
                       ImageClassifier imageClassifier =
                               ImageClassifier.createFromFileAndOptions(
                                       getApplicationContext(), "model.tflite", options);
                       List<Classifications> results = imageClassifier.classify(TensorImage.fromBitmap(processedImage));
                       Classifications classification = results.get(0);
                       String[] classes = {"New Zealand", "Lionhead", "Holland", "Californian", "Unknown"};

                       String result = classes[classification.getCategories().get(0).getIndex()]
                       ;

                       breedResultDialogBuilder.setMessage(result)
                               .setTitle("Detected Breed");
                       breedResultDialog = breedResultDialogBuilder.create();

                       breedResultDialog.show();

                   } catch (Exception e) {
                       Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                   }
                }
            });

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_camerapage);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.camerapage), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Button btnBreed = findViewById(R.id.btnBreed);
        Button btnDisease = findViewById(R.id.btnDisease);
        ImageButton btnCamera = findViewById(R.id.btnCamera);
        breedResultDialogBuilder = new AlertDialog.Builder(this);
        breedResultDialog = breedResultDialogBuilder.create();

        btnBreed.setOnClickListener(v -> openBreed());
        btnDisease.setOnClickListener(v -> openDisease());
        btnCamera.setOnClickListener(v -> openCamera());
    }

    private void openBreed() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED) {
            takePictureActivityResultLauncher.launch(null);
        } else {
            // You can directly ask for the permission.
            requestPermissions(
                    new String[] { Manifest.permission.CAMERA },
                    CAMERA_REQUEST_CODE);
        }
    }

    private void openDisease() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED) {
            takePictureActivityResultLauncher.launch(null);
        } else {
            // You can directly ask for the permission.
            requestPermissions(
                    new String[] { Manifest.permission.CAMERA },
                    CAMERA_REQUEST_CODE);
        }
    }

    private void openCamera() {

    }
}