package com.example.bunnycare;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
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
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import org.jetbrains.annotations.UnknownNullability;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Login extends AppCompatActivity {

    private EditText username;
    private EditText password;

    private Button btnGoLogin;
    private ImageButton btnGoogleLogin;

    private TextView btnLoginTab;
    private TextView btnCreateAccount;
    private TextView forgotPass;

    private ProgressBar progressBar;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private CredentialManager credentialManager;
    private CancellationSignal cancellationSignal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        credentialManager = CredentialManager.create(this);

        username = findViewById(R.id.username);
        password = findViewById(R.id.password);

        btnGoLogin = findViewById(R.id.btnGoLogin);
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);

        btnLoginTab = findViewById(R.id.btnLoginTab);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);

        forgotPass = findViewById(R.id.forgotPass);

        progressBar = findViewById(R.id.progressBar);

        if (btnGoLogin != null) {
            btnGoLogin.setOnClickListener(v -> loginUser());
        }

        if (btnCreateAccount != null) {
            btnCreateAccount.setOnClickListener(v -> {
                Intent intent = new Intent(Login.this, Register.class);
                startActivity(intent);
                finish();
            });
        }

        if (forgotPass != null) {
            forgotPass.setOnClickListener(v -> resetPassword());
        }

        if (btnGoogleLogin != null) {
            btnGoogleLogin.setOnClickListener(v -> signInWithGoogle());
        }
    }

    private void signInWithGoogle() {

        showLoading(true);

        GetGoogleIdOption googleIdOption =
                new GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId("396693874608-lpuh7f8ed8hl5o0vskcph3bv1s2692t0.apps.googleusercontent.com")
                        .build();

        GetCredentialRequest request =
                new GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build();

        cancellationSignal = new CancellationSignal();

        Executor executor = ContextCompat.getMainExecutor(this);

        credentialManager.getCredentialAsync(
                this,
                request,
                cancellationSignal,
                executor,
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {

                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleSignIn(result.getCredential());
                    }

                    @Override
                    public void onError(GetCredentialException e) {

                        showLoading(false);

                        android.util.Log.e("CredentialManager", "Google Sign-In failed", e);

                        if (e instanceof NoCredentialException) {
                            Toast.makeText(
                                    Login.this,
                                    "No Google account found on this device. Please add one in Settings.",
                                    Toast.LENGTH_LONG
                            ).show();
                        } else {
                            Toast.makeText(
                                    Login.this,
                                    "Google Sign-In failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
                }
        );
    }

    private void handleSignIn(Credential credential) {

        if (credential instanceof CustomCredential
                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                .equals(((CustomCredential) credential).getType())) {

            try {
                GoogleIdTokenCredential googleIdTokenCredential =
                        GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());

                firebaseAuthWithGoogle(googleIdTokenCredential);

            } catch (Exception e) {
                showLoading(false);
                android.util.Log.e("CredentialManager", "Failed to parse Google ID token", e);
                Toast.makeText(Login.this, "Google Sign-In Failed", Toast.LENGTH_SHORT).show();
            }

        } else {
            showLoading(false);
            Toast.makeText(Login.this, "Unexpected credential type", Toast.LENGTH_SHORT).show();
        }
    }

    private void firebaseAuthWithGoogle(GoogleIdTokenCredential googleIdTokenCredential) {

        AuthCredential firebaseCredential =
                GoogleAuthProvider.getCredential(googleIdTokenCredential.getIdToken(), null);

        auth.signInWithCredential(firebaseCredential)
                .addOnCompleteListener(this, task -> {

                    if (task.isSuccessful()) {

                        FirebaseUser user = auth.getCurrentUser();

                        if (user != null) {

                            boolean isNewUser =
                                    task.getResult().getAdditionalUserInfo() != null
                                            && task.getResult().getAdditionalUserInfo().isNewUser();

                            String uid = user.getUid();

                            if (isNewUser) {

                                Map<String, Object> userMap = new HashMap<>();
                                userMap.put("firstName", googleIdTokenCredential.getGivenName());
                                userMap.put("lastName", googleIdTokenCredential.getFamilyName());
                                userMap.put("username", googleIdTokenCredential.getDisplayName());
                                userMap.put("email", googleIdTokenCredential.getId());
                                userMap.put("verified", true);

                                db.collection("users")
                                        .document(uid)
                                        .set(userMap)
                                        .addOnCompleteListener(saveTask -> {
                                            showLoading(false);
                                            openHome();
                                        });

                            } else {
                                showLoading(false);
                                openHome();
                            }

                        } else {
                            showLoading(false);
                            Toast.makeText(Login.this, "Unable to get Google account", Toast.LENGTH_LONG).show();
                        }

                    } else {

                        showLoading(false);

                        String message = "Google Sign-In Failed";

                        if (task.getException() != null) {
                            message = task.getException().getMessage();
                            android.util.Log.e("GoogleSignIn", "Firebase auth failed", task.getException());
                        }

                        Toast.makeText(Login.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void openHome() {
        Intent intent = new Intent(Login.this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
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

                    if (task.isSuccessful()) {

                        FirebaseUser firebaseUser = auth.getCurrentUser();

                        if (firebaseUser == null) {
                            showLoading(false);
                            Toast.makeText(Login.this, "Login failed", Toast.LENGTH_LONG).show();
                            return;
                        }

                        firebaseUser.reload().addOnCompleteListener(reloadTask -> {

                            if (!firebaseUser.isEmailVerified()) {

                                firebaseUser.sendEmailVerification()
                                        .addOnCompleteListener(verifyTask -> {
                                            showLoading(false);
                                            auth.signOut();
                                            Toast.makeText(
                                                    Login.this,
                                                    "Please verify your email. A new verification link has been sent.",
                                                    Toast.LENGTH_LONG
                                            ).show();
                                        });

                                return;
                            }

                            showLoading(false);
                            Toast.makeText(Login.this, "Login successful", Toast.LENGTH_SHORT).show();
                            openHome();
                        });

                    } else {

                        showLoading(false);

                        String message = "Login failed";
                        if (task.getException() != null) {
                            message += task.getException().getMessage();
                        }

                        Toast.makeText(Login.this, message, Toast.LENGTH_LONG).show();
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
                        Toast.makeText(Login.this, "Password reset email sent", Toast.LENGTH_LONG).show();
                    } else {
                        String message = "Unable to send reset email";
                        if (task.getException() != null) {
                            message = task.getException().getMessage();
                        }
                        Toast.makeText(Login.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    public void signOut() {

        auth.signOut();

        ClearCredentialStateRequest clearRequest = new ClearCredentialStateRequest();

        credentialManager.clearCredentialStateAsync(
                clearRequest,
                new CancellationSignal(),
                Executors.newSingleThreadExecutor(),
                new CredentialManagerCallback<Void, ClearCredentialException>() {

                    @Override
                    public void onResult(Void result) {
                        Intent intent = new Intent(Login.this, Login.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onError(ClearCredentialException e) {
                        android.util.Log.e("CredentialManager", "Couldn't clear credential state", e);
                    }
                }
        );
    }

    @Override
    protected void onDestroy() {
        if (cancellationSignal != null) {
            cancellationSignal.cancel();
        }
        super.onDestroy();
    }

    private void showLoading(boolean loading) {

        if (progressBar != null) progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnGoLogin != null) btnGoLogin.setEnabled(!loading);
        if (btnCreateAccount != null) btnCreateAccount.setEnabled(!loading);
        if (forgotPass != null) forgotPass.setEnabled(!loading);
        if (btnLoginTab != null) btnLoginTab.setEnabled(!loading);
        if (btnGoogleLogin != null) btnGoogleLogin.setEnabled(!loading);
    }
}