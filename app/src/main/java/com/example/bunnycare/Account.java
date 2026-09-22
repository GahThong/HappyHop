package com.example.bunnycare;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Account extends Fragment {

    ImageView profileImage;
    TextView profileName, profileEmail, notificationBadge;
    View btnEditProfile, rowNotifications, rowPrivacy, rowLogout;
    Button btnVetPictureContribution;
    ActivityResultLauncher<Intent> vetContributionImagePickerLauncher;
    Uri vetContributionImageUri;
    AlertDialog vetContributionDialog;
    ImageView vetContributionPreview;
    TextView vetContributionBreedButton;
    TextView vetContributionDiseaseButton;
    RecyclerView rabbitsRecyclerView;

    FirebaseAuth mAuth;
    FirebaseFirestore db;
    FirebaseUser user;

    List<Rabbit> rabbitList;
    RabbitAdapter rabbitsAdapter;

    ListenerRegistration notificationsListener;

    Uri imageUri;
    ActivityResultLauncher<Intent> imagePickerLauncher;

    Uri verificationImageUri;
    ActivityResultLauncher<Intent> verificationImagePickerLauncher;

    String verificationType;

    boolean isVerifiedVet = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_account, container, false);

        profileImage = view.findViewById(R.id.profileImage);
        profileName = view.findViewById(R.id.profileName);
        profileEmail = view.findViewById(R.id.profileEmail);
        btnEditProfile = view.findViewById(R.id.btnEditProfile);
        btnVetPictureContribution = view.findViewById(R.id.btnVetPictureContribution);
        rabbitsRecyclerView = view.findViewById(R.id.rabbitsRecyclerView);
        rowNotifications = view.findViewById(R.id.rowNotifications);
        notificationBadge = view.findViewById(R.id.notificationBadge);
        rowPrivacy = view.findViewById(R.id.rowPrivacy);
        rowLogout = view.findViewById(R.id.rowLogout);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        user = mAuth.getCurrentUser();

        if (user == null) return view;

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {

                        imageUri = result.getData().getData();

                        Glide.with(requireContext())
                                .load(imageUri)
                                .circleCrop()
                                .into(profileImage);

                        uploadProfileImage();
                    }
                });

        verificationImagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {

                        verificationImageUri = result.getData().getData();
                        uploadVerificationPhoto();
                    }
                });

        vetContributionImagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {

                        vetContributionImageUri = result.getData().getData();

                        if (vetContributionPreview != null) {
                            Glide.with(requireContext())
                                    .load(vetContributionImageUri)
                                    .into(vetContributionPreview);

                            vetContributionPreview.setVisibility(View.VISIBLE);
                        }

                        updateVetContributionSubmitButton();
                    }
                });

        btnVetPictureContribution.setVisibility(View.GONE);
        btnVetPictureContribution.setOnClickListener(v -> showVetPictureContributionDialog());

        profileImage.setOnClickListener(v -> openGallery());

        rabbitList = new ArrayList<>();

        rabbitsAdapter = new RabbitAdapter(getContext(), rabbitList, new RabbitAdapter.OnRabbitItemListener() {

            @Override
            public void onRabbitTap(Rabbit rabbit, View anchorView) {

                Intent intent = new Intent(getActivity(), AddRabbitActivity.class);

                intent.putExtra("editMode", true);
                intent.putExtra("rabbitId", rabbit.getId());

                startActivity(intent);
            }

            @Override
            public void onRabbitLongPress(Rabbit rabbit) {

            }
        });

        rabbitsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        rabbitsRecyclerView.setAdapter(rabbitsAdapter);

        loadUser();
        loadRabbits();
        listenForNotificationCount();

        btnEditProfile.setOnClickListener(v -> openEditProfile());

        rowNotifications.setOnClickListener(v -> showNotifications());

        rowPrivacy.setOnClickListener(v -> openAccountSettings());

        rowLogout.setOnClickListener(v -> logOut());

        return view;
    }

    private void openGallery() {

        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");

        imagePickerLauncher.launch(intent);
    }

    private void openVerificationGallery() {

        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");

        verificationImagePickerLauncher.launch(intent);
    }

    private void uploadProfileImage() {

        if (imageUri == null) return;

        if (isAdded()) {
            Toast.makeText(getContext(), "Uploading photo...", Toast.LENGTH_SHORT).show();
        }

        MediaManager.get()
                .upload(imageUri)
                .unsigned("ml_default")
                .callback(new UploadCallback() {

                    @Override
                    public void onStart(String requestId) {
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {

                        String secureUrl = (String) resultData.get("secure_url");

                        Map<String, Object> map = new HashMap<>();
                        map.put("imageUrl", secureUrl);

                        db.collection("users")
                                .document(user.getUid())
                                .update(map)
                                .addOnSuccessListener(unused -> {

                                    if (!isAdded() || getContext() == null) return;

                                    Toast.makeText(getContext(), "Profile photo updated", Toast.LENGTH_SHORT).show();
                                });
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {

                        if (!isAdded() || getContext() == null) return;

                        Toast.makeText(getContext(), "Upload failed: " + error.getDescription(), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                    }

                }).dispatch();
    }

    private void uploadVerificationPhoto() {

        if (verificationImageUri == null || user == null || verificationType == null) return;

        if (isAdded()) {
            String message = verificationType.equals("veterinarian")
                    ? "Uploading veterinarian verification photo..."
                    : "Uploading feed supplier verification photo...";

            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }

        MediaManager.get()
                .upload(verificationImageUri)
                .unsigned("ml_default")
                .callback(new UploadCallback() {

                    @Override
                    public void onStart(String requestId) {
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {

                        String secureUrl = (String) resultData.get("secure_url");

                        Map<String, Object> request = new HashMap<>();

                        request.put("userId", user.getUid());
                        request.put("userEmail", user.getEmail());
                        request.put("verificationType", verificationType);
                        request.put("licenseImageUrl", secureUrl);
                        request.put("imageUrl", secureUrl);
                        request.put("status", "pending");
                        request.put("submittedAt", FieldValue.serverTimestamp());

                        if ("veterinarian".equals(verificationType)) {
                            request.put("documentType", "Veterinary License");
                        } else if ("feed_supplier".equals(verificationType)) {
                            request.put("documentType", "Feed Supplier Verification");
                        }

                        db.collection("verificationRequests")
                                .add(request)
                                .addOnSuccessListener(docRef -> {

                                    if (!isAdded() || getContext() == null) return;

                                    String message;

                                    if ("veterinarian".equals(verificationType)) {
                                        message = "Veterinarian verification submitted. We'll notify you once an admin reviews it.";
                                    } else {
                                        message = "Feed supplier verification submitted. We'll notify you once an admin reviews it.";
                                    }

                                    verificationImageUri = null;
                                    verificationType = null;

                                    Toast.makeText(
                                            getContext(),
                                            message,
                                            Toast.LENGTH_LONG
                                    ).show();
                                })
                                .addOnFailureListener(e -> {

                                    if (!isAdded() || getContext() == null) return;

                                    Toast.makeText(
                                            getContext(),
                                            "Couldn't submit for review: " + e.getMessage(),
                                            Toast.LENGTH_LONG
                                    ).show();
                                });
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {

                        if (!isAdded() || getContext() == null) return;

                        Toast.makeText(
                                getContext(),
                                "Upload failed: " + error.getDescription(),
                                Toast.LENGTH_LONG
                        ).show();
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                    }

                }).dispatch();
    }

    private void loadUser() {

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {

                    if (!isAdded() || getContext() == null) return;

                    if (!doc.exists()) return;

                    profileName.setText(doc.getString("username"));
                    profileEmail.setText(doc.getString("email"));

                    Boolean verified = doc.getBoolean("verified");
                    isVerifiedVet = verified != null && verified;

                    updateVetContributionButton();

                    applyVerifiedBadge();

                    String imageUrl = doc.getString("imageUrl");

                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        Glide.with(requireContext())
                                .load(imageUrl)
                                .circleCrop()
                                .into(profileImage);
                    }
                });
    }

    private void applyVerifiedBadge() {

        int badgePaddingPx = (int) (6 * getResources().getDisplayMetrics().density);
        int badgeSizePx = (int) (14 * getResources().getDisplayMetrics().density);

        profileName.setCompoundDrawablePadding(badgePaddingPx);

        if (isVerifiedVet) {

            Drawable verifiedBadge = null;

            try {
                verifiedBadge = ContextCompat.getDrawable(requireContext(), R.drawable.verified);
            } catch (Exception ignored) {
            }

            if (verifiedBadge != null) {
                verifiedBadge.setBounds(0, 0, badgeSizePx, badgeSizePx);
            }

            profileName.setCompoundDrawables(null, null, verifiedBadge, null);

        } else {
            profileName.setCompoundDrawables(null, null, null, null);
        }
    }

    private void updateVetContributionButton() {

        if (btnVetPictureContribution == null) return;

        btnVetPictureContribution.setVisibility(isVerifiedVet ? View.VISIBLE : View.GONE);
    }

    private void showVetPictureContributionDialog() {

        if (!isVerifiedVet) {
            Toast.makeText(
                    getContext(),
                    "Only verified vets can submit pictures.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        vetContributionImageUri = null;

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 8, 32, 8);

        TextView breedButton = new TextView(requireContext());
        vetContributionBreedButton = breedButton;

        breedButton.setText("Select Breed");
        breedButton.setTextColor(Color.BLACK);
        breedButton.setTextSize(15);
        breedButton.setGravity(android.view.Gravity.CENTER_VERTICAL);
        breedButton.setPadding(24, 18, 24, 18);

        GradientDrawable breedBg = new GradientDrawable();
        breedBg.setColor(Color.WHITE);
        breedBg.setCornerRadius(18);
        breedBg.setStroke(2, Color.LTGRAY);

        breedButton.setBackground(breedBg);

        TextView diseaseButton = new TextView(requireContext());
        vetContributionDiseaseButton = diseaseButton;

        diseaseButton.setText("Select Disease");
        diseaseButton.setTextColor(Color.BLACK);
        diseaseButton.setTextSize(15);
        diseaseButton.setGravity(android.view.Gravity.CENTER_VERTICAL);
        diseaseButton.setPadding(24, 18, 24, 18);

        GradientDrawable diseaseBg = new GradientDrawable();
        diseaseBg.setColor(Color.WHITE);
        diseaseBg.setCornerRadius(18);
        diseaseBg.setStroke(2, Color.LTGRAY);

        diseaseButton.setBackground(diseaseBg);

        ImageView preview = new ImageView(requireContext());
        vetContributionPreview = preview;

        preview.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                220
        ));

        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setVisibility(View.GONE);

        Button choosePicture = new Button(requireContext());
        choosePicture.setText("Choose Picture");
        choosePicture.setAllCaps(false);

        layout.addView(breedButton);
        layout.addView(diseaseButton);

        LinearLayout.LayoutParams previewParams =
                (LinearLayout.LayoutParams) preview.getLayoutParams();

        previewParams.topMargin = 18;
        previewParams.bottomMargin = 8;

        layout.addView(preview, previewParams);
        layout.addView(choosePicture);

        final String[] selectedBreed = {null};
        final String[] selectedDisease = {null};

        breedButton.setOnClickListener(v -> {

            String[] breeds = {
                    "Holland",
                    "California",
                    "New Zealand",
                    "Lionhead"
            };

            new AlertDialog.Builder(requireContext())
                    .setTitle("Select Breed")
                    .setItems(breeds, (dialog, which) -> {

                        selectedBreed[0] = breeds[which];

                        breedButton.setText(selectedBreed[0]);

                        updateVetContributionSubmitButton(
                                selectedBreed[0],
                                selectedDisease[0]
                        );
                    })
                    .show();
        });

        diseaseButton.setOnClickListener(v -> {

            String[] diseases = {
                    "Myxomatosis",
                    "Mites",
                    "Malocclusion",
                    "Pasteurellosis"
            };

            new AlertDialog.Builder(requireContext())
                    .setTitle("Select Disease")
                    .setItems(diseases, (dialog, which) -> {

                        selectedDisease[0] = diseases[which];

                        diseaseButton.setText(selectedDisease[0]);

                        updateVetContributionSubmitButton(
                                selectedBreed[0],
                                selectedDisease[0]
                        );
                    })
                    .show();
        });

        choosePicture.setOnClickListener(v -> {

            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");

            vetContributionImagePickerLauncher.launch(intent);
        });

        vetContributionDialog = new AlertDialog.Builder(requireContext())
                .setTitle("Wanna Help Us?")
                .setMessage("Select at least a breed or disease, then upload a clear rabbit picture.")
                .setView(layout)
                .setPositiveButton("Submit Picture", null)
                .setNegativeButton("Cancel", null)
                .create();

        vetContributionDialog.setOnShowListener(dialog -> {

            Button submitButton =
                    vetContributionDialog.getButton(AlertDialog.BUTTON_POSITIVE);

            submitButton.setEnabled(false);

            submitButton.setOnClickListener(v -> {

                boolean hasBreed = selectedBreed[0] != null;
                boolean hasDisease = selectedDisease[0] != null;
                boolean hasPhoto = vetContributionImageUri != null;

                if ((!hasBreed && !hasDisease) || !hasPhoto) {

                    Toast.makeText(
                            getContext(),
                            "Please select at least a breed or disease and choose a picture.",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                uploadVetContributionImage(
                        selectedBreed[0],
                        selectedDisease[0],
                        vetContributionImageUri
                );
            });
        });

        vetContributionDialog.show();
    }

    private void updateVetContributionSubmitButton() {

        String breed = vetContributionBreedButton != null
                ? vetContributionBreedButton.getText().toString()
                : null;

        String disease = vetContributionDiseaseButton != null
                ? vetContributionDiseaseButton.getText().toString()
                : null;

        if ("Select Breed".equals(breed)) {
            breed = null;
        }

        if ("Select Disease".equals(disease)) {
            disease = null;
        }

        updateVetContributionSubmitButton(breed, disease);
    }

    private void updateVetContributionSubmitButton(
            String breed,
            String disease
    ) {

        if (vetContributionDialog == null ||
                !vetContributionDialog.isShowing()) {
            return;
        }

        Button submitButton =
                vetContributionDialog.getButton(AlertDialog.BUTTON_POSITIVE);

        boolean hasBreed =
                breed != null && !breed.trim().isEmpty();

        boolean hasDisease =
                disease != null && !disease.trim().isEmpty();

        boolean hasPhoto =
                vetContributionImageUri != null;

        submitButton.setEnabled(
                (hasBreed || hasDisease) && hasPhoto
        );
    }

    private void uploadVetContributionImage(
            String breed,
            String disease,
            Uri imageUri
    ) {

        if (imageUri == null ||
                user == null ||
                !isVerifiedVet) {
            return;
        }

        Button submitButton = vetContributionDialog != null
                ? vetContributionDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                : null;

        if (submitButton != null) {
            submitButton.setEnabled(false);
        }

        Toast.makeText(
                getContext(),
                "Uploading picture...",
                Toast.LENGTH_SHORT
        ).show();

        MediaManager.get()
                .upload(imageUri)
                .unsigned("ml_default")
                .callback(new UploadCallback() {

                    @Override
                    public void onStart(String requestId) {
                    }

                    @Override
                    public void onProgress(
                            String requestId,
                            long bytes,
                            long totalBytes
                    ) {
                    }

                    @Override
                    public void onSuccess(
                            String requestId,
                            Map resultData
                    ) {

                        String secureUrl =
                                (String) resultData.get("secure_url");

                        Map<String, Object> data =
                                new HashMap<>();

                        data.put(
                                "breed",
                                breed != null
                                        ? breed
                                        : "Not specified"
                        );

                        data.put(
                                "disease",
                                disease != null
                                        ? disease
                                        : "Not specified"
                        );

                        data.put("imageUrl", secureUrl);
                        data.put("vetUid", user.getUid());
                        data.put("vetEmail", user.getEmail());
                        data.put(
                                "createdAt",
                                FieldValue.serverTimestamp()
                        );

                        db.collection("vetImageSubmissions")
                                .add(data)
                                .addOnSuccessListener(
                                        documentReference -> {

                                            if (vetContributionDialog != null &&
                                                    vetContributionDialog.isShowing()) {

                                                vetContributionDialog.dismiss();
                                            }

                                            if (isAdded() &&
                                                    getContext() != null) {

                                                Toast.makeText(
                                                        getContext(),
                                                        "Picture submitted successfully.",
                                                        Toast.LENGTH_LONG
                                                ).show();
                                            }
                                        })
                                .addOnFailureListener(e -> {

                                    if (submitButton != null) {
                                        submitButton.setEnabled(true);
                                    }

                                    if (isAdded() &&
                                            getContext() != null) {

                                        Toast.makeText(
                                                getContext(),
                                                "Couldn't save picture: " +
                                                        e.getMessage(),
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }
                                });
                    }

                    @Override
                    public void onError(
                            String requestId,
                            ErrorInfo error
                    ) {

                        if (submitButton != null) {
                            submitButton.setEnabled(true);
                        }

                        if (isAdded() &&
                                getContext() != null) {

                            Toast.makeText(
                                    getContext(),
                                    "Upload failed: " +
                                            error.getDescription(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }

                    @Override
                    public void onReschedule(
                            String requestId,
                            ErrorInfo error
                    ) {
                    }

                })
                .dispatch();
    }

    private void loadRabbits() {

        db.collection("rabbits")
                .whereEqualTo("ownerId", user.getUid())
                .addSnapshotListener((value, error) -> {

                    if (error != null || value == null) return;

                    rabbitList.clear();

                    for (DocumentSnapshot doc :
                            value.getDocuments()) {

                        Rabbit rabbit =
                                doc.toObject(Rabbit.class);

                        if (rabbit != null) {

                            rabbit.setId(doc.getId());
                            rabbitList.add(rabbit);
                        }
                    }

                    rabbitsAdapter.notifyDataSetChanged();
                });
    }

    private void listenForNotificationCount() {

        notificationsListener =
                db.collection("notifications")
                        .whereEqualTo(
                                "recipientId",
                                user.getUid()
                        )
                        .whereEqualTo(
                                "read",
                                false
                        )
                        .addSnapshotListener(
                                (value, error) -> {

                                    if (error != null ||
                                            value == null ||
                                            getContext() == null) {
                                        return;
                                    }

                                    int unread = value.size();

                                    if (unread > 0) {

                                        notificationBadge
                                                .setVisibility(
                                                        View.VISIBLE
                                                );

                                        notificationBadge
                                                .setText(
                                                        String.valueOf(
                                                                unread
                                                        )
                                                );

                                    } else {

                                        notificationBadge
                                                .setVisibility(
                                                        View.GONE
                                                );
                                    }
                                });
    }

    private void showNotifications() {

        BottomSheetDialog dialog =
                new BottomSheetDialog(requireContext());

        View sheet =
                LayoutInflater.from(getContext())
                        .inflate(
                                R.layout.fragment_notification,
                                null
                        );

        dialog.setContentView(sheet);
        dialog.show();

        RecyclerView notificationsRecyclerView =
                sheet.findViewById(
                        R.id.notificationsRecyclerView
                );

        View emptyNotifications =
                sheet.findViewById(
                        R.id.emptyNotifications
                );

        List<AppNotification> notificationList =
                new ArrayList<>();

        NotificationAdapter adapter =
                new NotificationAdapter(
                        getContext(),
                        notificationList
                );

        notificationsRecyclerView.setLayoutManager(
                new LinearLayoutManager(getContext())
        );

        notificationsRecyclerView.setAdapter(adapter);

        db.collection("notifications")
                .whereEqualTo(
                        "recipientId",
                        user.getUid()
                )
                .orderBy(
                        "timestamp",
                        Query.Direction.DESCENDING
                )
                .addSnapshotListener(
                        (value, error) -> {

                            if (error != null ||
                                    value == null) {
                                return;
                            }

                            notificationList.clear();

                            for (DocumentSnapshot doc :
                                    value.getDocuments()) {

                                AppNotification notification =
                                        doc.toObject(
                                                AppNotification.class
                                        );

                                if (notification != null) {

                                    notification.setId(
                                            doc.getId()
                                    );

                                    notificationList.add(
                                            notification
                                    );

                                    if (!notification.isRead()) {
                                        doc.getReference()
                                                .update(
                                                        "read",
                                                        true
                                                );
                                    }
                                }
                            }

                            adapter.notifyDataSetChanged();

                            emptyNotifications.setVisibility(
                                    notificationList.isEmpty()
                                            ? View.VISIBLE
                                            : View.GONE
                            );
                        });
    }

    private void openAccountSettings() {

        List<String> optionList =
                new ArrayList<>();

        optionList.add("Change Email");
        optionList.add("Change Password");

        if (!isVerifiedVet) {
            optionList.add("Verify Account");
        }

        String[] options =
                optionList.toArray(
                        new String[0]
                );

        new AlertDialog.Builder(getContext())
                .setTitle("Account Settings")
                .setItems(
                        options,
                        (dialog, which) -> {

                            String selected =
                                    options[which];

                            if (selected.equals(
                                    "Change Email")) {

                                promptReauth(
                                        this::promptChangeEmail
                                );

                            } else if (selected.equals(
                                    "Change Password")) {

                                promptReauth(
                                        this::promptChangePassword
                                );

                            } else if (selected.equals(
                                    "Verify Account")) {

                                promptVerifyAccount();
                            }
                        })
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    private void promptReauth(Runnable onSuccess) {

        if (user.getEmail() == null) {

            Toast.makeText(
                    getContext(),
                    "No email on file for this account.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        EditText passwordInput =
                new EditText(getContext());

        passwordInput.setHint("Current password");

        passwordInput.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT |
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        new AlertDialog.Builder(getContext())
                .setTitle("Confirm It's You")
                .setMessage(
                        "Please re-enter your password to continue."
                )
                .setView(passwordInput)
                .setPositiveButton(
                        "Confirm",
                        (dialog, which) -> {

                            String password =
                                    passwordInput
                                            .getText()
                                            .toString();

                            if (password.isEmpty()) return;

                            AuthCredential credential =
                                    EmailAuthProvider.getCredential(
                                            user.getEmail(),
                                            password
                                    );

                            user.reauthenticate(credential)
                                    .addOnSuccessListener(
                                            unused ->
                                                    onSuccess.run()
                                    )
                                    .addOnFailureListener(
                                            e -> {

                                                if (getContext() == null)
                                                    return;

                                                Toast.makeText(
                                                        getContext(),
                                                        "Re-authentication failed: " +
                                                                e.getMessage(),
                                                        Toast.LENGTH_LONG
                                                ).show();
                                            });
                        })
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    private void promptChangeEmail() {

        EditText input =
                new EditText(getContext());

        input.setHint("New email");

        input.setInputType(
                android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );

        new AlertDialog.Builder(getContext())
                .setTitle("Change Email")
                .setView(input)
                .setPositiveButton(
                        "Save",
                        (dialog, which) -> {

                            String newEmail =
                                    input.getText()
                                            .toString()
                                            .trim();

                            if (newEmail.isEmpty()) return;

                            user.updateEmail(newEmail)
                                    .addOnSuccessListener(
                                            unused -> {

                                                Map<String, Object> map =
                                                        new HashMap<>();

                                                map.put(
                                                        "email",
                                                        newEmail
                                                );

                                                db.collection("users")
                                                        .document(
                                                                user.getUid()
                                                        )
                                                        .update(map)
                                                        .addOnSuccessListener(
                                                                unused2 -> {

                                                                    if (getContext() == null)
                                                                        return;

                                                                    profileEmail
                                                                            .setText(
                                                                                    newEmail
                                                                            );

                                                                    Toast.makeText(
                                                                            getContext(),
                                                                            "Email updated",
                                                                            Toast.LENGTH_SHORT
                                                                    ).show();
                                                                });
                                            })
                                    .addOnFailureListener(
                                            e -> {

                                                if (getContext() == null)
                                                    return;

                                                Toast.makeText(
                                                        getContext(),
                                                        "Couldn't update email: " +
                                                                e.getMessage(),
                                                        Toast.LENGTH_LONG
                                                ).show();
                                            });
                        })
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    private void promptChangePassword() {

        EditText input =
                new EditText(getContext());

        input.setHint("New password");

        input.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT |
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        new AlertDialog.Builder(getContext())
                .setTitle("Change Password")
                .setView(input)
                .setPositiveButton(
                        "Save",
                        (dialog, which) -> {

                            String newPassword =
                                    input.getText()
                                            .toString();

                            if (newPassword.length() < 6) {

                                if (getContext() != null) {

                                    Toast.makeText(
                                            getContext(),
                                            "Password must be at least 6 characters",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                }

                                return;
                            }

                            user.updatePassword(newPassword)
                                    .addOnSuccessListener(
                                            unused -> {

                                                if (getContext() == null)
                                                    return;

                                                Toast.makeText(
                                                        getContext(),
                                                        "Password updated",
                                                        Toast.LENGTH_SHORT
                                                ).show();
                                            })
                                    .addOnFailureListener(
                                            e -> {

                                                if (getContext() == null)
                                                    return;

                                                Toast.makeText(
                                                        getContext(),
                                                        "Couldn't update password: " +
                                                                e.getMessage(),
                                                        Toast.LENGTH_LONG
                                                ).show();
                                            });
                        })
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    private void promptVerifyAccount() {

        String[] verificationOptions = {
                "Verify Veterinarian",
                "Verify Feed Suppliers"
        };

        new AlertDialog.Builder(getContext())
                .setTitle("Verify Account")
                .setItems(
                        verificationOptions,
                        (dialog, which) -> {

                            if (which == 0) {

                                verificationType = "veterinarian";

                                promptVerificationPhoto(
                                        "Verify Veterinarian",
                                        "Please upload a clear photo of your veterinary license. It will be sent to an admin for review."
                                );

                            } else {

                                verificationType = "feed_supplier";

                                promptVerificationPhoto(
                                        "Verify Feed Suppliers",
                                        "Please upload a clear photo proving that you are a feed supplier. It will be sent to an admin for review."
                                );
                            }
                        })
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    private void promptVerificationPhoto(
            String title,
            String message
    ) {

        verificationImageUri = null;

        new AlertDialog.Builder(getContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(
                        "Choose Photo",
                        (dialog, which) ->
                                openVerificationGallery()
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    private void openEditProfile() {

        EditText input =
                new EditText(getContext());

        input.setHint("Username");

        input.setText(
                profileName.getText().toString()
        );

        new AlertDialog.Builder(getContext())
                .setTitle("Edit Profile")
                .setView(input)
                .setPositiveButton(
                        "Save",
                        (dialog, which) -> {

                            String newUsername =
                                    input.getText()
                                            .toString()
                                            .trim();

                            if (newUsername.isEmpty())
                                return;

                            Map<String, Object> map =
                                    new HashMap<>();

                            map.put(
                                    "username",
                                    newUsername
                            );

                            db.collection("users")
                                    .document(
                                            user.getUid()
                                    )
                                    .update(map)
                                    .addOnSuccessListener(
                                            unused -> {

                                                profileName.setText(
                                                        newUsername
                                                );

                                                Toast.makeText(
                                                        getContext(),
                                                        "Username Updated",
                                                        Toast.LENGTH_SHORT
                                                ).show();
                                            });
                        })
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    private void logOut() {

        mAuth.signOut();

        startActivity(
                new Intent(
                        getActivity(),
                        Login.class
                )
        );

        requireActivity().finish();
    }

    @Override
    public void onDestroyView() {

        super.onDestroyView();

        if (notificationsListener != null) {
            notificationsListener.remove();
        }
    }
}