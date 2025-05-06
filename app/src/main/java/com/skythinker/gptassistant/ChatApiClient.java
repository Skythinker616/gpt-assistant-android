package com.skythinker.gptassistant;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.unfbx.chatgpt.OpenAiStreamClient;
import com.unfbx.chatgpt.entity.chat.BaseChatCompletion;
import com.unfbx.chatgpt.entity.chat.BaseMessage;
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
import java.util.Collections;
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
import com.unfbx.chatgpt.entity.chat.tool.ToolCallFunction;
import com.unfbx.chatgpt.entity.chat.tool.ToolCalls;
import com.unfbx.chatgpt.entity.chat.tool.ToolChoice;
import com.unfbx.chatgpt.entity.chat.tool.Tools;
import com.unfbx.chatgpt.entity.chat.tool.ToolsFunction;
import com.unfbx.chatgpt.entity.whisper.WhisperResponse;

public class ChatApiClient {
    // 消息回调接口
    public interface OnReceiveListener {
        void onMsgReceive(String message);
        void onError(String message);
        void onFunctionCall(ArrayList<CallingFunction> functions);
        void onFinished(boolean completed);
    }

    public static class CallingFunction {
        public String toolId = "";
        public String name = "";
        public String arguments = "";
    }

    String url = "";
    String apiKey = "";
    String model = "";
    float temperature = 0.5f;
    OnReceiveListener listener = null;

    OkHttpClient httpClient = null;
    OpenAiStreamClient chatGPT = null;

    List<Tools> functions = new ArrayList<>();

    ArrayList<CallingFunction> callingFunctions = new ArrayList<>();

    boolean isReasoning = false;

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
        if(url.isEmpty() || apiKey.isEmpty() || chatGPT == null) {
            listener.onError(context.getString(R.string.text_gpt_conf_error));
            return;
        }

        BaseChatCompletion chatCompletion = null;

        boolean hasAnyAtttachment = false;
        for(ChatMessage message : promptList) {
            if(message.attachments.size() > 0) {
                hasAnyAtttachment = true;
                break;
            }
        }

        if(!hasAnyAtttachment) { // 没有任何附件，使用普通content格式（兼容旧模型）
            ArrayList<Message> messageList = new ArrayList<>(); // 将消息数据转换为ChatGPT需要的格式
            for (ChatMessage message : promptList) {
                if (message.role == ChatRole.SYSTEM) {
                    messageList.add(Message.builder().role(Message.Role.SYSTEM).content(message.contentText).build());
                } else if (message.role == ChatRole.USER) {
                    messageList.add(Message.builder().role(Message.Role.USER).content(message.contentText).build());
                } else if (message.role == ChatRole.ASSISTANT) {
                    if (message.toolCalls.size() > 0) {
                        if(message.toolCalls.get(0).id != null) { // 用tool方式回复
                            ArrayList<ToolCalls> toolCallsList = new ArrayList<>();
                            for(ChatMessage.ToolCall toolCall : message.toolCalls) {
                                ToolCallFunction functionCall = ToolCallFunction.builder()
                                        .name(toolCall.functionName)
                                        .arguments(toolCall.arguments)
                                        .build();
                                ToolCalls toolCalls = ToolCalls.builder()
                                        .id(toolCall.id)
                                        .type(Tools.Type.FUNCTION.getName())
                                        .function(functionCall)
                                        .build();
                                toolCallsList.add(toolCalls);
                            }
                            messageList.add(Message.builder().role(Message.Role.ASSISTANT).toolCalls(toolCallsList).content("").build());
                        } else { // 用function方式回复（历史遗留）
                            ChatMessage.ToolCall toolCall = message.toolCalls.get(0);
                            FunctionCall functionCall = FunctionCall.builder()
                                .name(toolCall.functionName)
                                .arguments(toolCall.arguments)
                                .build();
                            messageList.add(Message.builder().role(Message.Role.ASSISTANT).functionCall(functionCall).content("").build());
                        }
                    } else {
                        messageList.add(Message.builder().role(Message.Role.ASSISTANT)
                                .content(message.contentText.replaceFirst("(?s)^<think>\\n.*?\\n</think>\\n", "")).build()); // 去除思维链内容
                    }
                } else if (message.role == ChatRole.FUNCTION) {
                    ChatMessage.ToolCall toolCall = message.toolCalls.get(0);
                    if(toolCall.id != null) { // 用tool方式回复
                        messageList.add(Message.builder().role(Message.Role.TOOL).toolCallId(toolCall.id).name(toolCall.functionName).content(toolCall.content).build());
                    } else { // 用function方式回复（历史遗留）
                        messageList.add(Message.builder().role(Message.Role.FUNCTION).name(toolCall.functionName).content(toolCall.content).build());
                    }
                }
            }

            if (!functions.isEmpty()) { // 如果有函数列表，则将函数列表传入
                chatCompletion = ChatCompletion.builder()
                        .messages(messageList)
                        .model(model.replaceAll("\\*$","")) // 去掉自定义模型结尾的*号
                        .tools(functions)
                        .toolChoice(ToolChoice.Choice.AUTO.getName())
                        .temperature(temperature)
                        .build();
            } else {
                chatCompletion = ChatCompletion.builder()
                        .messages(messageList)
                        .model(model.replaceAll("\\*$","")) // 去掉自定义模型结尾的*号
                        .temperature(temperature)
                        .build();
            }
        } else { // 含有附件，使用contentList格式
            ArrayList<MessagePicture> messageList = new ArrayList<>(); // 将消息数据转换为ChatGPT需要的格式
            for (ChatMessage message : promptList) {
                List<Content> contentList = new ArrayList<>();
                if (message.contentText != null) {
                    String contentText = message.role != ChatRole.ASSISTANT ? message.contentText :
                            message.contentText.replaceFirst("(?s)^<think>\\n.*?\\n</think>\\n", ""); // 去除思维链内容
                    contentList.add(Content.builder().type(Content.Type.TEXT.getName()).text(contentText).build());
                }
                for(ChatMessage.ToolCall toolCall : message.toolCalls) { // 处理函数调用
                    if(toolCall.content != null) {
                        contentList.add(Content.builder().type(Content.Type.TEXT.getName()).text(toolCall.content).build());
                    }
                }
                for(ChatMessage.Attachment attachment : message.attachments) { // 处理附件
                    if(attachment.type == ChatMessage.Attachment.Type.IMAGE && GlobalUtils.checkVisionSupport(model)) {
                        ImageUrl imageUrl = ImageUrl.builder().url("data:image/jpeg;base64," + attachment.content).build();
                        contentList.add(Content.builder().type(Content.Type.IMAGE_URL.getName()).imageUrl(imageUrl).build());
                    } else if(attachment.type == ChatMessage.Attachment.Type.TEXT) {
                        contentList.add(Content.builder().type(Content.Type.TEXT.getName()).text(attachment.content).build());
                    }
                }
                if (message.role == ChatRole.SYSTEM) {
                    messageList.add(MessagePicture.builder().role(Message.Role.SYSTEM).content(contentList).build());
                } else if (message.role == ChatRole.USER) {
                    messageList.add(MessagePicture.builder().role(Message.Role.USER).content(contentList).build());
                } else if (message.role == ChatRole.ASSISTANT) {
                    if (message.toolCalls.size() > 0) {
                        if(message.toolCalls.get(0).id != null) { // 用tool方式回复
                            ArrayList<ToolCalls> toolCallsList = new ArrayList<>();
                            for(ChatMessage.ToolCall toolCall : message.toolCalls) {
                                ToolCallFunction functionCall = ToolCallFunction.builder()
                                        .name(toolCall.functionName)
                                        .arguments(toolCall.arguments)
                                        .build();
                                ToolCalls toolCalls = ToolCalls.builder()
                                        .id(toolCall.id)
                                        .type(Tools.Type.FUNCTION.getName())
                                        .function(functionCall)
                                        .build();
                                toolCallsList.add(toolCalls);
                            }
                            messageList.add(MessagePicture.builder().role(Message.Role.ASSISTANT).toolCalls(toolCallsList).build());
                        } else { // 用function方式回复（历史遗留）
                            ChatMessage.ToolCall toolCall = message.toolCalls.get(0);
                            FunctionCall functionCall = FunctionCall.builder()
                                    .name(toolCall.functionName)
                                    .arguments(toolCall.arguments)
                                    .build();
                            messageList.add(MessagePicture.builder().role(Message.Role.ASSISTANT).functionCall(functionCall).build());
                        }
                    } else {
                        messageList.add(MessagePicture.builder().role(Message.Role.ASSISTANT).content(contentList).build());
                    }
                } else if (message.role == ChatRole.FUNCTION) {
                    ChatMessage.ToolCall toolCall = message.toolCalls.get(0);
                    if(toolCall.id != null) { // 用tool方式回复
                        messageList.add(MessagePicture.builder().role(Message.Role.TOOL).toolCallId(toolCall.id).name(toolCall.functionName).content(contentList).build());
                    } else { // 用function方式回复（历史遗留）
                        messageList.add(MessagePicture.builder().role(Message.Role.FUNCTION).name(toolCall.functionName).content(contentList).build());
                    }
                }
            }

            if (!functions.isEmpty()) { // 如果有函数列表，则将函数列表传入
                chatCompletion = ChatCompletionWithPicture.builder()
                        .messages(messageList)
                        .model(model.replaceAll("\\*$","")) // 去掉自定义Vision模型结尾的*号
                        .tools(functions)
                        .toolChoice(ToolChoice.Choice.AUTO.getName())
                        .temperature(temperature)
                        .build();
            } else {
                chatCompletion = ChatCompletionWithPicture.builder()
                        .messages(messageList)
                        .model(model.replaceAll("\\*$","")) // 去掉自定义Vision模型结尾的*号
                        .temperature(temperature)
                        .build();
            }
        }

        callingFunctions.clear(); // 清空当前函数调用列表

        chatGPT.streamChatCompletion(chatCompletion, new EventSourceListener() { // GPT返回消息回调
            @Override
            public void onOpen(EventSource eventSource, Response response) {
                Log.d("ChatApiClient", "onOpen");
            }

            @Override
            public void onEvent(EventSource eventSource, @Nullable String id, @Nullable String type, String data) {
                if(data.equals("[DONE]")){ // 回复完成
                    Log.d("ChatApiClient", "onEvent: DONE");
                    if(callingFunctions.isEmpty()) {
                        listener.onFinished(true);
                    } else {
                        listener.onFunctionCall(callingFunctions);
                    }
                } else { // 正在回复
                    Log.d("ChatApiClient", "onEvent: " + data);
                    JSONObject json = new JSONObject(data);
                    if(json.containsKey("choices") && json.getJSONArray("choices").size() > 0) {
                        JSONObject delta = ((JSONObject) json.getJSONArray("choices").get(0)).getJSONObject("delta");
                        if (delta != null) {
                            if (delta.containsKey("tool_calls")) { // GPT请求函数调用
                                JSONObject toolCall = delta.getJSONArray("tool_calls").getJSONObject(0);
                                JSONObject functionCall = toolCall.getJSONObject("function");
                                if (toolCall.containsKey("id") && functionCall.containsKey("name")) {
                                    CallingFunction callingFunction = new CallingFunction();
                                    callingFunction.toolId = toolCall.getStr("id");
                                    callingFunction.name = functionCall.getStr("name");
                                    callingFunctions.add(callingFunction);
                                }
                                if (callingFunctions.size() > 0 && functionCall.containsKey("arguments")) {
                                    callingFunctions.get(callingFunctions.size() - 1).arguments += functionCall.getStr("arguments");
                                }
                            } else if (delta.containsKey("content") && delta.getStr("content") != null) { // GPT返回普通消息
                                if (isReasoning) {
                                    isReasoning = false;
                                    listener.onMsgReceive("\n</think>\n");
                                }
                                listener.onMsgReceive(delta.getStr("content"));
                            } else if (delta.containsKey("reasoning_content") && delta.getStr("reasoning_content") != null) { // GPT返回思维链消息
                                if (!isReasoning) {
                                    isReasoning = true;
                                    listener.onMsgReceive("<think>\n");
                                }
                                listener.onMsgReceive(delta.getStr("reasoning_content"));
                            }
                        }
                    }
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
                        Log.d("ChatApiClient", "onFailure: " + err + "\n" + Log.getStackTraceString(throwable));
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
        try {
            chatGPT = new OpenAiStreamClient.Builder()
                    .apiKey(Arrays.asList(apiKey))
                    .apiHost(url)
                    .okHttpClient(httpClient)
                    .build();
        } catch (Exception e) {
            String err = context.getString(R.string.text_gpt_conf_error);
            if(e.getMessage() != null) {
                err += ": " + e.getMessage();
            }
            listener.onError(err);
        }
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

    // 设置温度
    public void setTemperature(float temperature) { this.temperature = temperature; }

    // 添加一个函数，有同名函数则覆盖
    public void addFunction(String name, String desc, String params, String[] required) {
        removeFunction(name); // 删除同名函数

        Parameters parameters = Parameters.builder()
                .type("object")
                .properties(new JSONObject(params))
                .required(Arrays.asList(required))
                .build();

        Tools tools = Tools.builder()
                .type(Tools.Type.FUNCTION.getName())
                .function(ToolsFunction.builder()
                        .name(name)
                        .description(desc)
                        .parameters(parameters)
                        .build())
                .build();

//        Functions functions = Functions.builder()
//                .name(name)
//                .description(desc)
//                .parameters(parameters)
//                .build();

        this.functions.add(tools);
    }

    // 删除一个函数
    public void removeFunction(String name) {
        for(int i = 0; i < this.functions.size(); i++) {
            if(this.functions.get(i).getFunction().getName().equals(name)) {
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
