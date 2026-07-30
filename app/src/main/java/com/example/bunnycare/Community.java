package com.example.bunnycare;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
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

public class Community extends Fragment {

    ActivityResultLauncher<PickVisualMediaRequest> pickMedia;
    ImageView postImagePreview;
    Uri selectedUri;

    List<posts> postList;
    PostsAdapter adapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pickMedia = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null) {
                        selectedUri = uri;

                        if (postImagePreview != null) {
                            postImagePreview.setImageURI(uri);
                            postImagePreview.setVisibility(View.VISIBLE);
                        }
                    }
                }
        );
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_community, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {

        RecyclerView recyclerView =
                view.findViewById(R.id.recyclerView);

        postList = new ArrayList<>();
        adapter = new PostsAdapter(getContext(), postList);

        recyclerView.setLayoutManager(
                new LinearLayoutManager(getContext())
        );

        recyclerView.setAdapter(adapter);

        FirebaseFirestore db =
                FirebaseFirestore.getInstance();

        db.collection("posts")
                .orderBy(
                        "timestamp",
                        Query.Direction.DESCENDING
                )
                .addSnapshotListener((value, error) -> {

                    if (error != null || value == null) {
                        return;
                    }

                    postList.clear();

                    for (DocumentSnapshot doc :
                            value.getDocuments()) {

                        posts p =
                                doc.toObject(posts.class);

                        if (p != null) {
                            p.setId(doc.getId());
                            postList.add(p);
                        }
                    }

                    adapter.notifyDataSetChanged();
                });

        ImageButton newPostButton =
                view.findViewById(R.id.newPost);

        newPostButton.setOnClickListener(v -> {

            selectedUri = null;

            BottomSheetDialog dialog =
                    new BottomSheetDialog(requireContext());

            View sheet = LayoutInflater
                    .from(getContext())
                    .inflate(
                            R.layout.fragment_new_post,
                            null
                    );

            dialog.setContentView(sheet);
            dialog.show();

            EditText postText =
                    sheet.findViewById(
                            R.id.postTextInput
                    );

            Button postBtn =
                    sheet.findViewById(R.id.post);

            View chooseImg =
                    sheet.findViewById(
                            R.id.choosePostImage
                    );

            postImagePreview =
                    sheet.findViewById(
                            R.id.postImagePreview
                    );

            chooseImg.setOnClickListener(v1 ->
                    pickMedia.launch(
                            new PickVisualMediaRequest
                                    .Builder()
                                    .setMediaType(
                                            ActivityResultContracts
                                                    .PickVisualMedia
                                                    .ImageOnly
                                                    .INSTANCE
                                    )
                                    .build()
                    )
            );

            postBtn.setOnClickListener(v2 -> {

                String rawComment =
                        postText.getText()
                                .toString()
                                .trim();

                String comment =
                        filterProfanity(rawComment);

                Uri uri = selectedUri;

                if (rawComment.isEmpty()
                        && uri == null) {

                    Toast.makeText(
                            getContext(),
                            "Post cannot be empty",
                            Toast.LENGTH_SHORT
                    ).show();

                    postText.requestFocus();
                    return;
                }

                postBtn.setEnabled(false);

                if (uri != null) {

                    MediaManager.get()
                            .upload(uri)
                            .unsigned("ml_default")
                            .callback(
                                    new UploadCallback() {

                                        @Override
                                        public void onStart(
                                                String requestId
                                        ) {}

                                        @Override
                                        public void onProgress(
                                                String requestId,
                                                long bytes,
                                                long totalBytes
                                        ) {}

                                        @Override
                                        public void onSuccess(
                                                String requestId,
                                                Map resultData
                                        ) {

                                            String imageUrl =
                                                    (String)
                                                            resultData.get(
                                                                    "secure_url"
                                                            );

                                            savePost(
                                                    imageUrl,
                                                    comment
                                            );

                                            dialog.dismiss();
                                        }

                                        @Override
                                        public void onError(
                                                String requestId,
                                                ErrorInfo error
                                        ) {

                                            postBtn.setEnabled(true);

                                            Log.e(
                                                    "UPLOAD_ERROR",
                                                    error.getDescription()
                                            );

                                            Toast.makeText(
                                                    getContext(),
                                                    "Image upload failed",
                                                    Toast.LENGTH_SHORT
                                            ).show();
                                        }

                                        @Override
                                        public void onReschedule(
                                                String requestId,
                                                ErrorInfo error
                                        ) {}
                                    }
                            )
                            .dispatch();

                } else {

                    savePost("", comment);
                    dialog.dismiss();
                }

                selectedUri = null;
            });
        });
    }

    private void savePost(
            String image,
            String comment
    ) {

        FirebaseUser user =
                FirebaseAuth.getInstance()
                        .getCurrentUser();

        if (user == null) {
            return;
        }

        FirebaseFirestore db =
                FirebaseFirestore.getInstance();

        db.collection("users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(
                        documentSnapshot -> {

                            String username =
                                    "Unknown";

                            if (documentSnapshot.exists()) {

                                String fetchedUsername =
                                        documentSnapshot
                                                .getString(
                                                        "username"
                                                );

                                if (fetchedUsername
                                        != null) {

                                    username =
                                            fetchedUsername;
                                }
                            }

                            Map<String, Object> post =
                                    new HashMap<>();

                            post.put(
                                    "post",
                                    comment
                            );

                            post.put(
                                    "postImage",
                                    image
                            );

                            post.put(
                                    "posterId",
                                    user.getUid()
                            );

                            post.put(
                                    "posterName",
                                    username
                            );

                            post.put(
                                    "likesCount",
                                    0
                            );

                            post.put(
                                    "commentCount",
                                    0
                            );

                            post.put(
                                    "timestamp",
                                    FieldValue
                                            .serverTimestamp()
                            );

                            db.collection("posts")
                                    .add(post)
                                    .addOnSuccessListener(
                                            unused -> {

                                                Toast.makeText(
                                                        getContext(),
                                                        "Posted successfully",
                                                        Toast.LENGTH_SHORT
                                                ).show();
                                            }
                                    );
                        });
    }

    private String filterProfanity(
            String text
    ) {

        if (text == null) {
            return "";
        }

        String[] badWords = {
                "fuck",
                "shit",
                "bitch",
                "asshole",
                "damn",
                "motherfucker",
                "bastard",
        };

        String filtered = text;

        for (String word : badWords) {

            String regex =
                    "(?i)\\b"
                            + word
                            + "\\b";

            String replacement =
                    new String(
                            new char[word.length()]
                    ).replace('\0', '*');

            filtered =
                    filtered.replaceAll(
                            regex,
                            replacement
                    );
        }

        return filtered;
    }
}