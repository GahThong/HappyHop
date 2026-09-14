package com.example.bunnycare;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Monitoring extends Fragment implements RabbitAdapter.OnRabbitItemListener {

    private RecyclerView recyclerView;
    private RabbitAdapter adapter;
    private List<Rabbit> rabbitList;
    private final List<Rabbit> allRabbits = new ArrayList<>();

    private EditText editSearchRabbit;
    private String currentQuery = "";

    private ImageButton btnAddRabbitHeader;
    private View emptyState;
    private View noResultsState;
    private View btnAddFirstRabbit;
    private LinearLayout rabbitHeader;

    private FirebaseFirestore db;
    private String highlightRabbitId;

    private final ActivityResultLauncher<Intent> addRabbitLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == android.app.Activity.RESULT_OK) {

                            if (result.getData() != null) {
                                highlightRabbitId =
                                        result.getData().getStringExtra("newRabbitId");
                            }

                            loadRabbits(this::scrollAndHighlightIfNeeded);
                        }
                    }
            );

    public static Monitoring newInstance() {
        return new Monitoring();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        return inflater.inflate(
                R.layout.health_monitoring,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();

        recyclerView = view.findViewById(R.id.recyclerRabbits);
        emptyState = view.findViewById(R.id.emptyState);
        noResultsState = view.findViewById(R.id.noResultsState);
        btnAddFirstRabbit = view.findViewById(R.id.btnAddFirstRabbit);
        btnAddRabbitHeader = view.findViewById(R.id.btnAddRabbitHeader);
        rabbitHeader = view.findViewById(R.id.rabbitHeader);
        editSearchRabbit = view.findViewById(R.id.editSearchRabbit);

        recyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext())
        );

        rabbitList = new ArrayList<>();

        adapter = new RabbitAdapter(
                requireContext(),
                rabbitList,
                this
        );

        recyclerView.setAdapter(adapter);

        View.OnClickListener addRabbitListener = v -> openAddRabbit();

        btnAddRabbitHeader.setOnClickListener(addRabbitListener);
        btnAddFirstRabbit.setOnClickListener(addRabbitListener);

        if (editSearchRabbit != null) {

            editSearchRabbit.addTextChangedListener(new TextWatcher() {

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                    currentQuery = s.toString().trim().toLowerCase(Locale.getDefault());

                    applyFilter();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        loadRabbits(null);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (db != null && rabbitList != null && adapter != null) {
            loadRabbits(this::scrollAndHighlightIfNeeded);
        }
    }

    private void openAddRabbit() {
        Intent intent = new Intent(
                requireActivity(),
                AddRabbitActivity.class
        );

        addRabbitLauncher.launch(intent);
    }

    @Override
    public void onRabbitTap(
            Rabbit rabbit,
            View anchorView
    ) {
        showRabbitOptions(rabbit);
    }

    @Override
    public void onRabbitLongPress(
            Rabbit rabbit
    ) {
        showRabbitOptions(rabbit);
    }

    private void showRabbitOptions(Rabbit rabbit) {

        String name = rabbit.getRabbitName();

        if (name == null || name.trim().isEmpty()) {
            name = "Rabbit";
        }

        String[] options = {
                "Edit Rabbit Details",
                "Delete Rabbit"
        };

        new AlertDialog.Builder(requireContext())
                .setTitle(name)
                .setItems(
                        options,
                        (dialog, which) -> {

                            if (which == 0) {
                                editRabbit(rabbit);
                            } else if (which == 1) {
                                confirmDeleteRabbit(rabbit);
                            }
                        }
                )
                .show();
    }

    private void editRabbit(Rabbit rabbit) {

        String rabbitId = rabbit.getId();

        if (rabbitId == null || rabbitId.isEmpty()) {

            Toast.makeText(
                    requireContext(),
                    "Rabbit ID not found",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Intent intent = new Intent(
                requireActivity(),
                AddRabbitActivity.class
        );

        intent.putExtra("editMode", true);
        intent.putExtra("rabbitId", rabbitId);

        addRabbitLauncher.launch(intent);
    }

    private void confirmDeleteRabbit(Rabbit rabbit) {

        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Rabbit")
                .setMessage(
                        "Are you sure you want to delete "
                                + safeName(rabbit)
                                + "?"
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Delete",
                        (dialog, which) ->
                                deleteRabbit(rabbit)
                )
                .show();
    }

    private void deleteRabbit(Rabbit rabbit) {

        String rabbitId = rabbit.getId();

        if (rabbitId == null || rabbitId.isEmpty()) {

            Toast.makeText(
                    requireContext(),
                    "Rabbit ID not found",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        db.collection("rabbits")
                .document(rabbitId)
                .delete()
                .addOnSuccessListener(unused -> {

                    allRabbits.remove(rabbit);

                    applyFilter();

                    Toast.makeText(
                            requireContext(),
                            "Rabbit deleted",
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            requireContext(),
                            "Delete failed: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private String safeName(Rabbit rabbit) {

        String name = rabbit.getRabbitName();

        if (name == null || name.trim().isEmpty()) {
            return "this rabbit";
        }

        return name;
    }

    /**
     * Filters allRabbits by currentQuery (matched against the rabbit's name)
     * into rabbitList, which the adapter is bound to, then refreshes the UI.
     */
    private void applyFilter() {

        if (rabbitList == null || adapter == null) {
            return;
        }

        rabbitList.clear();

        if (currentQuery.isEmpty()) {

            rabbitList.addAll(allRabbits);

        } else {

            for (Rabbit rabbit : allRabbits) {

                String name = rabbit.getRabbitName();

                if (name != null
                        && name.toLowerCase(Locale.getDefault()).contains(currentQuery)) {

                    rabbitList.add(rabbit);
                }
            }
        }

        adapter.notifyDataSetChanged();

        updateEmptyState();
    }

    private void updateEmptyState() {

        if (emptyState == null ||
                noResultsState == null ||
                recyclerView == null ||
                rabbitHeader == null) {
            return;
        }

        boolean hasAnyRabbits = !allRabbits.isEmpty();
        boolean hasVisibleResults = rabbitList != null && !rabbitList.isEmpty();

        if (!hasAnyRabbits) {

            // No rabbits at all — show the full "add your first rabbit" state.
            rabbitHeader.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            noResultsState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.GONE);

        } else if (!hasVisibleResults) {

            // Rabbits exist, but none match the current search.
            rabbitHeader.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            noResultsState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);

        } else {

            rabbitHeader.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            noResultsState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void loadRabbits(
            @Nullable Runnable onComplete
    ) {

        if (FirebaseAuth
                .getInstance()
                .getCurrentUser() == null) {

            allRabbits.clear();

            if (rabbitList != null) {
                rabbitList.clear();
            }

            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }

            updateEmptyState();

            return;
        }

        String uid =
                FirebaseAuth
                        .getInstance()
                        .getCurrentUser()
                        .getUid();

        db.collection("rabbits")
                .whereEqualTo("ownerId", uid)
                .get()
                .addOnSuccessListener(
                        queryDocumentSnapshots -> {

                            if (rabbitList == null ||
                                    adapter == null) {
                                return;
                            }

                            allRabbits.clear();

                            for (
                                    QueryDocumentSnapshot document
                                    : queryDocumentSnapshots
                            ) {

                                Rabbit rabbit =
                                        document.toObject(
                                                Rabbit.class
                                        );

                                if (rabbit != null) {

                                    rabbit.setId(
                                            document.getId()
                                    );

                                    allRabbits.add(
                                            rabbit
                                    );
                                }
                            }

                            applyFilter();

                            if (onComplete != null) {
                                onComplete.run();
                            }
                        }
                )
                .addOnFailureListener(e -> {

                    if (isAdded()) {

                        Toast.makeText(
                                requireContext(),
                                "Failed to load rabbits: "
                                        + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private void scrollAndHighlightIfNeeded() {

        if (highlightRabbitId == null ||
                rabbitList == null ||
                rabbitList.isEmpty()) {

            return;
        }

        String targetId = highlightRabbitId;

        highlightRabbitId = null;

        int position = -1;

        for (int i = 0; i < rabbitList.size(); i++) {

            Rabbit rabbit = rabbitList.get(i);

            if (targetId.equals(rabbit.getId())) {
                position = i;
                break;
            }
        }

        if (position < 0) {
            return;
        }

        final int finalPosition = position;

        recyclerView.scrollToPosition(finalPosition);

        recyclerView.postDelayed(() -> {

            RecyclerView.ViewHolder holder =
                    recyclerView.findViewHolderForAdapterPosition(
                            finalPosition
                    );

            if (holder != null) {

                View item = holder.itemView;

                item.animate()
                        .scaleX(1.05f)
                        .scaleY(1.05f)
                        .setDuration(200)
                        .withEndAction(
                                () -> item.animate()
                                        .scaleX(1f)
                                        .scaleY(1f)
                                        .setDuration(200)
                                        .start()
                        )
                        .start();
            }

        }, 200);
    }
}