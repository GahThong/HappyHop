package com.example.bunnycare;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostsAdapter extends RecyclerView.Adapter<PostsAdapter.ViewHolder> {

    Context context;
    List<posts> list;

    FirebaseFirestore db = FirebaseFirestore.getInstance();
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

    public PostsAdapter(Context context, List<posts> list) {
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_post, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        posts post = list.get(position);

        holder.userName.setText(post.getPosterName());
        holder.description.setText(post.getPost());
        holder.likeCount.setText(String.valueOf(post.getLikesCount()));
        holder.commentCount.setText(String.valueOf(post.getCommentCount()));

        if (post.getPostImage() != null && !post.getPostImage().isEmpty()) {
            holder.postImage.setVisibility(View.VISIBLE);
            Glide.with(context).load(post.getPostImage()).into(holder.postImage);
        } else {
            holder.postImage.setVisibility(View.GONE);
        }

        String postId = post.getId();

        db.collection("posts").document(postId)
                .addSnapshotListener((value, error) -> {
                    if (value != null && value.exists()) {
                        Long count = value.getLong("likesCount");
                        holder.likeCount.setText(String.valueOf(count != null ? count : 0));
                    }
                });

        holder.likeBtn.setOnClickListener(v -> {

            if (user == null) return;

            String uid = user.getUid();

            db.collection("posts")
                    .document(postId)
                    .collection("likes")
                    .document(uid)
                    .get()
                    .addOnSuccessListener(doc -> {

                        if (doc.exists()) {
                            db.collection("posts").document(postId)
                                    .collection("likes").document(uid)
                                    .delete();

                            db.collection("posts").document(postId)
                                    .update("likesCount", FieldValue.increment(-1));
                        } else {
                            Map<String, Object> like = new HashMap<>();
                            like.put("uid", uid);

                            db.collection("posts")
                                    .document(postId)
                                    .collection("likes")
                                    .document(uid)
                                    .set(like);

                            db.collection("posts").document(postId)
                                    .update("likesCount", FieldValue.increment(1));
                        }
                    });
        });

        holder.reportBtn.setOnClickListener(v -> {

            if (user == null) return;

            String uid = user.getUid();

            Map<String, Object> report = new HashMap<>();
            report.put("postId", postId);
            report.put("reportedBy", uid);
            report.put("timestamp", FieldValue.serverTimestamp());

            // Prevent duplicate reports
            db.collection("reports")
                    .document(postId + "_" + uid)
                    .set(report)
                    .addOnSuccessListener(doc -> {
                        Toast.makeText(context, "Post reported", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(context, "Failed to report", Toast.LENGTH_SHORT).show();
                    });
        });

        // =========================
        // COMMENT SYSTEM
        // =========================
        holder.commentBtn.setOnClickListener(v -> {

            BottomSheetDialog dialog = new BottomSheetDialog(context);
            View sheet = LayoutInflater.from(context).inflate(R.layout.comment_sheet, null);

            dialog.setContentView(sheet);
            dialog.show();

            RecyclerView recycler = sheet.findViewById(R.id.commentRecycler);
            EditText input = sheet.findViewById(R.id.commentInput);
            Button send = sheet.findViewById(R.id.sendComment);

            List<Map<String, Object>> commentList = new ArrayList<>();
            CommentAdapter adapter = new CommentAdapter(commentList);

            recycler.setLayoutManager(new LinearLayoutManager(context));
            recycler.setAdapter(adapter);

            db.collection("posts")
                    .document(postId)
                    .collection("comments")
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .addSnapshotListener((value, error) -> {

                        if (value == null) return;

                        commentList.clear();

                        for (DocumentSnapshot doc : value.getDocuments()) {
                            commentList.add(doc.getData());
                        }

                        adapter.notifyDataSetChanged();
                    });

            send.setOnClickListener(v1 -> {

                String text = input.getText().toString().trim();
                if (text.isEmpty() || user == null) return;

                String uid = user.getUid();

                db.collection("users").document(uid)
                        .get()
                        .addOnSuccessListener(userDoc -> {

                            String username = "Unknown";

                            if (userDoc.exists()) {
                                String name = userDoc.getString("userName");
                                if (name != null) username = name;
                            }

                            Map<String, Object> comment = new HashMap<>();
                            comment.put("text", text);
                            comment.put("uid", uid);
                            comment.put("userName", username);
                            comment.put("timestamp", FieldValue.serverTimestamp());

                            db.collection("posts")
                                    .document(postId)
                                    .collection("comments")
                                    .add(comment)
                                    .addOnSuccessListener(doc -> {

                                        db.collection("posts")
                                                .document(postId)
                                                .update("commentCount", FieldValue.increment(1));

                                        input.setText("");
                                    });
                        });
            });

            // REALTIME COMMENT COUNT
            db.collection("posts").document(postId)
                    .addSnapshotListener((value, error) -> {
                        if (value != null && value.exists()) {
                            Long count = value.getLong("commentCount");
                            holder.commentCount.setText(String.valueOf(count != null ? count : 0));
                        }
                    });
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        TextView userName, description, likeCount, commentCount;
        ImageView postImage, likeBtn, commentBtn, reportBtn;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            userName = itemView.findViewById(R.id.userName);
            description = itemView.findViewById(R.id.postDescriptionTextView);
            likeCount = itemView.findViewById(R.id.likeCount);
            commentCount = itemView.findViewById(R.id.commentCount);
            postImage = itemView.findViewById(R.id.postImageView);
            likeBtn = itemView.findViewById(R.id.likeBtn);
            commentBtn = itemView.findViewById(R.id.commentBtn);
            reportBtn = itemView.findViewById(R.id.reportBtn);
        }
    }
}