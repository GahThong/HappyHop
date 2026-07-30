package com.example.bunnycare;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class Register extends AppCompatActivity {

    private EditText firstName, lastName, username, email;
    private TextInputEditText password, repassword;
    private Button btnSignUp;
    private ProgressBar progressBar;
    private TextView textLogin;
    private ImageButton btnGoogleSignUp;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        firstName = findViewById(R.id.firstName);
        lastName = findViewById(R.id.lastName);
        username = findViewById(R.id.username);
        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        repassword = findViewById(R.id.repassword);
        btnSignUp = findViewById(R.id.btnSignUp);
        progressBar = findViewById(R.id.progressBar);
        textLogin = findViewById(R.id.textLogin);
        btnGoogleSignUp = findViewById(R.id.btnGoogleSignUp);

        btnSignUp.setOnClickListener(v -> validateInputs());

        textLogin.setOnClickListener(v -> {
            startActivity(new Intent(Register.this, Login.class));
            finish();
        });

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            firebaseAuthWithGoogle(account);
                        } catch (Exception e) {
                            Toast.makeText(this, "Google Sign-Up Failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        btnGoogleSignUp.setOnClickListener(v -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);

        progressBar.setVisibility(View.VISIBLE);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(View.GONE);

                    if (task.isSuccessful()) {

                        FirebaseUser user = mAuth.getCurrentUser();

                        if (user != null) {

                            boolean isNewUser = task.getResult()
                                    .getAdditionalUserInfo()
                                    .isNewUser();

                            String uid = user.getUid();

                            if (isNewUser) {
                                Map<String, Object> userMap = new HashMap<>();
                                userMap.put("firstName", account.getGivenName());
                                userMap.put("lastName", account.getFamilyName());
                                userMap.put("username", account.getDisplayName());
                                userMap.put("email", account.getEmail());
                                userMap.put("verified", true);

                                db.collection("users")
                                        .document(uid)
                                        .set(userMap);
                            }

                            startActivity(new Intent(Register.this, HomeActivity.class));
                            finish();
                        }

                    } else {
                        Toast.makeText(Register.this, "Google Authentication Failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void validateInputs() {

        String fName = firstName.getText().toString().trim();
        String lName = lastName.getText().toString().trim();
        String user = username.getText().toString().trim();
        String mail = email.getText().toString().trim();
        String pass = password.getText().toString().trim();
        String rePass = repassword.getText().toString().trim();

        if (TextUtils.isEmpty(fName) || TextUtils.isEmpty(lName) ||
                TextUtils.isEmpty(user) || TextUtils.isEmpty(mail) ||
                TextUtils.isEmpty(pass) || TextUtils.isEmpty(rePass)) {

            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!pass.equals(rePass)) {
            password.setError("Passwords do not match");
            repassword.setError("Passwords do not match");
            return;
        }

        if (!pass.matches("^(?=.*[A-Z])(?=.*[^a-zA-Z0-9]).{6,}$")) {
            password.setError("6+ chars, 1 uppercase, 1 special char");
            password.requestFocus();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        db.collection("users")
                .whereEqualTo("firstName", fName)
                .whereEqualTo("lastName", lName)
                .get()
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Name already exists", Toast.LENGTH_LONG).show();
                    } else {
                        createAccount(fName, lName, user, mail, pass);
                    }
                });
    }

    private void createAccount(String fName, String lName, String userName, String email, String password) {

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    progressBar.setVisibility(View.GONE);

                    if (task.isSuccessful()) {

                        FirebaseUser firebaseUser = mAuth.getCurrentUser();

                        if (firebaseUser != null) {

                            firebaseUser.sendEmailVerification();

                            String uid = firebaseUser.getUid();

                            Map<String, Object> userMap = new HashMap<>();
                            userMap.put("firstName", fName);
                            userMap.put("lastName", lName);
                            userMap.put("username", userName);
                            userMap.put("email", email);
                            userMap.put("verified", false);

                            db.collection("users")
                                    .document(uid)
                                    .set(userMap);

                            Toast.makeText(this,
                                    "Account created. Check email for verification.",
                                    Toast.LENGTH_LONG).show();

                            mAuth.signOut();
                            finish();
                        }

                    } else {
                        Toast.makeText(this,
                                task.getException() != null ?
                                        task.getException().getMessage() :
                                        "Registration failed",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}