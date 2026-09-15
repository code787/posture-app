package com.example.posture.plugins;

import android.speech.tts.TextToSpeech;
import android.content.Context;
import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@CapacitorPlugin(name = "NativeTTS")
public class NativeTTSPlugin extends Plugin implements TextToSpeech.OnInitListener {

    private static final String TAG = "NativeTTS";
    private TextToSpeech tts;
    private boolean isInitialized = false;
    private final Queue<String> pendingQueue = new ConcurrentLinkedQueue<>();

    public NativeTTSPlugin() {
        // TTS will be initialized when the bridge is ready
    }

    @PluginMethod
    public void init(PluginCall call) {
        if (tts == null) {
            Context ctx = getContext();
            tts = new TextToSpeech(ctx, this);
        }
        JSObject result = new JSObject();
        result.put("status", "initializing");
        call.resolve(result);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.CHINESE);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Chinese not supported, using default");
                tts.setLanguage(Locale.getDefault());
            }
            tts.setSpeechRate(0.9f);
            tts.setPitch(1.0f);
            isInitialized = true;
            Log.i(TAG, "TTS initialized successfully");

            // 处理排队的消息
            while (!pendingQueue.isEmpty()) {
                String text = pendingQueue.poll();
                speakInternal(text);
            }
        } else {
            Log.e(TAG, "TTS initialization failed: " + status);
        }
    }

    @PluginMethod
    public void speak(PluginCall call) {
        String text = call.getString("text", "");
        if (text.isEmpty()) {
            call.reject("Text is empty");
            return;
        }

        if (isInitialized) {
            speakInternal(text);
            JSObject result = new JSObject();
            result.put("status", "speaking");
            call.resolve(result);
        } else {
            // 加入队列等待初始化完成
            pendingQueue.add(text);
            if (tts == null) {
                Context ctx = getContext();
                tts = new TextToSpeech(ctx, this);
            }
            JSObject result = new JSObject();
            result.put("status", "queued");
            call.resolve(result);
        }
    }

    private void speakInternal(String text) {
        if (tts != null && isInitialized) {
            tts.stop();
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_" + System.currentTimeMillis());
        }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        if (tts != null) {
            tts.stop();
        }
        JSObject result = new JSObject();
        result.put("status", "stopped");
        call.resolve(result);
    }

    @PluginMethod
    public void isSpeaking(PluginCall call) {
        boolean speaking = tts != null && tts.isSpeaking();
        JSObject result = new JSObject();
        result.put("speaking", speaking);
        call.resolve(result);
    }

    public void destroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
            isInitialized = false;
        }
    }
}
