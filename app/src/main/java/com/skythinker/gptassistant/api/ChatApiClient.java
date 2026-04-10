package com.skythinker.gptassistant.api;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.skythinker.gptassistant.tool.GlobalUtils;
import com.skythinker.gptassistant.R;
import com.unfbx.chatgpt.OpenAiStreamClient;
import com.unfbx.chatgpt.entity.chat.BaseChatCompletion;
import com.unfbx.chatgpt.entity.chat.ChatCompletionWithPicture;
import com.unfbx.chatgpt.entity.chat.Content;
import com.unfbx.chatgpt.entity.chat.FunctionCall;
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

import com.skythinker.gptassistant.data.ChatManager.ChatMessage.ChatRole;
import com.skythinker.gptassistant.data.ChatManager.ChatMessage;
import com.unfbx.chatgpt.entity.chat.tool.ToolCallFunction;
import com.unfbx.chatgpt.entity.chat.tool.ToolCalls;
import com.unfbx.chatgpt.entity.chat.tool.ToolChoice;
import com.unfbx.chatgpt.entity.chat.tool.Tools;
import com.unfbx.chatgpt.entity.chat.tool.ToolsFunction;

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

    public static class SendOptions {
        public boolean allowVision = true;
        public boolean allowTools = true;
        public boolean allowThinking = true;
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
            .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
            .connectionSpecs(Arrays.asList(ConnectionSpec.CLEARTEXT, ConnectionSpec.COMPATIBLE_TLS))
            .build();
        setApiInfo(url, apiKey);
    }

    // 重置当前流式回复相关状态，仅在用户新发起提问前调用
    public void resetReasoningState() {
        isReasoning = false;
        callingFunctions.clear();
    }

    // 向GPT发送消息列表
    public void sendPromptList(List<ChatMessage> promptList) {
        sendPromptList(promptList, new SendOptions());
    }

    public void sendPromptList(List<ChatMessage> promptList, SendOptions sendOptions) {
        if(url.isEmpty() || apiKey.isEmpty() || chatGPT == null) {
            listener.onError(context.getString(R.string.text_gpt_conf_error));
            return;
        }

        if(sendOptions == null) {
            sendOptions = new SendOptions();
        }

        BaseChatCompletion chatCompletion = null;
        List<ChatMessage> promptListForSend = buildPromptListForSend(promptList, sendOptions.allowVision, sendOptions.allowTools);

        // 发送前按消息顺序处理assistant文本中的think段，兼容被tool调用打断的情况
        final String thinkStartTag = "<think>\n";
        final String thinkEndTag = "\n</think>\n";
        ArrayList<ChatMessage> normalizedPromptList = new ArrayList<>();
        ArrayList<ChatMessage> pendingThinkMessages = new ArrayList<>();
        boolean inThink = false;
        String pendingThinkFirstVisiblePrefix = "";
        for(ChatMessage sourceMessage : promptListForSend) {
            ChatMessage message = sourceMessage.clone();
            boolean isPlainAssistant = message.role == ChatRole.ASSISTANT && message.toolCalls.size() == 0;
            if(!isPlainAssistant) {
                normalizedPromptList.add(message);
                continue;
            }

            String rawText = message.contentText == null ? "" : message.contentText;
            normalizedPromptList.add(message); // 先保留原顺序，后续若think闭合再回填内容

            if(inThink) {
                pendingThinkMessages.add(message);
                int thinkEndIndex = rawText.indexOf(thinkEndTag);
                if(thinkEndIndex == -1) { // 当前消息仍处于未闭合think中，原样保留等待后续闭合
                    continue;
                }

                for(int i = 0; i < pendingThinkMessages.size() - 1; i++) {
                    pendingThinkMessages.get(i).contentText = i == 0 ? pendingThinkFirstVisiblePrefix : "";
                }

                inThink = false;
                String remainingText = rawText.substring(thinkEndIndex + thinkEndTag.length());
                StringBuilder visibleText = new StringBuilder();
                boolean openedNewThink = false;
                while(true) {
                    int thinkStartIndex = remainingText.indexOf(thinkStartTag);
                    if(thinkStartIndex == -1) {
                        visibleText.append(remainingText);
                        message.contentText = visibleText.toString();
                        break;
                    }
                    int nextThinkEndIndex = remainingText.indexOf(thinkEndTag, thinkStartIndex + thinkStartTag.length());
                    if(nextThinkEndIndex != -1) {
                        visibleText.append(remainingText, 0, thinkStartIndex);
                        remainingText = remainingText.substring(nextThinkEndIndex + thinkEndTag.length());
                    } else { // 同一条消息中再次开启了未闭合think，保留从新起点开始的原始内容
                        visibleText.append(remainingText, 0, thinkStartIndex);
                        message.contentText = visibleText + remainingText.substring(thinkStartIndex);
                        pendingThinkMessages.clear();
                        pendingThinkMessages.add(message);
                        pendingThinkFirstVisiblePrefix = visibleText.toString();
                        inThink = true;
                        openedNewThink = true;
                        break;
                    }
                }
                if(!openedNewThink) {
                    pendingThinkMessages.clear();
                    pendingThinkFirstVisiblePrefix = "";
                }
                continue;
            }

            String remainingText = rawText;
            StringBuilder visibleText = new StringBuilder();
            while(true) {
                int thinkStartIndex = remainingText.indexOf(thinkStartTag);
                if(thinkStartIndex == -1) {
                    visibleText.append(remainingText);
                    message.contentText = visibleText.toString();
                    break;
                }
                int thinkEndIndex = remainingText.indexOf(thinkEndTag, thinkStartIndex + thinkStartTag.length());
                if(thinkEndIndex != -1) { // 同一条消息内完整闭合的think直接剥离
                    visibleText.append(remainingText, 0, thinkStartIndex);
                    remainingText = remainingText.substring(thinkEndIndex + thinkEndTag.length());
                } else { // 记录从该起点开始的未闭合think，若直到上下文末尾仍未闭合则保留原始内容
                    visibleText.append(remainingText, 0, thinkStartIndex);
                    message.contentText = visibleText + remainingText.substring(thinkStartIndex);
                    pendingThinkMessages.clear();
                    pendingThinkMessages.add(message);
                    pendingThinkFirstVisiblePrefix = visibleText.toString();
                    inThink = true;
                    break;
                }
            }
        }
        for(int i = 0; i < normalizedPromptList.size(); i++) {
            ChatMessage message = normalizedPromptList.get(i);
            if(message.role == ChatRole.ASSISTANT
                    && message.toolCalls.size() == 0
                    && (message.contentText == null || message.contentText.isEmpty())) {
                normalizedPromptList.remove(i);
                i--;
            }
        }

        boolean hasAnyAtttachment = false;
        for(ChatMessage message : normalizedPromptList) {
            if(message.attachments.size() > 0) {
                hasAnyAtttachment = true;
                break;
            }
        }

        if(!hasAnyAtttachment) { // 没有任何附件，使用普通content格式（兼容旧模型）
            ArrayList<Message> messageList = new ArrayList<>(); // 将消息数据转换为ChatGPT需要的格式
            for (ChatMessage message : normalizedPromptList) {
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
                        messageList.add(Message.builder().role(Message.Role.ASSISTANT).content(message.contentText).build());
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

            if (sendOptions.allowTools && !functions.isEmpty()) { // 如果有函数列表，则将函数列表传入
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
            for (ChatMessage message : normalizedPromptList) {
                List<Content> contentList = new ArrayList<>();
                if (message.contentText != null) {
                    contentList.add(Content.builder().type(Content.Type.TEXT.getName()).text(message.contentText).build());
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

            if (sendOptions.allowTools && !functions.isEmpty()) { // 如果有函数列表，则将函数列表传入
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
                        ArrayList<CallingFunction> functionListCopy = new ArrayList<>();
                        for(CallingFunction callingFunction : callingFunctions) {
                            if(callingFunction.toolId.isEmpty() && callingFunction.name.isEmpty() && callingFunction.arguments.isEmpty()) {
                                continue;
                            }
                            CallingFunction functionCopy = new CallingFunction();
                            functionCopy.toolId = callingFunction.toolId;
                            functionCopy.name = callingFunction.name;
                            functionCopy.arguments = callingFunction.arguments;
                            functionListCopy.add(functionCopy);
                        }
                        if(functionListCopy.isEmpty()) {
                            listener.onFinished(true);
                        } else {
                            listener.onFunctionCall(functionListCopy);
                        }
                    }
                } else { // 正在回复
//                    Log.d("ChatApiClient", "onEvent: " + data);
                    JSONObject json = new JSONObject(data);
                    if(json.containsKey("choices") && json.getJSONArray("choices").size() > 0) {
                        JSONObject delta = ((JSONObject) json.getJSONArray("choices").get(0)).getJSONObject("delta");
                        if (delta != null) {
                            if (delta.containsKey("tool_calls") && delta.getJSONArray("tool_calls") != null) { // GPT请求函数调用
                                for(int i = 0; i < delta.getJSONArray("tool_calls").size(); i++) {
                                    JSONObject toolCall = delta.getJSONArray("tool_calls").getJSONObject(i);
                                    JSONObject functionCall = toolCall.getJSONObject("function");
                                    CallingFunction targetFunction;
                                    int toolIndex = toolCall.getInt("index", -1);
                                    if(toolIndex >= 0) {
                                        while(callingFunctions.size() <= toolIndex) {
                                            callingFunctions.add(new CallingFunction());
                                        }
                                        targetFunction = callingFunctions.get(toolIndex);
                                    } else if (toolCall.containsKey("id") || (functionCall != null && functionCall.containsKey("name"))) {
                                        targetFunction = new CallingFunction();
                                        callingFunctions.add(targetFunction);
                                    } else if(callingFunctions.size() > 0) {
                                        targetFunction = callingFunctions.get(callingFunctions.size() - 1);
                                    } else {
                                        targetFunction = new CallingFunction();
                                        callingFunctions.add(targetFunction);
                                    }
                                    if(toolCall.containsKey("id") && toolCall.getStr("id") != null) {
                                        targetFunction.toolId += toolCall.getStr("id");
                                    }
                                    if(functionCall != null && functionCall.containsKey("name") && functionCall.getStr("name") != null) {
                                        targetFunction.name += functionCall.getStr("name");
                                    }
                                    if(functionCall != null && functionCall.containsKey("arguments") && functionCall.getStr("arguments") != null) {
                                        targetFunction.arguments += functionCall.getStr("arguments");
                                    }
                                }
                            }
                            if (delta.containsKey("reasoning_content") && delta.getStr("reasoning_content") != null) { // GPT返回思维链消息
                                if (!isReasoning) {
                                    isReasoning = true;
                                    listener.onMsgReceive("<think>\n");
                                }
                                listener.onMsgReceive(delta.getStr("reasoning_content"));
                            }
                            if (delta.containsKey("content") && delta.getStr("content") != null) { // GPT返回普通消息
                                if (isReasoning) {
                                    isReasoning = false;
                                    listener.onMsgReceive("\n</think>\n");
                                }
                                listener.onMsgReceive(delta.getStr("content"));
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

    // 在底层统一裁剪不支持的能力，避免界面层维护多套发送副本逻辑。
    private List<ChatMessage> buildPromptListForSend(List<ChatMessage> promptList, boolean allowVision, boolean allowTools) {
        ArrayList<ChatMessage> promptListForSend = new ArrayList<>();
        for(ChatMessage sourceMessage : promptList) {
            ChatMessage message = sourceMessage.clone();
            if(!allowTools) {
                if(message.role == ChatRole.FUNCTION) {
                    continue;
                }
                if(message.toolCalls.size() > 0) {
                    message.toolCalls.clear();
                }
                if(message.role == ChatRole.ASSISTANT
                        && (message.contentText == null || message.contentText.isEmpty())
                        && message.attachments.size() == 0) {
                    continue;
                }
            }
            if(!allowVision) {
                for(int i = message.attachments.size() - 1; i >= 0; i--) {
                    if(message.attachments.get(i).type == ChatMessage.Attachment.Type.IMAGE) {
                        message.attachments.remove(i);
                    }
                }
            }
            promptListForSend.add(message);
        }
        return promptListForSend;
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
