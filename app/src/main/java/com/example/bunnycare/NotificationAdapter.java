package com.example.bunnycare;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private final Context context;
    private final List<AppNotification> notificationList;

    public NotificationAdapter(Context context, List<AppNotification> notificationList) {
        this.context = context;
        this.notificationList = notificationList;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_notification, parent, false);

        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {

        AppNotification notification = notificationList.get(position);

        holder.message.setText(notification.getMessage());

        if (notification.getTimestamp() != null) {
            CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                    notification.getTimestamp().toDate().getTime());
            holder.time.setText(relativeTime);
        } else {
            holder.time.setText("");
        }

        holder.unreadDot.setVisibility(notification.isRead() ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return notificationList.size();
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {

        TextView message, time;
        View unreadDot;

        NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.notificationMessage);
            time = itemView.findViewById(R.id.notificationTime);
            unreadDot = itemView.findViewById(R.id.unreadDot);
        }
    }
}