package com.example.bunnycare;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Map;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    List<Map<String, Object>> list;

    public NotificationAdapter(List<Map<String, Object>> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.notification_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        Map<String, Object> notif = list.get(position);

        String user = notif.get("fromUsername") != null ? notif.get("fromUsername").toString() : "Unknown";
        String type = notif.get("type") != null ? notif.get("type").toString() : "";
        String post = notif.get("postText") != null ? notif.get("postText").toString() : "";

        if ("comment".equals(type)) {
            holder.text.setText(user + " commented on your post " + post);
        } else if ("like".equals(type)) {
            holder.text.setText(user + " liked your post " + post);
        } else {
            holder.text.setText(user + " interacted with your post " + post);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.notificationText);
        }
    }
}