package com.example.bunnycare;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
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
    String imageUrl = "";

    ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_account, container, false);

        txtUsername = view.findViewById(R.id.accountUsername);
        txtEmail = view.findViewById(R.id.accountEmail);
        txtBreed = view.findViewById(R.id.accountBreed);
        txtRole = view.findViewById(R.id.accountRole);
        profileImage = view.findViewById(R.id.profileImage);
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
                        Glide.with(requireContext()).load(imageUri).circleCrop().into(profileImage);
                    }
                });

        profileImage.setOnClickListener(v -> openGallery());
        menuIcon.setOnClickListener(this::showMenu);

        loadUser();

        return view;
    }

    private void loadUser() {
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    txtUsername.setText(doc.getString("username"));
                    txtEmail.setText(doc.getString("email"));
                    txtBreed.setText(doc.getString("breed"));
                    imageUrl = doc.getString("imageUrl");

                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        Glide.with(requireContext()).load(imageUrl).circleCrop().into(profileImage);
                    }
                });
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void showMenu(View view) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.inflate(R.menu.menu_account);

        popup.setOnMenuItemClickListener(item -> {

            int id = item.getItemId();

            if (id == R.id.menu_account_setting) {
                showAccountSettings();
            }

            if (id == R.id.menu_qr) {
                showQRMenu();
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
                    if (i == 0) changeEmail();
                    if (i == 1) changePassword();
                }).show();
    }

    private void changeEmail() {
        EditText e = new EditText(getContext());

        new AlertDialog.Builder(getContext())
                .setTitle("Change Email")
                .setView(e)
                .setPositiveButton("Send", (d, w) -> {

                    String email = e.getText().toString();

                    user.verifyBeforeUpdateEmail(email)
                            .addOnSuccessListener(a -> {
                                db.collection("users").document(user.getUid())
                                        .update("email", email);
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void changePassword() {
        EditText oldP = new EditText(getContext());
        EditText newP = new EditText(getContext());
        EditText reP = new EditText(getContext());

        LinearLayout l = new LinearLayout(getContext());
        l.setOrientation(LinearLayout.VERTICAL);
        l.addView(oldP);
        l.addView(newP);
        l.addView(reP);

        new AlertDialog.Builder(getContext())
                .setTitle("Change Password")
                .setView(l)
                .setPositiveButton("Update", (d, w) -> {

                    if (!newP.getText().toString().equals(reP.getText().toString())) return;

                    AuthCredential c = EmailAuthProvider.getCredential(
                            user.getEmail(),
                            oldP.getText().toString()
                    );

                    user.reauthenticate(c)
                            .addOnSuccessListener(a ->
                                    user.updatePassword(newP.getText().toString())
                            );
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showQRMenu() {

        String[] options = {"Generate QR", "Scan QR"};

        new AlertDialog.Builder(getContext())
                .setTitle("QR Options")
                .setItems(options, (d, i) -> {

                    if (i == 0) generateQR();
                    if (i == 1) scanQR();

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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        if (result != null && result.getContents() != null) {

            String uid = result.getContents();

            db.collection("users")
                    .document(uid)
                    .get()
                    .addOnSuccessListener(doc -> {

                        String image = doc.getString("qrImage");

                        if (image == null || image.isEmpty()) {
                            Toast.makeText(getContext(), "No image found", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        ImageView img = new ImageView(getContext());
                        img.setAdjustViewBounds(true);

                        AlertDialog dialog = new AlertDialog.Builder(getContext())
                                .setTitle("QR Image")
                                .setView(img)
                                .setPositiveButton("Close", null)
                                .create();

                        dialog.show();

                        Glide.with(requireContext()).load(image).into(img);
                    });
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
}