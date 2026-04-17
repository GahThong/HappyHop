package com.example.bunnycare;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Map;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.ViewHolder> {

    List<Map<String, Object>> list;

    public CommentAdapter(List<Map<String, Object>> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.comment_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        Map<String, Object> comment = list.get(position);

        String userName = "";
        String text = "";

        Object nameObj = comment.get("userName");
        Object textObj = comment.get("text");

        if (nameObj != null) userName = String.valueOf(nameObj);
        if (textObj != null) text = String.valueOf(textObj);

        if (userName.trim().isEmpty()) {
            userName = "Unknown User";
        }

        holder.user.setText(userName);
        holder.text.setText(text);
    }
    

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView user, text;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            user = itemView.findViewById(R.id.commentUser);
            text = itemView.findViewById(R.id.commentText);
        }
    }
}