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
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

public class Register extends AppCompatActivity {

    private EditText firstName;
    private EditText lastName;
    private EditText username;
    private EditText email;

    private TextInputEditText password;
    private TextInputEditText repassword;

    private Button btnSignUp;
    private ProgressBar progressBar;

    private TextView btnLoginTab;
    private TextView btnCreateAccount;

    private ImageButton btnGoogleSignUp;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private CredentialManager credentialManager;
    private CancellationSignal cancellationSignal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        credentialManager = CredentialManager.create(this);

        firstName = findViewById(R.id.firstName);
        lastName = findViewById(R.id.lastName);
        username = findViewById(R.id.username);
        email = findViewById(R.id.email);

        password = findViewById(R.id.password);
        repassword = findViewById(R.id.repassword);

        btnSignUp = findViewById(R.id.btnSignUp);
        progressBar = findViewById(R.id.progressBar);

        btnLoginTab = findViewById(R.id.btnLoginTab);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);

        btnGoogleSignUp = findViewById(R.id.btnGoogleSignUp);

        if (btnSignUp != null) {
            btnSignUp.setOnClickListener(v -> validateInputs());
        }

        if (btnLoginTab != null) {
            btnLoginTab.setOnClickListener(v -> {
                Intent intent = new Intent(Register.this, Login.class);
                startActivity(intent);
                finish();
            });
        }

        if (btnGoogleSignUp != null) {
            btnGoogleSignUp.setOnClickListener(v -> signInWithGoogle());
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

                        android.util.Log.e(
                                "CredentialManager",
                                "Google Sign-Up failed",
                                e
                        );

                        if (e instanceof NoCredentialException) {
                            Toast.makeText(
                                    Register.this,
                                    "No Google account found. Please add a Google account to your device.",
                                    Toast.LENGTH_LONG
                            ).show();
                        } else {
                            String message = e.getMessage();

                            if (TextUtils.isEmpty(message)) {
                                message = "Google Sign-Up failed";
                            }

                            Toast.makeText(
                                    Register.this,
                                    message,
                                    Toast.LENGTH_LONG
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
                        GoogleIdTokenCredential.createFrom(
                                ((CustomCredential) credential).getData()
                        );

                firebaseAuthWithGoogle(googleIdTokenCredential);

            } catch (Exception e) {
                showLoading(false);

                android.util.Log.e(
                        "CredentialManager",
                        "Failed to parse Google ID token",
                        e
                );

                Toast.makeText(
                        Register.this,
                        "Unable to process Google account",
                        Toast.LENGTH_LONG
                ).show();
            }

        } else {
            showLoading(false);

            Toast.makeText(
                    Register.this,
                    "Unexpected Google credential",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void firebaseAuthWithGoogle(
            GoogleIdTokenCredential googleIdTokenCredential
    ) {

        String idToken = googleIdTokenCredential.getIdToken();

        if (TextUtils.isEmpty(idToken)) {
            showLoading(false);

            Toast.makeText(
                    Register.this,
                    "Google ID token is missing",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        AuthCredential firebaseCredential =
                GoogleAuthProvider.getCredential(idToken, null);

        mAuth.signInWithCredential(firebaseCredential)
                .addOnCompleteListener(this, task -> {

                    if (!task.isSuccessful()) {
                        showLoading(false);

                        String message = "Google Authentication Failed";

                        if (task.getException() != null
                                && !TextUtils.isEmpty(task.getException().getMessage())) {
                            message = task.getException().getMessage();
                        }

                        Toast.makeText(
                                Register.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    FirebaseUser user = mAuth.getCurrentUser();

                    if (user == null) {
                        showLoading(false);

                        Toast.makeText(
                                Register.this,
                                "Unable to get Google account",
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    boolean isNewUser =
                            task.getResult().getAdditionalUserInfo() != null
                                    && task.getResult()
                                    .getAdditionalUserInfo()
                                    .isNewUser();

                    String uid = user.getUid();

                    if (isNewUser) {

                        String givenName = googleIdTokenCredential.getGivenName();
                        String familyName = googleIdTokenCredential.getFamilyName();
                        String displayName = googleIdTokenCredential.getDisplayName();
                        String googleEmail = user.getEmail();

                        if (TextUtils.isEmpty(givenName)) {
                            givenName = "";
                        }

                        if (TextUtils.isEmpty(familyName)) {
                            familyName = "";
                        }

                        if (TextUtils.isEmpty(displayName)) {
                            displayName = givenName + familyName;
                        }

                        if (TextUtils.isEmpty(displayName)) {
                            displayName = "Google User";
                        }

                        if (TextUtils.isEmpty(googleEmail)) {
                            googleEmail = "";
                        }

                        Map<String, Object> userMap = new HashMap<>();

                        userMap.put("firstName", givenName);
                        userMap.put("lastName", familyName);
                        userMap.put("username", displayName);
                        userMap.put("email", googleEmail);
                        userMap.put("verified", true);

                        db.collection("users")
                                .document(uid)
                                .set(userMap)
                                .addOnCompleteListener(saveTask -> {

                                    showLoading(false);

                                    if (saveTask.isSuccessful()) {
                                        openHome();
                                    } else {
                                        mAuth.signOut();

                                        Toast.makeText(
                                                Register.this,
                                                "Account created but user data could not be saved",
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }
                                });

                    } else {
                        showLoading(false);
                        openHome();
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

        firstName.setError(null);
        lastName.setError(null);
        username.setError(null);
        email.setError(null);
        password.setError(null);
        repassword.setError(null);

        if (TextUtils.isEmpty(fName)) {
            firstName.setError("Enter your first name");
            firstName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(lName)) {
            lastName.setError("Enter your last name");
            lastName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(user)) {
            username.setError("Enter a username");
            username.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(mail)) {
            email.setError("Enter your email");
            email.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(mail).matches()) {
            email.setError("Enter a valid email");
            email.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(pass)) {
            password.setError("Enter a password");
            password.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(rePass)) {
            repassword.setError("Re-enter your password");
            repassword.requestFocus();
            return;
        }

        if (!pass.equals(rePass)) {
            password.setError("Passwords do not match");
            repassword.setError("Passwords do not match");
            password.requestFocus();
            return;
        }

        if (!pass.matches("^(?=.*[A-Z])(?=.*[^a-zA-Z0-9]).{6,}$")) {
            password.setError("6+ chars, 1 uppercase, 1 special char");
            password.requestFocus();
            return;
        }

        showLoading(true);

        db.collection("users")
                .whereEqualTo("username", user)
                .get()
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {

                        if (!task.getResult().isEmpty()) {
                            showLoading(false);

                            username.setError("Username already taken");
                            username.requestFocus();

                            Toast.makeText(
                                    Register.this,
                                    "Username already exists",
                                    Toast.LENGTH_LONG
                            ).show();

                        } else {
                            createAccount(
                                    fName,
                                    lName,
                                    user,
                                    mail,
                                    pass
                            );
                        }

                    } else {
                        showLoading(false);

                        Toast.makeText(
                                Register.this,
                                "Unable to check username",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private void createAccount(
            String fName,
            String lName,
            String userName,
            String emailAddress,
            String passwordValue
    ) {

        mAuth.createUserWithEmailAndPassword(
                        emailAddress,
                        passwordValue
                )
                .addOnCompleteListener(task -> {

                    if (!task.isSuccessful()) {
                        showLoading(false);

                        String message = "Registration failed";

                        if (task.getException() != null
                                && !TextUtils.isEmpty(task.getException().getMessage())) {
                            message = task.getException().getMessage();
                        }

                        Toast.makeText(
                                Register.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    FirebaseUser firebaseUser = mAuth.getCurrentUser();

                    if (firebaseUser == null) {
                        showLoading(false);

                        Toast.makeText(
                                Register.this,
                                "Registration failed",
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    firebaseUser.sendEmailVerification();

                    String uid = firebaseUser.getUid();

                    Map<String, Object> userMap = new HashMap<>();

                    userMap.put("firstName", fName);
                    userMap.put("lastName", lName);
                    userMap.put("username", userName);
                    userMap.put("email", emailAddress);
                    userMap.put("verified", false);

                    db.collection("users")
                            .document(uid)
                            .set(userMap)
                            .addOnCompleteListener(saveTask -> {

                                showLoading(false);

                                if (saveTask.isSuccessful()) {

                                    Toast.makeText(
                                            Register.this,
                                            "Account created. Check your email for verification.",
                                            Toast.LENGTH_LONG
                                    ).show();

                                    mAuth.signOut();

                                    Intent intent =
                                            new Intent(
                                                    Register.this,
                                                    Login.class
                                            );

                                    intent.addFlags(
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                    | Intent.FLAG_ACTIVITY_NEW_TASK
                                    );

                                    startActivity(intent);
                                    finish();

                                } else {

                                    mAuth.signOut();

                                    Toast.makeText(
                                            Register.this,
                                            "Account created but user data could not be saved",
                                            Toast.LENGTH_LONG
                                    ).show();
                                }
                            });
                });
    }

    private void openHome() {

        Intent intent =
                new Intent(
                        Register.this,
                        HomeActivity.class
                );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK
        );

        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {

        if (cancellationSignal != null) {
            cancellationSignal.cancel();
        }

        super.onDestroy();
    }

    private void showLoading(boolean loading) {

        if (progressBar != null) {
            progressBar.setVisibility(
                    loading
                            ? View.VISIBLE
                            : View.GONE
            );
        }

        if (btnSignUp != null) {
            btnSignUp.setEnabled(!loading);
        }

        if (btnGoogleSignUp != null) {
            btnGoogleSignUp.setEnabled(!loading);
        }

        if (btnLoginTab != null) {
            btnLoginTab.setEnabled(!loading);
        }

        if (btnCreateAccount != null) {
            btnCreateAccount.setEnabled(!loading);
        }
    }
}