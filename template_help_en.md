**[简体中文](template_help.md) | English**

# Template Writing Guide

> Note: Before reviewing this document, please ensure that the software is updated to the latest version.

## Basic Template

A dialogue template consists of two parts: the title and content. The title is displayed on the selection button below the main interface. When a user sends a question, the entered query will be merged with the selected template and sent to the GPT model.

For example, here is a simple template content:

```plaintext
Please translate the phrase "${input}" into English.
```

When a user selects this template and sends "你好" (hello), GPT will receive:

```plaintext
Please translate the phrase "你好" into English.
```

Thus, the answer will be "Hello."

If the template does not contain placeholders like `${input}`, the user's question will be appended to the template content and sent to the GPT model. For example, using the following template:

```plaintext
Please translate this phrase into English:
```

When a user selects this template and sends "你好" (hello), GPT will receive:

```plaintext
Please translate this phrase into English: 你好
```

> Note: For compatibility with older versions, `%input%` can also be used as a placeholder, but the newer syntax is recommended.

## Advanced Syntax

Starting from version 1.9.0, GPTAssistant supports advanced template syntax. By writing parameters at the beginning of the template, users can customize various template behaviors, including:

- Setting the model name to be used
- Setting whether the template is assigned the 'system' role
- Enabling voice broadcasting, continuous dialogue, and internet connectivity
- Adding custom dropdowns or text input boxes

Content enclosed in triple quotes at the beginning of the template will be recognized as template parameters, with each parameter occupying one line and consisting of a parameter name and a parameter value. Parameter names start with `@`, and there is a space between the parameter name and value, like this:

```plaintext
"""
@model gpt-3.5-turbo
@network false
@select Target Language|Simplified Chinese|English|Japanese|Korean
"""
Please translate the phrase "${input}" into ${Target Language}.
```

When this template is selected, a dropdown named "Target Language" will appear on the interface with options for "Simplified Chinese," "English," "Japanese," and "Korean." When sending a question, `${Target Language}` will be replaced with the selected option, using the `gpt-3.5-turbo` model for translation, and disabling internet connectivity.

### Available Parameters

| Parameter Name | Parameter Value | Description |
| --- | --- | --- |
| `@model` | e.g., `gpt-3.5-turbo` | Set the model name to be used |
| `@system` | `true` or `false` | Set whether the template is assigned the 'system' role |
| `@speak` | `true` or `false` | Enable voice broadcasting |
| `@network` | `true` or `false` | Enable internet connectivity |
| `@chat` | `true` or `false` | Enable continuous dialogue |
| `@select` | `Dropdown Name\|Option 1\|Option 2\|...` | Add a dropdown, and the selected option will replace the placeholder `${Dropdown Name}` |
| `@input` | `Input Box Name` | Add a text input box, and the entered content will replace the placeholder `${Input Box Name}` |

> Note: When viewing this page on the Gitee mobile app, table display issues may occur (only displaying the first two columns). Please switch to the desktop version at the bottom of the page.

Additionally, each option in `@select` can have a `[Option Name]` (i.e., `[Option Name]Option Content`) at the beginning to display the option name in the dropdown, with the content being replaced in the placeholder.

### Notes

- Triple quotes `"""` must be on a line by themselves, with no other content before or after; parameter names must be written at the beginning, with no space on the left
- When set as a 'system' role, the `${input}` placeholder will be disabled, and user-inputted questions will be sent as 'user' role following the template content
- If `@model`, `@speak`, `@network`, or `@chat` parameters are set, the corresponding global settings of the software will be temporarily overridden when selecting this template
- Avoid setting dropdown and input box names as `input` or using duplicate names
- Changing dropdown options or input box content during continuous dialogue will not replace the placeholder with the new content

## Community Discussions

In the GitHub discussion community, you can find [templates shared by other users](https://github.com/Skythinker616/gpt-assistant-android/discussions/categories/templates), or [share your own templates](https://github.com/Skythinker616/gpt-assistant-android/discussions/new?category=templates). We welcome your participation!
