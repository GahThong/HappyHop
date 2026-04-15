package com.example.bunnycare;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class PostsAdapter extends RecyclerView.Adapter<PostsAdapter.PostsViewHolder> {

    private List<posts> postList;

    // Constructor
    public PostsAdapter(List<posts> postList) {
        this.postList = postList;
    }

    @NonNull
    @Override
    public PostsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_post, parent, false);
        return new PostsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostsViewHolder holder, int position) {
        posts postItem = postList.get(position);

        holder.userName.setText(postItem.getPosterName());
        holder.postDescriptionTextView.setText(postItem.getPost());
       // holder.postImageView.setImageResource(postItem.getImageFromLink());
    }

    @Override
    public int getItemCount() {
        return postList.size();
    }

    // ViewHolder class
    static class PostsViewHolder extends RecyclerView.ViewHolder {
        TextView userName, postDescriptionTextView;
        ImageView postImageView;

        public PostsViewHolder(@NonNull View itemView) {

            super(itemView);

            userName = itemView.findViewById(R.id.userName);
            postDescriptionTextView = itemView.findViewById(R.id.postDescriptionTextView);
            postImageView = itemView.findViewById(R.id.postImageView);
        }
    }
}
