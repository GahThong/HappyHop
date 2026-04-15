package com.example.bunnycare;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.os.Bundle;

import androidx.activity.result.ActivityResultCallback;
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

    AlertDialog.Builder breedResultDialogBuilder;
    AlertDialog breedResultDialog;

    ActivityResultLauncher<Void> takePictureActivityResultLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(),
                    new ActivityResultCallback<Bitmap>() {
                        @Override
                        public void onActivityResult(Bitmap image) {

                            if (image == null) {
                                Toast.makeText(getActivity(), "No image captured", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            Bitmap processedImage = image;
                            int dimension = Math.min(processedImage.getWidth(), processedImage.getHeight());
                            processedImage = ThumbnailUtils.extractThumbnail(processedImage, dimension, dimension);
                            processedImage = Bitmap.createScaledBitmap(processedImage, imageSize, imageSize, false);

                            try {
                                // ✅ YOUR ORIGINAL SETTINGS (UNCHANGED)
                                ImageClassifier.ImageClassifierOptions options =
                                        ImageClassifier.ImageClassifierOptions.builder()
                                                .setBaseOptions(BaseOptions.builder().useGpu().build())
                                                .setMaxResults(1)
                                                .setScoreThreshold(0.99f)
                                                .build();

                                ImageClassifier imageClassifier =
                                        ImageClassifier.createFromFileAndOptions(
                                                getActivity(), "model.tflite", options);

                                List<Classifications> results =
                                        imageClassifier.classify(TensorImage.fromBitmap(processedImage));

                                Classifications classification = results.get(0);

                                String[] classes = {
                                        "New Zealand",
                                        "Lionhead",
                                        "Holland",
                                        "Californian",
                                        "Unknown"
                                };

                                String result = classes[
                                        classification.getCategories().get(0).getIndex()
                                        ];

                                String careInfo = getCareInfo(result);

                                breedResultDialogBuilder
                                        .setTitle("Detected Breed")
                                        .setMessage("Breed: " + result + "\n\n" + careInfo)
                                        .setPositiveButton("OK", null);

                                breedResultDialog = breedResultDialogBuilder.create();
                                breedResultDialog.show();

                            } catch (Exception e) {
                                Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                    });

    @SuppressLint("MissingInflatedId")
    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        Button btnBreed = view.findViewById(R.id.btnBreed);
        Button btnDisease = view.findViewById(R.id.btnDisease);

        breedResultDialogBuilder = new AlertDialog.Builder(getActivity());

        btnBreed.setOnClickListener(v -> openCamera());
        btnDisease.setOnClickListener(v -> openCamera());
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

    // ✅ CARE + FEEDING INFO
    private String getCareInfo(String breed) {
        switch (breed) {

            case "New Zealand":
                return "Care Tips:\n" +
                        "• Large cage and space to move\n" +
                        "• Moderate exercise\n\n" +
                        "Feeding Guide:\n" +
                        "• Hay: Unlimited\n" +
                        "• Pellets: 1/2 to 1 cup per day\n" +
                        "• Vegetables: 1–2 cups daily\n" +
                        "• Water: Always available";

            case "Lionhead":
                return "Care Tips:\n" +
                        "• Frequent grooming (long fur)\n" +
                        "• Keep in cool environment\n\n" +
                        "Feeding Guide:\n" +
                        "• Hay: Unlimited\n" +
                        "• Pellets: 1/4 to 1/2 cup per day\n" +
                        "• Vegetables: 1 cup daily\n" +
                        "• Water: Always clean and fresh";

            case "Holland":
                return "Care Tips:\n" +
                        "• Needs playtime and interaction\n" +
                        "• Small but active\n\n" +
                        "Feeding Guide:\n" +
                        "• Hay: Unlimited\n" +
                        "• Pellets: 1/4 cup per day\n" +
                        "• Vegetables: 1 cup daily\n" +
                        "• Water: Always available";

            case "Californian":
                return "Care Tips:\n" +
                        "• Keep clean and cool space\n" +
                        "• Monitor weight regularly\n\n" +
                        "Feeding Guide:\n" +
                        "• Hay: Unlimited\n" +
                        "• Pellets: 1/2 to 1 cup per day\n" +
                        "• Vegetables: 1–2 cups daily\n" +
                        "• Water: Always available";

            default:
                return "Care Tips:\n" +
                        "• Keep environment clean\n" +
                        "• Provide safe housing\n\n" +
                        "Feeding Guide:\n" +
                        "• Hay: Unlimited\n" +
                        "• Pellets: 1/4 to 1/2 cup per day\n" +
                        "• Vegetables: 1 cup daily\n" +
                        "• Water: Always available";
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }
}