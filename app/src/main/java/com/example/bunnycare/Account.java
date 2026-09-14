package com.example.bunnycare;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

/**
 * Profile screen: avatar, name, email, "Edit Profile", the "My Rabbits" list,
 * and the Account card (Notifications / Account Settings / Log Out).
 *
 * Assumed Firestore shape (adjust field names to match your project if different):
 *   users/{uid}              -> username, email, imageUrl
 *   rabbits                   -> ownerId, name, breed, imageUrl
 *   notifications              -> recipientId, message, read, timestamp
 *   verificationRequests      -> userId, licenseImageUrl, status, submittedAt
 */
public class Account extends Fragment {

    ImageView profileImage;
    TextView profileName, profileEmail, notificationBadge;
    View btnEditProfile, rowNotifications, rowPrivacy, rowLogout;
    RecyclerView rabbitsRecyclerView;

    FirebaseAuth mAuth;
    FirebaseFirestore db;
    FirebaseUser user;

    List<Rabbit> rabbitList;
    RabbitAdapter rabbitsAdapter;

    ListenerRegistration notificationsListener;

    Uri imageUri;
    ActivityResultLauncher<Intent> imagePickerLauncher;

    // Separate picker just for the vet license photo used in "Verify Account"
    Uri verificationImageUri;
    ActivityResultLauncher<Intent> verificationImagePickerLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_account, container, false);

        profileImage = view.findViewById(R.id.profileImage);
        profileName = view.findViewById(R.id.profileName);
        profileEmail = view.findViewById(R.id.profileEmail);
        btnEditProfile = view.findViewById(R.id.btnEditProfile);
        rabbitsRecyclerView = view.findViewById(R.id.rabbitsRecyclerView);
        rowNotifications = view.findViewById(R.id.rowNotifications);
        notificationBadge = view.findViewById(R.id.notificationBadge);
        rowPrivacy = view.findViewById(R.id.rowPrivacy); // TODO: rename id/label to "Account Settings" in XML
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
                // No long-press action on the profile screen for now
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

                    @Override public void onStart(String requestId) {}
                    @Override public void onProgress(String requestId, long bytes, long totalBytes) {}

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

                    @Override public void onReschedule(String requestId, ErrorInfo error) {}

                }).dispatch();
    }

    /**
     * Uploads the vet license photo and creates a pending verification
     * request for the admin to review. Does NOT flip any "verified" flag
     * client-side — that should only ever happen from the admin side
     * (e.g. an Admin Cloud Function or admin app writing back to the user doc)
     * so a user can't just mark themselves verified.
     */
    private void uploadVerificationPhoto() {

        if (verificationImageUri == null) return;

        if (isAdded()) {
            Toast.makeText(getContext(), "Uploading license photo...", Toast.LENGTH_SHORT).show();
        }

        MediaManager.get()
                .upload(verificationImageUri)
                .unsigned("ml_default")
                .callback(new UploadCallback() {

                    @Override public void onStart(String requestId) {}
                    @Override public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {

                        String secureUrl = (String) resultData.get("secure_url");

                        Map<String, Object> request = new HashMap<>();
                        request.put("userId", user.getUid());
                        request.put("userEmail", user.getEmail());
                        request.put("licenseImageUrl", secureUrl);
                        request.put("status", "pending");
                        request.put("submittedAt", FieldValue.serverTimestamp());

                        db.collection("verificationRequests")
                                .add(request)
                                .addOnSuccessListener(docRef -> {

                                    if (!isAdded() || getContext() == null) return;

                                    Toast.makeText(getContext(),
                                            "Submitted for review. We'll notify you once an admin verifies your license.",
                                            Toast.LENGTH_LONG).show();
                                })
                                .addOnFailureListener(e -> {

                                    if (!isAdded() || getContext() == null) return;

                                    Toast.makeText(getContext(),
                                            "Couldn't submit for review: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                });
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {

                        if (!isAdded() || getContext() == null) return;

                        Toast.makeText(getContext(), "Upload failed: " + error.getDescription(), Toast.LENGTH_LONG).show();
                    }

                    @Override public void onReschedule(String requestId, ErrorInfo error) {}

                }).dispatch();
    }

    private void loadUser() {

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {

                    if (!doc.exists()) return;

                    profileName.setText(doc.getString("username"));
                    profileEmail.setText(doc.getString("email"));

                    String imageUrl = doc.getString("imageUrl");

                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        Glide.with(requireContext())
                                .load(imageUrl)
                                .circleCrop()
                                .into(profileImage);
                    }
                });
    }

    private void loadRabbits() {

        db.collection("rabbits")
                .whereEqualTo("ownerId", user.getUid())
                .addSnapshotListener((value, error) -> {

                    if (error != null || value == null) return;

                    rabbitList.clear();

                    for (DocumentSnapshot doc : value.getDocuments()) {

                        Rabbit rabbit = doc.toObject(Rabbit.class);

                        if (rabbit != null) {
                            rabbit.setId(doc.getId());
                            rabbitList.add(rabbit);
                        }
                    }

                    rabbitsAdapter.notifyDataSetChanged();
                });
    }

    /**
     * Same pattern Community.java uses for its post feed: a live Firestore
     * listener ordered by timestamp, just scoped to this user's notifications.
     */
    private void listenForNotificationCount() {

        notificationsListener = db.collection("notifications")
                .whereEqualTo("recipientId", user.getUid())
                .whereEqualTo("read", false)
                .addSnapshotListener((value, error) -> {

                    if (error != null || value == null || getContext() == null) return;

                    int unread = value.size();

                    if (unread > 0) {
                        notificationBadge.setVisibility(View.VISIBLE);
                        notificationBadge.setText(String.valueOf(unread));
                    } else {
                        notificationBadge.setVisibility(View.GONE);
                    }
                });
    }

    private void showNotifications() {

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());

        View sheet = LayoutInflater.from(getContext())
                .inflate(R.layout.fragment_notification, null);

        dialog.setContentView(sheet);
        dialog.show();

        RecyclerView notificationsRecyclerView =
                sheet.findViewById(R.id.notificationsRecyclerView);

        View emptyNotifications = sheet.findViewById(R.id.emptyNotifications);

        List<AppNotification> notificationList = new ArrayList<>();
        NotificationAdapter adapter = new NotificationAdapter(getContext(), notificationList);

        notificationsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        notificationsRecyclerView.setAdapter(adapter);

        db.collection("notifications")
                .whereEqualTo("recipientId", user.getUid())
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {

                    if (error != null || value == null) return;

                    notificationList.clear();

                    for (DocumentSnapshot doc : value.getDocuments()) {

                        AppNotification notification = doc.toObject(AppNotification.class);

                        if (notification != null) {
                            notification.setId(doc.getId());
                            notificationList.add(notification);

                            // Mark as read once it's been shown
                            if (!notification.isRead()) {
                                doc.getReference().update("read", true);
                            }
                        }
                    }

                    adapter.notifyDataSetChanged();

                    emptyNotifications.setVisibility(
                            notificationList.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    /**
     * Replaces the old "Privacy" dialog. Presents Change Email / Change
     * Password / Verify Account as a simple option list.
     */
    private void openAccountSettings() {

        String[] options = {"Change Email", "Change Password", "Verify Account"};

        new AlertDialog.Builder(getContext())
                .setTitle("Account Settings")
                .setItems(options, (dialog, which) -> {

                    switch (which) {
                        case 0:
                            promptReauth(this::promptChangeEmail);
                            break;
                        case 1:
                            promptReauth(this::promptChangePassword);
                            break;
                        case 2:
                            promptVerifyAccount();
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Firebase requires a recent sign-in before sensitive changes like
     * updateEmail/updatePassword, or it throws FirebaseAuthRecentLoginRequiredException.
     * This asks for the current password and re-authenticates first.
     */
    private void promptReauth(Runnable onSuccess) {

        if (user.getEmail() == null) {
            Toast.makeText(getContext(), "No email on file for this account.", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText passwordInput = new EditText(getContext());
        passwordInput.setHint("Current password");
        passwordInput.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(getContext())
                .setTitle("Confirm It's You")
                .setMessage("Please re-enter your password to continue.")
                .setView(passwordInput)
                .setPositiveButton("Confirm", (dialog, which) -> {

                    String password = passwordInput.getText().toString();

                    if (password.isEmpty()) return;

                    AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);

                    user.reauthenticate(credential)
                            .addOnSuccessListener(unused -> onSuccess.run())
                            .addOnFailureListener(e -> {

                                if (getContext() == null) return;

                                Toast.makeText(getContext(), "Re-authentication failed: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptChangeEmail() {

        EditText input = new EditText(getContext());
        input.setHint("New email");
        input.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        new AlertDialog.Builder(getContext())
                .setTitle("Change Email")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {

                    String newEmail = input.getText().toString().trim();

                    if (newEmail.isEmpty()) return;

                    user.updateEmail(newEmail)
                            .addOnSuccessListener(unused -> {

                                Map<String, Object> map = new HashMap<>();
                                map.put("email", newEmail);

                                db.collection("users")
                                        .document(user.getUid())
                                        .update(map)
                                        .addOnSuccessListener(unused2 -> {

                                            if (getContext() == null) return;

                                            profileEmail.setText(newEmail);
                                            Toast.makeText(getContext(), "Email updated", Toast.LENGTH_SHORT).show();
                                        });
                            })
                            .addOnFailureListener(e -> {

                                if (getContext() == null) return;

                                Toast.makeText(getContext(), "Couldn't update email: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptChangePassword() {

        EditText input = new EditText(getContext());
        input.setHint("New password");
        input.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(getContext())
                .setTitle("Change Password")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {

                    String newPassword = input.getText().toString();

                    if (newPassword.length() < 6) {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }

                    user.updatePassword(newPassword)
                            .addOnSuccessListener(unused -> {

                                if (getContext() == null) return;

                                Toast.makeText(getContext(), "Password updated", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {

                                if (getContext() == null) return;

                                Toast.makeText(getContext(), "Couldn't update password: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptVerifyAccount() {

        new AlertDialog.Builder(getContext())
                .setTitle("Verify Account")
                .setMessage("To verify your account as a vet, please upload a clear photo of your veterinary license. " +
                        "It will be sent to an admin for review.")
                .setPositiveButton("Choose Photo", (dialog, which) -> openVerificationGallery())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openEditProfile() {

        EditText input = new EditText(getContext());

        input.setHint("Username");
        input.setText(profileName.getText().toString());

        new AlertDialog.Builder(getContext())
                .setTitle("Edit Profile")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {

                    String newUsername = input.getText().toString().trim();

                    if (newUsername.isEmpty()) return;

                    Map<String, Object> map = new HashMap<>();
                    map.put("username", newUsername);

                    db.collection("users")
                            .document(user.getUid())
                            .update(map)
                            .addOnSuccessListener(unused -> {

                                profileName.setText(newUsername);

                                android.widget.Toast.makeText(getContext(),
                                        "Username Updated",
                                        android.widget.Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void logOut() {

        mAuth.signOut();

        startActivity(new Intent(getActivity(), Login.class));

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