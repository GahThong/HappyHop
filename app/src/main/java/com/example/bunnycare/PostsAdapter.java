package com.example.bunnycare;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class PostsAdapter extends RecyclerView.Adapter<PostsAdapter.ViewHolder> {

    Context context;
    List<posts> list;

    FirebaseFirestore db = FirebaseFirestore.getInstance();

    public PostsAdapter(Context context, List<posts> list) {
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(context)
                .inflate(R.layout.layout_post, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        posts post = list.get(position);

        holder.userName.setText(post.getPosterName());
        holder.description.setText(post.getPost());

        holder.likeCount.setText(String.valueOf(post.getLikesCount()));
        holder.commentCount.setText(String.valueOf(post.getCommentCount()));

        Timestamp timestamp = post.getTimestamp();

        if (timestamp != null) {

            CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                    timestamp.toDate().getTime(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
            );

            holder.timestampTextView.setText(timeAgo);

        } else {

            holder.timestampTextView.setText("Just now");
        }

        if (post.getPostImage() != null && !post.getPostImage().isEmpty()) {

            holder.postImage.setVisibility(View.VISIBLE);

            Glide.with(context)
                    .load(post.getPostImage())
                    .into(holder.postImage);

        } else {

            holder.postImage.setVisibility(View.GONE);
        }

        if (post.getProfileImage() != null && !post.getProfileImage().isEmpty()) {

            Glide.with(context)
                    .load(post.getProfileImage())
                    .circleCrop()
                    .placeholder(R.drawable.account_user)
                    .into(holder.profileImageView);

        } else {

            holder.profileImageView.setImageResource(R.drawable.account_user);
        }

        db.collection("posts")
                .document(post.getId())
                .addSnapshotListener((value, error) -> {

                    if (value != null && value.exists()) {

                        Long likes = value.getLong("likesCount");
                        Long comments = value.getLong("commentCount");

                        holder.likeCount.setText(
                                String.valueOf(likes != null ? likes : 0)
                        );

                        holder.commentCount.setText(
                                String.valueOf(comments != null ? comments : 0)
                        );
                    }
                });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        TextView userName;
        TextView description;
        TextView likeCount;
        TextView commentCount;
        TextView timestampTextView;

        ImageView postImage;
        ImageView profileImageView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            userName = itemView.findViewById(R.id.userName);
            description = itemView.findViewById(R.id.postDescriptionTextView);
            likeCount = itemView.findViewById(R.id.likeCount);
            commentCount = itemView.findViewById(R.id.commentCount);
            timestampTextView = itemView.findViewById(R.id.timestampTextView);

            postImage = itemView.findViewById(R.id.postImageView);
            profileImageView = itemView.findViewById(R.id.profileImageView);
        }
    }
}