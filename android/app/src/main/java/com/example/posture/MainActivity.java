package com.example.posture;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;
import com.example.posture.plugins.NativeTTSPlugin;

/**
 * 语音能力全部走 NativeTTSPlugin，不再使用 addJavascriptInterface。
 * 权限请求只负责摄像头/麦克风。
 */
public class MainActivity extends BridgeActivity {

    private static final int PERMISSION_REQUEST = 100;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(NativeTTSPlugin.class);
        super.onCreate(savedInstanceState);
        requestPermissions();
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST);
        }
    }
}
