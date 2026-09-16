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

    private static final int PERMISSION_REQUEST = 100;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private WebView webView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(CameraPlugin.class);
        super.onCreate(savedInstanceState);

        tts = new TextToSpeech(this, this);
        webView = getBridge().getWebView();
        webView.addJavascriptInterface(new TTSInterface(), "Android");
        requestPermissions();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.CHINESE);
            tts.setSpeechRate(0.9f);
            tts.setPitch(1.0f);
            ttsReady = true;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (ttsReady) {
            webView.loadUrl("javascript:window._nativeTTSReady=true");
        }
    }

    public class TTSInterface {
        @JavascriptInterface
        public void speak(String text, float rate) {
            if (tts != null && ttsReady) {
                tts.stop();
                tts.setSpeechRate(rate);
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_" + System.currentTimeMillis());
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
