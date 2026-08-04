package com.example.bunnycare;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AddRabbitActivity extends AppCompatActivity {

    private ImageButton btnBack, btnUploadPhoto, btnUploadQr;
    private EditText editRabbitName, editAge, editWeight, editLastFed, editLastDrink;
    private TextView txtAiSummary;
    private ProgressBar progressAiSummary;
    private Button btnGenerateSummary, btnSaveRabbit;

    private FirebaseFirestore db;
    private DocumentReference rabbitRef;
    private final Executor geminiExecutor = Executors.newSingleThreadExecutor();

    private final SimpleDateFormat displayFormat =
            new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());

    private String photoUrl = null;
    private Bitmap photoBitmap = null;
    private String qrImageUrl = null;
    private boolean hasQr = false;

    private Long lastFedMillis = null;
    private Long lastDrinkMillis = null;

    private static final int TARGET_PHOTO = 1;
    private static final int TARGET_QR = 2;
    private int pendingImageTarget = 0;

    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_rabbit);

        db = FirebaseFirestore.getInstance();
        rabbitRef = db.collection("rabbits").document();

        bindViews();
        registerImagePicker();
        setListeners();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btnBack);
        btnUploadPhoto = findViewById(R.id.btnUploadPhoto);
        btnUploadQr = findViewById(R.id.btnUploadQr);
        editRabbitName = findViewById(R.id.editRabbitName);
        editAge = findViewById(R.id.editAge);
        editWeight = findViewById(R.id.editWeight);
        editLastFed = findViewById(R.id.editLastFed);
        editLastDrink = findViewById(R.id.editLastDrink);
        txtAiSummary = findViewById(R.id.txtAiSummary);
        progressAiSummary = findViewById(R.id.progressAiSummary);
        btnGenerateSummary = findViewById(R.id.btnGenerateSummary);
        btnSaveRabbit = findViewById(R.id.btnSaveRabbit);
    }

    private void setListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnUploadPhoto.setOnClickListener(v -> pickImage(TARGET_PHOTO));

        btnUploadQr.setOnClickListener(v -> showQrMenu());

        editLastFed.setOnClickListener(v -> showDateTimePicker(millis -> {
            lastFedMillis = millis;
            editLastFed.setText(displayFormat.format(millis));
        }));

        editLastDrink.setOnClickListener(v -> showDateTimePicker(millis -> {
            lastDrinkMillis = millis;
            editLastDrink.setText(displayFormat.format(millis));
        }));

        btnGenerateSummary.setOnClickListener(v -> generateAiSummary());

        btnSaveRabbit.setOnClickListener(v -> saveRabbit());
    }

    private void registerImagePicker() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                    Uri uri = result.getData().getData();
                    if (uri == null) return;

                    if (pendingImageTarget == TARGET_PHOTO) {
                        photoBitmap = decodeUri(uri);
                        uploadImageToCloudinary(uri, TARGET_PHOTO);
                    } else if (pendingImageTarget == TARGET_QR) {
                        uploadImageToCloudinary(uri, TARGET_QR);
                    }
                });
    }

    private void pickImage(int target) {
        pendingImageTarget = target;
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private Bitmap decodeUri(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(input);
        } catch (Exception e) {
            Log.e("AddRabbit", "Failed to decode picked image", e);
            return null;
        }
    }

    private void uploadImageToCloudinary(Uri uri, int target) {
        Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show();

        MediaManager.get()
                .upload(uri)
                .unsigned("ml_default")
                .callback(new UploadCallback() {
                    @Override public void onStart(String requestId) {}
                    @Override public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String secureUrl = (String) resultData.get("secure_url");
                        runOnUiThread(() -> {
                            if (target == TARGET_PHOTO) {
                                photoUrl = secureUrl;
                                Toast.makeText(AddRabbitActivity.this, "Photo uploaded", Toast.LENGTH_SHORT).show();
                            } else if (target == TARGET_QR) {
                                qrImageUrl = secureUrl;
                                hasQr = secureUrl != null && !secureUrl.isEmpty();
                                Toast.makeText(AddRabbitActivity.this, "QR image uploaded", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        runOnUiThread(() -> Toast.makeText(AddRabbitActivity.this,
                                "Upload failed: " + error.getDescription(), Toast.LENGTH_LONG).show());
                    }

                    @Override public void onReschedule(String requestId, ErrorInfo error) {}
                }).dispatch();
    }

    private void showQrMenu() {
        String[] options = {"Generate QR", "Scan QR", "Upload QR Image"};
        new AlertDialog.Builder(this)
                .setTitle("QR Options")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(this, QRActivity.class);
                        intent.putExtra("data", rabbitRef.getId());
                        startActivity(intent);
                    } else if (which == 1) {
                        new IntentIntegrator(this).initiateScan();
                    } else if (which == 2) {
                        pickImage(TARGET_QR);
                    }
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            Toast.makeText(this, "Scanned: " + result.getContents(), Toast.LENGTH_LONG).show();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private interface OnDateTimePicked {
        void onPicked(long millis);
    }

    private void showDateTimePicker(OnDateTimePicked callback) {
        Calendar now = Calendar.getInstance();

        DatePickerDialog datePicker = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    Calendar date = Calendar.getInstance();
                    date.set(Calendar.YEAR, year);
                    date.set(Calendar.MONTH, month);
                    date.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    TimePickerDialog timePicker = new TimePickerDialog(this,
                            (timeView, hourOfDay, minute) -> {
                                date.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                date.set(Calendar.MINUTE, minute);
                                date.set(Calendar.SECOND, 0);
                                callback.onPicked(date.getTimeInMillis());
                            },
                            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false);
                    timePicker.show();
                },
                now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));

        datePicker.show();
    }

    private void generateAiSummary() {
        String name = editRabbitName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Enter a name first", Toast.LENGTH_SHORT).show();
            return;
        }

        progressAiSummary.setVisibility(View.VISIBLE);
        btnGenerateSummary.setEnabled(false);
        txtAiSummary.setText("Generating...");

        String age = editAge.getText().toString().trim();
        String weight = editWeight.getText().toString().trim();
        String lastFed = editLastFed.getText().toString().trim();
        String lastDrink = editLastDrink.getText().toString().trim();

        geminiExecutor.execute(() -> sendToGemini(name, age, weight, lastFed, lastDrink, photoBitmap));
    }

    private void sendToGemini(String name, String age, String weight,
                              String lastFed, String lastDrink, @Nullable Bitmap photo) {

        GenerativeModel firebaseAI = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                .generativeModel("gemini-3.5-flash-lite");
        GenerativeModelFutures model = GenerativeModelFutures.from(firebaseAI);

        StringBuilder sb = new StringBuilder();
        sb.append("You are a rabbit care expert. Write a short, practical health summary and care ")
                .append("recommendation for a pet rabbit named \"").append(name).append("\".");
        if (!age.isEmpty()) sb.append(" Age: ").append(age).append(".");
        if (!weight.isEmpty()) sb.append(" Weight: ").append(weight).append(".");
        if (!lastFed.isEmpty()) sb.append(" Last fed: ").append(lastFed).append(".");
        if (!lastDrink.isEmpty()) sb.append(" Last had water: ").append(lastDrink).append(".");
        sb.append(" Keep it concise, plain text, no JSON.");

        Content.Builder contentBuilder = new Content.Builder().addText(sb.toString());
        if (photo != null) contentBuilder.addImage(photo);
        Content content = contentBuilder.build();

        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);
        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                runOnUiThread(() -> {
                    progressAiSummary.setVisibility(View.GONE);
                    btnGenerateSummary.setEnabled(true);
                    txtAiSummary.setText(result.getText());
                });
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e("AddRabbit", "Gemini summary failed", t);
                runOnUiThread(() -> {
                    progressAiSummary.setVisibility(View.GONE);
                    btnGenerateSummary.setEnabled(true);
                    txtAiSummary.setText("Couldn't generate a summary: " + t.getMessage());
                });
            }
        }, geminiExecutor);
    }

    private void saveRabbit() {
        String name = editRabbitName.getText().toString().trim();
        if (name.isEmpty()) {
            editRabbitName.setError("Required");
            return;
        }

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        Map<String, Object> map = new HashMap<>();
        map.put("rabbitName", name);
        map.put("age", editAge.getText().toString().trim());
        map.put("weight", editWeight.getText().toString().trim());
        map.put("lastFed", lastFedMillis != null ? displayFormat.format(lastFedMillis) : "");
        map.put("lastDrink", lastDrinkMillis != null ? displayFormat.format(lastDrinkMillis) : "");
        map.put("imageUrl", photoUrl);
        map.put("qrImage", qrImageUrl);
        map.put("hasQr", hasQr);
        map.put("ownerId", uid);
        map.put("createdAt", FieldValue.serverTimestamp());

        btnSaveRabbit.setEnabled(false);

        rabbitRef.set(map)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Rabbit saved", Toast.LENGTH_SHORT).show();

                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("newRabbitId", rabbitRef.getId());
                    setResult(RESULT_OK, resultIntent);

                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSaveRabbit.setEnabled(true);
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}