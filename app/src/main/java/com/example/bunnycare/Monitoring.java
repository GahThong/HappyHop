package com.example.bunnycare;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class Monitoring extends Fragment {

    private RecyclerView recyclerView;
    private RabbitAdapter adapter;
    private List<Rabbit> rabbitList;
    private ImageButton fabAddRabbit;

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

        recyclerView = view.findViewById(R.id.recyclerRabbits);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        rabbitList = new ArrayList<>();
        adapter = new RabbitAdapter(requireContext(), rabbitList);
        recyclerView.setAdapter(adapter);

        fabAddRabbit = view.findViewById(R.id.fabAddRabbit);

        fabAddRabbit.setOnClickListener(v -> {
            Intent intent = new Intent(requireActivity(), AddRabbitActivity.class);
            startActivity(intent);
        });

        loadRabbits();
    }

    private void loadRabbits() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore.getInstance()
                .collection("rabbits")
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
                });
    }
}