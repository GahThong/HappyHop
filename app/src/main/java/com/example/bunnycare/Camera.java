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

                        ImageClassifier.ImageClassifierOptions options;

                        if (currentMode.equals("BREED")) {
                            options = ImageClassifier.ImageClassifierOptions.builder()
                                    .setBaseOptions(BaseOptions.builder().build())
                                    .setMaxResults(1)
                                    .setScoreThreshold(0.95f)
                                    .build();
                        } else {
                            options = ImageClassifier.ImageClassifierOptions.builder()
                                    .setBaseOptions(BaseOptions.builder().build())
                                    .setMaxResults(1)
                                    .setScoreThreshold(0.95f)
                                    .build();
                        }

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
                return "🐰 Breed: New Zealand\n\n" +
                        "🥕 Diet:\n- Unlimited hay (Timothy/Grass)\n- 1/2 cup pellets daily\n- Fresh leafy vegetables\n\n" +
                        "🏠 Care:\n- Spacious cage with proper ventilation\n- Daily exercise (1–2 hours)\n\n" +
                        "⚠️ Notes:\n- Fast growers, monitor weight\n- Ensure constant clean water";

            case "lionhead":
                return "🐰 Breed: Lionhead\n\n" +
                        "🥕 Diet:\n- Unlimited hay\n- Small portion of pellets\n- Fresh greens daily\n\n" +
                        "🏠 Care:\n- Requires frequent grooming (long fur)\n- Keep fur clean to avoid matting\n\n" +
                        "⚠️ Notes:\n- Prone to hair ingestion → provide fiber-rich diet";

            case "holland":
            case "holland lop":
                return "🐰 Breed: Holland Lop\n\n" +
                        "🥕 Diet:\n- Unlimited hay\n- Controlled pellets (1/4–1/2 cup)\n\n" +
                        "🏠 Care:\n- Needs daily playtime and interaction\n- Clean ears regularly (lop ears prone to infection)\n\n" +
                        "⚠️ Notes:\n- Avoid overfeeding → prone to obesity";

            case "california":
                return "🐰 Breed: California\n\n" +
                        "🥕 Diet:\n- Balanced diet (hay + pellets + vegetables)\n\n" +
                        "🏠 Care:\n- Keep cage clean and dry\n- Provide cool environment (heat sensitive)\n\n" +
                        "⚠️ Notes:\n- Check fur and skin regularly";

            default:
                return "🐰 General Rabbit Care\n\n" +
                        "🥕 Diet:\n- Unlimited hay\n- Fresh vegetables\n- Limited pellets\n\n" +
                        "🏠 Care:\n- Clean cage regularly\n- Provide exercise daily\n\n" +
                        "⚠️ Notes:\n- Always provide clean water";
        }
    }

    private String getDiseaseInfo(String disease) {
        switch (disease.toLowerCase()) {

            case "myxomatosis":
                return "🦠 Disease: Myxomatosis\n\n" +
                        "⚠️ Symptoms:\n- Swelling (eyes, face)\n- Lethargy\n\n" +
                        "💊 Treatment:\n- No direct cure\n- Immediate isolation\n- Supportive veterinary care\n\n" +
                        "🏠 Action:\n- Keep rabbit warm\n- Disinfect environment\n\n" +
                        "🚨 Urgent: Visit veterinarian immediately";

            case "mites":
                return "🦠 Condition: Mites\n\n" +
                        "⚠️ Symptoms:\n- Scratching\n- Hair loss\n- Skin flakes\n\n" +
                        "💊 Treatment:\n- Anti-parasitic medication (Ivermectin)\n\n" +
                        "🏠 Action:\n- Clean cage thoroughly\n- Isolate affected rabbit\n\n" +
                        "✔️ Good prognosis if treated early";

            case "malocclusion":
                return "🦠 Condition: Malocclusion\n\n" +
                        "⚠️ Symptoms:\n- Overgrown teeth\n- Difficulty eating\n\n" +
                        "💊 Treatment:\n- Regular dental trimming\n- Vet intervention required\n\n" +
                        "🥕 Diet Support:\n- High-fiber hay to naturally wear teeth\n\n" +
                        "🚨 Requires long-term management";

            case "pasteurellosis":
                return "🦠 Disease: Pasteurellosis (Snuffles)\n\n" +
                        "⚠️ Symptoms:\n- Runny nose\n- Sneezing\n- Breathing difficulty\n\n" +
                        "💊 Treatment:\n- Antibiotics (vet prescribed)\n\n" +
                        "🏠 Action:\n- Keep environment clean\n- Reduce stress\n\n" +
                        "🚨 Seek veterinary care";
        }
        return "";
    }
}