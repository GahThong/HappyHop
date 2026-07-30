package com.example.bunnycare;

import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class Login extends AppCompatActivity {

    private FirebaseAuth mAuth;

    // Views
    private EditText editTextEmail;
    private TextInputEditText editTextPassword;
    private CheckBox rememberMe;

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

        // Initialize Views
        editTextEmail = findViewById(R.id.username);
        editTextPassword = findViewById(R.id.password);
        rememberMe = findViewById(R.id.rememberMe);

        btnLogin = findViewById(R.id.btnGoLogin);
        progressBar = findViewById(R.id.progressBar);
        textSignUp = findViewById(R.id.textSignUp);
        textForgotPassword = findViewById(R.id.forgotPass);
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);

        // Underline Sign Up
        textSignUp.setPaintFlags(
                textSignUp.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG
        );

        // Open Register Page
        textSignUp.setOnClickListener(view ->
                startActivity(new Intent(Login.this, Register.class))
        );

        // Forgot Password
        textForgotPassword.setOnClickListener(view ->
                showForgotPasswordDialog()
        );

        // Login Button
        btnLogin.setOnClickListener(view ->
                loginUser()
        );

        // Google Sign In
        GoogleSignInOptions gso =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {

                    if (result.getResultCode() == RESULT_OK) {

                        Task<GoogleSignInAccount> task =
                                GoogleSignIn.getSignedInAccountFromIntent(result.getData());

                        try {

                            GoogleSignInAccount account =
                                    task.getResult(ApiException.class);

                            firebaseAuthWithGoogle(account.getIdToken());

                        } catch (Exception e) {

                            Toast.makeText(
                                    Login.this,
                                    "Google Sign-In Failed",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
                });

        btnGoogleLogin.setOnClickListener(view -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });
    }

    private void loginUser() {

        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            editTextEmail.setError("Email is required");
            editTextEmail.requestFocus();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextEmail.setError("Enter a valid email");
            editTextEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            editTextPassword.setError("Password is required");
            editTextPassword.requestFocus();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    progressBar.setVisibility(View.GONE);

                    if (task.isSuccessful()) {

                        // Optional:
                        if (rememberMe.isChecked()) {
                            // Save login state using SharedPreferences if needed
                        }

                        Toast.makeText(
                                Login.this,
                                "Login Successful",
                                Toast.LENGTH_SHORT
                        ).show();

                        startActivity(new Intent(Login.this, HomeActivity.class));
                        finish();

                    } else {

                        Toast.makeText(
                                Login.this,
                                task.getException() != null
                                        ? task.getException().getMessage()
                                        : "Login Failed",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private void firebaseAuthWithGoogle(String idToken) {

        AuthCredential credential =
                GoogleAuthProvider.getCredential(idToken, null);

        progressBar.setVisibility(View.VISIBLE);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {

                    progressBar.setVisibility(View.GONE);

                    if (task.isSuccessful()) {

                        Toast.makeText(
                                Login.this,
                                "Google Login Successful",
                                Toast.LENGTH_SHORT
                        ).show();

                        startActivity(new Intent(Login.this, HomeActivity.class));
                        finish();

                    } else {

                        Toast.makeText(
                                Login.this,
                                "Authentication Failed",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });
    }

    private void showForgotPasswordDialog() {

        AlertDialog.Builder builder =
                new AlertDialog.Builder(Login.this);

        builder.setTitle("Reset Password");

        final EditText input = new EditText(Login.this);
        input.setHint("Enter your registered email");
        input.setPadding(40, 30, 40, 30);

        builder.setView(input);

        builder.setPositiveButton("Send", null);
        builder.setNegativeButton("Cancel",
                (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {

            Button sendBtn =
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE);

            sendBtn.setOnClickListener(v -> {

                String email =
                        input.getText().toString().trim();

                if (TextUtils.isEmpty(email)) {
                    input.setError("Email is required");
                    return;
                }

                if (!android.util.Patterns.EMAIL_ADDRESS
                        .matcher(email)
                        .matches()) {

                    input.setError("Enter a valid email");
                    return;
                }

                progressBar.setVisibility(View.VISIBLE);

                mAuth.sendPasswordResetEmail(email)
                        .addOnCompleteListener(task -> {

                            progressBar.setVisibility(View.GONE);

                            if (task.isSuccessful()) {

                                Toast.makeText(
                                        Login.this,
                                        "Password reset email sent. Check your inbox.",
                                        Toast.LENGTH_LONG
                                ).show();

                                dialog.dismiss();

                            } else {

                                Toast.makeText(
                                        Login.this,
                                        task.getException() != null
                                                ? task.getException().getMessage()
                                                : "Failed to send email",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        });
            });
        });

        dialog.show();
    }
}