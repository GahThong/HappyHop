package com.example.bunnycare;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
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

import java.util.List;

public class CameraActivity extends AppCompatActivity {

    int imageSize = 224;
    String currentMode = "BREED";

    ActivityResultLauncher<Void> takePictureLauncher;
    ActivityResultLauncher<String> permissionLauncher;

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

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        takePictureLauncher.launch(null);
                    } else {
                        Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                image -> {
                    if (image == null) {
                        Toast.makeText(this, "No image captured", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Bitmap processedImage = image;
                    int dimension = Math.min(processedImage.getWidth(), processedImage.getHeight());
                    processedImage = ThumbnailUtils.extractThumbnail(processedImage, dimension, dimension);
                    processedImage = Bitmap.createScaledBitmap(processedImage, imageSize, imageSize, false);

                    try {
                        ImageClassifier.ImageClassifierOptions options =
                                ImageClassifier.ImageClassifierOptions.builder()
                                        .setBaseOptions(BaseOptions.builder().build())
                                        .setMaxResults(1)
                                        .build();

                        String modelFile = currentMode.equals("BREED")
                                ? "model.tflite"
                                : "diseasemodel.tflite";

                        ImageClassifier classifier =
                                ImageClassifier.createFromFileAndOptions(this, modelFile, options);

                        List<Classifications> results =
                                classifier.classify(TensorImage.fromBitmap(processedImage));

                        if (results == null || results.isEmpty()
                                || results.get(0).getCategories().isEmpty()) {
                            Toast.makeText(this, "No prediction result", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Classifications classification = results.get(0);
                        String label = classification.getCategories().get(0).getLabel();

                        String info = currentMode.equals("BREED")
                                ? getCareInfo(label)
                                : getDiseaseInfo(label);

                        String title = currentMode.equals("BREED")
                                ? "Detected Breed"
                                : "Detected Disease";

                        new AlertDialog.Builder(this)
                                .setTitle(title)
                                .setMessage(label + "\n\n" + info)
                                .setPositiveButton("OK", null)
                                .show();

                    } catch (Exception e) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
        );

        btnBreed.setOnClickListener(v -> {
            currentMode = "BREED";
            openCamera();
        });

        btnDisease.setOnClickListener(v -> {
            currentMode = "DISEASE";
            openCamera();
        });

        btnCamera.setOnClickListener(v -> openCamera());
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            takePictureLauncher.launch(null);
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private String getCareInfo(String breed) {
        switch (breed.toLowerCase()) {
            case "new zealand":
                return "Large cage\nHay unlimited\nPellets 1/2–1 cup";
            case "lionhead":
                return "Groom often\nHay unlimited\nPellets 1/4–1/2 cup";
            case "holland lop":
            case "holland":
                return "Needs playtime\nHay unlimited\nPellets 1/4 cup";
            case "californian":
                return "Clean space\nHay unlimited\nPellets 1/2–1 cup";
            default:
                return "Basic rabbit care";
        }
    }

    private String getDiseaseInfo(String disease) {
        switch (disease.toLowerCase()) {
            case "myxomatosis":
                return "Isolate immediately\nVet ASAP";
            case "mites":
                return "Parasites\nUse anti-mite treatment";
            case "malocclusion":
                return "Dental issue\nNeeds vet trimming";
            case "pasteurellosis":
                return "Bacterial infection\nNeeds antibiotics";
            default:
                return "Consult veterinarian";
        }
    }
}