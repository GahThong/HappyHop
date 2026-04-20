package com.example.bunnycare;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.classifier.Classifications;
import org.tensorflow.lite.task.vision.classifier.ImageClassifier;

import java.util.List;

public class Camera extends Fragment {

    final int CAMERA_REQUEST_CODE = 1808;
    int imageSize = 224;

    AlertDialog.Builder resultDialogBuilder;

    String currentMode = "BREED";

    ActivityResultLauncher<Void> takePictureActivityResultLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(),
                    image -> {

                        if (image == null) {
                            Toast.makeText(getActivity(), "No image captured", Toast.LENGTH_SHORT).show();
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
                                            .setScoreThreshold(0.95f)
                                            .build();

                            String modelFile = currentMode.equals("BREED")
                                    ? "model.tflite"
                                    : "diseasemodel.tflite";

                            ImageClassifier classifier =
                                    ImageClassifier.createFromFileAndOptions(
                                            getActivity(), modelFile, options);

                            List<Classifications> results =
                                    classifier.classify(TensorImage.fromBitmap(processedImage));

                            Classifications classification = results.get(0);

                            String label = classification.getCategories().get(0).getLabel();
                            float confidence = classification.getCategories().get(0).getScore();

                            String info;

                            if (currentMode.equals("BREED")) {
                                info = getCareInfo(label);

                                resultDialogBuilder
                                        .setTitle("Detected Breed")
                                        .setMessage(label +
                                                "\n\n" + info)
                                        .setPositiveButton("OK", null);
                            } else {
                                info = getDiseaseInfo(label);

                                resultDialogBuilder
                                        .setTitle("Detected Disease")
                                        .setMessage(label +
                                                "\n\n" + info)
                                        .setPositiveButton("OK", null);
                            }

                            resultDialogBuilder.create().show();

                        } catch (Exception e) {
                            Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {

        Button btnBreed = view.findViewById(R.id.btnBreed);
        Button btnDisease = view.findViewById(R.id.btnDisease);

        resultDialogBuilder = new AlertDialog.Builder(getActivity());

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
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            takePictureActivityResultLauncher.launch(null);
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
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

            case "californian":
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }
}