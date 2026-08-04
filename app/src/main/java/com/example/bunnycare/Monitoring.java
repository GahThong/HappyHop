package com.example.bunnycare;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Monitoring extends Fragment implements RabbitAdapter.OnRabbitItemListener {

    private RecyclerView recyclerView;
    private RabbitAdapter adapter;
    private List<Rabbit> rabbitList;
    private ImageButton fabAddRabbit;

    private final Executor geminiExecutor = Executors.newSingleThreadExecutor();

    private FirebaseFirestore db;

    // Tracks which rabbit a pending photo/QR-image pick belongs to.
    private Rabbit pendingActionRabbit;

    // Id of a rabbit that should be scrolled to / highlighted / opened
    // once the next loadRabbits() finishes (set right after AddRabbitActivity
    // returns with a newly saved rabbit).
    private String highlightRabbitId;

    // IMPORTANT: registerForActivityResult() must be called unconditionally,
    // exactly once per Fragment instance, before the Fragment reaches STARTED.
    // Calling it from onViewCreated() (which can run again if the fragment's
    // view is recreated while the Fragment instance itself survives — e.g.
    // switching bottom-nav tabs without replacing the fragment) throws:
    //   IllegalStateException: LifecycleOwner ... attempting to register
    //   while current state is STARTED.
    // Declaring these as field initializers guarantees they're registered
    // exactly once, during Fragment construction, which is always safe.
    private final ActivityResultLauncher<Intent> photoPickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == android.app.Activity.RESULT_OK
                                && result.getData() != null
                                && pendingActionRabbit != null) {

                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                uploadImageToCloudinary(uri, pendingActionRabbit, "imageUrl");
                            }
                        }
                    });

    private final ActivityResultLauncher<Intent> qrImagePickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == android.app.Activity.RESULT_OK
                                && result.getData() != null
                                && pendingActionRabbit != null) {

                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                uploadImageToCloudinary(uri, pendingActionRabbit, "qrImage");
                            }
                        }
                    });

    private final ActivityResultLauncher<Intent> addRabbitLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == android.app.Activity.RESULT_OK
                                && result.getData() != null) {
                            highlightRabbitId = result.getData().getStringExtra("newRabbitId");
                        }
                        loadRabbits(this::scrollAndHighlightIfNeeded);
                    });

    public static Monitoring newInstance() {
        return new Monitoring();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.health_monitoring, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();

        recyclerView = view.findViewById(R.id.recyclerRabbits);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        rabbitList = new ArrayList<>();
        adapter = new RabbitAdapter(requireContext(), rabbitList, this);
        recyclerView.setAdapter(adapter);

        fabAddRabbit = view.findViewById(R.id.fabAddRabbit);

        // Launch for a result instead of a plain startActivity, so we can find
        // out which rabbit was just created and jump to it.
        fabAddRabbit.setOnClickListener(v -> {
            Intent intent = new Intent(requireActivity(), AddRabbitActivity.class);
            addRabbitLauncher.launch(intent);
        });

        loadRabbits(null);
    }

    // ---------------------------------------------------------------------
    // Refresh the list every time this fragment becomes visible again
    // (e.g. after returning from AddRabbitActivity via back button)
    // ---------------------------------------------------------------------
    @Override
    public void onResume() {
        super.onResume();
        loadRabbits(this::scrollAndHighlightIfNeeded);
    }

    // ---------------------------------------------------------------------
    // Scroll to / pulse / open the newly saved rabbit's card once it's loaded
    // ---------------------------------------------------------------------

    private void scrollAndHighlightIfNeeded() {
        if (highlightRabbitId == null || rabbitList == null || !isAdded()) return;

        final String targetId = highlightRabbitId;
        highlightRabbitId = null;

        int position = -1;
        for (int i = 0; i < rabbitList.size(); i++) {
            if (targetId.equals(rabbitList.get(i).getId())) {
                position = i;
                break;
            }
        }

        if (position < 0) return;

        final int finalPosition = position;
        recyclerView.scrollToPosition(finalPosition);
        recyclerView.post(() -> recyclerView.postDelayed(() -> pulseAndOpen(finalPosition), 150));
    }

    private void pulseAndOpen(int position) {
        if (!isAdded() || rabbitList == null || position < 0 || position >= rabbitList.size()) return;

        RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(position);
        if (vh == null || vh.itemView == null) return;

        View item = vh.itemView;
        Rabbit rabbit = rabbitList.get(position);

        item.animate()
                .scaleX(1.05f).scaleY(1.05f)
                .setDuration(200)
                .withEndAction(() -> item.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(200)
                        .withEndAction(() -> {
                            if (isAdded()) {
                                showRabbitItemMenu(item, rabbit);
                            }
                        })
                        .start())
                .start();
    }

    // ---------------------------------------------------------------------
    // RabbitAdapter.OnRabbitItemListener — tap opens menu, long press asks for AI care plan
    // ---------------------------------------------------------------------

    @Override
    public void onRabbitTap(Rabbit rabbit, View anchorView) {
        showRabbitItemMenu(anchorView, rabbit);
    }

    @Override
    public void onRabbitLongPress(Rabbit rabbit) {
        confirmAndRequestCareRecommendation(rabbit);
    }

    // ---------------------------------------------------------------------
    // Image upload (Cloudinary)
    // ---------------------------------------------------------------------

    private void uploadImageToCloudinary(Uri uri, Rabbit rabbit, String targetField) {

        Toast.makeText(requireContext(), "Uploading photo...", Toast.LENGTH_SHORT).show();

        MediaManager.get()
                .upload(uri)
                .unsigned("ml_default")
                .callback(new UploadCallback() {

                    @Override public void onStart(String requestId) {}
                    @Override public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String secureUrl = (String) resultData.get("secure_url");
                        saveRabbitField(rabbit, targetField, secureUrl);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        if (isAdded()) {
                            requireActivity().runOnUiThread(() ->
                                    Toast.makeText(requireContext(),
                                            "Upload failed: " + error.getDescription(),
                                            Toast.LENGTH_LONG).show());
                        }
                    }

                    @Override public void onReschedule(String requestId, ErrorInfo error) {}

                }).dispatch();
    }

    // ---------------------------------------------------------------------
    // Per-rabbit field updates (last fed food, photo url, qr image)
    // ---------------------------------------------------------------------

    private void saveRabbitField(Rabbit rabbit, String field, String value) {

        if (rabbit == null || rabbit.getId() == null) return;

        Map<String, Object> map = new HashMap<>();
        map.put(field, value);

        if ("qrImage".equals(field)) {
            map.put("hasQr", value != null && !value.isEmpty());
        }

        db.collection("rabbits")
                .document(rabbit.getId())
                .set(map, SetOptions.merge())
                .addOnSuccessListener(unused -> {

                    if ("imageUrl".equals(field)) {
                        rabbit.setImageUrl(value);
                    } else if ("qrImage".equals(field)) {
                        rabbit.setQrImage(value);
                        rabbit.setHasQr(value != null && !value.isEmpty());
                    } else if ("lastFedFood".equals(field)) {
                        rabbit.setLastFedFood(value);
                    } else if ("lastDrink".equals(field)) {
                        rabbit.setLastDrink(value);
                    }

                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            adapter.notifyDataSetChanged();
                            Toast.makeText(requireContext(), "Updated", Toast.LENGTH_SHORT).show();
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
    }

    private void promptLastFedFood(Rabbit rabbit) {

        EditText input = new EditText(requireContext());
        input.setHint("e.g. Timothy hay, pellets, leafy greens");

        if (rabbit.getLastFedFood() != null) {
            input.setText(rabbit.getLastFedFood());
            input.setSelection(input.getText().length());
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Last Fed Food: " + safeName(rabbit))
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String food = input.getText().toString().trim();
                    saveRabbitField(rabbit, "lastFedFood", food);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptLastDrink(Rabbit rabbit) {

        EditText input = new EditText(requireContext());
        input.setHint("e.g. Fresh water, changed 8am");

        if (rabbit.getLastDrink() != null) {
            input.setText(rabbit.getLastDrink());
            input.setSelection(input.getText().length());
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Last Drink: " + safeName(rabbit))
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String drink = input.getText().toString().trim();
                    saveRabbitField(rabbit, "lastDrink", drink);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pickPhotoForRabbit(Rabbit rabbit) {
        pendingActionRabbit = rabbit;

        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        photoPickerLauncher.launch(intent);
    }

    // ---------------------------------------------------------------------
    // QR: generate / scan / upload, same pattern as Account.showQRMenu
    // ---------------------------------------------------------------------

    private void showRabbitQrMenu(Rabbit rabbit) {

        String[] options = {"Generate QR", "Scan QR", "Upload QR Image"};

        new AlertDialog.Builder(requireContext())
                .setTitle("QR Options: " + safeName(rabbit))
                .setItems(options, (dialog, which) -> {

                    if (which == 0) {
                        generateQrForRabbit(rabbit);
                    }

                    if (which == 1) {
                        scanQr();
                    }

                    if (which == 2) {
                        uploadQrImageForRabbit(rabbit);
                    }
                })
                .show();
    }

    private void generateQrForRabbit(Rabbit rabbit) {
        Intent intent = new Intent(getActivity(), QRActivity.class);
        intent.putExtra("data", rabbit.getId());
        startActivity(intent);
    }

    private void scanQr() {
        IntentIntegrator.forSupportFragment(this).initiateScan();
    }

    private void uploadQrImageForRabbit(Rabbit rabbit) {
        pendingActionRabbit = rabbit;

        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        qrImagePickerLauncher.launch(intent);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        if (result != null && result.getContents() != null) {

            String scannedRabbitId = result.getContents();

            db.collection("rabbits")
                    .document(scannedRabbitId)
                    .get()
                    .addOnSuccessListener(doc -> {

                        if (!doc.exists()) {
                            Toast.makeText(getContext(), "Rabbit not found", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String imageUrl = doc.getString("imageUrl");

                        Intent resultIntent = new Intent(getActivity(), QrResultActivity.class);
                        resultIntent.putExtra("image", imageUrl);
                        startActivity(resultIntent);
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show());

            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    // ---------------------------------------------------------------------
    // Rabbit item interaction: tap opens menu (see onRabbitTap above), long press -> AI care plan
    // ---------------------------------------------------------------------

    private void showRabbitItemMenu(View anchor, Rabbit rabbit) {

        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.inflate(R.menu.menu_rabbit_item);

        popup.setOnMenuItemClickListener(item -> {

            int id = item.getItemId();

            if (id == R.id.menu_last_fed) {
                promptLastFedFood(rabbit);
            }

            if (id == R.id.menu_last_drink) {
                promptLastDrink(rabbit);
            }

            if (id == R.id.menu_change_photo) {
                pickPhotoForRabbit(rabbit);
            }

            if (id == R.id.menu_rabbit_qr) {
                showRabbitQrMenu(rabbit);
            }

            return true;
        });

        popup.show();
    }


    private void confirmAndRequestCareRecommendation(Rabbit rabbit) {
        String name = safeName(rabbit);
        new AlertDialog.Builder(requireContext())
                .setTitle(name)
                .setMessage("Get a Gemini-recommended care plan for " + name + "?")
                .setPositiveButton("Get recommendation", (dialog, which) -> requestCareRecommendation(rabbit))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void requestCareRecommendation(Rabbit rabbit) {
        Toast.makeText(requireContext(), "Asking Gemini for a care plan...", Toast.LENGTH_SHORT).show();

        geminiExecutor.execute(() -> {
            Bitmap photo = downloadImage(rabbit.getImageUrl());
            sendCareRequestToGemini(rabbit, photo);
        });
    }

    private Bitmap downloadImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return null;
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            try (InputStream input = connection.getInputStream()) {
                return BitmapFactory.decodeStream(input);
            }
        } catch (Exception e) {
            Log.e("Monitoring", "Failed to download rabbit photo", e);
            return null;
        }
    }

    private void sendCareRequestToGemini(Rabbit rabbit, @Nullable Bitmap photo) {
        GenerativeModel firebaseAI = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                .generativeModel("gemini-3.5-flash-lite");
        GenerativeModelFutures model = GenerativeModelFutures.from(firebaseAI);

        String prompt = buildCarePrompt(rabbit, photo != null);

        Content.Builder contentBuilder = new Content.Builder().addText(prompt);
        if (photo != null) {
            contentBuilder.addImage(photo);
        }
        Content content = contentBuilder.build();

        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);
        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                showCareRecommendation(rabbit, result.getText());
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e("Monitoring", "Gemini care recommendation failed", t);
                if (isAdded()) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "Could not get a recommendation: " + t.getMessage(), Toast.LENGTH_LONG).show());
                }
            }
        }, geminiExecutor);
    }

    private String buildCarePrompt(Rabbit rabbit, boolean hasPhoto) {
        String name = safeName(rabbit);
        StringBuilder sb = new StringBuilder();
        sb.append("You are a rabbit care expert. ");

        if (hasPhoto) {
            sb.append("Look at the attached photo of a pet rabbit named \"").append(name).append("\". ")
                    .append("Based on its apparent breed and visible condition, write a short, practical recommended ")
                    .append("care plan covering diet, housing/exercise, grooming, and any health precautions worth ")
                    .append("noting from the photo.");
        } else {
            sb.append("A user has a pet rabbit named \"").append(name).append("\" but no photo is available. ")
                    .append("Write a short, general recommended care plan covering diet, housing/exercise, and ")
                    .append("grooming suitable for a typical pet rabbit, and mention that a photo would allow more ")
                    .append("specific, breed-aware advice.");
        }

        if (rabbit.getLastFedFood() != null && !rabbit.getLastFedFood().isEmpty()) {
            sb.append(" The rabbit's last recorded food was: \"").append(rabbit.getLastFedFood()).append("\".");
        }

        sb.append(" Keep it concise, plain text, no JSON.");
        return sb.toString();
    }

    private void showCareRecommendation(Rabbit rabbit, String careText) {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> new AlertDialog.Builder(requireContext())
                .setTitle("Recommended Care: " + safeName(rabbit))
                .setMessage(careText)
                .setPositiveButton("OK", null)
                .show());
    }

    private String safeName(Rabbit rabbit) {
        String name = rabbit.getRabbitName();
        return (name == null || name.isEmpty()) ? "Your rabbit" : name;
    }

    // Accepts an optional callback that runs after the list has been reloaded
    // and the adapter refreshed, so callers can then scroll/highlight.
    private void loadRabbits(@Nullable Runnable onComplete) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("rabbits")
                .whereEqualTo("ownerId", uid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    rabbitList.clear();

                    for (var document : queryDocumentSnapshots.getDocuments()) {
                        Rabbit rabbit = document.toObject(Rabbit.class);
                        if (rabbit != null) {
                            rabbit.setId(document.getId());
                            rabbitList.add(rabbit);
                        }
                    }

                    adapter.notifyDataSetChanged();

                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
    }
}