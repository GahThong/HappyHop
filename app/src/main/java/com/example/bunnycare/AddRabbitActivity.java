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

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddRabbitActivity extends AppCompatActivity {

    private ImageButton btnUploadPhoto;

    private Button btnGenerateSummary;
    private Button btnSaveRabbit;

    private EditText editRabbitName;
    private EditText editBreed;
    private EditText editAge;
    private EditText editLastFed;
    private EditText editLastDrink;

    private TextView txtAiSummary;
    private TextView txtWeightTrend;

    private ProgressBar progressAiSummary;
    private View weightTrendCard;

    private FirebaseFirestore db;
    private DocumentReference rabbitRef;

    private final ExecutorService geminiExecutor =
            Executors.newSingleThreadExecutor();

    private final SimpleDateFormat displayFormat =
            new SimpleDateFormat(
                    "MMM d, yyyy h:mm a",
                    Locale.getDefault()
            );

    private String photoUrl = null;
    private Bitmap photoBitmap = null;
    private String weightValue = "";

    private Long lastFedMillis = null;
    private Long lastDrinkMillis = null;

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {

                        if (result.getResultCode() != RESULT_OK) {
                            return;
                        }

                        if (result.getData() == null) {
                            return;
                        }

                        Uri uri = result.getData().getData();

                        if (uri == null) {
                            return;
                        }

                        photoBitmap = decodeUri(uri);

                        uploadImageToCloudinary(uri);
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_add_rabbit);

        db = FirebaseFirestore.getInstance();

        rabbitRef =
                db.collection("rabbits").document();

        bindViews();
        setListeners();
    }

    private void bindViews() {

        btnUploadPhoto =
                findViewById(R.id.btnUploadPhoto);

        btnGenerateSummary =
                findViewById(R.id.btnGenerateSummary);

        btnSaveRabbit =
                findViewById(R.id.btnSaveRabbit);

        editRabbitName =
                findViewById(R.id.editRabbitName);

        editBreed =
                findViewById(R.id.editBreed);

        editAge =
                findViewById(R.id.editAge);

        editLastFed =
                findViewById(R.id.editLastFed);

        editLastDrink =
                findViewById(R.id.editLastDrink);

        txtAiSummary =
                findViewById(R.id.txtAiSummary);

        txtWeightTrend =
                findViewById(R.id.txtWeightTrend);

        progressAiSummary =
                findViewById(R.id.progressAiSummary);

        weightTrendCard =
                findViewById(R.id.weightTrendCard);
    }

    private void setListeners() {

        btnUploadPhoto.setOnClickListener(v ->
                pickPhoto()
        );

        editLastFed.setOnClickListener(v ->
                showDateTimePicker(millis -> {

                    lastFedMillis = millis;

                    editLastFed.setText(
                            displayFormat.format(millis)
                    );
                })
        );

        editLastDrink.setOnClickListener(v ->
                showDateTimePicker(millis -> {

                    lastDrinkMillis = millis;

                    editLastDrink.setText(
                            displayFormat.format(millis)
                    );
                })
        );

        weightTrendCard.setOnClickListener(v ->
                showWeightDialog()
        );

        btnGenerateSummary.setOnClickListener(v ->
                generateAiSummary()
        );

        btnSaveRabbit.setOnClickListener(v ->
                saveRabbit()
        );
    }

    private void pickPhoto() {

        Intent intent =
                new Intent(Intent.ACTION_PICK);

        intent.setType("image/*");

        imagePickerLauncher.launch(intent);
    }

    private Bitmap decodeUri(Uri uri) {

        try (
                InputStream input =
                        getContentResolver()
                                .openInputStream(uri)
        ) {

            return BitmapFactory.decodeStream(input);

        } catch (Exception e) {

            Log.e(
                    "AddRabbit",
                    "Failed to decode image",
                    e
            );

            return null;
        }
    }

    private void uploadImageToCloudinary(Uri uri) {

        Toast.makeText(
                this,
                "Uploading photo...",
                Toast.LENGTH_SHORT
        ).show();

        MediaManager.get()
                .upload(uri)
                .unsigned("ml_default")
                .callback(
                        new UploadCallback() {

                            @Override
                            public void onStart(
                                    String requestId
                            ) {
                            }

                            @Override
                            public void onProgress(
                                    String requestId,
                                    long bytes,
                                    long totalBytes
                            ) {
                            }

                            @Override
                            public void onSuccess(
                                    String requestId,
                                    Map resultData
                            ) {

                                String secureUrl =
                                        (String) resultData.get(
                                                "secure_url"
                                        );

                                runOnUiThread(() -> {

                                    photoUrl =
                                            secureUrl;

                                    Toast.makeText(
                                            AddRabbitActivity.this,
                                            "Photo uploaded",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                });
                            }

                            @Override
                            public void onError(
                                    String requestId,
                                    ErrorInfo error
                            ) {

                                runOnUiThread(() ->
                                        Toast.makeText(
                                                AddRabbitActivity.this,
                                                "Upload failed: "
                                                        + error.getDescription(),
                                                Toast.LENGTH_LONG
                                        ).show()
                                );
                            }

                            @Override
                            public void onReschedule(
                                    String requestId,
                                    ErrorInfo error
                            ) {
                            }
                        }
                )
                .dispatch();
    }

    private void showWeightDialog() {

        EditText input =
                new EditText(this);

        input.setHint("e.g. 1.9 kg");
        input.setSingleLine(true);

        if (!weightValue.isEmpty()) {

            input.setText(weightValue);

            input.setSelection(
                    input.getText().length()
            );
        }

        new AlertDialog.Builder(this)
                .setTitle("Current Weight")
                .setView(input)
                .setPositiveButton(
                        "Save",
                        (dialog, which) -> {

                            String weight =
                                    input.getText()
                                            .toString()
                                            .trim();

                            if (!weight.isEmpty()) {

                                weightValue =
                                        weight;

                                txtWeightTrend.setText(
                                        weight
                                );
                            }
                        }
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    private interface OnDateTimePicked {
        void onPicked(long millis);
    }

    private void showDateTimePicker(
            OnDateTimePicked callback
    ) {

        Calendar now =
                Calendar.getInstance();

        DatePickerDialog datePicker =
                new DatePickerDialog(
                        this,
                        (view, year, month, dayOfMonth) -> {

                            Calendar date =
                                    Calendar.getInstance();

                            date.set(
                                    Calendar.YEAR,
                                    year
                            );

                            date.set(
                                    Calendar.MONTH,
                                    month
                            );

                            date.set(
                                    Calendar.DAY_OF_MONTH,
                                    dayOfMonth
                            );

                            TimePickerDialog timePicker =
                                    new TimePickerDialog(
                                            this,
                                            (timeView,
                                             hourOfDay,
                                             minute) -> {

                                                date.set(
                                                        Calendar.HOUR_OF_DAY,
                                                        hourOfDay
                                                );

                                                date.set(
                                                        Calendar.MINUTE,
                                                        minute
                                                );

                                                date.set(
                                                        Calendar.SECOND,
                                                        0
                                                );

                                                callback.onPicked(
                                                        date.getTimeInMillis()
                                                );
                                            },
                                            now.get(
                                                    Calendar.HOUR_OF_DAY
                                            ),
                                            now.get(
                                                    Calendar.MINUTE
                                            ),
                                            false
                                    );

                            timePicker.show();
                        },
                        now.get(Calendar.YEAR),
                        now.get(Calendar.MONTH),
                        now.get(Calendar.DAY_OF_MONTH)
                );

        datePicker.show();
    }

    private void generateAiSummary() {

        String name =
                editRabbitName
                        .getText()
                        .toString()
                        .trim();

        if (name.isEmpty()) {

            editRabbitName.setError(
                    "Required"
            );

            editRabbitName.requestFocus();

            return;
        }

        progressAiSummary.setVisibility(
                View.VISIBLE
        );

        btnGenerateSummary.setEnabled(
                false
        );

        btnGenerateSummary.setText(
                "Generating..."
        );

        txtAiSummary.setText(
                "Gemini is analyzing your rabbit..."
        );

        String breed =
                editBreed
                        .getText()
                        .toString()
                        .trim();

        String age =
                editAge
                        .getText()
                        .toString()
                        .trim();

        String lastFed =
                editLastFed
                        .getText()
                        .toString()
                        .trim();

        String lastDrink =
                editLastDrink
                        .getText()
                        .toString()
                        .trim();

        geminiExecutor.execute(() ->
                sendToGemini(
                        name,
                        breed,
                        age,
                        weightValue,
                        lastFed,
                        lastDrink,
                        photoBitmap
                )
        );
    }

    private void sendToGemini(
            String name,
            String breed,
            String age,
            String weight,
            String lastFed,
            String lastDrink,
            @Nullable Bitmap photo
    ) {

        try {

            GenerativeModel firebaseAI =
                    FirebaseAI.getInstance(
                            GenerativeBackend.googleAI()
                    ).generativeModel(
                            "gemini-3.5-flash-lite"
                    );

            GenerativeModelFutures model =
                    GenerativeModelFutures.from(
                            firebaseAI
                    );

            StringBuilder prompt =
                    new StringBuilder();

            prompt.append(
                    "You are a rabbit care expert. "
                            + "Create a short practical AI health summary "
                            + "for a pet rabbit named \""
                            + name
                            + "\"."
            );

            if (!breed.isEmpty()) {

                prompt.append(
                        " Breed: "
                ).append(
                        breed
                ).append(".");
            }

            if (!age.isEmpty()) {

                prompt.append(
                        " Age: "
                ).append(
                        age
                ).append(".");
            }

            if (!weight.isEmpty()) {

                prompt.append(
                        " Current weight: "
                ).append(
                        weight
                ).append(".");
            }

            if (!lastFed.isEmpty()) {

                prompt.append(
                        " Last fed: "
                ).append(
                        lastFed
                ).append(".");
            }

            if (!lastDrink.isEmpty()) {

                prompt.append(
                        " Last water: "
                ).append(
                        lastDrink
                ).append(".");
            }

            prompt.append(
                    " Give a concise summary covering general condition, "
                            + "feeding, hydration, weight monitoring, "
                            + "and important things the owner should watch for."
            );

            prompt.append(
                    " Do not diagnose diseases."
            );

            prompt.append(
                    " If something seems concerning, recommend consulting "
                            + "a qualified rabbit veterinarian."
            );

            prompt.append(
                    " Keep the response short enough to display inside "
                            + "a mobile app."
            );

            prompt.append(
                    " Use plain text and no JSON."
            );

            Content.Builder contentBuilder =
                    new Content.Builder()
                            .addText(
                                    prompt.toString()
                            );

            if (photo != null) {

                contentBuilder.addImage(
                        photo
                );
            }

            Content content =
                    contentBuilder.build();

            ListenableFuture<GenerateContentResponse>
                    response =
                    model.generateContent(
                            content
                    );

            Futures.addCallback(
                    response,
                    new FutureCallback<GenerateContentResponse>() {

                        @Override
                        public void onSuccess(
                                GenerateContentResponse result
                        ) {

                            String text =
                                    result.getText();

                            runOnUiThread(() -> {

                                progressAiSummary
                                        .setVisibility(
                                                View.GONE
                                        );

                                btnGenerateSummary
                                        .setEnabled(
                                                true
                                        );

                                btnGenerateSummary
                                        .setText(
                                                "Generate AI Summary"
                                        );

                                if (text != null
                                        && !text.trim().isEmpty()) {

                                    txtAiSummary.setText(
                                            text
                                    );

                                } else {

                                    txtAiSummary.setText(
                                            "No summary was generated."
                                    );
                                }
                            });
                        }

                        @Override
                        public void onFailure(
                                Throwable t
                        ) {

                            Log.e(
                                    "AddRabbit",
                                    "Gemini summary failed",
                                    t
                            );

                            runOnUiThread(() -> {

                                progressAiSummary
                                        .setVisibility(
                                                View.GONE
                                        );

                                btnGenerateSummary
                                        .setEnabled(
                                                true
                                        );

                                btnGenerateSummary
                                        .setText(
                                                "Generate AI Summary"
                                        );

                                txtAiSummary.setText(
                                        "Couldn't generate a summary."
                                );

                                Toast.makeText(
                                        AddRabbitActivity.this,
                                        "AI error: "
                                                + t.getMessage(),
                                        Toast.LENGTH_LONG
                                ).show();
                            });
                        }
                    },
                    geminiExecutor
            );

        } catch (Exception e) {

            Log.e(
                    "AddRabbit",
                    "Gemini error",
                    e
            );

            runOnUiThread(() -> {

                progressAiSummary
                        .setVisibility(
                                View.GONE
                        );

                btnGenerateSummary
                        .setEnabled(
                                true
                        );

                btnGenerateSummary
                        .setText(
                                "Generate AI Summary"
                        );

                txtAiSummary.setText(
                        "Couldn't generate a summary."
                );

                Toast.makeText(
                        AddRabbitActivity.this,
                        "AI error: " + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            });
        }
    }

    private void saveRabbit() {

        String name =
                editRabbitName
                        .getText()
                        .toString()
                        .trim();

        String breed =
                editBreed
                        .getText()
                        .toString()
                        .trim();

        String age =
                editAge
                        .getText()
                        .toString()
                        .trim();

        if (name.isEmpty()) {

            editRabbitName.setError(
                    "Required"
            );

            editRabbitName.requestFocus();

            return;
        }

        if (FirebaseAuth
                .getInstance()
                .getCurrentUser() == null) {

            Toast.makeText(
                    this,
                    "Not signed in",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String uid =
                FirebaseAuth
                        .getInstance()
                        .getCurrentUser()
                        .getUid();

        Map<String, Object> map =
                new HashMap<>();

        map.put(
                "rabbitName",
                name
        );

        map.put(
                "breed",
                breed
        );

        map.put(
                "age",
                age
        );

        map.put(
                "weight",
                weightValue
        );

        map.put(
                "lastFed",
                lastFedMillis != null
                        ? displayFormat.format(
                        lastFedMillis
                )
                        : editLastFed
                        .getText()
                        .toString()
                        .trim()
        );

        map.put(
                "lastDrink",
                lastDrinkMillis != null
                        ? displayFormat.format(
                        lastDrinkMillis
                )
                        : editLastDrink
                        .getText()
                        .toString()
                        .trim()
        );

        map.put(
                "imageUrl",
                photoUrl
        );

        map.put(
                "ownerId",
                uid
        );

        String aiSummary =
                txtAiSummary
                        .getText()
                        .toString()
                        .trim();

        if (!aiSummary.isEmpty()
                && !aiSummary.equals(
                "Generate a summary to get personalized care recommendations."
        )
                && !aiSummary.equals(
                "Gemini is analyzing your rabbit..."
        )
                && !aiSummary.equals(
                "Couldn't generate a summary."
        )) {

            map.put(
                    "aiSummary",
                    aiSummary
            );
        }

        map.put(
                "createdAt",
                FieldValue.serverTimestamp()
        );

        btnSaveRabbit.setEnabled(
                false
        );

        btnSaveRabbit.setText(
                "Saving..."
        );

        rabbitRef
                .set(map)
                .addOnSuccessListener(unused -> {

                    Toast.makeText(
                            this,
                            "Rabbit saved",
                            Toast.LENGTH_SHORT
                    ).show();

                    Intent resultIntent =
                            new Intent();

                    resultIntent.putExtra(
                            "newRabbitId",
                            rabbitRef.getId()
                    );

                    setResult(
                            RESULT_OK,
                            resultIntent
                    );

                    finish();
                })
                .addOnFailureListener(e -> {

                    btnSaveRabbit.setEnabled(
                            true
                    );

                    btnSaveRabbit.setText(
                            "Save Rabbit"
                    );

                    Toast.makeText(
                            this,
                            "Save failed: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        geminiExecutor.shutdown();
    }
}