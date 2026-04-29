package com.example.bunnycare;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.HashMap;
import java.util.Map;

public class Account extends Fragment {

    EditText txtUsername, txtEmail, txtBreed;
    TextView txtRole;
    Button btnSave, btnDiscard;
    ImageView menuIcon, profileImage;

    FirebaseAuth mAuth;
    FirebaseFirestore db;
    FirebaseUser user;

    Uri imageUri;
    String imageUrl = "";

    ActivityResultLauncher<Intent> imagePickerLauncher;
    ActivityResultLauncher<Intent> medicalLauncher;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_account, container, false);

        txtUsername = view.findViewById(R.id.accountUsername);
        txtEmail = view.findViewById(R.id.accountEmail);
        txtBreed = view.findViewById(R.id.accountBreed);
        txtRole = view.findViewById(R.id.accountRole);

        profileImage = view.findViewById(R.id.profileImage);

        btnSave = view.findViewById(R.id.btnSave);
        btnDiscard = view.findViewById(R.id.btnDiscard);
        menuIcon = view.findViewById(R.id.menuIcon);

        txtRole.setText("Rabbit Owner");

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

        medicalLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        uploadMedical(result.getData().getData());
                    }
                });

        profileImage.setOnClickListener(v -> openGallery());
        menuIcon.setOnClickListener(this::showMenu);
        btnSave.setOnClickListener(v -> saveProfile());

        loadUser();

        return view;
    }

    private void loadUser() {

        db.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {

                    txtUsername.setText(doc.getString("username"));
                    txtEmail.setText(doc.getString("email"));
                    txtBreed.setText(doc.getString("breed"));

                    imageUrl = doc.getString("imageUrl");

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

    private void uploadToCloudinary() {

        if (imageUri == null) return;

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

    private void showMenu(View view) {

        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.inflate(R.menu.menu_account);

        popup.setOnMenuItemClickListener(item -> {

            int id = item.getItemId();

            if (id == R.id.menu_account_setting) {
                showAccountSettings();
            } else if (id == R.id.menu_qr) {
                showQRMenu();
            } else if (id == R.id.menu_logout) {
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

                    if (i == 0) changeEmail();
                    if (i == 1) changePassword();

                }).show();
    }

    private void changeEmail() {

        EditText newEmail = new EditText(getContext());
        newEmail.setHint("New Email");

        new AlertDialog.Builder(getContext())
                .setTitle("Change Email")
                .setView(newEmail)
                .setPositiveButton("Send Verification", (d, w) -> {

                    String email = newEmail.getText().toString();

                    user.verifyBeforeUpdateEmail(email)
                            .addOnSuccessListener(a -> {

                                db.collection("users")
                                        .document(user.getUid())
                                        .update("email", email);

                                Toast.makeText(getContext(), "Verification sent", Toast.LENGTH_LONG).show();
                            });

                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void changePassword() {

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);

        EditText oldPass = new EditText(getContext());
        EditText newPass = new EditText(getContext());
        EditText retypePass = new EditText(getContext());

        oldPass.setHint("Current Password");
        newPass.setHint("New Password");
        retypePass.setHint("Retype New Password");

        layout.addView(oldPass);
        layout.addView(newPass);
        layout.addView(retypePass);

        new AlertDialog.Builder(getContext())
                .setTitle("Change Password")
                .setView(layout)
                .setPositiveButton("Update", (d, w) -> {

                    String oldP = oldPass.getText().toString();
                    String newP = newPass.getText().toString();
                    String reP = retypePass.getText().toString();

                    if (!newP.equals(reP)) {
                        Toast.makeText(getContext(), "Password mismatch", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    AuthCredential c = EmailAuthProvider.getCredential(user.getEmail(), oldP);

                    user.reauthenticate(c).addOnSuccessListener(a ->
                            user.updatePassword(newP)
                    );

                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showQRMenu() {

        String[] options = {"Generate QR", "Scan QR", "Upload Medical Record", "Edit QR Images"};

        new AlertDialog.Builder(getContext())
                .setTitle("QR Options")
                .setItems(options, (d, i) -> {

                    if (i == 0) generateQR();
                    if (i == 1) scanQR();
                    if (i == 2) pickMedical();
                    if (i == 3) editMedical();

                }).show();
    }

    private void generateQR() {
        Intent i = new Intent(getActivity(), QRActivity.class);
        i.putExtra("data", user.getUid());
        startActivity(i);
    }

    private void scanQR() {
        IntentIntegrator.forSupportFragment(this).initiateScan();
    }

    private void pickMedical() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        medicalLauncher.launch(i);
    }

    private void editMedical() {
        Intent i = new Intent(Intent.ACTION_PICK);
        i.setType("image/*");
        medicalLauncher.launch(i);
    }

    private void uploadMedical(Uri uri) {

        MediaManager.get().upload(uri)
                .unsigned("ml_default")
                .callback(new UploadCallback() {

                    @Override public void onStart(String requestId) {}
                    @Override public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {

                        String url = (String) resultData.get("secure_url");

                        db.collection("users")
                                .document(user.getUid())
                                .update("medicalRecord", url);
                    }

                    @Override public void onError(String requestId, ErrorInfo error) {}
                    @Override public void onReschedule(String requestId, ErrorInfo error) {}

                }).dispatch();
    }

    private void showMedical(String url) {

        ImageView img = new ImageView(getContext());

        Glide.with(requireContext()).load(url).into(img);

        new AlertDialog.Builder(getContext())
                .setTitle("Medical Record")
                .setView(img)
                .setPositiveButton("Close", null)
                .show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        if (result != null && result.getContents() != null) {

            String uid = result.getContents();

            db.collection("users").document(uid)
                    .get()
                    .addOnSuccessListener(doc -> {

                        String url = doc.getString("medicalRecord");

                        if (url != null) showMedical(url);
                    });
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
}