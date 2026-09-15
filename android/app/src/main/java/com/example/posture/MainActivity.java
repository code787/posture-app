package com.example.posture;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;
import com.capacitorjs.plugins.camera.CameraPlugin;
import com.example.posture.plugins.NativeTTSPlugin;

public class MainActivity extends BridgeActivity {
  
  private static final int CAMERA_PERMISSION_REQUEST = 100;
  private NativeTTSPlugin ttsPlugin;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(CameraPlugin.class);
    registerPlugin(NativeTTSPlugin.class);
    super.onCreate(savedInstanceState);
    
    // 请求摄像头权限
    requestCameraPermission();
  }
  
  private void requestCameraPermission() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
        != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, 
        new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 
        CAMERA_PERMISSION_REQUEST);
    }
  }
  
  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }
  
  @Override
  public void onDestroy() {
    super.onDestroy();
  }
}
