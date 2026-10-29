package com.example.posture.plugins;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Locale;

/**
 * 原生 TTS 插件。
 *
 * 之前是通过 WebView.addJavascriptInterface 暴露 window.Android，但 Capacitor 在
 * super.onCreate() 里就已经开始加载页面，接口注入存在时序竞态，页面拿不到接口时
 * 会静默回退到 WebView 里并不工作的 speechSynthesis。
 * 改成标准 Capacitor 插件后，JS 侧通过 Capacitor.Plugins.NativeTTS 调用，稳定可靠。
 */
@CapacitorPlugin(name = "NativeTTS")
public class NativeTTSPlugin extends Plugin implements TextToSpeech.OnInitListener {

    private static final String TAG = "NativeTTS";

    private TextToSpeech tts;
    private volatile boolean ready = false;
    private volatile boolean failed = false;
    private volatile String reason = "";

    @Override
    public void load() {
        Context ctx = getContext();
        tts = new TextToSpeech(ctx.getApplicationContext(), this);
        // 极少数情况下引擎缺失会同步回调 onInit(FALSE)，这里补一条日志
        if (failed) {
            Log.e(TAG, "TTS unavailable: " + reason);
        }
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            failed = true;
            reason = "tts_init_failed_" + status;
            Log.e(TAG, "TTS init failed, status=" + status);
            return;
        }
        if (tts == null) {
            failed = true;
            reason = "tts_not_created";
            Log.e(TAG, "TTS init success but instance is null");
            return;
        }

        int langResult = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Chinese voice unavailable (" + langResult + "), falling back to system locale");
            reason = "missing_chinese_voice";
            langResult = tts.setLanguage(Locale.getDefault());
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                failed = true;
                reason = "no_voice_data";
                Log.e(TAG, "No usable voice for system locale either");
                return;
            }
        }

        tts.setSpeechRate(0.9f);
        tts.setPitch(1.0f);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
            }

            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "Utterance error: " + utteranceId);
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
                Log.e(TAG, "Utterance error: " + utteranceId + " code=" + errorCode);
            }
        });

        ready = true;
        Log.i(TAG, "TTS ready, language=" + currentLanguage());
    }

    private String currentLanguage() {
        try {
            if (tts != null && tts.getVoice() != null) {
                return tts.getVoice().getLocale().toLanguageTag();
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    @PluginMethod
    public void isReady(PluginCall call) {
        call.resolve(status());
    }

    @PluginMethod
    public void speak(PluginCall call) {
        String text = call.getString("text", "");
        if (text == null || text.trim().isEmpty()) {
            call.reject("empty_text");
            return;
        }
        if (!ready) {
            call.reject(failed ? (reason.isEmpty() ? "tts_unavailable" : reason) : "tts_initializing");
            return;
        }

        JSObject data = call.getData();
        float rate = (float) clamp(data.optDouble("rate", 0.9), 0.3, 2.0);
        float volume = (float) clamp(data.optDouble("volume", 1.0), 0.0, 1.0);

        tts.stop();
        tts.setSpeechRate(rate);

        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume);

        int result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "utterance_" + System.currentTimeMillis());
        if (result == TextToSpeech.ERROR) {
            call.reject("speak_failed");
            return;
        }

        JSObject res = new JSObject();
        res.put("status", "speaking");
        call.resolve(res);
    }

    @PluginMethod
    public void stop(PluginCall call) {
        if (tts != null) {
            tts.stop();
        }
        call.resolve(new JSObject().put("status", "stopped"));
    }

    private JSObject status() {
        JSObject obj = new JSObject();
        obj.put("ready", ready);
        obj.put("failed", failed);
        obj.put("reason", reason);
        obj.put("language", ready ? currentLanguage() : "");
        return obj;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected void handleOnDestroy() {
        ready = false;
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.handleOnDestroy();
    }
}
