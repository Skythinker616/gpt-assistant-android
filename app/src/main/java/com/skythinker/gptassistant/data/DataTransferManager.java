package com.skythinker.gptassistant.data;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import com.skythinker.gptassistant.BuildConfig;
import com.skythinker.gptassistant.R;
import com.skythinker.gptassistant.data.ChatManager.ChatMessage;
import com.skythinker.gptassistant.data.ChatManager.ChatMessage.Attachment;
import com.skythinker.gptassistant.data.ChatManager.ChatMessage.ChatRole;
import com.skythinker.gptassistant.data.ChatManager.ChatMessage.ToolCall;
import com.skythinker.gptassistant.data.ChatManager.Conversation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import cn.hutool.crypto.digest.MD5;

public class DataTransferManager {

    private static final int FORMAT_VERSION = 2;
    private static final String ENTRY_MANIFEST = "manifest.json";
    private static final String ENTRY_LLM = "llm.json";
    private static final String ENTRY_ASR = "asr.json";
    private static final String ENTRY_TEMPLATES = "templates.json";
    private static final String ENTRY_CONVERSATIONS = "conversations.json";
    private static final String ENTRY_ATTACHMENT_IMAGES = "attachments/images/";
    private static final String ENTRY_ATTACHMENT_TEXTS = "attachments/texts/";

    public static class Options {
        public boolean includeLlm = true;
        public boolean includeAsr = true;
        public boolean includeTemplates = true;
        public boolean includeChats = true;

        public boolean isEmpty() {
            return !includeLlm && !includeAsr && !includeTemplates && !includeChats;
        }
    }

    public static class ExportResult {
        public boolean llmExported;
        public boolean asrExported;
        public int templateCount;
        public int conversationCount;
        public int attachmentCount;
    }

    public static class ImportResult {
        public boolean llmImported;
        public boolean asrImported;
        public int importedTemplateCount;
        public int skippedTemplateCount;
        public int renamedTemplateCount;
        public int importedConversationCount;
        public int skippedConversationCount;
    }

    private DataTransferManager() { }

    public static ExportResult exportToZip(Context context, Uri uri, Options options) throws Exception {
        GlobalDataHolder.init(context);
        ChatMessage.setContext(context);

        OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
        if(outputStream == null) {
            throw new IllegalStateException("Failed to open output stream.");
        }

        ExportResult result = new ExportResult();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            if(options.includeLlm) {
                writeJsonEntry(zipOutputStream, ENTRY_LLM, buildLlmJson());
                result.llmExported = true;
            }
            if(options.includeAsr) {
                writeJsonEntry(zipOutputStream, ENTRY_ASR, buildAsrJson());
                result.asrExported = true;
            }
            if(options.includeTemplates) {
                JSONArray templatesJson = buildTemplatesJson();
                writeJsonEntry(zipOutputStream, ENTRY_TEMPLATES, templatesJson);
                result.templateCount = templatesJson.length();
            }
            if(options.includeChats) {
                ChatManager chatManager = new ChatManager(context);
                try {
                    JSONArray conversationsJson = new JSONArray();
                    Set<String> writtenAttachmentEntries = new HashSet<>();
                    for(Conversation conversation : chatManager.getAllConversations()) {
                        conversationsJson.put(conversationToJson(conversation, zipOutputStream, writtenAttachmentEntries, result));
                    }
                    writeJsonEntry(zipOutputStream, ENTRY_CONVERSATIONS, conversationsJson);
                    result.conversationCount = conversationsJson.length();
                } finally {
                    chatManager.destroy();
                }
            }
            writeJsonEntry(zipOutputStream, ENTRY_MANIFEST, buildManifestJson(options, result));
            zipOutputStream.finish();
        }
        return result;
    }

    public static ImportResult importFromZip(Context context, Uri uri, Options options) throws Exception {
        GlobalDataHolder.init(context);
        ChatMessage.setContext(context);

        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if(inputStream == null) {
            throw new IllegalStateException("Failed to open input stream.");
        }

        File tempDir = new File(context.getCacheDir(), "data_transfer_import_" + System.currentTimeMillis());
        tempDir.mkdirs();
        try {
            unzipToTempDir(tempDir, inputStream);

            ImportResult result = new ImportResult();
            File llmFile = new File(tempDir, ENTRY_LLM);
            File asrFile = new File(tempDir, ENTRY_ASR);
            File templatesFile = new File(tempDir, ENTRY_TEMPLATES);
            File conversationsFile = new File(tempDir, ENTRY_CONVERSATIONS);

            if(options.includeLlm && llmFile.exists()) {
                importLlmJson(new JSONObject(readFileAsString(llmFile)));
                result.llmImported = true;
            }
            if(options.includeAsr && asrFile.exists()) {
                importAsrJson(new JSONObject(readFileAsString(asrFile)));
                result.asrImported = true;
            }
            if(options.includeTemplates && templatesFile.exists()) {
                importTemplatesJson(context, new JSONArray(readFileAsString(templatesFile)), result);
            }
            if(options.includeChats && conversationsFile.exists()) {
                importConversationsJson(context, new JSONArray(readFileAsString(conversationsFile)), tempDir, result);
            }
            return result;
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static JSONObject buildManifestJson(Options options, ExportResult result) throws Exception {
        JSONObject manifestJson = new JSONObject();
        manifestJson.put("formatVersion", FORMAT_VERSION);
        manifestJson.put("appVersion", BuildConfig.VERSION_NAME);
        manifestJson.put("exportedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        manifestJson.put("llm", options.includeLlm);
        manifestJson.put("asr", options.includeAsr);
        manifestJson.put("templates", options.includeTemplates);
        manifestJson.put("conversations", options.includeChats);
        manifestJson.put("templateCount", result.templateCount);
        manifestJson.put("conversationCount", result.conversationCount);
        manifestJson.put("attachmentCount", result.attachmentCount);
        return manifestJson;
    }

    private static JSONObject buildLlmJson() throws Exception {
        JSONObject llmJson = new JSONObject();
        llmJson.put("apiHost", GlobalDataHolder.getGptApiHost());
        llmJson.put("apiKey", GlobalDataHolder.getGptApiKey());
        llmJson.put("model", GlobalDataHolder.getGptModel());
        llmJson.put("temperature", GlobalDataHolder.getGptTemperature());
        llmJson.put("maxContextNum", GlobalDataHolder.getGptMaxContextNum());

        JSONArray customModelsJson = new JSONArray();
        for(CustomModelProfile profile : GlobalDataHolder.getCustomModelProfiles()) {
            customModelsJson.put(profile.toJson());
        }
        llmJson.put("customModelProfiles", customModelsJson);
        return llmJson;
    }

    private static JSONObject buildAsrJson() throws Exception {
        JSONObject asrJson = new JSONObject();
        asrJson.put("useWhisper", GlobalDataHolder.getAsrUseWhisper());
        asrJson.put("useBaidu", GlobalDataHolder.getAsrUseBaidu());
        asrJson.put("useGoogle", GlobalDataHolder.getAsrUseGoogle());
        asrJson.put("appId", GlobalDataHolder.getAsrAppId());
        asrJson.put("apiKey", GlobalDataHolder.getAsrApiKey());
        asrJson.put("secretKey", GlobalDataHolder.getAsrSecretKey());
        asrJson.put("useRealTime", GlobalDataHolder.getAsrUseRealTime());
        return asrJson;
    }

    private static JSONArray buildTemplatesJson() throws Exception {
        JSONArray templatesJson = new JSONArray();
        for(PromptTabData tabData : GlobalDataHolder.getTabDataList()) {
            JSONObject templateJson = new JSONObject();
            templateJson.put("title", tabData.getTitle());
            templateJson.put("prompt", tabData.getPrompt());
            templatesJson.put(templateJson);
        }
        return templatesJson;
    }

    private static JSONObject conversationToJson(
            Conversation conversation,
            ZipOutputStream zipOutputStream,
            Set<String> writtenAttachmentEntries,
            ExportResult result
    ) throws Exception {
        JSONObject conversationJson = new JSONObject();
        conversationJson.put("time", conversation.time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        conversationJson.put("title", conversation.title);

        JSONArray messagesJson = new JSONArray();
        for(ChatMessage message : conversation.messages) {
            messagesJson.put(chatMessageToJson(message, zipOutputStream, writtenAttachmentEntries, result));
        }
        conversationJson.put("messages", messagesJson);
        return conversationJson;
    }

    private static JSONObject chatMessageToJson(
            ChatMessage message,
            ZipOutputStream zipOutputStream,
            Set<String> writtenAttachmentEntries,
            ExportResult result
    ) throws Exception {
        JSONObject messageJson = new JSONObject();
        messageJson.put("role", message.role.name());
        if(message.contentText != null) {
            messageJson.put("text", message.contentText);
        }

        if(message.toolCalls.size() > 0) {
            JSONArray toolsJson = new JSONArray();
            for(ToolCall toolCall : message.toolCalls) {
                JSONObject toolJson = new JSONObject();
                if(toolCall.id != null) {
                    toolJson.put("id", toolCall.id);
                }
                if(toolCall.functionName != null) {
                    toolJson.put("function", toolCall.functionName);
                }
                if(toolCall.arguments != null) {
                    toolJson.put("arguments", toolCall.arguments);
                }
                if(toolCall.content != null) {
                    toolJson.put("content", toolCall.content);
                }
                toolsJson.put(toolJson);
            }
            messageJson.put("tools", toolsJson);
        }

        if(message.attachments.size() > 0) {
            JSONArray attachmentsJson = new JSONArray();
            for(Attachment attachment : message.attachments) {
                if(attachment.uuid == null || attachment.uuid.isEmpty()) {
                    attachment.uuid = UUID.randomUUID().toString();
                }
                String entryName = getAttachmentEntryName(attachment.uuid, attachment.type);

                JSONObject attachmentJson = new JSONObject();
                attachmentJson.put("uuid", attachment.uuid);
                attachmentJson.put("name", attachment.name);
                attachmentJson.put("type", attachment.type.name());
                attachmentJson.put("entry", entryName);
                attachmentsJson.put(attachmentJson);

                if(writtenAttachmentEntries.add(entryName)) {
                    writeAttachmentEntry(zipOutputStream, entryName, attachment);
                    result.attachmentCount++;
                }
            }
            messageJson.put("attachments", attachmentsJson);
        }
        return messageJson;
    }

    private static void importLlmJson(JSONObject llmJson) {
        String host = llmJson.has("apiHost") ? llmJson.optString("apiHost", GlobalDataHolder.getGptApiHost()) : GlobalDataHolder.getGptApiHost();
        String key = llmJson.has("apiKey") ? llmJson.optString("apiKey", GlobalDataHolder.getGptApiKey()) : GlobalDataHolder.getGptApiKey();
        String model = llmJson.has("model") ? llmJson.optString("model", GlobalDataHolder.getGptModel()) : GlobalDataHolder.getGptModel();
        List<CustomModelProfile> customModels = new ArrayList<>(GlobalDataHolder.getCustomModelProfiles());
        if(llmJson.has("customModelProfiles")) {
            customModels.clear();
            JSONArray customModelsJson = llmJson.optJSONArray("customModelProfiles");
            if(customModelsJson != null) {
                for(int i = 0; i < customModelsJson.length(); i++) {
                    JSONObject customModelJson = customModelsJson.optJSONObject(i);
                    if(customModelJson != null) {
                        CustomModelProfile profile = CustomModelProfile.fromJson(customModelJson);
                        if(!profile.id.isEmpty()) {
                            customModels.add(profile);
                        }
                    }
                }
            }
        }
        GlobalDataHolder.saveGptApiInfo(host, key, model, customModels);

        float temperature = llmJson.has("temperature")
                ? (float) llmJson.optDouble("temperature", GlobalDataHolder.getGptTemperature())
                : GlobalDataHolder.getGptTemperature();
        int maxContextNum = llmJson.has("maxContextNum")
                ? llmJson.optInt("maxContextNum", GlobalDataHolder.getGptMaxContextNum())
                : GlobalDataHolder.getGptMaxContextNum();
        GlobalDataHolder.saveModelParams(temperature, maxContextNum);
    }

    private static void importAsrJson(JSONObject asrJson) {
        boolean useWhisper = asrJson.has("useWhisper") ? asrJson.optBoolean("useWhisper", GlobalDataHolder.getAsrUseWhisper()) : GlobalDataHolder.getAsrUseWhisper();
        boolean useBaidu = asrJson.has("useBaidu") ? asrJson.optBoolean("useBaidu", GlobalDataHolder.getAsrUseBaidu()) : GlobalDataHolder.getAsrUseBaidu();
        boolean useGoogle = asrJson.has("useGoogle") ? asrJson.optBoolean("useGoogle", GlobalDataHolder.getAsrUseGoogle()) : GlobalDataHolder.getAsrUseGoogle();
        GlobalDataHolder.saveAsrSelection(useWhisper, useBaidu, useGoogle);

        String appId = asrJson.has("appId") ? asrJson.optString("appId", GlobalDataHolder.getAsrAppId()) : GlobalDataHolder.getAsrAppId();
        String apiKey = asrJson.has("apiKey") ? asrJson.optString("apiKey", GlobalDataHolder.getAsrApiKey()) : GlobalDataHolder.getAsrApiKey();
        String secretKey = asrJson.has("secretKey") ? asrJson.optString("secretKey", GlobalDataHolder.getAsrSecretKey()) : GlobalDataHolder.getAsrSecretKey();
        boolean useRealTime = asrJson.has("useRealTime") ? asrJson.optBoolean("useRealTime", GlobalDataHolder.getAsrUseRealTime()) : GlobalDataHolder.getAsrUseRealTime();
        GlobalDataHolder.saveBaiduAsrInfo(appId, apiKey, secretKey, useRealTime);
    }

    private static void importTemplatesJson(Context context, JSONArray templatesJson, ImportResult result) {
        List<PromptTabData> templateList = GlobalDataHolder.getTabDataList();
        for(int i = 0; i < templatesJson.length(); i++) {
            JSONObject templateJson = templatesJson.optJSONObject(i);
            if(templateJson == null) {
                continue;
            }

            String title = templateJson.optString("title", "");
            String prompt = templateJson.optString("prompt", "");
            if(containsSameTemplate(templateList, title, prompt)) {
                result.skippedTemplateCount++;
                continue;
            }
            if(containsSameTitle(templateList, title)) {
                title = buildImportedTemplateTitle(context, templateList, title);
                result.renamedTemplateCount++;
            }

            templateList.add(new PromptTabData(title, prompt));
            result.importedTemplateCount++;
        }
        if(result.importedTemplateCount > 0) {
            GlobalDataHolder.saveTabDataList();
        }
    }

    private static void importConversationsJson(Context context, JSONArray conversationsJson, File tempDir, ImportResult result) {
        ChatManager chatManager = new ChatManager(context);
        try {
            Set<String> conversationFingerprints = new HashSet<>();
            chatManager.forEachConversation(false, conversation -> {
                conversationFingerprints.add(buildConversationFingerprint(conversation));
                clearConversationAttachmentContent(conversation);
            });

            for(int i = 0; i < conversationsJson.length(); i++) {
                JSONObject conversationJson = conversationsJson.optJSONObject(i);
                if(conversationJson == null) {
                    continue;
                }

                Conversation conversation = parseConversationJson(context, conversationJson, tempDir);
                String fingerprint = buildConversationFingerprint(conversation);
                if(conversationFingerprints.contains(fingerprint)) {
                    clearConversationAttachmentContent(conversation);
                    result.skippedConversationCount++;
                    continue;
                }

                chatManager.addConversation(conversation);
                conversationFingerprints.add(fingerprint);
                clearConversationAttachmentContent(conversation);
                result.importedConversationCount++;
            }
        } finally {
            chatManager.destroy();
        }
    }

    private static Conversation parseConversationJson(Context context, JSONObject conversationJson, File tempDir) {
        Conversation conversation = new Conversation();
        conversation.title = conversationJson.optString("title", "");
        try {
            conversation.time = LocalDateTime.parse(conversationJson.optString("time"), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            conversation.time = LocalDateTime.now();
        }

        conversation.messages.clear();
        JSONArray messagesJson = conversationJson.optJSONArray("messages");
        if(messagesJson != null) {
            for(int i = 0; i < messagesJson.length(); i++) {
                JSONObject messageJson = messagesJson.optJSONObject(i);
                if(messageJson != null) {
                    conversation.messages.add(parseMessageJson(context, messageJson, tempDir));
                }
            }
        }
        return conversation;
    }

    private static ChatMessage parseMessageJson(Context context, JSONObject messageJson, File tempDir) {
        ChatRole role = ChatRole.fromName(messageJson.optString("role", ChatRole.USER.name()));
        if(role == null) {
            role = ChatRole.USER;
        }

        ChatMessage message = new ChatMessage(role);
        message.contentText = getOptionalString(messageJson, "text");

        JSONArray toolsJson = messageJson.optJSONArray("tools");
        if(toolsJson != null) {
            for(int i = 0; i < toolsJson.length(); i++) {
                JSONObject toolJson = toolsJson.optJSONObject(i);
                if(toolJson == null) {
                    continue;
                }

                ToolCall toolCall = new ToolCall();
                toolCall.id = getOptionalString(toolJson, "id");
                toolCall.functionName = getOptionalString(toolJson, "function");
                toolCall.arguments = getOptionalString(toolJson, "arguments");
                toolCall.content = getOptionalString(toolJson, "content");
                message.addFunctionCall(toolCall);
            }
        }

        JSONArray attachmentsJson = messageJson.optJSONArray("attachments");
        if(attachmentsJson != null) {
            for(int i = 0; i < attachmentsJson.length(); i++) {
                JSONObject attachmentJson = attachmentsJson.optJSONObject(i);
                if(attachmentJson == null) {
                    continue;
                }

                Attachment attachment = parseAttachmentJson(context, attachmentJson, tempDir);
                if(attachment != null) {
                    message.addAttachment(attachment);
                }
            }
        }
        return message;
    }

    private static Attachment parseAttachmentJson(Context context, JSONObject attachmentJson, File tempDir) {
        Attachment.Type type;
        try {
            type = Attachment.Type.valueOf(attachmentJson.optString("type", Attachment.Type.TEXT.name()));
        } catch (Exception e) {
            type = Attachment.Type.TEXT;
        }

        String uuid = attachmentJson.optString("uuid", UUID.randomUUID().toString());
        String entryName = attachmentJson.optString("entry", getAttachmentEntryName(uuid, type));
        File attachmentFile = resolveZipEntryFile(tempDir, entryName);

        String content = null;
        if(attachmentFile.exists()) {
            try {
                byte[] attachmentBytes = readAllBytes(new FileInputStream(attachmentFile));
                if (type == Attachment.Type.IMAGE) {
                    content = Base64.encodeToString(attachmentBytes, Base64.NO_WRAP);
                } else {
                    content = new String(attachmentBytes, StandardCharsets.UTF_8);
                }
            }catch (Exception e) {
                Log.e("DataTransfer", "Failed to read attachment file: " + attachmentFile.getAbsolutePath(), e);
            }
        } else if(attachmentJson.has("content")) {
            content = attachmentJson.optString("content", null);
        }

        if(content == null) {
            return null;
        }

        Attachment attachment = new Attachment();
        attachment.uuid = uuid;
        attachment.type = type;
        attachment.name = getOptionalString(attachmentJson, "name");
        attachment.content = content;
        resolveAttachmentUuidConflict(context, attachment);
        return attachment;
    }

    private static String buildConversationFingerprint(Conversation conversation) {
        // 聊天记录去重仅比较“实际内容”，不比较数据库 id 和附件 uuid。
        JSONObject normalizedConversationJson = new JSONObject();

        try {
            normalizedConversationJson.put("time", conversation.time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            normalizedConversationJson.put("title", conversation.title);

            JSONArray messagesJson = new JSONArray();
            for (ChatMessage message : conversation.messages) {
                JSONObject normalizedMessageJson = new JSONObject();
                normalizedMessageJson.put("role", message.role.name());
                normalizedMessageJson.put("text", message.contentText);

                JSONArray toolsJson = new JSONArray();
                for (ToolCall toolCall : message.toolCalls) {
                    JSONObject normalizedToolJson = new JSONObject();
                    normalizedToolJson.put("function", toolCall.functionName);
                    normalizedToolJson.put("arguments", toolCall.arguments);
                    normalizedToolJson.put("content", toolCall.content);
                    toolsJson.put(normalizedToolJson);
                }
                normalizedMessageJson.put("tools", toolsJson);

                JSONArray attachmentsJson = new JSONArray();
                for (Attachment attachment : message.attachments) {
                    if(attachment.content == null) {
                        attachment.loadFile();
                    }
                    JSONObject normalizedAttachmentJson = new JSONObject();
                    normalizedAttachmentJson.put("type", attachment.type.name());
                    normalizedAttachmentJson.put("name", attachment.name);
                    normalizedAttachmentJson.put("content", MD5.create().digestHex(attachment.content == null ? "" : attachment.content));
                    attachmentsJson.put(normalizedAttachmentJson);
                }
                normalizedMessageJson.put("attachments", attachmentsJson);
                messagesJson.put(normalizedMessageJson);
            }
            normalizedConversationJson.put("messages", messagesJson);
        } catch (JSONException e) {
            e.printStackTrace(); // 不太可能发生，除非 Conversation 对象被篡改了。
        }

        return MD5.create().digestHex(normalizedConversationJson.toString());
    }

    private static void resolveAttachmentUuidConflict(Context context, Attachment attachment) {
        // 默认复用原 uuid，只有真的撞到同名不同内容文件时才重新分配。
        while(true) {
            File localFile = new File(getLocalAttachmentPath(context, attachment.uuid, attachment.type));
            if(!localFile.exists()) {
                return;
            }

            Attachment existingAttachment = Attachment.loadExist(attachment.uuid, attachment.name, attachment.type, true);
            if(Objects.equals(existingAttachment.content, attachment.content)) {
                return;
            }
            attachment.uuid = UUID.randomUUID().toString();
        }
    }

    private static boolean containsSameTemplate(List<PromptTabData> templateList, String title, String prompt) {
        for(PromptTabData tabData : templateList) {
            if(Objects.equals(tabData.getTitle(), title) && Objects.equals(tabData.getPrompt(), prompt)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSameTitle(List<PromptTabData> templateList, String title) {
        for(PromptTabData tabData : templateList) {
            if(Objects.equals(tabData.getTitle(), title)) {
                return true;
            }
        }
        return false;
    }

    private static String buildImportedTemplateTitle(Context context, List<PromptTabData> templateList, String title) {
        String baseTitle = (title == null || title.isEmpty())
                ? context.getString(R.string.data_transfer_template_default_title)
                : title;
        for(int i = 1; ; i++) {
            String candidate = context.getString(R.string.data_transfer_imported_suffix, baseTitle, i);
            if(!containsSameTitle(templateList, candidate)) {
                return candidate;
            }
        }
    }

    private static void writeAttachmentEntry(ZipOutputStream zipOutputStream, String entryName, Attachment attachment) throws Exception {
        byte[] bytes = getAttachmentBytes(attachment);
        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        zipOutputStream.write(bytes);
        zipOutputStream.closeEntry();
    }

    private static byte[] getAttachmentBytes(Attachment attachment) throws Exception {
        if(attachment.type == Attachment.Type.IMAGE) {
            return Base64.decode(attachment.content == null ? "" : attachment.content, Base64.NO_WRAP);
        }
        return (attachment.content == null ? "" : attachment.content).getBytes(StandardCharsets.UTF_8);
    }

    private static String getAttachmentEntryName(String uuid, Attachment.Type type) {
        if(type == Attachment.Type.IMAGE) {
            return ENTRY_ATTACHMENT_IMAGES + uuid + ".jpg";
        }
        return ENTRY_ATTACHMENT_TEXTS + uuid + ".txt";
    }

    private static String getLocalAttachmentPath(Context context, String uuid, Attachment.Type type) {
        String baseDir = type == Attachment.Type.IMAGE ? "images" : "texts";
        String extension = type == Attachment.Type.IMAGE ? ".jpg" : ".txt";
        return context.getFilesDir().getAbsolutePath() + "/" + baseDir + "/" + uuid + extension;
    }

    private static void writeJsonEntry(ZipOutputStream zipOutputStream, String entryName, Object json) throws Exception {
        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        String content = json instanceof JSONArray ? ((JSONArray) json).toString(2) : ((JSONObject) json).toString(2);
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private static String readFileAsString(File file) throws Exception {
        return new String(readAllBytes(new FileInputStream(file)), StandardCharsets.UTF_8);
    }

    private static byte[] readAllBytes(InputStream inputStream) throws Exception {
        try (InputStream in = inputStream; ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while((read = in.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    // 先将 zip 落到缓存目录，再按需读取，避免大备份包一次性占用太多内存。
    private static void unzipToTempDir(File tempDir, InputStream inputStream) throws Exception {
        try (InputStream in = inputStream; ZipInputStream zipInputStream = new ZipInputStream(in)) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while((entry = zipInputStream.getNextEntry()) != null) {
                File targetFile = resolveZipEntryFile(tempDir, entry.getName());
                if(entry.isDirectory()) {
                    targetFile.mkdirs();
                } else {
                    File parentFile = targetFile.getParentFile();
                    if(parentFile != null && !parentFile.exists()) {
                        parentFile.mkdirs();
                    }
                    try (FileOutputStream fileOutputStream = new FileOutputStream(targetFile)) {
                        int read;
                        while((read = zipInputStream.read(buffer)) != -1) {
                            fileOutputStream.write(buffer, 0, read);
                        }
                    }
                }
                zipInputStream.closeEntry();
            }
        }
    }

    private static File resolveZipEntryFile(File tempDir, String entryName) {
        File targetFile = new File(tempDir, entryName);
        try {
            String basePath = tempDir.getCanonicalPath() + File.separator;
            String targetPath = targetFile.getCanonicalPath();
            if(!targetPath.startsWith(basePath) && !targetPath.equals(tempDir.getCanonicalPath())) {
                throw new IllegalStateException("Illegal zip entry path.");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Illegal zip entry path.", e);
        }
        return targetFile;
    }

    private static void clearConversationAttachmentContent(Conversation conversation) {
        for(ChatMessage message : conversation.messages) {
            for(Attachment attachment : message.attachments) {
                attachment.content = null;
            }
        }
    }

    private static void deleteRecursively(File file) {
        if(file == null || !file.exists()) {
            return;
        }
        if(file.isDirectory()) {
            File[] children = file.listFiles();
            if(children != null) {
                for(File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private static String getOptionalString(JSONObject jsonObject, String key) {
        if(!jsonObject.has(key) || jsonObject.isNull(key)) {
            return null;
        }
        return jsonObject.optString(key, null);
    }

    public static String buildExportSummary(Context context, ExportResult result, Options options) {
        List<String> lines = new ArrayList<>();
        if(options.includeLlm) {
            lines.add(context.getString(R.string.data_transfer_summary_export_llm));
        }
        if(options.includeAsr) {
            lines.add(context.getString(R.string.data_transfer_summary_export_asr));
        }
        if(options.includeTemplates) {
            lines.add(context.getString(R.string.data_transfer_summary_export_templates, result.templateCount));
        }
        if(options.includeChats) {
            lines.add(context.getString(R.string.data_transfer_summary_export_chats, result.conversationCount, result.attachmentCount));
        }
        return joinLines(lines);
    }

    public static String buildImportSummary(Context context, ImportResult result, Options options) {
        List<String> lines = new ArrayList<>();
        if(options.includeLlm) {
            lines.add(context.getString(result.llmImported
                    ? R.string.data_transfer_summary_llm_imported
                    : R.string.data_transfer_summary_llm_skipped));
        }
        if(options.includeAsr) {
            lines.add(context.getString(result.asrImported
                    ? R.string.data_transfer_summary_asr_imported
                    : R.string.data_transfer_summary_asr_skipped));
        }
        if(options.includeTemplates) {
            lines.add(context.getString(
                    R.string.data_transfer_summary_templates,
                    result.importedTemplateCount,
                    result.skippedTemplateCount
            ));
            if(result.renamedTemplateCount > 0) {
                lines.add(context.getString(R.string.data_transfer_summary_templates_renamed, result.renamedTemplateCount));
            }
        }
        if(options.includeChats) {
            lines.add(context.getString(
                    R.string.data_transfer_summary_chats,
                    result.importedConversationCount,
                    result.skippedConversationCount
            ));
        }
        return joinLines(lines);
    }

    private static String joinLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < lines.size(); i++) {
            if(i > 0) {
                builder.append("\n");
            }
            builder.append(lines.get(i));
        }
        return builder.toString();
    }

    public static String buildDefaultFileName() {
        return String.format(
                Locale.getDefault(),
                "GPTAssistant_%s.zip",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        );
    }
}
