package com.skythinker.gptassistant;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.unfbx.chatgpt.OpenAiStreamClient;
import com.unfbx.chatgpt.entity.chat.BaseChatCompletion;
import com.unfbx.chatgpt.entity.chat.ChatCompletionWithPicture;
import com.unfbx.chatgpt.entity.chat.Content;
import com.unfbx.chatgpt.entity.chat.FunctionCall;
import com.unfbx.chatgpt.entity.chat.Functions;
import com.unfbx.chatgpt.entity.chat.ImageUrl;
import com.unfbx.chatgpt.entity.chat.Message;
import com.unfbx.chatgpt.entity.chat.ChatCompletion;
import com.unfbx.chatgpt.entity.chat.MessagePicture;
import com.unfbx.chatgpt.entity.chat.Parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.hutool.json.JSONObject;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.internal.http2.StreamResetException;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;

public class ChatApiClient {
    public interface OnReceiveListener {
        void onMsgReceive(String message);
        void onError(String message);
        void onFunctionCall(String name, String arg);
        void onFinished(boolean completed);
    }

    public enum ChatRole {
        SYSTEM,
        USER,
        ASSISTANT,
        FUNCTION
    }

    public static class ChatMessage {
        public ChatRole role;
        public String contentText;
        public String contentImageBase64;
        public String functionName;
        public ChatMessage(ChatRole role) {
            this.role = role;
        }
        public ChatMessage setText(String text) {
            this.contentText = text;
            return this;
        }
        public ChatMessage setImage(String base64) {
            this.contentImageBase64 = base64;
            return this;
        }
        public ChatMessage setFunction(String name) {
            this.functionName = name;
            return this;
        }
    }

    String url = "";
    String apiKey = "";
    String model = "";
    OnReceiveListener listener = null;

    OkHttpClient httpClient = null;
    OpenAiStreamClient chatGPT = null;

    List<Functions> functions = new ArrayList<>();

    String callingFuncName = "";
    String callingFuncArg = "";

    public ChatApiClient(String url, String apiKey, String model, OnReceiveListener listener) {
        this.listener = listener;
        this.model = model;
        httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .connectionSpecs(Arrays.asList(ConnectionSpec.CLEARTEXT, ConnectionSpec.COMPATIBLE_TLS))
            .build();
        setApiInfo(url, apiKey);
    }

//    public void sendPrompt(String systemPrompt, String userPrompt) {
//        if(systemPrompt == null && userPrompt == null) {
//            listener.onError("模板和问题内容均为空");
//            return;
//        }
//
//        sendPromptList(Arrays.asList(
//            Pair.create(ChatRole.SYSTEM, systemPrompt),
//            Pair.create(ChatRole.USER, userPrompt)
//        ));
//    }

    public void sendPromptList(List<ChatMessage> promptList) {
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

        BaseChatCompletion chatCompletion = null;

        if(!model.contains("vision")) {
            ArrayList<Message> messageList = new ArrayList<>();
            for (ChatMessage message : promptList) {
                if (message.role == ChatRole.SYSTEM) {
                    messageList.add(Message.builder().role(Message.Role.SYSTEM).content(message.contentText).build());
                } else if (message.role == ChatRole.USER) {
                    messageList.add(Message.builder().role(Message.Role.USER).content(message.contentText).build());
                } else if (message.role == ChatRole.ASSISTANT) {
                    if (message.functionName != null) {
                        FunctionCall functionCall = FunctionCall.builder()
                                .name(message.functionName)
                                .arguments(message.contentText)
                                .build();
                        messageList.add(Message.builder().role(Message.Role.ASSISTANT).functionCall(functionCall).build());
                    } else {
                        messageList.add(Message.builder().role(Message.Role.ASSISTANT).content(message.contentText).build());
                    }
                } else if (message.role == ChatRole.FUNCTION) {
                    messageList.add(Message.builder().role(Message.Role.FUNCTION).name(message.functionName).content(message.contentText).build());
                }
            }

            if (!functions.isEmpty()) {
                chatCompletion = ChatCompletion.builder()
                        .messages(messageList)
                        .model(model)
                        .functions(functions)
                        .functionCall("auto")
                        .build();
            } else {
                chatCompletion = ChatCompletion.builder()
                        .messages(messageList)
                        .model(model)
                        .build();
            }
        } else {
            ArrayList<MessagePicture> messageList = new ArrayList<>();
            for (ChatMessage message : promptList) {
                List<Content> contentList = new ArrayList<>();
                if (message.contentText != null) {
                    contentList.add(Content.builder().type(Content.Type.TEXT.getName()).text(message.contentText).build());
                }
                if(message.contentImageBase64 != null) {
                    ImageUrl imageUrl = ImageUrl.builder().url("data:image/jpeg;base64," + message.contentImageBase64).build();
                    contentList.add(Content.builder().type(Content.Type.IMAGE_URL.getName()).imageUrl(imageUrl).build());
                }
                if (message.role == ChatRole.SYSTEM) {
                    messageList.add(MessagePicture.builder().role(Message.Role.SYSTEM).content(contentList).build());
                } else if (message.role == ChatRole.USER) {
                    messageList.add(MessagePicture.builder().role(Message.Role.USER).content(contentList).build());
                } else if (message.role == ChatRole.ASSISTANT) {
                    if (message.functionName != null) {
                        FunctionCall functionCall = FunctionCall.builder()
                                .name(message.functionName)
                                .arguments(message.contentText)
                                .build();
                        messageList.add(MessagePicture.builder().role(Message.Role.ASSISTANT).functionCall(functionCall).build());
                    } else {
                        messageList.add(MessagePicture.builder().role(Message.Role.ASSISTANT).content(contentList).build());
                    }
                } else if (message.role == ChatRole.FUNCTION) {
                    messageList.add(MessagePicture.builder().role(Message.Role.FUNCTION).name(message.functionName).content(contentList).build());
                }
            }

            chatCompletion = ChatCompletionWithPicture.builder()
                    .messages(messageList)
                    .model(model)
                    .build();
        }

        callingFuncName = callingFuncArg = "";

        chatGPT.streamChatCompletion(chatCompletion, new EventSourceListener() {
            @Override
            public void onOpen(EventSource eventSource, Response response) {
                Log.d("ChatApiClient", "onOpen");
            }

            @Override
            public void onEvent(EventSource eventSource, @Nullable String id, @Nullable String type, String data) {
                if(data.equals("[DONE]")){
                    Log.d("ChatApiClient", "onEvent: DONE");
                    if(callingFuncName.isEmpty()) {
                        listener.onFinished(true);
                    } else {
                        listener.onFunctionCall(callingFuncName, callingFuncArg);
                    }
                } else {
                    JSONObject delta = ((JSONObject) (new JSONObject(data)).getJSONArray("choices").get(0)).getJSONObject("delta");
                    if (delta.containsKey("function_call")) {
                        JSONObject functionCall = delta.getJSONObject("function_call");
                        if(functionCall.containsKey("name"))
                            callingFuncName = functionCall.getStr("name");
                        callingFuncArg += functionCall.getStr("arguments");
                    } else if(delta.containsKey("content")) {
                        String msg = delta.getStr("content");
                        if(msg != null)
                            listener.onMsgReceive(msg);
                    }
//                    Log.d("ChatApiClient", "onEvent: " + data);
                }
            }

            @Override
            public void onClosed(EventSource eventSource) {
                Log.d("ChatApiClient", "onClosed");
            }

            @Override
            public void onFailure(EventSource eventSource, @Nullable Throwable throwable, @Nullable Response response) {
                if(throwable != null) {
                    if(throwable instanceof StreamResetException) {
                        Log.d("ChatApiClient", "onFailure: Cancelled");
                        listener.onFinished(false);
                    } else {
                        String err = throwable.toString();
                        Log.d("ChatApiClient", "onFailure: " + err);
                        if(err.equals("java.io.IOException: Canceled")) {
                            err = "请求已取消";
                        } else if(err.equals("java.net.SocketTimeoutException: timeout")) {
                            err = "请求超时";
                        }
                        listener.onError(err);
                    }
                } else {
                    if(response != null && response.body() != null) {
                        try {
                            String err = response.body().string();
                            if(err.length() > 300) {
                                err = err.substring(0, 300);
                                err += "...";
                            }
                            listener.onError(err);
                        } catch (IOException ignore) { }
                    } else {
                        listener.onError("未知错误");
                    }
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
        chatGPT = new OpenAiStreamClient.Builder()
            .apiKey(Arrays.asList(apiKey))
            .apiHost(url)
            .okHttpClient(httpClient)
            .build();
    }

    public boolean isStreaming() {
        return httpClient.connectionPool().connectionCount() - httpClient.connectionPool().idleConnectionCount() > 0;
    }

    public void stop() {
        httpClient.dispatcher().cancelAll();
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void addFunction(String name, String desc, String params, String[] required) {
        Parameters parameters = Parameters.builder()
                .type("object")
                .properties(new JSONObject(params))
                .required(Arrays.asList(required))
                .build();

        Functions functions = Functions.builder()
                .name(name)
                .description(desc)
                .parameters(parameters)
                .build();

        this.functions.add(functions);
    }

    public void clearAllFunctions() {
        this.functions.clear();
    }
}
