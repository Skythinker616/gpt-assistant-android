<p>
	<b><a href="README_en.md">简体中文</a> | English</b>
</p>

<div align=center>
	<img src="readme_img/icon.jpg" height="100px"/>
	<h1>GPT Assistant</h1>
</div>

This is an Android voice assistant based on ChatGPT, allowing users to invoke and engage in voice conversations directly from any interface using the phone's volume keys, in the fastest way to ask questions and receive replies.

<div align=center>
	<font size=3>
		<b>Free Chatting · Voice Interaction · Internet Connection · Image Recognition</b>
	</font>
</div>

<br>

<div align=center>
	<p>
		<a href="https://gitee.com/skythinker/gpt-assistant-android">
			<img src="https://gitee.com/skythinker/gpt-assistant-android/badge/star.svg"/>
			<img src="https://gitee.com/skythinker/gpt-assistant-android/badge/fork.svg"/>
		</a>
		<a href="https://github.com/Skythinker616/gpt-assistant-android">
			<img src="https://img.shields.io/github/stars/skythinker616/gpt-assistant-android?logo=github&style=flat"/>
			<img src="https://img.shields.io/github/forks/skythinker616/gpt-assistant-android?logo=github&style=flat"/>
		</a>
		<img src="https://img.shields.io/badge/License-GPL3.0-red"/>
	</p>
</div>

---

## Introduction

### Project Features

- Supports predefined **question templates**, including using advanced template syntax to add dropdowns and other controls to the interface.
- **Internet connectivity support**, allowing GPT to fetch online web pages.
- Supports uploading images from the camera or gallery to the GPT Vision model.
- Captures volume key events through accessibility features to enable invocation from **any interface**.
- Supports direct invocation from the **global context menu** (system menu that appears after selecting text).
- Supports invocation through the status bar **shortcut button**.
- Supports rendering **Markdown**.
- Uses Huawei/Baidu/Whisper/Google APIs for **voice input**.
- Utilizes the system TTS engine for voice output.

### Instructions for Use in China

This software retrieves responses from the OpenAI API. When used in China, you can use a third-party forwarding service, such as [Chatanywhere](https://github.com/chatanywhere/GPT_API_free), which provides both free and paid services. Please refer to the [instructions below](#Usage) for details.

> Note: Chatanywhere registration requires a GitHub account, so make sure you can log in to GitHub when registering.

### Cost Explanation

This software does not incur any charges; users can use all features for free. However, if there are special requirements, the following third-party services used **may** incur charges:

1. ChatGPT API usage fees

   - For example, with Chatanywhere, the **free service** limits the call frequency to `gpt-3.5-turbo` model to no more than **60 requests/hour/IP & Key**. This is sufficient for personal use. If a higher call frequency or the `gpt-4` model is required, you can choose the paid service.

2. Speech recognition API fees

   This software currently supports Huawei, Google, Baidu, and Whisper four speech recognition interfaces:

   - (Default interface) **Huawei HMS** provides a free speech recognition interface. Therefore, the program includes the author's API key for direct use. Unless there are special circumstances, this interface will remain available during the Huawei free period.

   - **Google** also provides a free interface but may not be accessible in China. It is recommended for users outside China.

   - **Baidu** offers 150,000 times and 180 days of free usage for new users of short speech recognition, with additional charges of ¥0.0034/call beyond the free limit.

   - **Whisper** interface is provided by OpenAI and uses the same interface parameters as GPT chat. Call charges can be referenced in the [official documentation](https://openai.com/pricing).

---

## Demonstration

**I. Basic Usage: Control using only the volume keys**

1. Long-press the volume down key to bring up the interface.

2. Hold down the volume key to start voice input.

3. Release and press again to send the question.

4. While receiving a reply, automatic voice playback is enabled.

<div align="center">
	<img src="readme_img/usage.gif" height="400px">
</div>

**II. Trigger with status bar shortcut button**

Pull down the status bar, click the "GPT" button to bring up the interface. The keyboard will automatically appear for manual question input.

<div align="center">
	<img src="readme_img/tile_btn.gif" height="400px">
</div>

**III. Invoking from the global context menu**

In the system menu that appears after selecting text, click on the "GPTAssistant" option to directly invoke the application and add the selected text to the input box.

<div align="center">
	<img src="readme_img/context_menu.gif" height="400px">
</div>

**IV. Supports continuous conversation**

Activate the conversation icon above to keep the current session and engage in continuous conversation (click the avatar icon on the left to perform actions such as deleting, retrying, etc., for individual messages).

<div align="center">
	<img src="readme_img/multi_chat.gif" height="400px">
</div>

**V. Supports advanced template syntax**

Advanced template syntax allows the addition of dropdowns and other controls to the interface by adding parameters at the beginning of the template. You can find specific instructions in [Template Writing Instructions](template_help_en.md).

<div align="center">
	<img src="readme_img/template_code.png" height="140px">
	<img src="readme_img/template_ui.png" height="140px">
</div>

Click the button in the upper right corner of the template editing page to load the online template list. More templates or sharing your own templates can be obtained in the [discussion community](https://github.com/Skythinker616/gpt-assistant-android/discussions/categories/templates). Shared templates may be dynamically updated to the online template list.

**VI. Supports uploading images to Vision**

When the selected model includes `vision` (such as `gpt-4-vision-preview`), a camera icon will appear on the left side of the input box. Clicking it allows you to take photos or choose images from the gallery.

When sharing images from other applications, you can also choose this program to add images to the input box.

<div align="center">
	<img src="readme_img/vision.gif" height="400px">
	<img src="readme_img/vision_room.jpg" height="400px">
	<img src="readme_img/vision_math.jpg" height="400px">
</div>

Test results show that the `gpt-4-vision-preview` model has good recognition performance and can be used for scenarios such as photo recognition, text translation, and photo answering.

> Note: Vision models are generally not available for free (such as Chatanywhere). Users who need it may consider paid services.

**VII. Supports GPT Internet Connection**

This program implements OpenAI's Function interface, allowing GPT to make internet requests. The program will automatically return the required web page data to GPT, giving GPT internet capabilities (requires enabling internet options in settings).

<div align="center">
	<img src="readme_img/web_time.png" height="120px">
	<img src="readme_img/web_weather.png" height="120px">
</div>
<div align="center">
	<img src="readme_img/web_jd.png" height="180px">
	<img src="readme_img/web_zhihu.png" height="180px">
</div>

> Note 1: The above images are test results using the `gpt-3.5-turbo` model. It is recommended to add phrases such as "Baidu search," "Bing search," "online retrieval," or "retrieve from xxx" before asking questions to achieve better internet results.
> 
> Note 2: If you use the questions in the images but do not get the correct answer, it may be due to the randomness of GPT, resulting in accessing the wrong URL or website content changes causing fetching failure. You can try modifying the way you ask questions.
> 
> Note 3: Due to the need to send web content to GPT, internet usage will consume a large number of tokens. Please use the `gpt-4` model cautiously.
>
> Note 4: The `gpt-4-vision-preview` model does not currently support internet access.

---

## Usage Instructions

### 1. Download and Install

Directly download the latest release APK file and install it.

### 2. Configure OpenAI

The program uses the OpenAI API and requires users to enter their own API_KEY in the settings. You can choose to use the official service or a third-party forwarding service.

- **Using the Chatanywhere forwarding service** (recommended for use in China)

   Chatanywhere provides free and paid OpenAI API forwarding services. The free service currently limits the call frequency to `gpt-3.5-turbo` model to no more than **60 requests/hour/IP & Key**. The paid service has no limitations. Users can refer to their [project homepage](https://github.com/chatanywhere/GPT_API_free) to obtain the address and key and fill in the settings.

- **Using the official service**

   Register an account on the OpenAI website and obtain the API_KEY. Fill in the website `https://api.openai.com/` and API_KEY in the settings.

### 3. Configure Speech Recognition (Optional)

> Note: The program defaults to using the Huawei speech recognition interface. If there are no special circumstances, this step is not required.

**Baidu Speech Recognition Interface**

Users can refer to the [official documentation for Baidu Speech Recognition](https://cloud.baidu.com/doc/SPEECH/s/qknh9i8ed) to register and create an application. Obtain the AppID, API Key, and Secret Key and fill in the settings.

If the "Enable long speech" option is turned off, the program uses the Baidu short speech recognition interface. If enabled, it uses the real-time speech recognition interface, and users need to select the corresponding service when creating the application based on their needs.

Additionally, when creating the application, set the "Voice Package Name" to "Android" and fill in the software package name `com.skythinker.gptassistant`.

![Set Voice Package Name](readme_img/asr_set_package.jpg)

**Google Speech Recognition Interface**

Users need to ensure that the [Google app](https://play.google.com/store/apps/details?id=com.google.android.googlequicksearchbox) is installed on the system. Then, follow the app's instructions to set Google as the system speech recognition engine and allow it to use microphone permissions. In the software settings, choose the Google speech recognition interface.

**Whisper Speech Recognition Interface**

If the OpenAI interface used supports the Whisper model, select the Whisper speech recognition interface in the software settings to use it.

### 4. Start Using

1. Enable accessibility service as prompted by the software and allow the software to run in the background.

2. Check if the "Pop-up window" permission exists in the settings. If it does, allow it; if not, ignore it.

   > If you find that the phone vibrates after long-pressing the volume down key but the interface does not pop up, it is likely due to the lack of this permission.

3. Start using normally, referring to the operational steps in the [demonstration section](#Demonstration).

---

## Q&A

### Software Invocation

**Q: Long-pressing the volume down key only adjusts the volume and nothing else happens?**

A: Please enable the accessibility service for this software in the settings (may need to be re-enabled after restarting the phone, it is recommended to set it as an accessibility shortcut).

**Q: After long-pressing the volume down key, the phone vibrates, but no interface pops up?**

A: Please allow the program the "pop-up interface in the background" permission in the settings.

**Q: Unable to use the volume key to invoke after a period of inactivity?**

A: Please allow the program to run in the background in the settings.

### Voice Broadcast

**Q: No sound in voice broadcast / not pleasant to listen to?**

A: The software calls the system's built-in TTS (Text To Speech) service. You can enter the system settings through the software setting "Open system speech settings" and choose a suitable speech engine. If you are not satisfied with the system's built-in engine, you can also install third-party TTS engines like Xunfei.

**Q: What is the difference in the recognition effect of different interfaces?**

A: Tested in scenes of mixed Chinese and English speech:

- Huawei interface (real-time speech recognition) has high accuracy, especially for single-sentence recognition.
- Baidu performs well in recognizing long sentences, and the segmentation of sentences is reasonable, but it is difficult to achieve mixed Chinese and English recognition (uses the Chinese Mandarin model).
- Google supports many languages, and the Chinese recognition effect is average, and it does not add punctuation.
- Whisper supports many languages, the Chinese recognition effect is acceptable, but there are sometimes uncontrolled situations between simplified and traditional Chinese, and it does not support speaking while outputting.

In a pure English scene, Huawei, Google, and Whisper all perform well.

### Networking Related

**Q: What websites can GPT access when connected to the Internet?**

A: The program uses Android WebView to load web pages, and websites that can be opened by the native browser can be accessed.

**Q: What content can GPT get from the website?**

A: For general websites, GPT is only allowed to access pure text content. For specially adapted websites, GPT can also get search result links. Adapted websites include Baidu, Bing, Google, Google Scholar, Zhihu, Weibo, JD, GitHub, Bilibili, and CNKI.

> If you think other websites need to be adapted, you can submit an Issue.

**Q: Why does GPT say it cannot retrieve content when accessing some websites?**

A: Reasons such as webpage loading timeout (15s), requiring login, and requiring verification may cause this problem. You can try asking again or ask GPT to change the website it accesses.

### Other Usage Issues

**Q: Why doesn't the list have the model I need?**

A: The software only includes a few commonly used models. You can add custom models in the settings (separated by English semicolons), and the added models will appear in the list.

**Q: Tables cannot be displayed normally in the content returned by GPT?**

A: The Markdown renderer used cannot produce stable results in testing, so table rendering is not supported for now.

**Q: Display failure, timeout prompt, or error code 502/503?**

A: Exclude network factors. This error is generally generated by the OpenAI interface, and it may be due to high server load. Please try again or wait for some time before trying again. [View OpenAI real-time status](https://status.openai.com/)

### Development Related

**Q: After compiling the repository code, Huawei HMS speech recognition cannot be used?**

A: To prevent abuse, the keys in the repository have package name and signature verification enabled. Therefore, if you want to compile and use it yourself, you need to create an AppGallery application according to [Huawei's official documentation](https://developer.huawei.com/consumer/cn/doc/hiai-Guides/ml-asr-0000001050066212#section699935381711) and replace the authentication information, including the `app/agconnect-services.json` file and the `hms_api_key` field in `app/src/main/res/values/strings.xml`.

---

## Major Feature Update Log

- **2023.09.10** Released the first version, supporting basic dialogue, Baidu voice input, TTS output, Markdown rendering, and other functions.
- **2023.09.13** Added support for continuous dialogue, GPT-4, Baidu long speech recognition, and context menu invocation.
- **2023.10.06** Added Huawei HMS speech recognition.
- **2023.11.06** Added networking functionality.
- **2023.12.04** Added Vision image recognition functionality.
- **2023.12.21** Added support for advanced template syntax.
- **2024.01.08** Added support for Google and Whisper speech recognition, online template list.

---

## TODO

- Support rendering Markdown tables.
- Continuous voice communication.

---

## Test Environment

Tested models:

| Model           | System Version  | Android Version | Program Version |
| :-------------: | :-------------: | :-------------: | :-------------: |
| Honor 7C        | EMUI 8.0.0       | Android 8        | 1.9.1           |
| Honor 20        | HarmonyOS 3.0.0  | Android 10       | 1.9.1           |
| Honor 20        | HarmonyOS 4.0    | Android 10       | 1.10.0          |
| Huawei Mate 30  | HarmonyOS 3.0.0  | Android 12       | 1.6.0           |
| Huawei Mate 30  | HarmonyOS 4.0    | Android 12       | 1.8.0           |
| Honor Magic 4   | MagicOS 7.0      | Android 13       | 1.9.1           |
| Redmi K20 Pro   | MIUI 12.5.6      | Android 11       | 1.5.0           |
| Redmi K60 Pro   | MIUI 14.0.23     | Android 13       | 1.7.0           |
| Xiaomi 13       | MIUI 14.0.5      | Android 14       | 1.10.0          |
| Pixel 2 (Emulator) | Android 12    | Android 12       | 1.7.0           |

---

## Improvement & Contribution

If you have improvement suggestions or want to contribute, feel free to submit an Issue or Pull Request.

---

## Privacy Statement

This program will not collect any personal information from users in any way. Voice input will be sent directly to Huawei or Baidu API, and questions will be sent directly to the OpenAI API without passing through other intermediate servers.

---

## Open Source Projects Used

- [Markwon](https://github.com/noties/Markwon): Markdown renderer on Android.
- [chatgpt-java](https://github.com/Grt1228/chatgpt-java): Java wrapper for the OpenAI API.

---
<!--
## Support/Donation

If you find GPT Assistant helpful, you can give it a star, or you can donate to buy me a cup of tea. Thank you very much for your support!

[![Star History Chart](https://api.star-history.com/svg?repos=Skythinker616/gpt-assistant-android&type=Date)](https://star-history.com/#Skythinker616/gpt-assistant-android&Date)

<details>
	<summary>View Donation Codes</summary>
	<div align="center">
		<img src="readme_img/wechat.png" height="180px">
		<img src="readme_img/alipay.jpg" height="180px">
	</div>
	<br>
</details>
-->
