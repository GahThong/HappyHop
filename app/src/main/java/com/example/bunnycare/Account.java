package com.example.bunnycare;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.*;
import android.widget.*;

import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;

public class Account extends Fragment {

    EditText txtUsername, txtEmail, txtBreed;
    AutoCompleteTextView txtRole;
    Button btnSave, btnDiscard;
    ImageView menuIcon;

    FirebaseAuth mAuth;
    FirebaseFirestore db;
    FirebaseUser user;

    User originalUser;

    public Account() {}

    public static Fragment newInstance() {
        return null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_account, container, false);

        txtUsername = view.findViewById(R.id.accountUsername);
        txtEmail = view.findViewById(R.id.accountEmail);
        txtBreed = view.findViewById(R.id.accountBreed);
        txtRole = view.findViewById(R.id.accountRole);
        btnSave = view.findViewById(R.id.btnSave);
        btnDiscard = view.findViewById(R.id.btnDiscard);
        menuIcon = view.findViewById(R.id.menuIcon);

        String[] roles = {"Pet Owner", "Vet"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_dropdown_item_1line, roles);
        txtRole.setAdapter(adapter);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        user = mAuth.getCurrentUser();

        if (user != null) {
            db.collection("users").document(user.getUid())
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String username = doc.getString("username");
                            String email = doc.getString("email");
                            String breed = doc.getString("breed");
                            String role = doc.getString("role");

                            txtUsername.setText(username);
                            txtEmail.setText(email);
                            txtBreed.setText(breed);
                            txtRole.setText(role, false);

                            originalUser = new User(username, email, breed, role);
                        }
                    });
        }

        btnSave.setOnClickListener(v -> {
            if (user != null) {
                User updatedUser = new User(
                        txtUsername.getText().toString(),
                        txtEmail.getText().toString(),
                        txtBreed.getText().toString(),
                        txtRole.getText().toString()
                );

                db.collection("users").document(user.getUid())
                        .set(updatedUser)
                        .addOnSuccessListener(a -> {
                            Toast.makeText(getActivity(), "Saved!", Toast.LENGTH_SHORT).show();
                            originalUser = updatedUser;
                        });
            }
        });

        btnDiscard.setOnClickListener(v -> {
            if (originalUser != null) {
                txtUsername.setText(originalUser.getUsername());
                txtEmail.setText(originalUser.getEmail());
                txtBreed.setText(originalUser.getBreed());
                txtRole.setText(originalUser.getRole(), false);
            }
        });

        menuIcon.setOnClickListener(v -> showMenu(v));

        return view;
    }

    private void showMenu(View view) {
        PopupMenu popup = new PopupMenu(getContext(), view);
        popup.inflate(R.menu.menu_account);

        popup.setOnMenuItemClickListener(item -> {

            if (item.getItemId() == R.id.menu_logout) {
                mAuth.signOut();
                startActivity(new Intent(getActivity(), Login.class));
                getActivity().finish();
                return true;
            }

            if (item.getItemId() == R.id.menu_verify_account) {
                startActivity(new Intent(getActivity(), Login.class));
                getActivity().finish();
                return true;
            }

            if (item.getItemId() == R.id.menu_qr) {
                startActivity(new Intent(getActivity(), QRActivity.class));
                return true;
            }

            return false;
        });

        popup.show();
    }
}