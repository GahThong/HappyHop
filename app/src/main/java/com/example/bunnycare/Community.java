package com.example.bunnycare;

import android.net.Uri;
import android.os.Bundle;
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

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class Community extends Fragment {

    ActivityResultLauncher<PickVisualMediaRequest> pickMedia;
    ImageView postImagePreview;
    Uri selectedUri;

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
                        }
                    }
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_community, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        ImageButton newPostButton = view.findViewById(R.id.newPost);

        newPostButton.setOnClickListener(v -> {

            BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
            View sheet = LayoutInflater.from(getContext())
                    .inflate(R.layout.fragment_new_post, null);

            dialog.setContentView(sheet);
            dialog.show();

            EditText postText = sheet.findViewById(R.id.postTextInput);
            Button postBtn = sheet.findViewById(R.id.post);
            Button chooseImg = sheet.findViewById(R.id.choosePostImage);
            postImagePreview = sheet.findViewById(R.id.postImagePreview);

            chooseImg.setOnClickListener(v1 ->
                    pickMedia.launch(new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build())
            );

            postBtn.setOnClickListener(v2 -> {

                String comment = postText.getText().toString().trim();
                Uri uri = selectedUri;

                if (uri != null) {

                    MediaManager.get()
                            .upload(uri)
                            .unsigned("ml_default")
                            .callback(new UploadCallback() {

                                @Override
                                public void onStart(String requestId) {}

                                @Override
                                public void onProgress(String requestId, long bytes, long totalBytes) {}

                                @Override
                                public void onSuccess(String requestId, Map resultData) {

                                    String imageUrl = (String) resultData.get("secure_url");
                                    savePost(imageUrl, comment);
                                    dialog.dismiss();
                                }

                                @Override
                                public void onError(String requestId, ErrorInfo error) {
                                    Toast.makeText(getContext(),
                                            "Upload failed: " + error.getDescription(),
                                            Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onReschedule(String requestId, ErrorInfo error) {}
                            })
                            .dispatch();

                } else {
                    savePost("", comment);
                    dialog.dismiss();
                }
            });
        });
    }

    private void savePost(String image, String comment) {

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> post = new HashMap<>();
        post.put("post", comment);
        post.put("postImage", image);
        post.put("posterId", user.getUid());

        db.collection("posts").add(post);
    }
}