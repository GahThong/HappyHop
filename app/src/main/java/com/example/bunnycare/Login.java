package com.example.bunnycare;

import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.*;
import com.google.android.material.textfield.TextInputEditText;

public class Login extends AppCompatActivity {

    private FirebaseAuth mAuth;

    private TextInputEditText editTextEmail, editTextPassword;
    private Button btnLogin;
    private ProgressBar progressBar;
    private TextView textSignUp, textForgotPassword;
    private ImageButton btnGoogleLogin;

    private GoogleSignInClient googleSignInClient;

    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user != null) {
            startActivity(new Intent(Login.this, HomeActivity.class));
            finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        editTextEmail = findViewById(R.id.username);
        editTextPassword = findViewById(R.id.password);
        btnLogin = findViewById(R.id.btnGoLogin);
        progressBar = findViewById(R.id.progressBar);
        textSignUp = findViewById(R.id.textSignUp);
        textForgotPassword = findViewById(R.id.forgotPass);
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);

        textSignUp.setPaintFlags(textSignUp.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        textSignUp.setOnClickListener(view ->
                startActivity(new Intent(Login.this, Register.class))
        );

        textForgotPassword.setOnClickListener(view -> showForgotPasswordDialog());

        btnLogin.setOnClickListener(view -> loginUser());

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
                            firebaseAuthWithGoogle(account.getIdToken());
                        } catch (Exception e) {
                            Toast.makeText(this, "Google Sign-In Failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        btnGoogleLogin.setOnClickListener(view -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);

        progressBar.setVisibility(View.VISIBLE);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        startActivity(new Intent(Login.this, HomeActivity.class));
                        finish();
                    } else {
                        Toast.makeText(Login.this, "Authentication Failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loginUser() {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(Login.this, "Enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        startActivity(new Intent(Login.this, HomeActivity.class));
                        finish();
                    } else {
                        Toast.makeText(Login.this,
                                task.getException() != null ? task.getException().getMessage() : "Login failed",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showForgotPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(Login.this);
        builder.setTitle("Reset Password");

        final EditText input = new EditText(Login.this);
        input.setHint("Enter your registered email");
        input.setPadding(40, 30, 40, 30);
        builder.setView(input);

        builder.setPositiveButton("Send", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            Button sendBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

            sendBtn.setOnClickListener(v -> {
                String email = input.getText().toString().trim();

                if (TextUtils.isEmpty(email)) {
                    input.setError("Email is required");
                    return;
                }

                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    input.setError("Enter a valid email");
                    return;
                }

                progressBar.setVisibility(View.VISIBLE);

                mAuth.sendPasswordResetEmail(email)
                        .addOnCompleteListener(task -> {
                            progressBar.setVisibility(View.GONE);

                            if (task.isSuccessful()) {
                                Toast.makeText(Login.this,
                                        "Password reset email sent. Check your inbox.",
                                        Toast.LENGTH_LONG).show();
                                dialog.dismiss();
                            } else {
                                Toast.makeText(Login.this,
                                        task.getException() != null ?
                                                task.getException().getMessage() :
                                                "Failed to send email",
                                        Toast.LENGTH_LONG).show();
                            }
                        });
            });
        });

        dialog.show();
    }
}