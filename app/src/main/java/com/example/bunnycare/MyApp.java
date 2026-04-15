package com.example.bunnycare;

import android.app.Application;
import com.cloudinary.android.MediaManager;
import java.util.HashMap;
import java.util.Map;

public class MyApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        Map<String, Object> config = new HashMap<>();
        config.put("cloud_name", "dpxjb2acm");
        config.put("api_key", "658123376666118");

        MediaManager.init(this, config);
    }
}