package com.example.bunnycare;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;

import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.classifier.Classifications;
import org.tensorflow.lite.task.vision.classifier.ImageClassifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Camera extends Fragment {

    int imageSize = 224;
    String currentMode = "BREED";

    String[] breedLabels = {
            "Holland",
            "California",
            "New Zealand",
            "Lionhead"
    };

    String[] diseaseLabels = {
            "Myxomatosis",
            "Mites",
            "Malocclusion",
            "Pasteurellosis"
    };

    // Rabbit features we ask the user to pick a photo of, for the higher-accuracy Gemini pass.
    String[] featureNames = {
            "Legs",
            "Arms",
            "Ears",
            "Nose",
            "Fur",
            "Tail"
    };

    ActivityResultLauncher<Void> takePictureLauncher;
    ActivityResultLauncher<String> featurePickerLauncher;
    ActivityResultLauncher<String> permissionLauncher;

    Executor geminiExecutor = Executors.newSingleThreadExecutor();

    // State for the original capture + the opt-in multi-feature Gemini refinement.
    Bitmap lastOriginalImage;
    List<Bitmap> featureImages = new ArrayList<>();
    int currentFeatureIndex = 0;

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

        // Main capture -> runs the local TFLite classifier and shows the result,
        // then offers the opt-in "want better result with %?" Gemini refinement.
        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                image -> {
                    if (image == null) {
                        Toast.makeText(requireContext(), "No image captured", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    lastOriginalImage = image;
                    runLocalClassification(image);
                }
        );

        // Follow-up picks for individual rabbit features (legs, arms, ears, nose, fur, tail),
        // used only when the user opts into the Gemini refinement. Picked from the gallery
        // rather than captured live, so no camera permission is needed for this step.
        featurePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) {
                        Toast.makeText(requireContext(), "No image selected, skipping this feature", Toast.LENGTH_SHORT).show();
                    } else {
                        Bitmap bitmap = loadBitmapFromUri(uri);
                        if (bitmap != null) {
                            featureImages.add(bitmap);
                        } else {
                            Toast.makeText(requireContext(), "Couldn't load that image, skipping this feature", Toast.LENGTH_SHORT).show();
                        }
                    }
                    currentFeatureIndex++;
                    promptNextFeatureOrRun();
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

    // Converts a picked gallery Uri into a Bitmap.
    private Bitmap loadBitmapFromUri(Uri uri) {
        try {
            return MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), uri);
        } catch (IOException e) {
            Log.e("Camera", "Failed to load bitmap from gallery Uri", e);
            return null;
        }
    }

    // ---------------------------------------------------------------
    // Local TFLite classification (works for both BREED and DISEASE)
    // ---------------------------------------------------------------
    private void runLocalClassification(Bitmap image) {
        Bitmap processedImage = image.copy(Bitmap.Config.ARGB_8888, true);
        int dimension = Math.min(processedImage.getWidth(), processedImage.getHeight());
        processedImage = ThumbnailUtils.extractThumbnail(processedImage, dimension, dimension);
        processedImage = Bitmap.createScaledBitmap(processedImage, imageSize, imageSize, false);

        try {
            ImageClassifier.ImageClassifierOptions options =
                    ImageClassifier.ImageClassifierOptions.builder()
                            .setBaseOptions(BaseOptions.builder().build())
                            .setMaxResults(1)
                            .setScoreThreshold(0.95f)
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
                label = (index >= 0 && index < breedLabels.length) ? breedLabels[index] : "Unknown";
            } else {
                label = (index >= 0 && index < diseaseLabels.length) ? diseaseLabels[index] : "Unknown";
            }

            String info = currentMode.equals("BREED")
                    ? getCareInfo(label)
                    : getDiseaseInfo(label);

            String title = currentMode.equals("BREED")
                    ? "Detected Breed"
                    : "Detected Disease";

            showResultDialog(title, label, info);

        } catch (Exception e) {
            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // Shows the TFLite result, with a neutral button offering the Gemini refinement.
    private void showResultDialog(String title, String label, String info) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(label + "\n\n" + info)
                .setPositiveButton("OK", null)
                .setNeutralButton("Want better result with %?", (dialog, which) -> startFeatureCaptureFlow())
                .show();
    }

    // ---------------------------------------------------------------
    // Opt-in Gemini refinement: pick close-ups of specific features from the gallery
    // ---------------------------------------------------------------
    private void startFeatureCaptureFlow() {
        featureImages = new ArrayList<>();
        currentFeatureIndex = 0;
        promptNextFeatureOrRun();
    }

    private void promptNextFeatureOrRun() {
        if (currentFeatureIndex < featureNames.length) {
            String feature = featureNames[currentFeatureIndex];
            new AlertDialog.Builder(requireContext())
                    .setTitle("Better result with Gemini")
                    .setMessage("Please choose a close-up photo of the rabbit's " + feature.toLowerCase(Locale.ROOT) + " from your gallery.")
                    .setCancelable(false)
                    .setPositiveButton("Choose photo", (dialog, which) -> featurePickerLauncher.launch("image/*"))
                    .setNegativeButton("Skip", (dialog, which) -> {
                        currentFeatureIndex++;
                        promptNextFeatureOrRun();
                    })
                    .show();
        } else {
            runGeminiRefinement();
        }
    }

    private void runGeminiRefinement() {
        if (lastOriginalImage == null) {
            Toast.makeText(requireContext(), "Missing original photo, please try again", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(requireContext(), "Analyzing with Gemini...", Toast.LENGTH_SHORT).show();

        GenerativeModel firebaseAI = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                .generativeModel("gemini-3.5-flash-lite");
        GenerativeModelFutures model = GenerativeModelFutures.from(firebaseAI);

        boolean isBreedMode = currentMode.equals("BREED");
        String[] categories = isBreedMode ? breedLabels : diseaseLabels;
        String categoryList = String.join(", ", categories);

        String basePrompt = isBreedMode
                ? "You are a rabbit breed expert. Analyze the overall photo plus the close-up feature photos "
                + "provided (each labeled) to identify the rabbit's breed as precisely as possible. "
                + "If it looks like a mix, estimate a percentage breakdown across these possible breeds: "
                + categoryList + ". Also write a short recommended care plan for this rabbit covering diet, "
                + "housing/exercise, and any breed-specific notes, based on the identified breed. "
                + "Respond ONLY in JSON, no extra text, in this exact format: "
                + "{\"label\": \"Holland\", \"confidences\": {\"Holland\": 80, \"California\": 10, "
                + "\"New Zealand\": 5, \"Lionhead\": 5}, \"care\": \"short recommended care plan here\"}"
                : "You are a rabbit health expert. Analyze the overall photo plus the close-up feature photos "
                + "provided (each labeled) to identify any disease or condition as precisely as possible. "
                + "Estimate a percentage likelihood across these possible conditions: "
                + categoryList + ". Also write short recommended care guidance for the identified condition, "
                + "covering immediate at-home steps, whether a vet visit is needed, and any warning signs to "
                + "watch for. This is general guidance only, not a substitute for veterinary diagnosis. "
                + "Respond ONLY in JSON, no extra text, in this exact format: "
                + "{\"label\": \"Mites\", \"confidences\": {\"Myxomatosis\": 5, \"Mites\": 80, "
                + "\"Malocclusion\": 5, \"Pasteurellosis\": 10}, \"care\": \"short recommended care guidance here\"}";

        Content.Builder contentBuilder = new Content.Builder()
                .addText(basePrompt)
                .addText("Overall photo:")
                .addImage(lastOriginalImage);

        for (int i = 0; i < featureImages.size(); i++) {
            String label = i < featureNames.length ? featureNames[i] : ("Feature " + (i + 1));
            contentBuilder.addText("Close-up of the " + label.toLowerCase(Locale.ROOT) + ":");
            contentBuilder.addImage(featureImages.get(i));
        }

        Content content = contentBuilder.build();

        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);
        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                handleGeminiResult(result.getText());
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e("Camera", "Gemini refinement failed", t);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "Gemini analysis failed: " + t.getMessage(), Toast.LENGTH_LONG).show());
                }
            }
        }, geminiExecutor);
    }

    private void handleGeminiResult(String rawText) {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            try {
                String json = extractJson(rawText);
                JSONObject obj = new JSONObject(json);
                String label = obj.optString("label", "Unknown");
                JSONObject confidences = obj.optJSONObject("confidences");
                String care = obj.optString("care", "").trim();

                StringBuilder message = new StringBuilder();
                message.append("Best match: ").append(label).append("\n\n");

                if (confidences != null) {
                    java.util.Iterator<String> keys = confidences.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        message.append(key).append(": ").append(confidences.optInt(key, 0)).append("%\n");
                    }
                }

                if (!care.isEmpty()) {
                    message.append("\nRecommended Care:\n").append(care).append("\n");
                }

                String title = currentMode.equals("BREED")
                        ? "Gemini Breed Estimate"
                        : "Gemini Health Estimate";

                new AlertDialog.Builder(requireContext())
                        .setTitle(title)
                        .setMessage(message.toString())
                        .setPositiveButton("OK", null)
                        .show();

            } catch (JSONException e) {
                Log.e("Camera", "Failed to parse Gemini response: " + rawText, e);
                new AlertDialog.Builder(requireContext())
                        .setTitle("Gemini Result")
                        .setMessage(rawText)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    // Gemini sometimes wraps JSON in markdown fences; strip those if present.
    private String extractJson(String text) {
        if (text == null) return "{}";
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(json)?", "").trim();
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
            }
        }
        return trimmed;
    }

    private String getCareInfo(String breed) {
        switch (breed.toLowerCase()) {

            case "new zealand":
                return " Breed: New Zealand\n\n Diet:\n- Unlimited hay\n- Pellets\n- Vegetables\n\n Care:\n- Spacious cage\n- Daily exercise\n\n️ Notes:\n- Monitor weight\n\n"
                        + "Video Guide: \n" + "https://youtu.be/FbibvbYxIzw?si=GPenxib7t4yb1Ko2";

            case "lionhead":
                return " Breed: Lionhead\n\n Diet:\n- Hay + greens\n\n Care:\n- Grooming required\n\n️ Notes:\n- Avoid hair ingestion\n\n"
                        + "Video Guide: \n" + "https://youtu.be/57y91glfDGc?si=TGHYF2oIh40aiCxw";

            case "holland":
            case "holland lop":
                return " Breed: Holland Lop\n\n Diet:\n- Hay + controlled pellets\n\n Care:\n- Ear cleaning\n\n️ Notes:\n- Avoid obesity\n\n"
                        + "Video Guide: \n" + "https://youtu.be/HfLwpvfjuuI?si=dXG-fEA-BqKQ5mIJ";

            case "california":
                return " Breed: California\n\n Diet:\n- Balanced diet\n\n Care:\n- Cool environment\n\n Notes:\n- Check skin regularly\n\n"
                        + "Video Guide: \n" + "https://youtu.be/3fJJRRsDwUo?si=hKjR826zbbiKWGFk";

            default:
                return " General Rabbit Care\n\n Diet:\n- Hay + vegetables\n\n Care:\n- Clean cage\n\nNotes:\n- Fresh water always";
        }
    }

    private String getDiseaseInfo(String disease) {
        switch (disease.toLowerCase()) {

            case "myxomatosis":
                return " Myxomatosis\n️ Severe viral disease\n Vet required\n\n"
                        + "Additional Info: \n" + "https://youtu.be/RUezkUetEEA?si=gcsRr-tNhlvpU7sg";

            case "mites":
                return " Mites\n️ Skin irritation\n Treat with ivermectin\n\n"
                        + "Additional Info: \n" + "https://youtu.be/iQyPn2VL70s?si=EAnvWMapiA8YgJRW";

            case "malocclusion":
                return " Malocclusion\n️ Teeth problem\n Needs dental care\n\n"
                        + "Additional Info: \n" + "https://youtu.be/2F328Q38uJc?si=PQNXStwEciaECc8h";

            case "pasteurellosis":
                return " Pasteurellosis\n⚠ Respiratory infection\n Antibiotics required\n\n"
                        + "Additional Info: \n" + "https://youtu.be/Uxj0VIqC83Q?si=J5OugchFPDFVkepC";
        }
        return "";
    }
}