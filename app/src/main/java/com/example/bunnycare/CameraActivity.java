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

    final int CAMERA_REQUEST_CODE = 1808;
    int imageSize = 224;

    AlertDialog.Builder resultDialogBuilder;
    AlertDialog resultDialog;

    String currentMode = "BREED";

    ActivityResultLauncher<Void> takePictureActivityResultLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(),
                    image -> {

                        if (image == null) {
                            Toast.makeText(getApplicationContext(), "No image captured", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Bitmap processedImage = image;
                        int dimension = Math.min(processedImage.getWidth(), processedImage.getHeight());
                        processedImage = ThumbnailUtils.extractThumbnail(processedImage, dimension, dimension);
                        processedImage = Bitmap.createScaledBitmap(processedImage, imageSize, imageSize, false);

                        try {

                            ImageClassifier.ImageClassifierOptions options =
                                    ImageClassifier.ImageClassifierOptions.builder()
                                            .setBaseOptions(BaseOptions.builder().useGpu().build())
                                            .setMaxResults(1)
                                            .setScoreThreshold(0.90f)
                                            .build();

                            String modelFile = currentMode.equals("BREED")
                                    ? "model.tflite"
                                    : "diseasemodel.tflite";

                            ImageClassifier imageClassifier =
                                    ImageClassifier.createFromFileAndOptions(
                                            getApplicationContext(), modelFile, options);

                            List<Classifications> results =
                                    imageClassifier.classify(TensorImage.fromBitmap(processedImage));

                            Classifications classification = results.get(0);
                            int index = classification.getCategories().get(0).getIndex();

                            String result;
                            String info;

                            if (currentMode.equals("BREED")) {

                                String[] classes = {
                                        "New Zealand",
                                        "Lionhead",
                                        "Holland",
                                        "Californian",
                                        "Unknown"
                                };

                                if (index >= classes.length) index = classes.length - 1;

                                result = classes[index];
                                info = getCareInfo(result);

                                resultDialogBuilder
                                        .setTitle("Detected Breed")
                                        .setMessage("Breed: " + result + "\n\n" + info)
                                        .setPositiveButton("OK", null);

                            } else {

                                String[] diseaseClasses = {
                                        "Myxomatosis",
                                        "Mites",
                                        "Malocclusion",
                                        "Pasteurellosis"
                                };

                                if (index >= diseaseClasses.length) index = diseaseClasses.length - 1;

                                result = diseaseClasses[index];
                                info = getDiseaseInfo(result);

                                resultDialogBuilder
                                        .setTitle("Detected Disease")
                                        .setPositiveButton("OK", null);
                            }

                            resultDialog = resultDialogBuilder.create();
                            resultDialog.show();

                        } catch (Exception e) {
                            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
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

        resultDialogBuilder = new AlertDialog.Builder(this);

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
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED) {

            takePictureActivityResultLauncher.launch(null);

        } else {
            requestPermissions(
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_REQUEST_CODE);
        }
    }

    private String getCareInfo(String breed) {
        switch (breed) {
            case "New Zealand":
                return "Large cage\nHay unlimited\nPellets 1/2–1 cup";
            case "Lionhead":
                return "Groom often\nHay unlimited\nPellets 1/4–1/2 cup";
            case "Holland":
                return "Needs playtime\nHay unlimited\nPellets 1/4 cup";
            case "Californian":
                return "Clean space\nHay unlimited\nPellets 1/2–1 cup";
            default:
                return "Basic rabbit care";
        }
    }

    private String getDiseaseInfo(String disease) {
        switch (disease) {

            case "Myxomatosis":
                return "Serious viral disease\nIsolate and vet immediately";

            case "Mites":
                return "Parasites\nTreat with medication\nClean cage";

            case "Malocclusion":
                return "Teeth problem\nNeeds trimming";

            case "Pasteurellosis":
                return "Bacterial infection\nNeeds antibiotics";

            default:
                return "Consult veterinarian";
        }
    }
}