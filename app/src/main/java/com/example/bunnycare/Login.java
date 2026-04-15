package com.example.bunnycare;

import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class Login extends AppCompatActivity {

    private FirebaseAuth mAuth;

    private TextInputEditText editTextEmail, editTextPassword;
    private Button btnLogin;
    private ProgressBar progressBar;
    private TextView textSignUp, textForgotPassword;
    private ImageButton btnGoogleLogin;

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        // Auto-login if user already signed in
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

        // Bind views
        editTextEmail = findViewById(R.id.username);
        editTextPassword = findViewById(R.id.password);
        btnLogin = findViewById(R.id.btnGoLogin);
        progressBar = findViewById(R.id.progressBar);
        textSignUp = findViewById(R.id.textSignUp);
        textForgotPassword = findViewById(R.id.forgotPass);
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);

        // Underline Sign Up text
        textSignUp.setPaintFlags(textSignUp.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        // Navigate to RegisterActivity
        textSignUp.setOnClickListener(view -> {
            startActivity(new Intent(Login.this, Register.class));
        });

        // Forgot password
        textForgotPassword.setOnClickListener(view -> showForgotPasswordDialog());

        // Login button
        btnLogin.setOnClickListener(view -> loginUser());

        // Placeholder Google login
        btnGoogleLogin.setOnClickListener(view ->
                Toast.makeText(Login.this, "Google Login clicked", Toast.LENGTH_SHORT).show()
        );
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
                        Toast.makeText(Login.this, "Login Successful", Toast.LENGTH_SHORT).show();
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
        input.setHint("Enter your email");
        builder.setView(input);

        builder.setPositiveButton("Send", (dialog, which) -> {
            String email = input.getText().toString().trim();
            if (TextUtils.isEmpty(email)) {
                Toast.makeText(Login.this, "Enter your email", Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(Login.this, "Recovery email sent!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(Login.this, "Failed to send recovery email", Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }
}