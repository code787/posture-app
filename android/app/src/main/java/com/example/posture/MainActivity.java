package com.example.posture;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(CameraPlugin.class);
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate: Starting TTS init");
        tts = new TextToSpeech(this, this);

        // 注入 JS 接口
        WebView wv = getBridge().getWebView();
        wv.addJavascriptInterface(new TTSBridge(), "Android");
        Log.d(TAG, "JS Interface registered as 'Android'");

        requestPermissions();
    }

    @Override
    public void onInit(int status) {
        Log.d(TAG, "onInit status=" + status);
        if (status == TextToSpeech.SUCCESS) {
            // 检查可用语言
            Set<Locale> langs = tts.getAvailableLanguages();
            Log.d(TAG, "Available languages: " + (langs != null ? langs.toString() : "null"));

            int result = tts.setLanguage(Locale.CHINESE);
            Log.d(TAG, "setLanguage(ZH) result=" + result);

            tts.setSpeechRate(0.9f);
            tts.setPitch(1.0f);
            ttsReady = true;

            // 检查音频音量
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int vol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            Log.d(TAG, "Music volume: " + vol + "/" + maxVol);

            // 通知 JS
            final String js = "javascript:window._nativeTTSReady=true;console.log('[TTS] Native ready')";
            runOnUiThread(() -> getBridge().getWebView().evaluateJavascript(js, null));

            // 立即测试一次
            testSpeak();
        } else {
            Log.e(TAG, "TTS init FAILED: " + status);
        }
    }

    private void testSpeak() {
        Log.d(TAG, "testSpeak: speaking test phrase");
        tts.speak("测试", TextToSpeech.QUEUE_FLUSH, null, "test_init");
    }

    public class TTSBridge {
        @JavascriptInterface
        public void speak(String text, float rate) {
            Log.d(TAG, "JS speak: text=" + text + " rate=" + rate + " ready=" + ttsReady);
            if (tts != null && ttsReady) {
                tts.stop();
                tts.setSpeechRate(rate);
                int result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "js_" + System.currentTimeMillis());
                Log.d(TAG, "tts.speak returned: " + result);
            } else {
                Log.e(TAG, "speak called but tts=" + tts + " ready=" + ttsReady);
            }
        }

        @JavascriptInterface
        public void stop() {
            if (tts != null) tts.stop();
        }

        @JavascriptInterface
        public boolean isReady() {
            Log.d(TAG, "isReady=" + ttsReady);
            return ttsReady;
        }

        @JavascriptInterface
        public String debug() {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int vol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int alarmVol = am.getStreamVolume(AudioManager.STREAM_ALARM);
            boolean muted = am.isStreamMute(AudioManager.STREAM_MUSIC);
            return "TTS_ready=" + ttsReady +
                   " music_vol=" + vol + "/" + maxVol +
                   " alarm_vol=" + alarmVol +
                   " music_muted=" + muted;
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
