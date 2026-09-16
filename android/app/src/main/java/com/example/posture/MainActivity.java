package com.example.posture;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;
import com.capacitorjs.plugins.camera.CameraPlugin;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends BridgeActivity {

    private static final String TAG = "PostureTTS";
    private static final int PERMISSION_REQUEST = 100;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private Handler handler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(CameraPlugin.class);
        super.onCreate(savedInstanceState);

        handler = new Handler(getMainLooper());

        // 注入 JS 接口
        WebView wv = getBridge().getWebView();
        wv.addJavascriptInterface(new TTSBridge(), "Android");

        // 延迟初始化 TTS，等 Bridge 完全就绪
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                initTTS();
            }
        }, 1000);

        requestPermissions();
    }

    private void initTTS() {
        Log.d(TAG, "=== Starting TTS init ===");

        // 先检查是否有 TTS 引擎
        Intent checkIntent = new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        boolean hasEngine = getPackageManager().resolveActivity(checkIntent, 0) != null;
        Log.d(TAG, "Has TTS engine: " + hasEngine);

        if (!hasEngine) {
            Log.e(TAG, "No TTS engine found! Prompting install...");
            notifyJS(false, "No TTS engine. Please install Google TTS.");
            // 跳转到 Play Store 安装 TTS
            try {
                Intent installIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.google.android.tts"));
                startActivity(installIntent);
            } catch (Exception e) {
                Log.e(TAG, "Cannot open Play Store", e);
            }
            return;
        }

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                Log.d(TAG, "=== onInit callback! status=" + status + " ===");
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true;

                    // 列出可用语言
                    Set<Locale> langs = tts.getAvailableLanguages();
                    Log.d(TAG, "Languages: " + langs);

                    int r = tts.setLanguage(Locale.CHINESE);
                    Log.d(TAG, "setLanguage(CHINESE)=" + r);

                    tts.setSpeechRate(0.9f);
                    tts.setPitch(1.0f);

                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String id) { Log.d(TAG, "utterance START: " + id); }
                        @Override public void onDone(String id) { Log.d(TAG, "utterance DONE: " + id); }
                        @Override public void onError(String id) { Log.e(TAG, "utterance ERROR: " + id); }
                        @Override public void onError(String id, int code) { Log.e(TAG, "utterance ERROR: " + id + " code=" + code); }
                    });

                    // 测试播报
                    tts.speak("语音初始化成功", TextToSpeech.QUEUE_FLUSH, null, "init_test");
                    Log.d(TAG, "Test speak sent");

                    notifyJS(true, "OK");
                } else {
                    Log.e(TAG, "Init FAILED: " + status);
                    notifyJS(false, "Init failed: " + status);

                    // 尝试其他引擎
                    retryWithDifferentEngine();
                }
            }
        });
    }

    private void retryWithDifferentEngine() {
        Log.d(TAG, "Trying alternative TTS engine...");
        if (tts != null) {
            tts.shutdown();
        }
        // 用默认引擎重试
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                tts = new TextToSpeech(MainActivity.this, new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        Log.d(TAG, "Retry onInit status=" + status);
                        if (status == TextToSpeech.SUCCESS) {
                            ttsReady = true;
                            tts.setLanguage(Locale.CHINESE);
                            tts.setSpeechRate(0.9f);
                            notifyJS(true, "Retry OK");
                        }
                    }
                });
            }
        }, 2000);
    }

    private void notifyJS(boolean ready, String msg) {
        String js = "javascript:window._nativeTTSReady=" + ready +
                    ";console.log('[TTS] ready=" + ready + " msg=" + msg + "')";
        runOnUiThread(() -> {
            try {
                getBridge().getWebView().evaluateJavascript(js, null);
            } catch (Exception e) {
                Log.e(TAG, "evalJS failed", e);
            }
        });
    }

    public class TTSBridge {
        @JavascriptInterface
        public void speak(String text, float rate) {
            Log.d(TAG, "JS.speak: ready=" + ttsReady);
            if (tts != null && ttsReady) {
                tts.stop();
                tts.setSpeechRate(rate);
                int r = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "js_" + System.currentTimeMillis());
                Log.d(TAG, "speak result=" + r);
            } else {
                // 震动作为备选
                Log.d(TAG, "TTS not ready, vibrating");
                try {
                    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    if (v != null) v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                } catch (Exception e) {
                    Log.e(TAG, "vibrate failed", e);
                }
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
            return "ready=" + ttsReady + " tts_null=" + (tts == null);
        }
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO},
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
