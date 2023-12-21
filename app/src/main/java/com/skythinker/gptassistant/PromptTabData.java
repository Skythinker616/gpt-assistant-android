package com.skythinker.gptassistant;

import java.io.Serializable;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;

public class PromptTabData implements Serializable {
    private static final long serialVersionUID = 2279047712444757921L;

    private String tabTitle;
    private String prompt;

    public PromptTabData(String tabTitle, String prompt) {
        this.tabTitle = tabTitle;
        this.prompt = prompt;
    }

    public String getTitle() {
        return tabTitle;
    }
    public void setTitle(String tabTitle) { this.tabTitle = tabTitle; }

    public String getPrompt() {
        return prompt;
    }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    // 解析模板中的参数到JSONObject中
    public JSONObject parseParams() {
        JSONObject params = new JSONObject();

        try {

            Matcher headerMatcher = Pattern.compile("(?s)^\"\"\"\\n(.*?)\\n\"\"\"\\n").matcher(prompt);

            if (headerMatcher.find()) {
                Matcher lineMatcher = Pattern.compile("^@(\\w+)\\s+(.*)$", Pattern.MULTILINE).matcher(headerMatcher.group(1));

                JSONObject inputObject = new JSONObject();

                while (lineMatcher.find()) {
                    String name = lineMatcher.group(1);
                    String value = lineMatcher.group(2);
                    if (name == null || value == null)
                        continue;
                    value = value.trim();
                    if (name.equals("model")) { // 文本型参数
                        params.putOpt(name, value);
                    } else if (Arrays.asList("system", "speak", "chat", "network").contains(name)) { // 布尔型参数
                        params.putOpt(name, value.equals("true"));
                    } else if (name.equals("input")) { // 输入型参数
                        inputObject.putOpt(value, new JSONObject().putOpt("type", "text"));
                    } else if (name.equals("select")) { // 选择型参数
                        String[] selectParams = value.split("\\|");
                        if (selectParams.length > 0) {
                            JSONArray itemsArray = new JSONArray();
                            for (int i = 1; i < selectParams.length; i++) {
                                itemsArray.put(selectParams[i].trim());
                            }
                            inputObject.putOpt(selectParams[0].trim(), new JSONObject().putOpt("type", "select").putOpt("items", itemsArray));
                        }
                    }
                }

                if (inputObject.size() > 0)
                    params.putOpt("input", inputObject);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return params;
    }

    // 去除模板头部的参数部分，仅获取模板内容
    public String getContentWithoutParams() {
        return prompt.replaceFirst("(?s)^\"\"\"\\n(.*?)\\n\"\"\"\\n", "");
    }

    // 将参数填充到模板中
    public String getFormattedPrompt(JSONObject params) {
        String template = getContentWithoutParams();
        for(String key : params.keySet()) {
            template = template.replace("${" + key + "}", params.getStr(key));
        }
        return template;
    }
}
