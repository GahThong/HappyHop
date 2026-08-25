package com.example.bunnycare;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddRabbitActivity extends AppCompatActivity {

    private ImageButton btnUploadPhoto;
    private ImageButton btnViewHistory;

    private Button btnGenerateSummary;
    private Button btnSaveRabbit;

    private EditText editRabbitName;
    private EditText editBreed;
    private EditText editAge;
    private EditText editLastFed;
    private EditText editLastDrink;

    private Spinner spinnerSex;

    private static final String[] SEX_OPTIONS = {"Male", "Female"};

    private TextView txtAiSummary;
    private TextView txtWeightTrend;

    private ProgressBar progressAiSummary;
    private View weightTrendCard;
    private LinearLayout weightChartContainer;

    private FirebaseFirestore db;
    private DocumentReference rabbitRef;

    private final ExecutorService geminiExecutor =
            Executors.newSingleThreadExecutor();

    private final SimpleDateFormat displayFormat =
            new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());

    private String photoUrl = null;
    private Bitmap photoBitmap = null;
    private String weightValue = "";
    private final List<WeightEntry> weightHistory = new ArrayList<>();
    private boolean weightEditedThisSession = false;

    private Long lastFedMillis = null;
    private Long lastDrinkMillis = null;

    private boolean editMode = false;
    private String editingRabbitId = null;

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

        bindViews();

        setListeners();

        Intent intent = getIntent();

        editMode = intent.getBooleanExtra("editMode", false);

        editingRabbitId = intent.getStringExtra("rabbitId");

        if (editMode && editingRabbitId != null && !editingRabbitId.isEmpty()) {

            rabbitRef = db.collection("rabbits").document(editingRabbitId);

            btnSaveRabbit.setText("Update Rabbit");

            loadRabbitForEditing();

        } else {

            rabbitRef = db.collection("rabbits").document();
        }
    }

    private void bindViews() {

        btnUploadPhoto = findViewById(R.id.btnUploadPhoto);
        btnViewHistory = findViewById(R.id.btnViewHistory);
        btnGenerateSummary = findViewById(R.id.btnGenerateSummary);
        btnSaveRabbit = findViewById(R.id.btnSaveRabbit);

        editRabbitName = findViewById(R.id.editRabbitName);
        editBreed = findViewById(R.id.editBreed);
        editAge = findViewById(R.id.editAge);
        editLastFed = findViewById(R.id.editLastFed);
        editLastDrink = findViewById(R.id.editLastDrink);

        spinnerSex = findViewById(R.id.spinnerSex);

        ArrayAdapter<String> sexAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                SEX_OPTIONS
        );

        sexAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinnerSex.setAdapter(sexAdapter);

        txtAiSummary = findViewById(R.id.txtAiSummary);
        txtWeightTrend = findViewById(R.id.txtWeightTrend);

        progressAiSummary = findViewById(R.id.progressAiSummary);
        weightTrendCard = findViewById(R.id.weightTrendCard);
        weightChartContainer = findViewById(R.id.weightChartContainer);
    }

    private void setListeners() {

        btnUploadPhoto.setOnClickListener(v -> pickPhoto());

        editLastFed.setOnClickListener(v ->
                showDateTimePicker(millis -> {
                    lastFedMillis = millis;
                    editLastFed.setText(displayFormat.format(millis));
                })
        );

        editLastDrink.setOnClickListener(v ->
                showDateTimePicker(millis -> {
                    lastDrinkMillis = millis;
                    editLastDrink.setText(displayFormat.format(millis));
                })
        );

        weightTrendCard.setOnClickListener(v -> showWeightDialog());

        btnGenerateSummary.setOnClickListener(v -> generateAiSummary());

        btnViewHistory.setOnClickListener(v -> showSummaryHistory());

        btnSaveRabbit.setOnClickListener(v -> saveRabbit());
    }

    private void loadRabbitForEditing() {

        btnSaveRabbit.setEnabled(false);

        db.collection("rabbits")
                .document(editingRabbitId)
                .get()
                .addOnSuccessListener(document -> {

                    if (!document.exists()) {

                        Toast.makeText(this, "Rabbit not found", Toast.LENGTH_LONG).show();

                        finish();

                        return;
                    }

                    loadRabbitData(document);

                    btnSaveRabbit.setEnabled(true);
                })
                .addOnFailureListener(e -> {

                    Toast.makeText(this, "Failed to load rabbit: " + e.getMessage(), Toast.LENGTH_LONG).show();

                    finish();
                });
    }

    @SuppressWarnings("unchecked")
    private void loadRabbitData(DocumentSnapshot document) {

        String name = document.getString("rabbitName");
        String breed = document.getString("breed");
        String age = document.getString("age");
        String sex = document.getString("sex");
        String weight = document.getString("weight");
        String lastFed = document.getString("lastFed");
        String lastDrink = document.getString("lastDrink");
        String imageUrl = document.getString("imageUrl");
        String aiSummary = document.getString("aiSummary");

        if (name != null) {
            editRabbitName.setText(name);
        }

        if (breed != null) {
            editBreed.setText(breed);
        }

        if (age != null) {
            editAge.setText(age);
        }

        if (sex != null) {

            for (int i = 0; i < SEX_OPTIONS.length; i++) {

                if (SEX_OPTIONS[i].equalsIgnoreCase(sex)) {

                    spinnerSex.setSelection(i);

                    break;
                }
            }
        }

        if (weight != null && !weight.isEmpty()) {
            weightValue = weight;
        }

        if (lastFed != null && !lastFed.isEmpty()) {
            editLastFed.setText(lastFed);
        }

        if (lastDrink != null && !lastDrink.isEmpty()) {
            editLastDrink.setText(lastDrink);
        }

        if (aiSummary != null && !aiSummary.isEmpty()) {
            txtAiSummary.setText(aiSummary);
        }

        if (imageUrl != null && !imageUrl.isEmpty()) {

            photoUrl = imageUrl;

            Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.rabbit_thumb_bg)
                    .error(R.drawable.rabbit_thumb_bg)
                    .into(btnUploadPhoto);
        }

        List<Map<String, Object>> historyRaw =
                (List<Map<String, Object>>) document.get("weightHistory");

        weightHistory.clear();

        if (historyRaw != null) {

            for (Map<String, Object> item : historyRaw) {

                Object valueObj = item.get("value");
                Object timeObj = item.get("timestamp");

                if (valueObj instanceof Number && timeObj instanceof Number) {

                    weightHistory.add(new WeightEntry(
                            ((Number) valueObj).doubleValue(),
                            ((Number) timeObj).longValue()
                    ));
                }
            }
        }

        renderWeightChart();
    }

    private void pickPhoto() {

        Intent intent = new Intent(Intent.ACTION_PICK);

        intent.setType("image/*");

        imagePickerLauncher.launch(intent);
    }

    private Bitmap decodeUri(Uri uri) {

        try (InputStream input = getContentResolver().openInputStream(uri)) {

            return BitmapFactory.decodeStream(input);

        } catch (Exception e) {

            Log.e("AddRabbit", "Failed to decode image", e);

            return null;
        }
    }

    private void uploadImageToCloudinary(Uri uri) {

        Toast.makeText(this, "Uploading photo...", Toast.LENGTH_SHORT).show();

        MediaManager.get()
                .upload(uri)
                .unsigned("ml_default")
                .callback(new UploadCallback() {

                    @Override
                    public void onStart(String requestId) {
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {

                        String secureUrl = (String) resultData.get("secure_url");

                        runOnUiThread(() -> {

                            photoUrl = secureUrl;

                            Toast.makeText(AddRabbitActivity.this, "Photo uploaded", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {

                        runOnUiThread(() ->
                                Toast.makeText(AddRabbitActivity.this, "Upload failed: " + error.getDescription(), Toast.LENGTH_LONG).show()
                        );
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                    }
                })
                .dispatch();
    }

    private void showWeightDialog() {

        EditText input = new EditText(this);

        input.setHint("e.g. 1.9 kg");

        input.setSingleLine(true);

        if (!weightValue.isEmpty()) {

            input.setText(weightValue);

            input.setSelection(input.getText().length());
        }

        new AlertDialog.Builder(this)
                .setTitle("Current Weight")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {

                    String weight = input.getText().toString().trim();

                    if (weight.isEmpty()) {
                        return;
                    }

                    String numericPart = weight.replaceAll("[^0-9.]", "");

                    if (numericPart.isEmpty()) {

                        Toast.makeText(this, "Enter a valid number", Toast.LENGTH_SHORT).show();

                        return;
                    }

                    try {

                        double parsed = Double.parseDouble(numericPart);

                        weightValue = weight;

                        if (weightEditedThisSession && !weightHistory.isEmpty()) {

                            WeightEntry pendingEntry = weightHistory.get(weightHistory.size() - 1);

                            pendingEntry.value = parsed;
                            pendingEntry.timestamp = System.currentTimeMillis();

                        } else {

                            weightHistory.add(new WeightEntry(parsed, System.currentTimeMillis()));

                            weightEditedThisSession = true;
                        }

                        renderWeightChart();

                    } catch (NumberFormatException e) {

                        Toast.makeText(this, "Enter a valid number", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void renderWeightChart() {

        weightChartContainer.removeAllViews();

        if (weightHistory.isEmpty()) {

            txtWeightTrend.setText(weightValue.isEmpty() ? "No data yet" : weightValue);

            return;
        }

        List<WeightEntry> sorted = new ArrayList<>(weightHistory);

        Collections.sort(sorted, (a, b) -> Long.compare(a.timestamp, b.timestamp));

        int start = Math.max(0, sorted.size() - 4);

        List<WeightEntry> recent = sorted.subList(start, sorted.size());

        double maxWeight = Collections.max(recent, Comparator.comparingDouble(e -> e.value)).value;
        double minWeight = Collections.min(recent, Comparator.comparingDouble(e -> e.value)).value;
        double range = Math.max(maxWeight - minWeight, 0.1);

        int maxHeightPx = dpToPx(90);

        for (int i = 0; i < recent.size(); i++) {

            WeightEntry entry = recent.get(i);

            float fraction = (float) (0.3 + 0.7 * ((entry.value - minWeight) / range));

            int barHeightPx = (int) (maxHeightPx * fraction);

            View bar = new View(this);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, barHeightPx, 1f);

            params.setMarginEnd(dpToPx(8));

            bar.setLayoutParams(params);

            boolean isLast = i == recent.size() - 1;
            boolean trendDown = isLast && i > 0 && entry.value < recent.get(i - 1).value;

            GradientDrawable bg = new GradientDrawable();

            bg.setColor(Color.parseColor((isLast && trendDown) ? "#C96A4D" : "#E0CBA8"));

            bg.setCornerRadii(new float[]{16, 16, 16, 16, 0, 0, 0, 0});

            bar.setBackground(bg);

            weightChartContainer.addView(bar);
        }

        WeightEntry latest = recent.get(recent.size() - 1);

        String arrow = "";

        if (recent.size() > 1) {

            double prev = recent.get(recent.size() - 2).value;

            arrow = latest.value < prev ? " \u2193" : (latest.value > prev ? " \u2191" : "");
        }

        txtWeightTrend.setText(latest.value + " kg" + arrow);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private interface OnDateTimePicked {
        void onPicked(long millis);
    }

    private void showDateTimePicker(OnDateTimePicked callback) {

        Calendar now = Calendar.getInstance();

        DatePickerDialog datePicker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {

                    Calendar date = Calendar.getInstance();

                    date.set(Calendar.YEAR, year);
                    date.set(Calendar.MONTH, month);
                    date.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    boolean isToday =
                            year == now.get(Calendar.YEAR)
                                    && month == now.get(Calendar.MONTH)
                                    && dayOfMonth == now.get(Calendar.DAY_OF_MONTH);

                    int maxHour = isToday ? now.get(Calendar.HOUR_OF_DAY) : 23;
                    int maxMinute = isToday ? now.get(Calendar.MINUTE) : 59;

                    TimePickerDialog timePicker = new TimePickerDialog(
                            this,
                            (timeView, hourOfDay, minute) -> {

                                date.set(Calendar.HOUR_OF_DAY, hourOfDay);
                                date.set(Calendar.MINUTE, minute);
                                date.set(Calendar.SECOND, 0);

                                long selectedMillis = date.getTimeInMillis();
                                long nowMillis = System.currentTimeMillis();

                                if (selectedMillis > nowMillis) {

                                    Toast.makeText(
                                            this,
                                            "Time cannot be in the future",
                                            Toast.LENGTH_SHORT
                                    ).show();

                                    selectedMillis = nowMillis;
                                }

                                callback.onPicked(selectedMillis);
                            },
                            maxHour,
                            maxMinute,
                            false
                    );

                    timePicker.show();
                },
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)
        );

        datePicker.getDatePicker().setMaxDate(now.getTimeInMillis());

        datePicker.show();
    }

    private void generateAiSummary() {

        String name = editRabbitName.getText().toString().trim();

        if (name.isEmpty()) {

            editRabbitName.setError("Required");

            editRabbitName.requestFocus();

            return;
        }

        progressAiSummary.setVisibility(View.VISIBLE);

        btnGenerateSummary.setEnabled(false);

        btnGenerateSummary.setText("Generating...");

        txtAiSummary.setText("Gemini is analyzing your rabbit...");

        String breed = editBreed.getText().toString().trim();
        String age = editAge.getText().toString().trim();
        String sex = spinnerSex.getSelectedItem() != null
                ? spinnerSex.getSelectedItem().toString()
                : "";
        String lastFed = editLastFed.getText().toString().trim();
        String lastDrink = editLastDrink.getText().toString().trim();

        geminiExecutor.execute(() ->
                sendToGemini(name, breed, age, sex, weightValue, lastFed, lastDrink, photoBitmap)
        );
    }

    private void sendToGemini(
            String name,
            String breed,
            String age,
            String sex,
            String weight,
            String lastFed,
            String lastDrink,
            @Nullable Bitmap photo
    ) {

        try {

            GenerativeModel firebaseAI = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                    .generativeModel("gemini-3.5-flash-lite");

            GenerativeModelFutures model = GenerativeModelFutures.from(firebaseAI);

            StringBuilder prompt = new StringBuilder();

            prompt.append("You are a rabbit care expert. Create a short practical AI health summary for a pet rabbit named \"")
                    .append(name).append("\".");

            if (!breed.isEmpty()) {
                prompt.append(" Breed: ").append(breed).append(".");
            }

            if (!age.isEmpty()) {
                prompt.append(" Age: ").append(age).append(".");
            }

            if (!sex.isEmpty()) {
                prompt.append(" Sex: ").append(sex).append(".");
            }

            if (!weight.isEmpty()) {
                prompt.append(" Current weight: ").append(weight).append(".");
            }

            if (!lastFed.isEmpty()) {
                prompt.append(" Last fed: ").append(lastFed).append(".");
            }

            if (!lastDrink.isEmpty()) {
                prompt.append(" Last water: ").append(lastDrink).append(".");
            }

            prompt.append(" Give a concise summary covering general condition, feeding, hydration, weight monitoring, and important things the owner should watch for.");
            prompt.append(" Do not diagnose diseases.");
            prompt.append(" If something seems concerning, recommend consulting a qualified rabbit veterinarian.");
            prompt.append(" Keep the response short enough to display inside a mobile app.");
            prompt.append(" Use plain text and no JSON.");

            Content.Builder contentBuilder = new Content.Builder().addText(prompt.toString());

            if (photo != null) {
                contentBuilder.addImage(photo);
            }

            Content content = contentBuilder.build();

            ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

            Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {

                @Override
                public void onSuccess(GenerateContentResponse result) {

                    String text = result.getText();

                    runOnUiThread(() -> {

                        progressAiSummary.setVisibility(View.GONE);

                        btnGenerateSummary.setEnabled(true);

                        btnGenerateSummary.setText("Generate AI Summary");

                        if (text != null && !text.trim().isEmpty()) {

                            txtAiSummary.setText(text);

                            saveSummaryToHistory(text);

                        } else {
                            txtAiSummary.setText("No summary was generated.");
                        }
                    });
                }

                @Override
                public void onFailure(Throwable t) {

                    Log.e("AddRabbit", "Gemini summary failed", t);

                    runOnUiThread(() -> {

                        progressAiSummary.setVisibility(View.GONE);

                        btnGenerateSummary.setEnabled(true);

                        btnGenerateSummary.setText("Generate AI Summary");

                        txtAiSummary.setText("Couldn't generate a summary.");

                        Toast.makeText(AddRabbitActivity.this, "AI error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }, geminiExecutor);

        } catch (Exception e) {

            Log.e("AddRabbit", "Gemini error", e);

            runOnUiThread(() -> {

                progressAiSummary.setVisibility(View.GONE);

                btnGenerateSummary.setEnabled(true);

                btnGenerateSummary.setText("Generate AI Summary");

                txtAiSummary.setText("Couldn't generate a summary.");

                Toast.makeText(AddRabbitActivity.this, "AI error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }

    /**
     * Saves a generated AI summary into the rabbit's summaries subcollection
     * so past summaries aren't lost when a new one is generated.
     */
    private void saveSummaryToHistory(String text) {

        if (rabbitRef == null) {
            return;
        }

        Map<String, Object> entry = new HashMap<>();

        entry.put("text", text);
        entry.put("timestamp", System.currentTimeMillis());

        rabbitRef.collection("summaries")
                .add(entry)
                .addOnFailureListener(e ->
                        Log.e("AddRabbit", "Failed to save summary history", e)
                );
    }

    /**
     * Loads past AI summaries for this rabbit (newest first) and lets the
     * user tap one to view it in full.
     */
    private void showSummaryHistory() {

        if (rabbitRef == null) {
            return;
        }

        rabbitRef.collection("summaries")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    if (querySnapshot.isEmpty()) {

                        new AlertDialog.Builder(this)
                                .setTitle("Summary History")
                                .setMessage("No previous AI summaries yet. Generate one to start building history.")
                                .setPositiveButton("OK", null)
                                .show();

                        return;
                    }

                    List<String> dates = new ArrayList<>();
                    List<String> texts = new ArrayList<>();

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {

                        String text = doc.getString("text");
                        Long timestamp = doc.getLong("timestamp");

                        if (text == null || timestamp == null) {
                            continue;
                        }

                        dates.add(displayFormat.format(timestamp));
                        texts.add(text);
                    }

                    if (dates.isEmpty()) {

                        Toast.makeText(this, "No history entries found", Toast.LENGTH_SHORT).show();

                        return;
                    }

                    CharSequence[] items = dates.toArray(new CharSequence[0]);

                    new AlertDialog.Builder(this)
                            .setTitle("Summary History")
                            .setItems(items, (dialog, which) ->
                                    new AlertDialog.Builder(this)
                                            .setTitle(dates.get(which))
                                            .setMessage(texts.get(which))
                                            .setPositiveButton("Close", null)
                                            .show()
                            )
                            .show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load history: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    private void saveRabbit() {

        String name = editRabbitName.getText().toString().trim();
        String breed = editBreed.getText().toString().trim();
        String age = editAge.getText().toString().trim();

        if (name.isEmpty()) {

            editRabbitName.setError("Required");

            editRabbitName.requestFocus();

            return;
        }

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {

            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show();

            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        Map<String, Object> map = new HashMap<>();

        String sex = spinnerSex.getSelectedItem() != null
                ? spinnerSex.getSelectedItem().toString()
                : "";

        map.put("rabbitName", name);
        map.put("breed", breed);
        map.put("age", age);
        map.put("sex", sex);
        map.put("weight", weightValue);

        map.put("lastFed", lastFedMillis != null
                ? displayFormat.format(lastFedMillis)
                : editLastFed.getText().toString().trim());

        map.put("lastDrink", lastDrinkMillis != null
                ? displayFormat.format(lastDrinkMillis)
                : editLastDrink.getText().toString().trim());

        if (photoUrl != null) {
            map.put("imageUrl", photoUrl);
        }

        if (!editMode) {
            map.put("ownerId", uid);
        }

        String aiSummary = txtAiSummary.getText().toString().trim();

        if (!aiSummary.isEmpty()
                && !aiSummary.equals("Generate a summary to get personalized care recommendations.")
                && !aiSummary.equals("Gemini is analyzing your rabbit...")
                && !aiSummary.equals("Couldn't generate a summary.")) {

            map.put("aiSummary", aiSummary);
        }

        if (!editMode) {
            map.put("createdAt", FieldValue.serverTimestamp());
        }

        List<Map<String, Object>> historyToSave = new ArrayList<>();

        for (WeightEntry entry : weightHistory) {

            Map<String, Object> m = new HashMap<>();

            m.put("value", entry.value);
            m.put("timestamp", entry.timestamp);

            historyToSave.add(m);
        }

        map.put("weightHistory", historyToSave);

        btnSaveRabbit.setEnabled(false);

        btnSaveRabbit.setText(editMode ? "Updating..." : "Saving...");

        rabbitRef
                .set(map, SetOptions.merge())
                .addOnSuccessListener(unused -> {

                    Toast.makeText(this, editMode ? "Rabbit updated" : "Rabbit saved", Toast.LENGTH_SHORT).show();

                    weightEditedThisSession = false;

                    Intent resultIntent = new Intent();

                    resultIntent.putExtra("newRabbitId", rabbitRef.getId());

                    setResult(RESULT_OK, resultIntent);

                    finish();
                })
                .addOnFailureListener(e -> {

                    btnSaveRabbit.setEnabled(true);

                    btnSaveRabbit.setText(editMode ? "Update Rabbit" : "Save Rabbit");

                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        geminiExecutor.shutdown();
    }
}