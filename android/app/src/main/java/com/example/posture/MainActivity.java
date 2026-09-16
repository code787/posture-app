package com.example.posture;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;
import com.capacitorjs.plugins.camera.CameraPlugin;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends BridgeActivity implements TextToSpeech.OnInitListener {

    private static final String TAG = "PostureTTS";
    private static final int PERMISSION_REQUEST = 100;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private int initAttempts = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(CameraPlugin.class);
        super.onCreate(savedInstanceState);

        WebView wv = getBridge().getWebView();
        wv.addJavascriptInterface(new TTSBridge(), "Android");

        // 尝试初始化 TTS
        initTTS();

        requestPermissions();
    }

    private void initTTS() {
        initAttempts++;
        Log.d(TAG, "initTTS attempt #" + initAttempts);
        tts = new TextToSpeech(this, this);
    }

    @Override
    public void onInit(int status) {
        Log.d(TAG, "=== onInit called, status=" + status + " ===");

        if (status == TextToSpeech.SUCCESS) {
            // 列出所有可用引擎
            tts.setEngineByPackageName(null); // use default

            Set<Locale> langs = tts.getAvailableLanguages();
            if (langs != null) {
                Log.d(TAG, "Available langs: " + langs.toString());
                for (Locale l : langs) {
                    Log.d(TAG, "  Lang: " + l.toString());
                }
            } else {
                Log.e(TAG, "getAvailableLanguages() returned null!");
            }

            int r = tts.setLanguage(Locale.CHINESE);
            Log.d(TAG, "setLanguage(CHINESE) = " + r);

            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Chinese not supported, trying default locale");
                tts.setLanguage(Locale.getDefault());
            }

            tts.setSpeechRate(0.9f);
            tts.setPitch(1.0f);
            ttsReady = true;

            Log.d(TAG, "TTS READY! Testing speak...");
            // 立即测试
            int speakResult = tts.speak("TTS初始化成功", TextToSpeech.QUEUE_FLUSH, null, "init_test");
            Log.d(TAG, "test speak result=" + speakResult);

            notifyJS(true);
        } else {
            Log.e(TAG, "TTS init FAILED with status: " + status);
            Log.e(TAG, "ERROR = TextToSpeech.ERROR");

            // 如果失败了，尝试换引擎重试
            if (initAttempts < 3) {
                Log.d(TAG, "Retrying TTS init in 2 seconds...");
                new android.os.Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (tts != null) tts.shutdown();
                        initTTS();
                    }
                }, 2000);
            } else {
                notifyJS(false);
            }
        }
    }

    private void notifyJS(boolean ready) {
        String js = "javascript:window._nativeTTSReady=" + ready +
                    ";console.log('[TTS] Native ready=" + ready + "')";
        runOnUiThread(() -> {
            try {
                getBridge().getWebView().evaluateJavascript(js, null);
            } catch (Exception e) {
                Log.e(TAG, "eval JS failed", e);
            }
        });
    }

    public class TTSBridge {
        @JavascriptInterface
        public void speak(String text, float rate) {
            Log.d(TAG, "JS.speak: ready=" + ttsReady + " text=" + text);
            if (tts != null && ttsReady) {
                tts.stop();
                tts.setSpeechRate(rate);
                int r = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "js_" + System.currentTimeMillis());
                Log.d(TAG, "tts.speak() returned " + r);
            } else {
                Log.e(TAG, "speak called but TTS not ready! tts=" + tts);
            }
        }

        @JavascriptInterface
        public void stop() {
            if (tts != null) tts.stop();
        }

        @JavascriptInterface
        public boolean isReady() {
            return ttsReady;
        }

        @JavascriptInterface
        public String debug() {
            return "ready=" + ttsReady + " attempts=" + initAttempts +
                   " tts_null=" + (tts == null);
        }
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQUEST);
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
