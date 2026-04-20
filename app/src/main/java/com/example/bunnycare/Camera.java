package com.example.bunnycare;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.classifier.Classifications;
import org.tensorflow.lite.task.vision.classifier.ImageClassifier;

import java.util.List;

public class Camera extends Fragment {

    int imageSize = 224;
    String currentMode = "BREED";

    String[] breedLabels = {
            "Holland",
            "California",
            "New Zealand",
            "Lionhead"
    };

    ActivityResultLauncher<Void> takePictureLauncher;
    ActivityResultLauncher<String> permissionLauncher;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        Button btnBreed = view.findViewById(R.id.btnBreed);
        Button btnDisease = view.findViewById(R.id.btnDisease);

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        takePictureLauncher.launch(null);
                    } else {
                        Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                image -> {

                    if (image == null) {
                        Toast.makeText(requireContext(), "No image captured", Toast.LENGTH_SHORT).show();
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
                                ImageClassifier.createFromFileAndOptions(requireContext(), modelFile, options);

                        List<Classifications> results =
                                classifier.classify(TensorImage.fromBitmap(processedImage));

                        if (results == null || results.isEmpty() || results.get(0).getCategories().isEmpty()) {
                            Toast.makeText(requireContext(), "No prediction", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Classifications classification = results.get(0);

                        int index = classification.getCategories().get(0).getIndex();
                        String label;

                        if (currentMode.equals("BREED")) {
                            if (index >= 0 && index < breedLabels.length) {
                                label = breedLabels[index];
                            } else {
                                label = "Unknown";
                            }
                        } else {
                            label = classification.getCategories().get(0).getLabel();
                        }

                        String info = currentMode.equals("BREED")
                                ? getCareInfo(label)
                                : getDiseaseInfo(label);

                        String title = currentMode.equals("BREED")
                                ? "Detected Breed"
                                : "Detected Disease";

                        new AlertDialog.Builder(requireContext())
                                .setTitle(title)
                                .setMessage(label + "\n\n" + info)
                                .setPositiveButton("OK", null)
                                .show();

                    } catch (Exception e) {
                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
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
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            takePictureLauncher.launch(null);
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private String getCareInfo(String breed) {
        switch (breed.toLowerCase()) {
            case "new zealand":
                return "Hay unlimited\nPellets 1/2 cup";
            case "lionhead":
                return "Regular grooming\nHay unlimited";
            case "holland":
            case "holland lop":
                return "Playtime needed\nHay unlimited";
            case "california":
                return "Clean cage\nBalanced diet";
            default:
                return "Basic rabbit care";
        }
    }

    private String getDiseaseInfo(String disease) {
        switch (disease.toLowerCase()) {
            case "myxomatosis":
                return "Isolate + Vet";
            case "mites":
                return "Anti-parasitic treatment";
            case "malocclusion":
                return "Dental care needed";
            case "pasteurellosis":
                return "Antibiotics required";
            default:
                return "Consult vet";
        }
    }
}