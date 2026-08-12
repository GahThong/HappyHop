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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Profile screen: avatar, name, email, "Edit Profile", the "My Rabbits" list,
 * and the Account card (Notifications / Privacy / Log Out).
 *
 * Assumed Firestore shape (adjust field names to match your project if different):
 *   users/{uid}          -> username, email, imageUrl
 *   rabbits               -> ownerId, name, breed, imageUrl
 *   notifications         -> recipientId, message, read, timestamp
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

        rowPrivacy.setOnClickListener(v -> showPrivacy());

        rowLogout.setOnClickListener(v -> logOut());

        return view;
    }

    private void openGallery() {

        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");

        imagePickerLauncher.launch(intent);
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

    private void showPrivacy() {

        new AlertDialog.Builder(getContext())
                .setTitle("Privacy")
                // TODO: replace with real privacy settings (data sharing, visibility, etc.)
                .setMessage("Privacy settings go here.")
                .setPositiveButton("Close", null)
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