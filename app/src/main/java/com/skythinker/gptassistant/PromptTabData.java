package com.skythinker.gptassistant;

import java.io.Serializable;

public class PromptTabData implements Serializable {
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
}
