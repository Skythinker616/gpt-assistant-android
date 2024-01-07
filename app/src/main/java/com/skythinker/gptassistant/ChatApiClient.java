package com.skythinker.gptassistant;

import android.content.Context;
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

import java.io.File;
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

import com.skythinker.gptassistant.ChatManager.ChatMessage.ChatRole;
import com.skythinker.gptassistant.ChatManager.ChatMessage;
import com.unfbx.chatgpt.entity.whisper.WhisperResponse;

public class ChatApiClient {
    // 消息回调接口
    public interface OnReceiveListener {
        void onMsgReceive(String message);
        void onError(String message);
        void onFunctionCall(String name, String arg);
        void onFinished(boolean completed);
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

    Context context = null;

    public ChatApiClient(Context context, String url, String apiKey, String model, OnReceiveListener listener) {
        this.context = context;
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

    // 向GPT发送消息列表
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

        if(!model.contains("vision")) { // 使用非Vision模型
            ArrayList<Message> messageList = new ArrayList<>(); // 将消息数据转换为ChatGPT需要的格式
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

            if (!functions.isEmpty()) { // 如果有函数列表，则将函数列表传入
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
        } else { // 使用的是Vision模型
            ArrayList<MessagePicture> messageList = new ArrayList<>(); // 将消息数据转换为ChatGPT需要的格式
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

        chatGPT.streamChatCompletion(chatCompletion, new EventSourceListener() { // GPT返回消息回调
            @Override
            public void onOpen(EventSource eventSource, Response response) {
                Log.d("ChatApiClient", "onOpen");
            }

            @Override
            public void onEvent(EventSource eventSource, @Nullable String id, @Nullable String type, String data) {
                if(data.equals("[DONE]")){ // 回复完成
                    Log.d("ChatApiClient", "onEvent: DONE");
                    if(callingFuncName.isEmpty()) {
                        listener.onFinished(true);
                    } else {
                        listener.onFunctionCall(callingFuncName, callingFuncArg);
                    }
                } else { // 正在回复
                    JSONObject delta = ((JSONObject) (new JSONObject(data)).getJSONArray("choices").get(0)).getJSONObject("delta");
                    if (delta.containsKey("function_call")) { // GPT请求函数调用
                        JSONObject functionCall = delta.getJSONObject("function_call");
                        if(functionCall.containsKey("name"))
                            callingFuncName = functionCall.getStr("name");
                        callingFuncArg += functionCall.getStr("arguments");
                    } else if(delta.containsKey("content")) { // GPT返回普通消息
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
                    if(throwable instanceof StreamResetException) { // 请求被用户取消，不算错误
                        Log.d("ChatApiClient", "onFailure: Cancelled");
                        listener.onFinished(false);
                    } else {
                        String err = throwable.toString();
                        Log.d("ChatApiClient", "onFailure: " + err);
                        if(err.equals("java.io.IOException: Canceled")) { // 解释常见的错误
                            err = context.getString(R.string.text_gpt_cancel);
                        } else if(err.equals("java.net.SocketTimeoutException: timeout")) {
                            err = context.getString(R.string.text_gpt_timeout);
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
                        listener.onError(context.getString(R.string.text_gpt_unknown_error));
                    }
                }
            }
        });
    }

    // 配置API信息
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

    // 获取当前是否正在请求GPT
    public boolean isStreaming() {
        return httpClient.connectionPool().connectionCount() - httpClient.connectionPool().idleConnectionCount() > 0;
    }

    // 中断当前请求
    public void stop() {
        httpClient.dispatcher().cancelAll();
    }

    // 设置使用的模型
    public void setModel(String model) {
        this.model = model;
    }

    // 添加一个函数，有同名函数则覆盖
    public void addFunction(String name, String desc, String params, String[] required) {
        removeFunction(name); // 删除同名函数

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

    // 删除一个函数
    public void removeFunction(String name) {
        for(int i = 0; i < this.functions.size(); i++) {
            if(this.functions.get(i).getName().equals(name)) {
                this.functions.remove(i);
                break;
            }
        }
    }

    // 删除所有函数
    public void clearAllFunctions() {
        this.functions.clear();
    }
}
