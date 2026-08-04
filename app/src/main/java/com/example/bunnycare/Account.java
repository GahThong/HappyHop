package com.example.bunnycare;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.HashMap;
import java.util.Map;

public class Account extends Fragment {

    EditText txtUsername, txtEmail, txtBreed;
    TextView txtRole;
    ImageView menuIcon, profileImage;

    FirebaseAuth mAuth;
    FirebaseFirestore db;
    FirebaseUser user;

    Uri imageUri;
    Button btnSave, btnDiscard;
    String imageUrl = "";
    ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    public android.view.View onCreateView(android.view.LayoutInflater inflater, ViewGroup container,
                                          android.os.Bundle savedInstanceState) {

        android.view.View view = inflater.inflate(R.layout.fragment_account, container, false);

        txtUsername = view.findViewById(R.id.accountUsername);
        txtEmail = view.findViewById(R.id.accountEmail);
        txtBreed = view.findViewById(R.id.accountBreed);
        txtRole = view.findViewById(R.id.accountRole);
        profileImage = view.findViewById(R.id.profileImage);
        menuIcon = view.findViewById(R.id.menuIcon);
        btnSave = view.findViewById(R.id.btnSave);
        btnDiscard = view.findViewById(R.id.btnDiscard);
        txtRole.setText("Rabbit Owner");

        txtEmail.setEnabled(false);
        txtEmail.setFocusable(false);
        txtEmail.setClickable(false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        user = mAuth.getCurrentUser();

        if (user == null) return view;

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {

                        imageUri = result.getData().getData();

                        Glide.with(requireContext())
                                .load(imageUri)
                                .circleCrop()
                                .into(profileImage);
                    }
                });

        profileImage.setOnClickListener(v -> openGallery());

        menuIcon.setOnClickListener(this::showMenu);

        txtUsername.setOnFocusChangeListener((v, hasFocus) -> {

            if (!hasFocus) {

                String newUsername = txtUsername.getText().toString().trim();

                Map<String, Object> map = new HashMap<>();
                map.put("username", newUsername);

                db.collection("users")
                        .document(user.getUid())
                        .update(map)
                        .addOnSuccessListener(unused ->
                                Toast.makeText(getContext(),
                                        "Username Updated",
                                        Toast.LENGTH_SHORT).show());
            }
        });

        btnSave.setOnClickListener(v -> uploadToCloudinary());
        loadUser();

        return view;
    }
    private void uploadToCloudinary() {

        if (imageUri == null) {
            saveProfile();
            return;
        }

        MediaManager.get()
                .upload(imageUri)
                .unsigned("ml_default")
                .callback(new UploadCallback() {

                    @Override public void onStart(String requestId) {}
                    @Override public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {

                        imageUrl = (String) resultData.get("secure_url");
                        saveProfile();
                    }

                    @Override public void onError(String requestId, ErrorInfo error) {}
                    @Override public void onReschedule(String requestId, ErrorInfo error) {}

                }).dispatch();
    }

    private void saveProfile() {

        Map<String, Object> map = new HashMap<>();
        map.put("username", txtUsername.getText().toString().trim());
        map.put("email", txtEmail.getText().toString().trim());
        map.put("breed", txtBreed.getText().toString().trim());
        map.put("role", "Rabbit Owner");
        map.put("imageUrl", imageUrl);

        db.collection("users")
                .document(user.getUid())
                .set(map, SetOptions.merge())
                .addOnSuccessListener(a ->
                        Toast.makeText(getContext(), "Saved", Toast.LENGTH_SHORT).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }
    private void loadUser() {

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {

                    txtUsername.setText(doc.getString("username"));
                    txtEmail.setText(doc.getString("email"));
                    txtBreed.setText(doc.getString("breed"));

                    String imageUrl = doc.getString("imageUrl");

                    if (imageUrl != null && !imageUrl.isEmpty()) {

                        Glide.with(requireContext())
                                .load(imageUrl)
                                .circleCrop()
                                .into(profileImage);
                    }
                });
    }

    private void openGallery() {

        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");

        imagePickerLauncher.launch(intent);
    }

    private void showMenu(android.view.View view) {

        PopupMenu popup = new PopupMenu(requireContext(), view);

        popup.inflate(R.menu.menu_account);

        popup.setOnMenuItemClickListener(item -> {

            int id = item.getItemId();

            if (id == R.id.menu_account_setting) {
                showAccountSettings();
            }


            if (id == R.id.menu_logout) {

                mAuth.signOut();

                startActivity(new Intent(getActivity(), Login.class));

                requireActivity().finish();
            }

            return true;
        });

        popup.show();
    }

    private void showAccountSettings() {

        String[] options = {"Change Email", "Change Password"};

        new AlertDialog.Builder(getContext())
                .setTitle("Account Settings")
                .setItems(options, (d, i) -> {

                    if (i == 0) {
                        changeEmail();
                    }

                    if (i == 1) {
                        changePassword();
                    }
                })
                .show();
    }

    private void changeEmail() {

        EditText e = new EditText(getContext());

        new AlertDialog.Builder(getContext())
                .setTitle("Change Email")
                .setView(e)
                .setPositiveButton("Send", (d, w) -> {

                    String email = e.getText().toString().trim();

                    user.verifyBeforeUpdateEmail(email)
                            .addOnSuccessListener(a -> {

                                db.collection("users")
                                        .document(user.getUid())
                                        .update("email", email);

                                Toast.makeText(getContext(),
                                        "Verification Sent",
                                        Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void changePassword() {

        EditText oldP = new EditText(getContext());
        EditText newP = new EditText(getContext());
        EditText reP = new EditText(getContext());

        oldP.setHint("Current Password");
        newP.setHint("New Password");
        reP.setHint("Confirm Password");

        LinearLayout layout = new LinearLayout(getContext());

        layout.setOrientation(LinearLayout.VERTICAL);

        layout.addView(oldP);
        layout.addView(newP);
        layout.addView(reP);

        new AlertDialog.Builder(getContext())
                .setTitle("Change Password")
                .setView(layout)
                .setPositiveButton("Update", (d, w) -> {

                    String oldPass = oldP.getText().toString().trim();
                    String newPass = newP.getText().toString().trim();
                    String rePass = reP.getText().toString().trim();

                    if (!newPass.equals(rePass)) {

                        Toast.makeText(getContext(),
                                "Passwords do not match",
                                Toast.LENGTH_SHORT).show();

                        return;
                    }

                    AuthCredential credential =
                            EmailAuthProvider.getCredential(user.getEmail(), oldPass);

                    user.reauthenticate(credential)
                            .addOnSuccessListener(a ->
                                    user.updatePassword(newPass)
                                            .addOnSuccessListener(unused ->
                                                    Toast.makeText(getContext(),
                                                            "Password Updated",
                                                            Toast.LENGTH_SHORT).show()
                                            )
                            );
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showFloatingImage(String imageUrl) {

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());

        LinearLayout layout = new LinearLayout(requireContext());

        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 40);

        TextView title = new TextView(requireContext());

        title.setText("Medical Record");
        title.setTextSize(22);
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, 0, 0, 30);

        ImageView imageView = new ImageView(requireContext());

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        800
                );

        imageView.setLayoutParams(params);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        Glide.with(requireContext())
                .load(imageUrl)
                .into(imageView);

        layout.addView(title);
        layout.addView(imageView);

        builder.setView(layout)
                .setPositiveButton("Close", null);

        builder.create().show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        IntentResult result =
                IntentIntegrator.parseActivityResult(
                        requestCode,
                        resultCode,
                        data
                );

        if (result != null && result.getContents() != null) {

            String uid = result.getContents();

            db.collection("users")
                    .document(uid)
                    .get()
                    .addOnSuccessListener(doc -> {

                        String image = doc.getString("qrImage");

                        if (image == null || image.isEmpty()) {

                            Toast.makeText(getContext(),
                                    "No image found",
                                    Toast.LENGTH_SHORT).show();

                            return;
                        }

                        showFloatingImage(image);
                    });

            return;
        }

        if (requestCode == 2001 && data != null) {

            Uri uri = data.getData();

            if (uri != null) {

                db.collection("users")
                        .document(user.getUid())
                        .update("qrImage", uri.toString())
                        .addOnSuccessListener(a ->
                                Toast.makeText(getContext(),
                                        "QR Image Updated",
                                        Toast.LENGTH_SHORT).show()
                        );
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
}