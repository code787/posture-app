package com.example.posture;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;
import com.capacitorjs.plugins.camera.CameraPlugin;
import java.util.Locale;

public class MainActivity extends BridgeActivity implements TextToSpeech.OnInitListener {

    private static final int CAMERA_PERMISSION_REQUEST = 100;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(CameraPlugin.class);
        super.onCreate(savedInstanceState);

        // 初始化 TTS
        tts = new TextToSpeech(this, this);

        // 请求摄像头权限
        requestCameraPermission();

        // 注入 JavaScriptInterface
        addTTSInterface();
    }

    private void addTTSInterface() {
        // 等待 Bridge 准备好后注入接口
        getBridge().webView.post(new Runnable() {
            @Override
            public void run() {
                getBridge().webView.addJavascriptInterface(new Object() {
                    @JavascriptInterface
                    public void speak(String text, float rate, float volume) {
                        speakNative(text, rate, volume);
                    }

                    @JavascriptInterface
                    public void stop() {
                        if (tts != null) tts.stop();
                    }

                    @JavascriptInterface
                    public boolean isReady() {
                        return ttsReady;
                    }
                }, "NativeTTS");
            }
        });
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.CHINESE);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.getDefault());
            }
            tts.setSpeechRate(0.9f);
            tts.setPitch(1.0f);
            ttsReady = true;

            // 通知 WebView TTS 已就绪
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    getBridge().webView.evaluateJavascript(
                        "window._nativeTTSReady = true; console.log('[NativeTTS] Ready');", null);
                }
            });
        }
    }

    private void speakNative(final String text, final float rate, final float volume) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (tts != null && ttsReady) {
                    tts.stop();
                    tts.setSpeechRate(rate);
                    // Android TextToSpeech volume is controlled by stream, not setter
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "posture_" + System.currentTimeMillis());
                }
            }
        });
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
    public void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
