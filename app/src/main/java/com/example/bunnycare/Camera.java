package com.example.bunnycare;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.text.LineBreaker;
import android.media.ThumbnailUtils;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

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

    // Confidence gate used on the breed model to decide whether a rabbit
    // is present in the photo at all.
    static final float RABBIT_PRESENCE_THRESHOLD = 0.95f;
    // Confidence needed before we report a specific disease instead of
    // "No signs of illness detected".
    static final float DISEASE_REPORT_THRESHOLD = 0.95f;

    static final String NO_ILLNESS_LABEL = "No signs of illness detected";

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

    String[] featureNames = {
            "Legs",
            "Arms",
            "Ears",
            "Nose",
            "Fur",
            "Tail"
    };

    ActivityResultLauncher<Void> takePictureLauncher;
    ActivityResultLauncher<Void> featureCameraLauncher;
    ActivityResultLauncher<String> mainImagePickerLauncher;
    ActivityResultLauncher<String> featurePickerLauncher;
    ActivityResultLauncher<String> permissionLauncher;

    // Tracks whether a pending camera permission request was triggered by
    // the main scan flow or by the per-feature close-up capture flow, so
    // the permission callback knows which launcher to resume with.
    boolean pendingFeatureCameraCapture = false;

    Executor geminiExecutor = Executors.newSingleThreadExecutor();

    Bitmap lastOriginalImage;
    List<Bitmap> featureImages = new ArrayList<>();
    int currentFeatureIndex = 0;

    private View statusBadge;
    private View statusDot;
    private TextView txtStatus;

    private LinearLayout yourRabbitsRow;
    private FirebaseFirestore db;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        Button btnOpenCamera = view.findViewById(R.id.btnOpenCamera);
        Button btnUploadPhoto = view.findViewById(R.id.btnUploadPhoto);

        statusBadge = view.findViewById(R.id.statusBadge);
        statusDot = view.findViewById(R.id.statusDot);
        txtStatus = view.findViewById(R.id.txtStatus);

        yourRabbitsRow = view.findViewById(R.id.yourRabbitsRow);
        db = FirebaseFirestore.getInstance();

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        if (pendingFeatureCameraCapture) {
                            featureCameraLauncher.launch(null);
                        } else {
                            takePictureLauncher.launch(null);
                        }
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

                    lastOriginalImage = image;
                    runLocalClassification(image);
                }
        );

        mainImagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) {
                        Toast.makeText(requireContext(), "No image selected", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Bitmap bitmap = loadBitmapFromUri(uri);

                    if (bitmap == null) {
                        Toast.makeText(requireContext(), "Couldn't load that image, please try another", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    lastOriginalImage = bitmap;
                    runLocalClassification(bitmap);
                }
        );

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

        featureCameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                image -> {
                    if (image == null) {
                        Toast.makeText(requireContext(), "No image captured, skipping this feature", Toast.LENGTH_SHORT).show();
                    } else {
                        featureImages.add(image);
                    }

                    currentFeatureIndex++;
                    promptNextFeatureOrRun();
                }
        );

        btnOpenCamera.setOnClickListener(v -> openCamera());
        btnUploadPhoto.setOnClickListener(v -> mainImagePickerLauncher.launch("image/*"));

        setupConnectivityMonitoring();

        loadUserRabbits();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (db != null && yourRabbitsRow != null) {
            loadUserRabbits();
        }
    }

    private void loadUserRabbits() {

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            yourRabbitsRow.removeAllViews();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("rabbits")
                .whereEqualTo("ownerId", uid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {

                    if (yourRabbitsRow == null || !isAdded()) {
                        return;
                    }

                    yourRabbitsRow.removeAllViews();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {

                        Rabbit rabbit = document.toObject(Rabbit.class);

                        if (rabbit != null) {
                            rabbit.setId(document.getId());
                            yourRabbitsRow.addView(buildRabbitItemView(rabbit));
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Log.e("Camera", "Failed to load rabbits", e)
                );
    }

    private View buildRabbitItemView(Rabbit rabbit) {

        LinearLayout item = new LinearLayout(requireContext());
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        itemParams.setMarginEnd(dpToPx(14));
        item.setLayoutParams(itemParams);

        ImageView thumb = new ImageView(requireContext());

        LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(
                dpToPx(78),
                dpToPx(78)
        );

        thumb.setLayoutParams(thumbParams);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);

        String imageUrl = rabbit.getImageUrl();

        if (imageUrl != null && !imageUrl.isEmpty()) {

            Glide.with(this)
                    .load(imageUrl)
                    .transform(new CenterCrop(), new RoundedCorners(dpToPx(16)))
                    .placeholder(R.drawable.rabbit_thumb_bg)
                    .error(R.drawable.rabbit_thumb_bg)
                    .into(thumb);

        } else {

            thumb.setImageDrawable(null);
            thumb.setBackgroundResource(R.drawable.rabbit_thumb_bg);
        }

        TextView name = new TextView(requireContext());

        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        nameParams.topMargin = dpToPx(6);
        name.setLayoutParams(nameParams);

        String rabbitName = rabbit.getRabbitName();

        name.setText(
                rabbitName == null || rabbitName.trim().isEmpty()
                        ? "Rabbit"
                        : rabbitName
        );

        name.setTextSize(12f);
        name.setTextColor(0xFF3A3226);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);

        item.addView(thumb);
        item.addView(name);

        item.setOnClickListener(v -> {

            Intent intent = new Intent(requireActivity(), AddRabbitActivity.class);
            intent.putExtra("editMode", true);
            intent.putExtra("rabbitId", rabbit.getId());
            startActivity(intent);
        });

        return item;
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    /**
     * Makes any http/https URLs in an AlertDialog's message clickable, and
     * justifies the body text. Must be called AFTER dialog.show(), since
     * the message TextView doesn't exist until the dialog is actually
     * inflated.
     */
    private void styleDialogMessage(AlertDialog dialog) {

        TextView messageView = dialog.findViewById(android.R.id.message);

        if (messageView == null) {
            return;
        }

        Linkify.addLinks(messageView, Linkify.WEB_URLS);
        messageView.setMovementMethod(LinkMovementMethod.getInstance());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            messageView.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD);
        }
    }

    private void setupConnectivityMonitoring() {

        connectivityManager = (ConnectivityManager)
                requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            updateStatusBadge(false);
            return;
        }

        updateStatusBadge(isCurrentlyOnline());

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {

            @Override
            public void onAvailable(@NonNull Network network) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() ->
                            updateStatusBadge(true)
                    );
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() ->
                            updateStatusBadge(isCurrentlyOnline())
                    );
                }
            }

            @Override
            public void onCapabilitiesChanged(
                    @NonNull Network network,
                    @NonNull NetworkCapabilities capabilities
            ) {
                if (isAdded()) {

                    boolean online =
                            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

                    requireActivity().runOnUiThread(() ->
                            updateStatusBadge(online)
                    );
                }
            }
        };

        connectivityManager.registerNetworkCallback(request, networkCallback);
    }

    private boolean isCurrentlyOnline() {

        if (connectivityManager == null) {
            return false;
        }

        Network network = connectivityManager.getActiveNetwork();

        if (network == null) {
            return false;
        }

        NetworkCapabilities capabilities =
                connectivityManager.getNetworkCapabilities(network);

        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void updateStatusBadge(boolean online) {

        if (statusBadge == null || statusDot == null || txtStatus == null) {
            return;
        }

        if (online) {

            statusBadge.setBackgroundResource(R.drawable.status_pill_online);
            statusDot.setBackgroundResource(R.drawable.status_dot_online);
            txtStatus.setText("Online · Gemini AI");
            txtStatus.setTextColor(0xFF1F6B37);

        } else {

            statusBadge.setBackgroundResource(R.drawable.status_pill_offline);
            statusDot.setBackgroundResource(R.drawable.status_dot_offline);
            txtStatus.setText("Offline");
            txtStatus.setTextColor(0xFF5C5748);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }

        networkCallback = null;
        statusBadge = null;
        statusDot = null;
        txtStatus = null;
        yourRabbitsRow = null;
    }

    private void openCamera() {

        pendingFeatureCameraCapture = false;

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED) {

            takePictureLauncher.launch(null);

        } else {

            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    /**
     * Same as openCamera(), but resumes the per-feature close-up capture
     * flow instead of the main scan flow once a photo is taken (or
     * permission is granted).
     */
    private void openCameraForFeature() {

        pendingFeatureCameraCapture = true;

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED) {

            featureCameraLauncher.launch(null);

        } else {

            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private Bitmap loadBitmapFromUri(Uri uri) {

        try {

            return MediaStore.Images.Media.getBitmap(
                    requireContext().getContentResolver(),
                    uri
            );

        } catch (IOException e) {

            Log.e("Camera", "Failed to load bitmap from gallery Uri", e);
            return null;
        }
    }

    /**
     * Runs both the breed classifier and the disease classifier on the
     * same photo and reports both results together. The breed model's
     * confidence is used as the gate for "is there actually a rabbit in
     * this photo" — if that's too low we don't bother reporting disease
     * either, since we can't trust that we're looking at a rabbit at all.
     */
    private void runLocalClassification(Bitmap image) {

        Bitmap processedImage =
                image.copy(Bitmap.Config.ARGB_8888, true);

        int dimension =
                Math.min(
                        processedImage.getWidth(),
                        processedImage.getHeight()
                );

        processedImage =
                ThumbnailUtils.extractThumbnail(
                        processedImage,
                        dimension,
                        dimension
                );

        processedImage =
                Bitmap.createScaledBitmap(
                        processedImage,
                        imageSize,
                        imageSize,
                        false
                );

        try {

            // --- Breed classification (also gates rabbit presence) ---
            ImageClassifier.ImageClassifierOptions breedOptions =
                    ImageClassifier.ImageClassifierOptions.builder()
                            .setBaseOptions(BaseOptions.builder().build())
                            .setMaxResults(1)
                            .setScoreThreshold(0.01f)
                            .build();

            ImageClassifier breedClassifier =
                    ImageClassifier.createFromFileAndOptions(
                            requireContext(),
                            "model.tflite",
                            breedOptions
                    );

            List<Classifications> breedResults =
                    breedClassifier.classify(
                            TensorImage.fromBitmap(processedImage)
                    );

            if (breedResults == null
                    || breedResults.isEmpty()
                    || breedResults.get(0).getCategories().isEmpty()) {

                showNoRabbitDetectedDialog();
                return;
            }

            Classifications breedClassification = breedResults.get(0);

            float breedConfidence =
                    breedClassification.getCategories().get(0).getScore();

            int breedIndex =
                    breedClassification.getCategories().get(0).getIndex();

            if (breedConfidence < RABBIT_PRESENCE_THRESHOLD) {
                showNoRabbitDetectedDialog();
                return;
            }

            String breedLabel =
                    (breedIndex >= 0 && breedIndex < breedLabels.length)
                            ? breedLabels[breedIndex]
                            : "Unknown";

            if (breedLabel.equals("Unknown")) {
                showNoRabbitDetectedDialog();
                return;
            }

            // --- Disease classification (informational only) ---
            String diseaseLabel = NO_ILLNESS_LABEL;

            try {

                ImageClassifier.ImageClassifierOptions diseaseOptions =
                        ImageClassifier.ImageClassifierOptions.builder()
                                .setBaseOptions(BaseOptions.builder().build())
                                .setMaxResults(1)
                                .setScoreThreshold(0.01f)
                                .build();

                ImageClassifier diseaseClassifier =
                        ImageClassifier.createFromFileAndOptions(
                                requireContext(),
                                "diseasemodel.tflite",
                                diseaseOptions
                        );

                List<Classifications> diseaseResults =
                        diseaseClassifier.classify(
                                TensorImage.fromBitmap(processedImage)
                        );

                if (diseaseResults != null
                        && !diseaseResults.isEmpty()
                        && !diseaseResults.get(0).getCategories().isEmpty()) {

                    Classifications diseaseClassification = diseaseResults.get(0);

                    float diseaseConfidence =
                            diseaseClassification.getCategories().get(0).getScore();

                    int diseaseIndex =
                            diseaseClassification.getCategories().get(0).getIndex();

                    if (diseaseConfidence >= DISEASE_REPORT_THRESHOLD
                            && diseaseIndex >= 0
                            && diseaseIndex < diseaseLabels.length) {

                        diseaseLabel = diseaseLabels[diseaseIndex];
                    }
                }

            } catch (Exception e) {
                Log.e("Camera", "Disease classification failed", e);
            }

            String careInfo = getCareInfo(breedLabel);

            String diseaseInfo =
                    diseaseLabel.equals(NO_ILLNESS_LABEL)
                            ? ""
                            : getDiseaseInfo(diseaseLabel);

            showResultDialog(breedLabel, diseaseLabel, careInfo, diseaseInfo);

        } catch (Exception e) {

            Log.e("Camera", "Classification failed", e);

            Toast.makeText(
                    requireContext(),
                    "Unable to analyze the picture. Please try again.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void showNoRabbitDetectedDialog() {

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("No Rabbit Detected")
                .setMessage(
                        "No rabbit could be detected in this picture. Please take or choose another picture with the rabbit clearly visible."
                )
                .setPositiveButton(
                        "Take Picture Again",
                        (d, which) -> openCamera()
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();

        styleDialogMessage(dialog);
    }

    private void showResultDialog(
            String breedLabel,
            String diseaseLabel,
            String careInfo,
            String diseaseInfo
    ) {

        StringBuilder message = new StringBuilder();

        message.append("Breed: ").append(breedLabel).append("\n");
        message.append("Health: ").append(diseaseLabel).append("\n\n");
        message.append(careInfo);

        if (!diseaseInfo.isEmpty()) {
            message.append("\n\n").append(diseaseInfo);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Scan Results")
                .setMessage(message.toString())
                .setPositiveButton("OK", null)
                .setNeutralButton(
                        "Improve with Gemini",
                        (d, which) -> startFeatureCaptureFlow()
                )
                .show();

        styleDialogMessage(dialog);
    }

    private void startFeatureCaptureFlow() {

        featureImages = new ArrayList<>();
        currentFeatureIndex = 0;
        promptNextFeatureOrRun();
    }

    private void promptNextFeatureOrRun() {

        if (currentFeatureIndex < featureNames.length) {

            String feature =
                    featureNames[currentFeatureIndex];

            new AlertDialog.Builder(requireContext())
                    .setTitle("Better result with Gemini")
                    .setMessage(
                            "Please choose a close-up photo of the rabbit's "
                                    + feature.toLowerCase(Locale.ROOT)
                                    + " from your gallery."
                    )
                    .setCancelable(false)
                    .setPositiveButton(
                            "Choose Photo",
                            (dialog, which) ->
                                    featurePickerLauncher.launch("image/*")
                    )
                    .setNeutralButton(
                            "Take Picture",
                            (dialog, which) -> openCameraForFeature()
                    )
                    .setNegativeButton(
                            "Skip",
                            (dialog, which) -> {

                                currentFeatureIndex++;
                                promptNextFeatureOrRun();
                            }
                    )
                    .show();

        } else {

            runGeminiRefinement();
        }
    }

    /**
     * Sends the original photo plus any feature close-ups to Gemini and
     * asks it to identify BOTH the breed and any disease/condition in a
     * single combined response.
     */
    private void runGeminiRefinement() {

        if (lastOriginalImage == null) {

            Toast.makeText(
                    requireContext(),
                    "Missing original photo, please try again",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Toast.makeText(
                requireContext(),
                "Analyzing with Gemini...",
                Toast.LENGTH_SHORT
        ).show();

        GenerativeModel firebaseAI =
                FirebaseAI.getInstance(
                        GenerativeBackend.googleAI()
                ).generativeModel(
                        "gemini-3.5-flash-lite"
                );

        GenerativeModelFutures model =
                GenerativeModelFutures.from(firebaseAI);

        String breedList =
                String.join(", ", breedLabels);

        String diseaseList =
                String.join(", ", diseaseLabels) + ", Healthy";

        String basePrompt =
                "You are both a rabbit breed expert and a rabbit health expert. Analyze the overall photo plus "
                        + "the close-up feature photos provided (each labeled) to identify BOTH the rabbit's breed "
                        + "AND any visible disease or health condition in a single pass.\n\n"
                        + "For the breed, evaluate the rabbit against ALL recognized domestic rabbit breeds. "
                        + "Return a percentage confidence for every breed that is reasonably plausible based on the photos. "
                        + "Do not use categories such as \"Other\", \"Unknown\", \"Unidentified\", or \"Mixed\" as a breed. "
                        + "If the rabbit does not match the provided breed list, identify the actual rabbit breed that it "
                        + "most closely resembles and provide that breed's name. "
                        + "If the rabbit appears to be a mix, provide the most likely actual breeds contributing to the mix "
                        + "and assign estimated percentages to each. "
                        + "Every breed name must be the name of an actual recognized rabbit breed. "
                        + "The breed confidence percentages must add up to exactly 100.\n\n"
                        + "For health, evaluate the rabbit against ALL known rabbit diseases and health conditions that "
                        + "could potentially be identified from visible photographic signs. "
                        + "Return a percentage likelihood for every relevant disease or health condition. "
                        + "Do not use \"Other\" or \"Unknown\" as a disease category. "
                        + "If there are no visible signs of illness, give \"Healthy\" the highest percentage. "
                        + "The disease confidence percentages must add up to exactly 100.\n\n"
                        + "Also write one short combined care plan covering diet, housing/exercise, breed-specific notes, "
                        + "and any health guidance or warning signs based on what you found. "
                        + "This is general guidance only, not a substitute for veterinary diagnosis.\n\n"
                        + "Respond ONLY in JSON, no extra text, in this exact format: "
                        + "{\"breed\": \"Holland Lop\", \"breed_confidences\": {\"Holland Lop\": 80, "
                        + "\"Mini Lop\": 10, \"Netherland Dwarf\": 5, \"Lionhead\": 5}, "
                        + "\"disease\": \"Healthy\", \"disease_confidences\": {\"Healthy\": 80, "
                        + "\"Mites\": 5, \"Malocclusion\": 5, \"Pasteurellosis\": 5, \"Myxomatosis\": 5}, "
                        + "\"care\": \"short combined care plan here\"}";;

        Content.Builder contentBuilder =
                new Content.Builder()
                        .addText(basePrompt)
                        .addText("Overall photo:")
                        .addImage(lastOriginalImage);

        for (int i = 0; i < featureImages.size(); i++) {

            String label =
                    i < featureNames.length
                            ? featureNames[i]
                            : ("Feature " + (i + 1));

            contentBuilder
                    .addText(
                            "Close-up of the "
                                    + label.toLowerCase(Locale.ROOT)
                                    + ":"
                    )
                    .addImage(featureImages.get(i));
        }

        Content content =
                contentBuilder.build();

        ListenableFuture<GenerateContentResponse> response =
                model.generateContent(content);

        Futures.addCallback(
                response,
                new FutureCallback<GenerateContentResponse>() {

                    @Override
                    public void onSuccess(
                            GenerateContentResponse result
                    ) {

                        handleGeminiResult(result.getText());
                    }

                    @Override
                    public void onFailure(Throwable t) {

                        Log.e(
                                "Camera",
                                "Gemini refinement failed",
                                t
                        );

                        if (isAdded()) {

                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(
                                            requireContext(),
                                            "Gemini analysis failed: "
                                                    + t.getMessage(),
                                            Toast.LENGTH_LONG
                                    ).show()
                            );
                        }
                    }
                },
                geminiExecutor
        );
    }

    private void handleGeminiResult(String rawText) {

        if (!isAdded()) {
            return;
        }

        requireActivity().runOnUiThread(() -> {

            try {

                String json =
                        extractJson(rawText);

                JSONObject obj =
                        new JSONObject(json);

                String breedLabel =
                        obj.optString(
                                "breed",
                                "Unknown"
                        );

                JSONObject breedConfidences =
                        obj.optJSONObject("breed_confidences");

                String diseaseLabel =
                        obj.optString(
                                "disease",
                                NO_ILLNESS_LABEL
                        );

                JSONObject diseaseConfidences =
                        obj.optJSONObject("disease_confidences");

                String care =
                        obj.optString(
                                "care",
                                ""
                        ).trim();

                if (breedLabel.equalsIgnoreCase("Unknown")
                        || breedLabel.equalsIgnoreCase("No rabbit")
                        || breedLabel.equalsIgnoreCase("No rabbit detected")) {

                    showNoRabbitDetectedDialog();
                    return;
                }

                StringBuilder message =
                        new StringBuilder();

                message.append("Breed: ")
                        .append(breedLabel)
                        .append("\n\n");

                appendConfidences(message, breedConfidences);

                message.append("\nHealth: ")
                        .append(diseaseLabel)
                        .append("\n\n");

                if (!diseaseLabel.equalsIgnoreCase("Healthy")) {
                    appendConfidences(message, diseaseConfidences);
                }

                if (!care.isEmpty()) {

                    message.append(
                                    "\nRecommended Care:\n"
                            )
                            .append(care)
                            .append("\n");
                }

                AlertDialog dialog = new AlertDialog.Builder(requireContext())
                        .setTitle("Gemini Breed & Health Estimate")
                        .setMessage(message.toString())
                        .setPositiveButton("OK", null)
                        .show();

                styleDialogMessage(dialog);

            } catch (JSONException e) {

                Log.e(
                        "Camera",
                        "Failed to parse Gemini response: " + rawText,
                        e
                );

                AlertDialog fallbackDialog = new AlertDialog.Builder(requireContext())
                        .setTitle("Gemini Result")
                        .setMessage(rawText)
                        .setPositiveButton("OK", null)
                        .show();

                styleDialogMessage(fallbackDialog);
            }
        });
    }

    private void appendConfidences(StringBuilder message, JSONObject confidences) {

        if (confidences == null) {
            return;
        }

        java.util.Iterator<String> keys =
                confidences.keys();

        while (keys.hasNext()) {

            String key =
                    keys.next();

            message.append(key)
                    .append(": ")
                    .append(
                            confidences.optInt(
                                    key,
                                    0
                            )
                    )
                    .append("%\n");
        }
    }

    private String extractJson(String text) {

        if (text == null) {
            return "{}";
        }

        String trimmed =
                text.trim();

        if (trimmed.startsWith("```")) {

            trimmed =
                    trimmed
                            .replaceFirst(
                                    "^```(json)?",
                                    ""
                            )
                            .trim();

            if (trimmed.endsWith("```")) {

                trimmed =
                        trimmed.substring(
                                0,
                                trimmed.length() - 3
                        ).trim();
            }
        }

        return trimmed;
    }

    /**
     * Breed-specific care summary. Figures are drawn from Merck Veterinary
     * Manual, PetMD, Animal Humane Society, and the Californian/Holland Lop
     * breed guides — general portion rule of thumb is about 1/4 cup of
     * pellets per 4-5 lb of body weight, with hay making up the bulk of
     * the diet.
     */
    private String getCareInfo(String breed) {

        switch (breed.toLowerCase()) {

            case "new zealand":

                return "Breed: New Zealand (9-12 lb, lifespan 5-8 yrs)\n\n"
                        + "Diet:\n"
                        + "- Hay: unlimited, at least 80% of daily diet\n"
                        + "- Leafy greens: ~1 cup per 2-3 lb of body weight/day (rotate kale, spinach, parsley, romaine, dandelion greens, arugula, bok choy)\n"
                        + "- Pellets: ~1/4 cup per 4-5 lb of body weight/day\n"
                        + "- Fruit: only occasionally, 1-2 tbsp per 5 lb, 1-2x/week (too much can cause obesity or GI stasis)\n\n"
                        + "Care:\n"
                        + "- Spacious cage/enclosure with daily exercise time\n"
                        + "- Fresh water available at all times\n\n"
                        + "Notes:\n"
                        + "- Larger breed — monitor weight to avoid obesity\n\n"
                        + "Video Guide: \n"
                        + "https://youtu.be/FbibvbYxIzw?si=GPenxib7t4yb1Ko2";

            case "lionhead":

                return "Breed: Lionhead (2.5-3.75 lb, lifespan 7-9 yrs)\n\n"
                        + "Diet:\n"
                        + "- Hay: unlimited fresh Timothy hay, roughly their own body weight worth per day\n"
                        + "- Greens: ~1 cup per 2 lb of body weight/day (arugula, parsley, mint, basil, cilantro, spinach, romaine — avoid iceberg lettuce, little nutritional value)\n"
                        + "- Small amounts of broccoli, bell pepper, squash, kale, zucchini, Brussels sprouts, or carrot tops; carrots themselves sparingly (high in carbs)\n"
                        + "- Pellets: about 1/8 cup/day for an adult (smaller breed than average)\n\n"
                        + "Care:\n"
                        + "- Regular grooming required for the long mane fur\n\n"
                        + "Notes:\n"
                        + "- Watch for hair ingestion/GI blockage from grooming\n\n"
                        + "Video Guide: \n"
                        + "https://youtu.be/57y91glfDGc?si=TGHYF2oIh40aiCxw";

            case "holland":
            case "holland lop":

                return "Breed: Holland Lop (up to 4 lb, lifespan 7-10 yrs)\n\n"
                        + "Diet:\n"
                        + "- Hay: unlimited amounts, majority of diet\n"
                        + "- Fresh greens: smaller amount daily\n"
                        + "- Pellets: ~1/4 cup per 4-5 lb of body weight/day\n\n"
                        + "Care:\n"
                        + "- Check and clean ears regularly (lop ears are prone to wax buildup/infection)\n\n"
                        + "Notes:\n"
                        + "- Small breed — avoid overfeeding pellets to prevent obesity\n\n"
                        + "Video Guide: \n"
                        + "https://youtu.be/HfLwpvfjuuI?si=dXG-fEA-BqKQ5mIJ";

            case "california":

                return "Breed: Californian (2.5-4 kg)\n\n"
                        + "Diet:\n"
                        + "- Hay (timothy, orchard grass, or meadow hay): 80-85% of daily diet\n"
                        + "- Pellets: ~1/4 cup per 5 lb of body weight/day for adults; free-choice for growing/nursing rabbits. Choose high-fiber, balanced-protein pellets with minimal fillers\n"
                        + "- Fresh greens for extra vitamins: romaine lettuce, mustard greens, cilantro, basil, dandelion greens\n\n"
                        + "Care:\n"
                        + "- Keep in a cool environment — dense coat is prone to overheating\n\n"
                        + "Notes:\n"
                        + "- Check skin/coat regularly; can be anxious or easily startled, males may show aggression\n\n"
                        + "Video Guide: \n"
                        + "https://youtu.be/3fJJRRsDwUo?si=hKjR826zbbiKWGFk";

            default:

                return "General Rabbit Care\n\n"
                        + "Diet:\n"
                        + "- Timothy hay freely available at all times (ad libitum)\n"
                        + "- Pellets: ~1/4 cup per 5 lb of body weight/day, controlled portions to prevent obesity\n"
                        + "- Fresh, clean water always available — rabbits drink about 120 mL per kg of body weight/day (roughly double a cat or dog of similar size); an open bowl may get more use than a sipper bottle\n"
                        + "- Keep dietary calcium around 0.4-0.5% for adult non-breeding rabbits; avoid diets based mainly on alfalfa meal, which raises the risk of kidney/urinary calcium problems\n\n"
                        + "Foods & plants to avoid:\n"
                        + "- Aloe, azalea, calla lily, lily of the valley, philodendron, corn plant, carnation\n"
                        + "- Apple seeds, raw beans, sweet potato, rhubarb leaves, potato eyes/shoots/green parts, chocolate\n\n"
                        + "Notes:\n"
                        + "- Loss of appetite can be a sign of dehydration or illness — seek veterinary care if a rabbit stops eating";
        }
    }

    private String getDiseaseInfo(String disease) {

        switch (disease.toLowerCase()) {

            case "myxomatosis":

                return " Myxomatosis\n️ Severe viral disease\n Vet required\n If unsure of this result please Schedule a visit with your Veterinarian.\n\n"
                        + "Additional Info: \n"
                        + "https://youtu.be/RUezkUetEEA?si=gcsRr-tNhlvpU7sg";

            case "mites":

                return " Mites\n️ Skin irritation\n Treat with ivermectin\n If unsure of this result please Schedule a visit with your Veterinarian.\n\n"
                        + "Additional Info: \n"
                        + "https://youtu.be/iQyPn2VL70s?si=EAnvWMapiA8YgJRW";

            case "malocclusion":

                return " Malocclusion\n️ Teeth problem\n Needs dental care\n If unsure of this result please Schedule a visit with your Veterinarian.\n\n"
                        + "Additional Info: \n"
                        + "https://youtu.be/2F328Q38uJc?si=PQNXStwEciaECc8h";

            case "pasteurellosis":

                return " Pasteurellosis\n⚠ Respiratory infection\n Antibiotics required\n If unsure of this result please Schedule a visit with your Veterinarian.\n\n"
                        + "Additional Info: \n"
                        + "https://youtu.be/Uxj0VIqC83Q?si=J5OugchFPDFVkepC";
        }

        return "";
    }
}