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

    public static Camera newInstance() {
        return new Camera();
    }

    final int CAMERA_REQUEST_CODE = 1808;
    int imageSize = 224;

    AlertDialog.Builder resultDialogBuilder;
    AlertDialog resultDialog;

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
                                            .setScoreThreshold(0.90f)
                                            .build();


                            String modelFile = currentMode.equals("BREED")
                                    ? "model.tflite"
                                    : "diseasemodel.tflite";

                            ImageClassifier imageClassifier =
                                    ImageClassifier.createFromFileAndOptions(
                                            getActivity(), modelFile, options);

                            List<Classifications> results =
                                    imageClassifier.classify(TensorImage.fromBitmap(processedImage));

                            Classifications classification = results.get(0);

                            int index = classification.getCategories().get(0).getIndex();
                            float confidence = classification.getCategories().get(0).getScore();

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
                                        .setMessage("Breed: " + result +
                                                "\n\n" + info)
                                        .setPositiveButton("OK", null);

                            }
                            // 🔹 DISEASE MODE
                            else {

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
                                        .setMessage("Result: " + result +
                                                "\n\n" + info)
                                        .setPositiveButton("OK", null);
                            }

                            resultDialog = resultDialogBuilder.create();
                            resultDialog.show();

                        } catch (Exception e) {
                            Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });

    @SuppressLint("MissingInflatedId")
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
        if (ContextCompat.checkSelfPermission(
                getActivity(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {

            takePictureActivityResultLauncher.launch(null);

        } else {
            requestPermissions(
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_REQUEST_CODE
            );
        }
    }


    private String getCareInfo(String breed) {
        switch (breed) {

            case "New Zealand":
                return "Care Tips:\n• Large cage\n• Moderate exercise\n\nFeeding:\n• Hay unlimited\n• Pellets 1/2–1 cup";

            case "Lionhead":
                return "Care Tips:\n• Groom regularly\n• Cool environment\n\nFeeding:\n• Hay unlimited\n• Pellets 1/4–1/2 cup";

            case "Holland":
                return "Care Tips:\n• Needs playtime\n\nFeeding:\n• Hay unlimited\n• Pellets 1/4 cup";

            case "Californian":
                return "Care Tips:\n• Clean environment\n\nFeeding:\n• Hay unlimited\n• Pellets 1/2–1 cup";

            default:
                return "Basic rabbit care applies.";
        }
    }


    private String getDiseaseInfo(String disease) {
        switch (disease) {

            case "Myxomatosis":
                return "⚠ Serious viral disease\n\nSymptoms:\n• Swollen eyes\n• Lethargy\n\nAction:\n• Isolate immediately\n• Vet ASAP";

            case "Mites":
                return "Parasite infection\n\nSymptoms:\n• Itching\n• Hair loss\n\nTreatment:\n• Anti-mite meds\n• Clean cage";

            case "Malocclusion":
                return "Dental issue\n\nSymptoms:\n• Overgrown teeth\n• Difficulty eating\n\nTreatment:\n• Vet trimming\n• Chew toys";

            case "Pasteurellosis":
                return "Bacterial infection\n\nSymptoms:\n• Runny nose\n• Sneezing\n\nTreatment:\n• Antibiotics\n• Clean area";

            default:
                return "Unknown disease.\nConsult a veterinarian.";
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }
}