package com.skythinker.gptassistant;

import android.util.Log;

import com.plexpt.chatgpt.ChatGPT;
import com.plexpt.chatgpt.ChatGPTStream;
import com.plexpt.chatgpt.entity.chat.ChatCompletion;
import com.plexpt.chatgpt.entity.chat.Message;
import com.plexpt.chatgpt.listener.AbstractStreamListener;
import com.plexpt.chatgpt.listener.ConsoleStreamListener;

import java.util.ArrayList;
import java.util.Arrays;

import okhttp3.sse.EventSource;

public class ChatApiClient {
    public interface OnReceiveListener {
        void onReceive(String message);
        void onError(String message);
        void onFinished();
    }

    String url = "";
    String apiKey = "";
    ChatGPTStream chatGPT = null;
    OnReceiveListener listener = null;

    public ChatApiClient(String url, String apiKey, OnReceiveListener listener) {
        this.listener = listener;
        setApiInfo(url, apiKey);
    }

    public void sendPrompt(String systemPrompt, String userPrompt) {
        if(url.isEmpty()) {
            listener.onError("请在设置中填写服务器地址");
            return;
        } else if(apiKey.isEmpty()) {
            listener.onError("请在设置中填写ApiKey");
            return;
        } else if(chatGPT == null) {
            listener.onError("ChatGPT初始化失败");
            return;
        }
        if(systemPrompt == null && userPrompt == null) {
            listener.onError("模板和问题内容均为空");
            return;
        }

        ArrayList<Message> messages = new ArrayList<>();
        if(systemPrompt != null) {
            messages.add(Message.ofSystem(systemPrompt));
        }
        if(userPrompt != null) {
            messages.add(Message.of(userPrompt));
        }

        ChatCompletion chatCompletion = ChatCompletion.builder()
                .messages(messages)
                .build();
        chatGPT.streamChatCompletion(chatCompletion, new AbstractStreamListener() {
            @Override
            public void onMsg(String message) {
                listener.onReceive(message);
            }

            @Override
            public void onError(Throwable throwable, String response) {
                if(throwable != null) {
                    String err = throwable.toString();
                    listener.onError(err);
                } else {
                    if(response.length() > 300) {
                        response = response.substring(0, 300);
                        response += "...";
                    }
                    listener.onError(response);
                }
            }

            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                super.onEvent(eventSource, id, type, data);
                if(data.equals("[DONE]")){
                    Log.d("ChatApiClient", "onEvent: DONE");
                    listener.onFinished();
                }
            }
        });
    }

    public void setApiInfo(String url, String apiKey) {
        if(this.url.equals(url) && this.apiKey.equals(apiKey)) {
            return;
        }
        this.url = url;
        this.apiKey = apiKey;
        chatGPT = ChatGPTStream.builder()
                .timeout(600)
                .apiKey(apiKey)
                .apiHost(url)
                .build()
                .init();
    }
}
