package com.example.bunnycare;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
        String postId = post.getId();

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

        if (post.getProfileImage() != null && !post.getProfileImage().isEmpty()) {
            Glide.with(context)
                    .load(post.getProfileImage())
                    .into(holder.profileImageView);
        } else {
            holder.profileImageView.setImageResource(R.drawable.account_user);
        }

        db.collection("posts").document(postId)
                .addSnapshotListener((value, error) -> {

                    if (value != null && value.exists()) {

                        Long likes = value.getLong("likesCount");
                        Long comments = value.getLong("commentCount");

                        holder.likeCount.setText(String.valueOf(likes != null ? likes : 0));
                        holder.commentCount.setText(String.valueOf(comments != null ? comments : 0));
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

                            db.collection("posts")
                                    .document(postId)
                                    .collection("likes")
                                    .document(uid)
                                    .delete();

                            db.collection("posts")
                                    .document(postId)
                                    .update("likesCount", FieldValue.increment(-1));

                        } else {

                            Map<String, Object> like = new HashMap<>();
                            like.put("uid", uid);

                            db.collection("posts")
                                    .document(postId)
                                    .collection("likes")
                                    .document(uid)
                                    .set(like);

                            db.collection("posts")
                                    .document(postId)
                                    .update("likesCount", FieldValue.increment(1));

                            db.collection("users").document(uid)
                                    .get()
                                    .addOnSuccessListener(userDoc -> {

                                        String username;

                                        if (userDoc.exists()) {
                                            String name = userDoc.getString("username");
                                            username = name != null ? name : "Unknown";
                                        } else {
                                            username = "Unknown";
                                        }

                                        db.collection("posts")
                                                .document(postId)
                                                .get()
                                                .addOnSuccessListener(originalPost -> {

                                                    String posterId =
                                                            originalPost.getString("posterId");

                                                    if (posterId != null &&
                                                            !posterId.equals(uid)) {

                                                        Map<String, Object> notification =
                                                                new HashMap<>();

                                                        notification.put("toUserId", posterId);
                                                        notification.put("fromUserName",
                                                                username);
                                                        notification.put("type", "like");
                                                        notification.put("postText",
                                                                originalPost.get("post"));
                                                        notification.put("timestamp",
                                                                FieldValue.serverTimestamp());

                                                        db.collection("notifications")
                                                                .add(notification);
                                                    }
                                                });
                                    });
                        }
                    });
        });

        // 3-dot menu: Report for everyone, Edit/Delete only for the post's owner
        holder.menuBtn.setOnClickListener(v -> {

            PopupMenu popup = new PopupMenu(context, v);
            popup.getMenu().add("Report Post");

            boolean isOwner = user != null
                    && post.getPosterId() != null
                    && post.getPosterId().equals(user.getUid());

            if (isOwner) {
                popup.getMenu().add("Edit Post");
                popup.getMenu().add("Delete Post");
            }

            popup.setOnMenuItemClickListener(item -> {

                String title = item.getTitle().toString();
                int currentPosition = holder.getBindingAdapterPosition();

                if (currentPosition == RecyclerView.NO_POSITION) return true;

                if (title.equals("Report Post")) {
                    showReportSheet(postId);
                } else if (title.equals("Edit Post")) {
                    showEditDialog(post, holder);
                } else if (title.equals("Delete Post")) {
                    confirmDeletePost(postId, currentPosition);
                }

                return true;
            });

            popup.show();
        });

        holder.commentBtn.setOnClickListener(v -> {

            BottomSheetDialog dialog = new BottomSheetDialog(context);

            View sheet = LayoutInflater.from(context)
                    .inflate(R.layout.comment_sheet, null);

            dialog.setContentView(sheet);
            dialog.show();

            RecyclerView recycler =
                    sheet.findViewById(R.id.commentRecycler);

            EditText input =
                    sheet.findViewById(R.id.commentInput);

            Button send =
                    sheet.findViewById(R.id.sendComment);

            List<Map<String, Object>> commentList =
                    new ArrayList<>();

            CommentAdapter adapter =
                    new CommentAdapter(commentList);

            recycler.setLayoutManager(
                    new LinearLayoutManager(context));

            recycler.setAdapter(adapter);

            db.collection("posts")
                    .document(postId)
                    .collection("comments")
                    .orderBy("timestamp",
                            Query.Direction.ASCENDING)
                    .addSnapshotListener((value, error) -> {

                        if (value == null) return;

                        commentList.clear();

                        for (DocumentSnapshot doc :
                                value.getDocuments()) {

                            commentList.add(doc.getData());
                        }

                        adapter.notifyDataSetChanged();
                    });

            send.setOnClickListener(v1 -> {

                String text =
                        input.getText().toString().trim();

                if (text.isEmpty() || user == null) return;

                String uid = user.getUid();

                db.collection("users")
                        .document(uid)
                        .get()
                        .addOnSuccessListener(userDoc -> {

                            String username;

                            if (userDoc.exists()) {

                                String name =
                                        userDoc.getString("username");

                                username =
                                        name != null ? name : "Unknown";

                            } else {

                                username = "Unknown";
                            }

                            Map<String, Object> comment =
                                    new HashMap<>();

                            comment.put("text", text);
                            comment.put("uid", uid);
                            comment.put("username", username);
                            comment.put("timestamp",
                                    FieldValue.serverTimestamp());

                            db.collection("posts")
                                    .document(postId)
                                    .collection("comments")
                                    .add(comment)
                                    .addOnSuccessListener(doc -> {

                                        db.collection("posts")
                                                .document(postId)
                                                .update("commentCount",
                                                        FieldValue.increment(1));

                                        input.setText("");
                                    });

                            db.collection("posts")
                                    .document(postId)
                                    .get()
                                    .addOnSuccessListener(originalPost -> {

                                        String posterId =
                                                originalPost.getString("posterId");

                                        if (posterId != null &&
                                                !posterId.equals(uid)) {

                                            Map<String, Object> notification =
                                                    new HashMap<>();

                                            notification.put("toUserId",
                                                    posterId);

                                            notification.put("fromUserName",
                                                    username);

                                            notification.put("type",
                                                    "comment");

                                            notification.put("postText",
                                                    originalPost.get("post"));

                                            notification.put("timestamp",
                                                    FieldValue.serverTimestamp());

                                            db.collection("notifications")
                                                    .add(notification);
                                        }
                                    });
                        });
            });
        });
    }

    private void showReportSheet(String postId) {

        if (user == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(context);

        View sheet = LayoutInflater.from(context)
                .inflate(R.layout.report_sheet, null);

        dialog.setContentView(sheet);
        dialog.show();

        CheckBox rabbitTopic = sheet.findViewById(R.id.rabbitTopic);
        CheckBox cursingWords = sheet.findViewById(R.id.cursingWords);
        CheckBox violence = sheet.findViewById(R.id.violence);
        CheckBox hate = sheet.findViewById(R.id.hate);
        CheckBox others = sheet.findViewById(R.id.others);

        EditText reportInput = sheet.findViewById(R.id.reportInput);

        Button submitReport = sheet.findViewById(R.id.submitReport);

        others.setOnCheckedChangeListener((buttonView, isChecked) -> {

            if (isChecked) {
                reportInput.setVisibility(View.VISIBLE);
            } else {
                reportInput.setVisibility(View.GONE);
            }
        });

        submitReport.setOnClickListener(v1 -> {

            List<String> reasons = new ArrayList<>();

            if (rabbitTopic.isChecked()) {
                reasons.add("Not including rabbit topic");
            }

            if (cursingWords.isChecked()) {
                reasons.add("Cursing words");
            }

            if (violence.isChecked()) {
                reasons.add("Violence");
            }

            if (hate.isChecked()) {
                reasons.add("Hate");
            }

            if (others.isChecked()) {

                String otherText =
                        reportInput.getText().toString().trim();

                if (otherText.isEmpty()) {
                    reportInput.setError("Enter reason");
                    return;
                }

                reasons.add(otherText);
            }

            if (reasons.isEmpty()) {

                Toast.makeText(context,
                        "Select at least one reason",
                        Toast.LENGTH_SHORT).show();

                return;
            }

            String uid = user.getUid();

            Map<String, Object> report = new HashMap<>();
            report.put("postId", postId);
            report.put("reportedBy", uid);
            report.put("reasons", reasons);
            report.put("handled", false);
            report.put("timestamp", FieldValue.serverTimestamp());

            db.collection("reports")
                    .document(postId + "_" + uid)
                    .set(report)
                    .addOnSuccessListener(doc -> {

                        Toast.makeText(context,
                                "Post reported",
                                Toast.LENGTH_SHORT).show();

                        dialog.dismiss();
                    })
                    .addOnFailureListener(e ->

                            Toast.makeText(context,
                                    "Failed to report",
                                    Toast.LENGTH_SHORT).show());
        });
    }

    private void showEditDialog(posts post, ViewHolder holder) {

        EditText input = new EditText(context);
        input.setText(post.getPost());
        input.setSelection(input.getText().length());

        int padding = 32;
        input.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(context)
                .setTitle("Edit Post")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {

                    String newText = input.getText().toString().trim();

                    if (newText.isEmpty()) {
                        Toast.makeText(context, "Post can't be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    db.collection("posts")
                            .document(post.getId())
                            .update("post", newText)
                            .addOnSuccessListener(unused -> {

                                post.setPost(newText);
                                holder.description.setText(newText);

                                Toast.makeText(context, "Post updated", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(context, "Failed to update", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeletePost(String postId, int position) {

        new AlertDialog.Builder(context)
                .setTitle("Delete Post")
                .setMessage("Are you sure you want to delete this post?")
                .setPositiveButton("Delete", (dialog, which) -> {

                    db.collection("posts")
                            .document(postId)
                            .delete()
                            .addOnSuccessListener(unused -> {

                                if (position < list.size()) {
                                    list.remove(position);
                                    notifyItemRemoved(position);
                                    notifyItemRangeChanged(position, list.size());
                                }

                                Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        TextView userName,
                description,
                likeCount,
                commentCount;

        ImageView postImage,
                likeBtn,
                commentBtn,
                menuBtn,
                profileImageView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            userName =
                    itemView.findViewById(R.id.userName);

            description =
                    itemView.findViewById(
                            R.id.postDescriptionTextView);

            likeCount =
                    itemView.findViewById(R.id.likeCount);

            commentCount =
                    itemView.findViewById(R.id.commentCount);

            postImage =
                    itemView.findViewById(R.id.postImageView);

            likeBtn =
                    itemView.findViewById(R.id.likeBtn);

            commentBtn =
                    itemView.findViewById(R.id.commentBtn);

            menuBtn =
                    itemView.findViewById(R.id.menuBtn);

            profileImageView =
                    itemView.findViewById(R.id.profileImageView);
        }
    }
}