package com.skythinker.gptassistant;

import android.content.Context;

import com.unfbx.chatgpt.OpenAiClient;
import com.unfbx.chatgpt.entity.whisper.WhisperResponse;

import java.io.File;
import java.util.Arrays;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;

public class WhisperApiClient {
    String url = "";
    String apiKey = "";
    OkHttpClient httpClient = null;
    OpenAiClient chatGPT = null;
    Context context = null;

    public WhisperApiClient(Context context, String url, String apiKey) {
        this.context = context;
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .connectionSpecs(Arrays.asList(ConnectionSpec.CLEARTEXT, ConnectionSpec.COMPATIBLE_TLS))
                .build();
        setApiInfo(url, apiKey);
    }

    // 配置API信息
    public void setApiInfo(String url, String apiKey) {
        if(this.url.equals(url) && this.apiKey.equals(apiKey)) {
            return;
        }
        this.url = url;
        this.apiKey = apiKey;
        try {
            chatGPT = new OpenAiClient.Builder()
                    .apiKey(Arrays.asList(apiKey))
                    .apiHost(url)
                    .okHttpClient(httpClient)
                    .build();
        } catch (Exception ignored) { }
    }

    public String getWhisperResult(File file) throws Exception{
        if(chatGPT == null) {
            throw new Exception(context.getString(R.string.text_whisper_param_error));
        }
        WhisperResponse whisperResponse = chatGPT.speechToTextTranscriptions(file);
        return whisperResponse.getText();
    }
}
