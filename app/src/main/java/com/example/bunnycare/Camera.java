package com.example.bunnycare;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
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
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
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
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
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
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.classifier.Classifications;
import org.tensorflow.lite.task.vision.classifier.ImageClassifier;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Camera extends Fragment {

    int imageSize = 224;

    static final float RABBIT_PRESENCE_THRESHOLD = 0.99f;
    static final float BREED_MIN_MARGIN = 0.1f;

    static final String NO_ILLNESS_LABEL = "No signs of illness detected";

    static final String COLLECTION_USERS = "users";
    static final String COLLECTION_VET_REVIEWS = "vetRecommendations";

    static final String STATUS_PENDING = "pending_approval";

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

    ActivityResultLauncher<Void> takePictureLauncher;
    ActivityResultLauncher<String> mainImagePickerLauncher;
    ActivityResultLauncher<String> permissionLauncher;

    Executor geminiExecutor = Executors.newSingleThreadExecutor();

    Bitmap lastOriginalImage;

    private PopupWindow geminiTipPopup;
    private AlertDialog loadingDialog;
    private View statusBadge;
    private View statusDot;
    private TextView txtStatus;
    private LinearLayout yourRabbitsRow;
    private FirebaseFirestore db;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private interface RoleCallback {
        void onResult(boolean isVet);
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {

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
                        showPictureGuidelinesOverlay(
                                () -> takePictureLauncher.launch(null)
                        );
                    } else {
                        Toast.makeText(
                                requireContext(),
                                "Permission denied",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                image -> {
                    if (image == null) {
                        Toast.makeText(
                                requireContext(),
                                "No image captured",
                                Toast.LENGTH_SHORT
                        ).show();
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
                        Toast.makeText(
                                requireContext(),
                                "No image selected",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    Bitmap bitmap = loadBitmapFromUri(uri);

                    if (bitmap == null) {
                        Toast.makeText(
                                requireContext(),
                                "Couldn't load that image, please try another",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    lastOriginalImage = bitmap;
                    runLocalClassification(bitmap);
                }
        );

        btnOpenCamera.setOnClickListener(v -> openCamera());

        btnUploadPhoto.setOnClickListener(v ->
                showPictureGuidelinesOverlay(
                        () -> mainImagePickerLauncher.launch("image/*")
                )
        );

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
        item.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout.LayoutParams itemParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        itemParams.setMarginEnd(dpToPx(14));
        item.setLayoutParams(itemParams);

        ImageView thumb = new ImageView(requireContext());

        thumb.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(78), dpToPx(78)));
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

        LinearLayout.LayoutParams nameParams =
                new LinearLayout.LayoutParams(
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

    private void highlightVetRequired(SpannableStringBuilder sb) {

        final String target = "Vet required";
        final int RED = 0xFFD32F2F;
        final int RED_HIGHLIGHT = 0x33D32F2F;

        String text = sb.toString();
        int index = text.indexOf(target);

        while (index >= 0) {

            int end = index + target.length();

            sb.setSpan(new ForegroundColorSpan(RED), index, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), index, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new BackgroundColorSpan(RED_HIGHLIGHT), index, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            index = text.indexOf(target, end);
        }
    }

    private void setupConnectivityMonitoring() {

        connectivityManager =
                (ConnectivityManager) requireContext()
                        .getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            updateStatusBadge(false);
            return;
        }

        updateStatusBadge(isCurrentlyOnline());

        NetworkRequest request =
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {

            @Override
            public void onAvailable(@NonNull Network network) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> updateStatusBadge(true));
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() ->
                            updateStatusBadge(isCurrentlyOnline()));
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

                    requireActivity().runOnUiThread(() -> updateStatusBadge(online));
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

        if (geminiTipPopup != null && geminiTipPopup.isShowing()) {
            geminiTipPopup.dismiss();
        }

        dismissLoading();

        geminiTipPopup = null;
        networkCallback = null;
        statusBadge = null;
        statusDot = null;
        txtStatus = null;
        yourRabbitsRow = null;
    }

    private void openCamera() {

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED) {

            showPictureGuidelinesOverlay(() -> takePictureLauncher.launch(null));

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

    private void showPictureGuidelinesOverlay(Runnable onProceed) {

        if (getView() == null || !isAdded()) {

            if (onProceed != null) {
                onProceed.run();
            }

            return;
        }

        if (geminiTipPopup != null && geminiTipPopup.isShowing()) {
            return;
        }

        Context context = requireContext();

        LinearLayout container = new LinearLayout(context);

        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFFF8F5EA);
        background.setCornerRadius(dpToPx(22));
        background.setStroke(dpToPx(1), 0xFFE3DECF);

        container.setBackground(background);
        container.setElevation(dpToPx(12));

        ImageView guidelineImage = new ImageView(context);

        guidelineImage.setImageResource(R.drawable.picture_guidelines);
        guidelineImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        guidelineImage.setLayoutParams(
                new LinearLayout.LayoutParams(dpToPx(360), dpToPx(300))
        );

        container.addView(guidelineImage);

        Button continueButton = new Button(context);

        continueButton.setText("Continue");
        continueButton.setTextSize(15f);
        continueButton.setTextColor(Color.WHITE);
        continueButton.setAllCaps(false);

        GradientDrawable continueBackground = new GradientDrawable();
        continueBackground.setColor(0xFF3A7D44);
        continueBackground.setCornerRadius(dpToPx(12));

        continueButton.setBackground(continueBackground);
        continueButton.setPadding(dpToPx(28), dpToPx(8), dpToPx(28), dpToPx(8));

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        buttonParams.gravity = Gravity.END;
        buttonParams.topMargin = dpToPx(4);
        buttonParams.bottomMargin = dpToPx(8);
        buttonParams.rightMargin = dpToPx(8);

        continueButton.setLayoutParams(buttonParams);

        container.addView(continueButton);

        PopupWindow popupWindow =
                new PopupWindow(
                        container,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        true
                );

        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(true);

        geminiTipPopup = popupWindow;

        continueButton.setOnClickListener(v -> {

            popupWindow.dismiss();

            if (onProceed != null) {
                onProceed.run();
            }
        });

        popupWindow.setOnDismissListener(() -> {
            if (geminiTipPopup == popupWindow) {
                geminiTipPopup = null;
            }
        });

        popupWindow.showAtLocation(getView(), Gravity.CENTER, 0, 0);
    }

    private void runLocalClassification(Bitmap image) {

        Bitmap processedImage = image.copy(Bitmap.Config.ARGB_8888, true);

        int dimension = Math.min(processedImage.getWidth(), processedImage.getHeight());

        processedImage = ThumbnailUtils.extractThumbnail(processedImage, dimension, dimension);
        processedImage = Bitmap.createScaledBitmap(processedImage, imageSize, imageSize, false);

        try {

            ImageClassifier.ImageClassifierOptions breedOptions =
                    ImageClassifier.ImageClassifierOptions
                            .builder()
                            .setBaseOptions(BaseOptions.builder().build())
                            .setMaxResults(breedLabels.length)
                            .setScoreThreshold(0.01f)
                            .build();

            ImageClassifier breedClassifier =
                    ImageClassifier.createFromFileAndOptions(
                            requireContext(),
                            "model.tflite",
                            breedOptions
                    );

            List<Classifications> breedResults =
                    breedClassifier.classify(TensorImage.fromBitmap(processedImage));

            if (breedResults == null
                    || breedResults.isEmpty()
                    || breedResults.get(0).getCategories().isEmpty()) {

                showNoRabbitDetectedDialog();
                return;
            }

            List<Category> breedCategories = breedResults.get(0).getCategories();

            int breedIndex = -1;
            float breedConfidence = -1f;
            float secondBestConfidence = -1f;

            for (Category category : breedCategories) {

                float score = category.getScore();

                if (score > breedConfidence) {
                    secondBestConfidence = breedConfidence;
                    breedConfidence = score;
                    breedIndex = category.getIndex();
                } else if (score > secondBestConfidence) {
                    secondBestConfidence = score;
                }
            }

            if (secondBestConfidence < 0f) {
                secondBestConfidence = 0f;
            }

            boolean clearlyDecisive =
                    (breedConfidence - secondBestConfidence) >= BREED_MIN_MARGIN;

            if (breedIndex < 0
                    || breedConfidence < RABBIT_PRESENCE_THRESHOLD
                    || !clearlyDecisive) {

                showNoRabbitDetectedDialog();
                return;
            }

            String breedLabel =
                    breedIndex < breedLabels.length
                            ? breedLabels[breedIndex]
                            : "Unknown";

            if (breedLabel.equals("Unknown")) {
                showNoRabbitDetectedDialog();
                return;
            }

            String careInfo = getCareInfo(breedLabel);

            if (isCurrentlyOnline()) {

                showLoading();
                runGeminiDiseaseAnalysis(image, breedLabel, careInfo);

            } else {

                fetchApprovedAdditionsThenShow(
                        breedLabel,
                        "Unavailable (offline, connect to the internet for a health check)",
                        "",
                        careInfo,
                        ""
                );
            }

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

        AlertDialog dialog =
                new AlertDialog.Builder(requireContext())
                        .setTitle("No Rabbit Detected")
                        .setMessage("No rabbit could be detected in this picture. Please take or choose another picture with the rabbit clearly visible.")
                        .setPositiveButton("Take Picture Again", (d, which) -> openCamera())
                        .setNeutralButton("Choose Photo", (d, which) ->
                                showPictureGuidelinesOverlay(
                                        () -> mainImagePickerLauncher.launch("image/*")
                                ))
                        .setNegativeButton("Cancel", null)
                        .show();

        styleDialogMessage(dialog);
    }

    private void runGeminiDiseaseAnalysis(
            Bitmap image,
            String breedLabel,
            String careInfo
    ) {

        GenerativeModel firebaseAI =
                FirebaseAI.getInstance(GenerativeBackend.googleAI())
                        .generativeModel("gemini-3.5-flash-lite");

        GenerativeModelFutures model = GenerativeModelFutures.from(firebaseAI);

        String prompt =
                "You are a rabbit health expert. Analyze the rabbit photo provided and identify any visible "
                        + "disease or health condition. Do NOT identify the breed.\n\n"
                        + "Evaluate the rabbit against ALL known rabbit diseases and health conditions that could "
                        + "potentially be identified from visible photographic signs. Prefer these names when they "
                        + "apply: " + String.join(", ", diseaseLabels) + ", Healthy. "
                        + "Return a percentage likelihood for every relevant disease or health condition. "
                        + "Do not use \"Other\" or \"Unknown\" as a disease category. "
                        + "If there are no visible signs of illness, give \"Healthy\" the highest percentage. "
                        + "Myxomatosis is a severe, comparatively rare condition with very distinctive signs "
                        + "(swollen eyelids, puffy skin/lumps around the face, ears, and genitals, thick eye or nasal discharge). "
                        + "Only assign Myxomatosis a meaningfully high percentage if those specific signs are clearly visible; "
                        + "otherwise keep its percentage low and favor more common explanations (e.g. Healthy, Mites, "
                        + "Malocclusion, Pasteurellosis, or general skin/fur conditions). "
                        + "The disease confidence percentages must add up to exactly 100. "
                        + "This is general guidance only, not a substitute for veterinary diagnosis.\n\n"
                        + "Respond ONLY in JSON, no extra text, in this exact format: "
                        + "{\"disease\": \"Healthy\", \"disease_confidences\": {\"Healthy\": 80, "
                        + "\"Mites\": 5, \"Malocclusion\": 5, \"Pasteurellosis\": 5, \"Myxomatosis\": 5}}";

        Content content =
                new Content.Builder()
                        .addText(prompt)
                        .addText("Rabbit photo:")
                        .addImage(image)
                        .build();

        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Futures.addCallback(
                response,
                new FutureCallback<GenerateContentResponse>() {

                    @Override
                    public void onSuccess(GenerateContentResponse result) {
                        handleGeminiDiseaseResult(result.getText(), breedLabel, careInfo);
                    }

                    @Override
                    public void onFailure(Throwable t) {

                        Log.e("Camera", "Gemini disease analysis failed", t);

                        if (!isAdded()) {
                            return;
                        }

                        requireActivity().runOnUiThread(() -> {

                            dismissLoading();

                            Toast.makeText(
                                    requireContext(),
                                    "Gemini health check failed: " + t.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show();

                            fetchApprovedAdditionsThenShow(
                                    breedLabel,
                                    "Unavailable (health check failed, please try again)",
                                    "",
                                    careInfo,
                                    ""
                            );
                        });
                    }
                },
                geminiExecutor
        );
    }

    private void handleGeminiDiseaseResult(
            String rawText,
            String breedLabel,
            String careInfo
    ) {

        if (!isAdded()) {
            return;
        }

        requireActivity().runOnUiThread(() -> {

            dismissLoading();

            String diseaseLabel;
            String confidenceText = "";
            String diseaseInfo = "";

            try {

                JSONObject obj = new JSONObject(extractJson(rawText));

                String rawDisease = obj.optString("disease", "Healthy").trim();

                if (rawDisease.isEmpty() || rawDisease.equalsIgnoreCase("Healthy")) {

                    diseaseLabel = NO_ILLNESS_LABEL;

                } else {

                    diseaseLabel = rawDisease;
                    confidenceText = buildConfidenceText(obj.optJSONObject("disease_confidences"));
                    diseaseInfo = getDiseaseInfo(rawDisease);
                }

            } catch (JSONException e) {

                Log.e("Camera", "Failed to parse Gemini response: " + rawText, e);
                diseaseLabel = "Unavailable (couldn't read the health result)";
            }

            fetchApprovedAdditionsThenShow(breedLabel, diseaseLabel, confidenceText, careInfo, diseaseInfo);
        });
    }

    private String buildConfidenceText(JSONObject confidences) {

        if (confidences == null) {
            return "";
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        Iterator<String> keys = confidences.keys();

        while (keys.hasNext()) {

            String key = keys.next();
            int value = confidences.optInt(key, 0);

            if (value > 0) {
                entries.add(new AbstractMap.SimpleEntry<>(key, value));
            }
        }

        Collections.sort(entries, (a, b) -> b.getValue() - a.getValue());

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, Integer> entry : entries) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("%\n");
        }

        return sb.toString().trim();
    }

    private String extractJson(String text) {

        if (text == null) {
            return "{}";
        }

        String trimmed = text.trim();

        if (trimmed.startsWith("```")) {

            trimmed = trimmed.replaceFirst("^```(json)?", "").trim();

            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
            }
        }

        return trimmed;
    }

    private void checkIfCurrentUserIsVet(RoleCallback callback) {

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            callback.onResult(false);
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection(COLLECTION_USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {

                    if (doc == null || !doc.exists()) {
                        callback.onResult(false);
                        return;
                    }

                    Boolean verifiedVet = doc.getBoolean("verifiedVet");

                    callback.onResult(
                            verifiedVet != null && verifiedVet
                    );
                })
                .addOnFailureListener(e -> {
                    Log.e("Camera", "Failed to check vet verification status", e);
                    callback.onResult(false);
                });
    }

    private void onAddRecommendationClicked(
            String breedLabel,
            String diseaseLabel
    ) {

        checkIfCurrentUserIsVet(isVet -> {

            if (!isAdded()) {
                return;
            }

            if (isVet) {
                showAddRecommendationDialog(breedLabel, diseaseLabel);
            } else {
                Toast.makeText(
                        requireContext(),
                        "Only verified vets can add a recommendation.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void showAddRecommendationDialog(
            String breedLabel,
            String diseaseLabel
    ) {

        Context context = requireContext();

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(4));

        EditText dietInput = addLabeledInput(context, container, "Diet", "e.g. Increase leafy greens, add more hay volume", 0);
        EditText careInput = addLabeledInput(context, container, "Care", "e.g. Increase enclosure ventilation, daily grooming", 14);
        EditText treatmentInput = addLabeledInput(context, container, "Treatment", "e.g. Ivermectin dosage, frequency, duration", 14);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Add Recommendation")
                .setMessage("This will be submitted for admin approval before it's added.")
                .setView(container)
                .setPositiveButton("Submit for Approval", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {

            Button submitButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

            submitButton.setOnClickListener(v -> {

                String diet = dietInput.getText().toString().trim();
                String care = careInput.getText().toString().trim();
                String treatment = treatmentInput.getText().toString().trim();

                if (diet.isEmpty() && care.isEmpty() && treatment.isEmpty()) {
                    Toast.makeText(
                            context,
                            "Please fill in at least one field first.",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                submitVetRecommendation(breedLabel, diseaseLabel, diet, care, treatment);

                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private EditText addLabeledInput(
            Context context,
            LinearLayout container,
            String labelText,
            String hint,
            int topMarginDp
    ) {

        TextView label = new TextView(context);
        label.setText(labelText);
        label.setTextColor(0xFF3A3226);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);

        if (topMarginDp > 0) {
            LinearLayout.LayoutParams labelParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
            labelParams.topMargin = dpToPx(topMarginDp);
            label.setLayoutParams(labelParams);
        }

        EditText input = new EditText(context);
        input.setHint(hint);
        input.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        input.setMinLines(2);

        container.addView(label);
        container.addView(input);

        return input;
    }

    private void submitVetRecommendation(
            String breedLabel,
            String diseaseLabel,
            String diet,
            String care,
            String treatment
    ) {

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(
                    requireContext(),
                    "You must be signed in to submit a recommendation.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        String diseaseKey =
                (diseaseLabel != null && !diseaseLabel.equals(NO_ILLNESS_LABEL))
                        ? diseaseLabel
                        : null;

        Map<String, Object> data = new HashMap<>();
        data.put("breed", breedLabel);
        data.put("diseaseResult", diseaseKey);

        if (!diet.isEmpty()) {
            data.put("dietRecommendation", diet);
        }
        if (!care.isEmpty()) {
            data.put("careRecommendation", care);
        }
        if (!treatment.isEmpty()) {
            data.put("treatmentRecommendation", treatment);
        }

        data.put("vetUid", uid);
        data.put("status", STATUS_PENDING);
        data.put("approved", false);
        data.put("approvedBy", null);
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection(COLLECTION_VET_REVIEWS)
                .add(data)
                .addOnSuccessListener(ref -> {
                    if (isAdded()) {
                        Toast.makeText(
                                requireContext(),
                                "Submitted — pending admin approval.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Camera", "Failed to submit vet recommendation", e);
                    if (isAdded()) {
                        Toast.makeText(
                                requireContext(),
                                "Failed to submit: " + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private void fetchApprovedAdditionsThenShow(
            String breedLabel,
            String diseaseLabel,
            String confidenceText,
            String careInfo,
            String diseaseInfo
    ) {

        String diseaseKey =
                (diseaseLabel != null && !diseaseLabel.equals(NO_ILLNESS_LABEL))
                        ? diseaseLabel
                        : null;

        Task<QuerySnapshot> breedTask =
                db.collection(COLLECTION_VET_REVIEWS)
                        .whereEqualTo("approved", true)
                        .whereEqualTo("breed", breedLabel)
                        .get();

        List<Task<?>> tasks = new ArrayList<>();
        tasks.add(breedTask);

        Task<QuerySnapshot> diseaseTask = null;

        if (diseaseKey != null) {
            diseaseTask = db.collection(COLLECTION_VET_REVIEWS)
                    .whereEqualTo("approved", true)
                    .whereEqualTo("diseaseResult", diseaseKey)
                    .get();
            tasks.add(diseaseTask);
        }

        final Task<QuerySnapshot> finalDiseaseTask = diseaseTask;

        Tasks.whenAllComplete(tasks)
                .addOnSuccessListener(results -> {

                    if (!isAdded()) {
                        return;
                    }

                    StringBuilder dietAdd = new StringBuilder();
                    StringBuilder careAdd = new StringBuilder();
                    StringBuilder treatmentAdd = new StringBuilder();

                    if (breedTask.isSuccessful() && breedTask.getResult() != null) {
                        for (QueryDocumentSnapshot doc : breedTask.getResult()) {
                            appendIfPresent(dietAdd, doc.getString("dietRecommendation"));
                            appendIfPresent(careAdd, doc.getString("careRecommendation"));
                        }
                    }

                    if (finalDiseaseTask != null
                            && finalDiseaseTask.isSuccessful()
                            && finalDiseaseTask.getResult() != null) {
                        for (QueryDocumentSnapshot doc : finalDiseaseTask.getResult()) {
                            appendIfPresent(treatmentAdd, doc.getString("treatmentRecommendation"));
                        }
                    }

                    String mergedCareInfo = careInfo;

                    if (dietAdd.length() > 0) {
                        mergedCareInfo += "\n\nVet-Approved Diet Notes:\n" + dietAdd;
                    }
                    if (careAdd.length() > 0) {
                        mergedCareInfo += "\n\nVet-Approved Care Notes:\n" + careAdd;
                    }

                    String mergedDiseaseInfo = diseaseInfo;

                    if (treatmentAdd.length() > 0) {
                        mergedDiseaseInfo += "\n\nVet-Approved Treatment Notes:\n" + treatmentAdd;
                    }

                    showResultDialog(breedLabel, diseaseLabel, confidenceText, mergedCareInfo, mergedDiseaseInfo);
                })
                .addOnFailureListener(e -> {
                    Log.e("Camera", "Failed to fetch approved vet additions", e);
                    if (isAdded()) {
                        showResultDialog(breedLabel, diseaseLabel, confidenceText, careInfo, diseaseInfo);
                    }
                });
    }

    private void appendIfPresent(StringBuilder sb, String text) {

        if (text != null && !text.trim().isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("• ").append(text.trim());
        }
    }

    private void showLoading() {

        dismissLoading();

        loadingDialog =
                new AlertDialog.Builder(requireContext())
                        .setTitle("Analyzing")
                        .setMessage("Identifying breed and checking health...")
                        .setCancelable(false)
                        .show();
    }

    private void dismissLoading() {

        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }

        loadingDialog = null;
    }

    private void showResultDialog(
            String breedLabel,
            String diseaseLabel,
            String confidenceText,
            String careInfo,
            String diseaseInfo
    ) {

        if (!isAdded()) {
            return;
        }

        SpannableStringBuilder message = new SpannableStringBuilder();

        message.append("Breed: ").append(breedLabel).append("\n");
        message.append("Health: ").append(diseaseLabel).append("\n");

        if (!confidenceText.isEmpty()) {
            message.append("\n").append(confidenceText).append("\n");
        }

        message.append("\n").append(careInfo);

        if (!diseaseInfo.isEmpty()) {
            message.append("\n\n").append(diseaseInfo);
        }

        highlightVetRequired(message);

        checkIfCurrentUserIsVet(isVet -> {

            if (!isAdded()) {
                return;
            }

            AlertDialog.Builder builder =
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Scan Results")
                            .setMessage(message)
                            .setPositiveButton("OK", null);

            if (isVet) {
                builder.setNeutralButton(
                        "Vet: Add Recommendation",
                        (d, which) ->
                                showAddRecommendationDialog(breedLabel, diseaseLabel)
                );
            }

            AlertDialog dialog = builder.create();
            dialog.show();
            styleDialogMessage(dialog);
        });
    }

    private String getCareInfo(String breed) {

        switch (breed.toLowerCase()) {

            case "new zealand":

                return "Breed: New Zealand (9-12 lb, lifespan 5-8 yrs)\n\n"
                        + "Diet:\n"
                        + "- Hay/Grass: Unlimited Damong Carabao, Damong Napier, or Bermuda grass (at least 80% of daily diet)\n"
                        + "- Leafy Greens: ~1 cup per 2-3 lb of body weight/day (rotate Talbos ng Kamote, Pechay, Wansoy, Mustasa, or Dahon ng Saging)\n"
                        + "- Pellets (Optional): ~1/4 cup per 4-5 lb of body weight/day (if using commercial feeds)\n"
                        + "- Treats: Only occasionally, 1-2 tbsp of Saging or Papaya per 5 lb, 1-2x/week\n\n"
                        + "Food Preparation Instructions:\n"
                        + "1. Hugas (Washing): Wash harvested grasses or market greens thoroughly under clean running water to remove pesticides and soil parasites.\n"
                        + "2. Pagpapalanta (Wilting): Spread greens on a clean tray in a shaded, well-ventilated area for 2-4 hours. Do not dry directly under the sun. Wilting reduces excess moisture to prevent bloat and fatal diarrhea.\n"
                        + "3. Pagpuputol (Portioning): Serve in large, fresh, wilted bundles.\n\n"
                        + "Care:\n"
                        + "- Spacious cage/enclosure with daily exercise time\n"
                        + "- Fresh, cool water available at all times\n"
                        + "- Provide solid floor mats or wooden rest boards to prevent sore hocks on wire floors due to heavy weight\n\n"
                        + "Notes:\n"
                        + "- Larger breed — monitor weight to avoid obesity and limit high-protein greens like Talbos ng Kamote to 1 handful per day\n\n"
                        + "Video Guide:\n"
                        + "https://youtu.be/FbibvbYxIzw?si=GPenxib7t4yb1Ko2";

            case "lionhead":

                return "Breed: Lionhead (2.5-3.75 lb, lifespan 7-9 yrs)\n\n"
                        + "Diet:\n"
                        + "- Hay/Grass: Unlimited Damong Carabao or Damong Napier (rough fiber helps pass ingested mane fur)\n"
                        + "- Greens: ~1 cup per 2 lb of body weight/day (Wansoy, Minta, Pechay, Dahon ng Bayabas — avoid Kangkong in large amounts due to high moisture and calcium content)\n"
                        + "- Pellets (Optional): About 1/8 cup/day for an adult\n\n"
                        + "Food Preparation Instructions:\n"
                        + "1. Hugas (Washing): Thoroughly rinse all harvested Damong Carabao or market greens under running water.\n"
                        + "2. Pagpapalanta (Wilting): Air-dry/wilt in the shade for 2-4 hours until leaves are soft and not dripping wet.\n"
                        + "3. Portioning & Health Fiber: Chop grass into manageable lengths. Include 1-2 Dahon ng Bayabas weekly as a natural astringent to keep digestion firm and prevent hairballs.\n\n"
                        + "Care:\n"
                        + "- Regular grooming required for the long mane fur to prevent wool block and severe matting in Philippine humidity\n"
                        + "- Keep in well-ventilated space to prevent heat stress\n\n"
                        + "Notes:\n"
                        + "- High risk of hair ingestion/GI blockage from grooming\n\n"
                        + "Video Guide:\n"
                        + "https://youtu.be/57y91glfDGc?si=TGHYF2oIh40aiCxw";

            case "holland":
            case "holland lop":

                return "Breed: Holland Lop (up to 4 lb, lifespan 7-10 yrs)\n\n"
                        + "Diet:\n"
                        + "- Hay/Grass: Unlimited coarse Damong Carabao or Bermuda grass (majority of diet)\n"
                        + "- Fresh Greens: Small amounts of Wansoy, Pechay, or Talbos ng Kamote daily\n"
                        + "- Pellets (Optional): ~1/8 to 1/4 cup per day\n\n"
                        + "Food Preparation Instructions:\n"
                        + "1. Hugas (Washing): Clean all local forage and leaves with running water.\n"
                        + "2. Pagpapalanta (Wilting): Spread out for 2-4 hours indoors or in a shaded spot to let moisture evaporate before feeding.\n"
                        + "3. Pagpuputol (Portioning): Cut long grass stalks into smaller 4-6 inch pieces for easier chewing and to accommodate their short jaws.\n\n"
                        + "Care:\n"
                        + "- Check and clean ears regularly (floppy ears trap moisture in local humidity, making them prone to ear mites or wax buildup)\n"
                        + "- Provide cooling tiles or frozen water bottles wrapped in cloth on hot Philippine summer days\n\n"
                        + "Notes:\n"
                        + "- Small, flat-faced breed — avoid overfeeding treats like Saging or Papaya (limit to paper-thin slices) to prevent obesity and dental issues\n\n"
                        + "Video Guide:\n"
                        + "https://youtu.be/HfLwpvfjuuI?si=dXG-fEA-BqKQ5mIJ";

            case "california":

                return "Breed: Californian (2.5-4 kg)\n\n"
                        + "Diet:\n"
                        + "- Hay/Grass: 80-85% of daily diet consisting of Damong Carabao, Napier, or Bermuda grass\n"
                        + "- Fresh Greens: Pechay, Mustasa, Wansoy, or small amounts of Kangkong (max 1-2 stalks as hydration treat)\n"
                        + "- Pellets (Optional): ~1/4 cup per 5 lb of body weight/day for adults\n\n"
                        + "Food Preparation Instructions:\n"
                        + "1. Hugas (Washing): Thoroughly wash all foraged grasses and greens to eliminate wild animal waste, dirt, and pesticides.\n"
                        + "2. Pagpapalanta (Wilting): Leave greens in a shaded, airy area for 2 to 4 hours to wilt. Never serve wet grass directly.\n"
                        + "3. Serving: Provide wilted grasses freely alongside clean, room-temperature water.\n\n"
                        + "Care:\n"
                        + "- Keep in a cool, well-ventilated environment — dense coat is prone to overheating in tropical weather\n"
                        + "- Avoid bare wire-bottom cages; use rubber mats or wood boards\n\n"
                        + "Notes:\n"
                        + "- Check skin/coat regularly; can be anxious or easily startled\n\n"
                        + "Video Guide:\n"
                        + "https://youtu.be/3fJJRRsDwUo?si=hKjR826zbbiKWGFk";

            default:

                return "General Rabbit Care\n\n"
                        + "Diet:\n"
                        + "- Local Grasses (Damong Carabao, Napier, Bermuda grass) freely available at all times\n"
                        + "- Leafy Greens (Pechay, Talbos ng Kamote, Wansoy): Small daily portions\n"
                        + "- Pellets (Optional): ~1/4 cup per 5 lb of body weight/day, controlled portions to prevent obesity\n"
                        + "- Fresh, clean water always available — an open ceramic bowl is often preferred over a sipper bottle\n\n"
                        + "Food Preparation Instructions:\n"
                        + "1. Hugas (Washing): Always rinse freshly harvested grasses and market produce thoroughly.\n"
                        + "2. Pagpapalanta (Wilting): Wilt all fresh forage for 2-4 hours in a shaded, airy area before feeding to prevent severe bloat or diarrhea.\n\n"
                        + "Foods & Plants to Avoid:\n"
                        + "1. Iceberg Lettuce (contains laudanum, causes severe diarrhea)\n"
                        + "2. Excessive Kangkong (high oxalic acid and water content)\n"
                        + "3. Toxic Plants: Aloe, azalea, calla lily, lily of the valley, philodendron, corn plant, carnation\n"
                        + "4. Toxic Foods: Apple seeds, raw beans, sweet potato tubers, rhubarb leaves, potato shoots, chocolate, garlic, onions\n\n"
                        + "Notes:\n"
                        + "- Loss of appetite can be a sign of heatstroke, GI stasis, or illness — seek immediate veterinary care if a rabbit stops eating";
        }
    }

    private String getDiseaseInfo(String disease) {

        switch (disease.toLowerCase()) {

            case "myxomatosis":

                return " Myxomatosis\n️Vet required\n If unsure of this result please Schedule a visit with your Veterinarian.\n\n"
                        + "Additional Info: \n"
                        + "https://youtu.be/RUezkUetEEA?si=gcsRr-tNhlvpU7sg";

            case "mites":

                return " Mites\n️ If unsure of this result please Schedule a visit with your Veterinarian.\n\n"
                        + "Additional Info: \n"
                        + "https://youtu.be/iQyPn2VL70s?si=EAnvWMapiA8YgJRW";

            case "malocclusion":

                return " Malocclusion\n️ If unsure of this result please Schedule a visit with your Veterinarian.\n\n"
                        + "Additional Info: \n"
                        + "https://youtu.be/2F328Q38uJc?si=PQNXStwEciaECc8h";

            case "pasteurellosis":

                return " Pasteurellosis\n If unsure of this result please Schedule a visit with your Veterinarian.\n\n"
                        + "Additional Info: \n"
                        + "https://youtu.be/Uxj0VIqC83Q?si=J5OugchFPDFVkepC";
        }

        return " " + disease + "\n If unsure of this result please Schedule a visit with your Veterinarian.";
    }
}