package com.example.bunnycare;

import android.app.Application;
import com.cloudinary.android.MediaManager;
import java.util.HashMap;
import java.util.Map;

public class MyApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        initCloudinary();

    }

    private void initCloudinary() { Map config = new HashMap(); config.put("cloud_name", "dpxjb2acm"); MediaManager.init(this, config); }
}