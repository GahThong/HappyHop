package com.example.bunnycare;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class Login extends AppCompatActivity {

    private EditText username;
    private EditText password;

    private Button btnGoLogin;
    private ImageButton btnGoogleLogin;

    private TextView btnLoginTab;
    private TextView btnCreateAccount;
    private TextView textSignUp;
    private TextView forgotPass;

    private ProgressBar progressBar;

    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();

        username = findViewById(R.id.username);
        password = findViewById(R.id.password);

        btnGoLogin = findViewById(R.id.btnGoLogin);
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);

        btnLoginTab = findViewById(R.id.btnLoginTab);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);

        textSignUp = findViewById(R.id.textSignUp);
        forgotPass = findViewById(R.id.forgotPass);

        progressBar = findViewById(R.id.progressBar);

        if (btnGoLogin != null) {
            btnGoLogin.setOnClickListener(v -> loginUser());
        }

        if (btnLoginTab != null) {
            btnLoginTab.setOnClickListener(v -> {
            });
        }

        if (btnCreateAccount != null) {
            btnCreateAccount.setOnClickListener(v -> {
                Intent intent = new Intent(
                        Login.this,
                        Register.class
                );

                startActivity(intent);
                finish();
            });
        }

        if (textSignUp != null) {
            textSignUp.setOnClickListener(v -> {
                Intent intent = new Intent(
                        Login.this,
                        Register.class
                );

                startActivity(intent);
                finish();
            });
        }

        if (forgotPass != null) {
            forgotPass.setOnClickListener(v -> resetPassword());
        }

        if (btnGoogleLogin != null) {
            btnGoogleLogin.setOnClickListener(v -> {

                Toast.makeText(
                        Login.this,
                        "Google login is not configured yet.",
                        Toast.LENGTH_SHORT
                ).show();

            });
        }
    }

    private void loginUser() {

        String email = username.getText().toString().trim();
        String pass = password.getText().toString().trim();

        username.setError(null);
        password.setError(null);

        if (TextUtils.isEmpty(email)) {
            username.setError("Enter your email");
            username.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            username.setError("Enter a valid email");
            username.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(pass)) {
            password.setError("Enter your password");
            password.requestFocus();
            return;
        }

        showLoading(true);

        auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener(this, task -> {

                    showLoading(false);

                    if (task.isSuccessful()) {

                        Toast.makeText(
                                Login.this,
                                "Login successful",
                                Toast.LENGTH_SHORT
                        ).show();

                        Intent intent = new Intent(
                                Login.this,
                                HomeActivity.class
                        );

                        intent.addFlags(
                                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                        Intent.FLAG_ACTIVITY_NEW_TASK
                        );

                        startActivity(intent);
                        finish();

                    } else {

                        String message = "Login failed";

                        if (task.getException() != null) {
                            message =
                                    task.getException().getMessage();
                        }

                        Toast.makeText(
                                Login.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private void resetPassword() {

        String email = username.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            username.setError("Enter your email first");
            username.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            username.setError("Enter a valid email");
            username.requestFocus();
            return;
        }

        showLoading(true);

        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(this, task -> {

                    showLoading(false);

                    if (task.isSuccessful()) {

                        Toast.makeText(
                                Login.this,
                                "Password reset email sent",
                                Toast.LENGTH_LONG
                        ).show();

                    } else {

                        String message =
                                "Unable to send reset email";

                        if (task.getException() != null) {
                            message =
                                    task.getException().getMessage();
                        }

                        Toast.makeText(
                                Login.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private void showLoading(boolean loading) {

        if (progressBar != null) {
            progressBar.setVisibility(
                    loading
                            ? View.VISIBLE
                            : View.GONE
            );
        }

        if (btnGoLogin != null) {
            btnGoLogin.setEnabled(!loading);
        }

        if (btnCreateAccount != null) {
            btnCreateAccount.setEnabled(!loading);
        }

        if (textSignUp != null) {
            textSignUp.setEnabled(!loading);
        }

        if (forgotPass != null) {
            forgotPass.setEnabled(!loading);
        }

        if (btnLoginTab != null) {
            btnLoginTab.setEnabled(!loading);
        }

        if (btnGoogleLogin != null) {
            btnGoogleLogin.setEnabled(!loading);
        }
    }
}