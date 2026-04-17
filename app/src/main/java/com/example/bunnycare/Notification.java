package com.example.bunnycare;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Notification extends Fragment {

    RecyclerView recyclerView;
    List<Map<String, Object>> notifList;
    NotificationAdapter adapter;

    public static Notification newInstance() {
        return new Notification();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notification, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recyclerView = view.findViewById(R.id.recyclerViewNotif);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        notifList = new ArrayList<>();
        adapter = new NotificationAdapter(notifList);
        recyclerView.setAdapter(adapter);

        String uid = FirebaseAuth.getInstance().getUid();

        FirebaseFirestore.getInstance()
                .collection("notifications")
                .whereEqualTo("toUserId", uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (value == null) return;

                    notifList.clear();

                    for (DocumentSnapshot doc : value.getDocuments()) {
                        notifList.add(doc.getData());
                    }

                    adapter.notifyDataSetChanged();
                });
    }
}