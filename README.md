# GPT Assistant

---

## 简介

GPT Assistant 是一个基于ChatGPT的安卓端语音助手，允许用户通过手机音量键从任意界面唤起并直接进行语音交流，用最快捷的方式询问并获取回复

**本项目有以下特点：**

- 支持用户预设**问题模板**
- 通过无障碍功能捕获音量键事件，实现在**任意界面唤起**
- 使用百度短语音识别API进行**语音输入**
- 调用系统TTS引擎**输出语音**
- 支持对**Markdown**进行渲染
- 若不使用音量键和语音输入，支持通过状态栏**快捷按钮**唤起

---

## 效果展示

<img src="readme_img/usage.gif" height="600px">

---

## 使用方法

### 1. 下载安装

直接下载最新发行版中的apk文件，安装即可

### 2. 配置 API_KEY

程序使用的是OpenAI API，需要用户在设置中填入自己的API_KEY，可以选择使用官方服务或第三方转发服务

**使用官方服务**

在OpenAI官网注册账号并获取API_KEY，在设置中填写网址`https://api.openai.com/`和API_KEY

若在国内使用，则建议使用第三方转发服务，以下方的Chatanywhere为例

**使用Chatanywhere转发服务**

Chatanywhere提供了免费和付费的OpenAI API转发服务，目前免费服务限制60请求/小时/IP&Key调用频率，付费服务则无限制，可以在国内直接访问，用户可以参照其[项目主页](https://github.com/chatanywhere/GPT_API_free)获取地址和KEY填入设置中

### 3. 配置语音识别

> 注：如果不需要使用语音输入功能，可以跳过这一步

用户可以参照[百度语音识别官方文档](https://cloud.baidu.com/doc/SPEECH/s/qknh9i8ed)注册并创建应用，然后获取AppID、API Key和Secret Key填入设置中

注意，在创建应用时，需要勾选“短语音识别”服务，然后将“语音包名”设置为“Android”，并填入本软件包名`com.skythinker.gptassistant`

![设置语音包名](readme_img/asr_set_package.jpg)

### 4. 开始使用

1. 根据软件提示开启无障碍服务，并允许软件在后台运行

2. 对于部分厂商的较新系统，需要手动在设置中允许应用的“后台弹出界面”权限

	> 若发现长按音量下键后手机震动一下但没有弹出界面，大概率是因为缺少该权限

3. 在任意界面长按音量下键，手机会震动一下并弹出界面，按住音量键不放即可进行语音输入，松开即可停止输入。输入完成后的3秒内再次短按音量下键，或点击发送按钮，即可向ChatGPT发送问题

4. 在任意界面下拉状态栏并点击“GPT”快捷按钮，也可以唤起界面，此时不会触发语音输入，键盘会自动弹出，用户可以手动输入问题

---

## 测试环境

已测试通过的机型：

| 机型 | 系统版本 | Android 版本 | 本程序版本 |
| :--: | :-----: | :----------: | :-------: |
| 荣耀 7C | EMUI 8.0.0 | Android 8 | 1.2.0 |
| 荣耀 20 | HarmonyOS 3.0.0 | Android 10 | 1.2.0 |
| 华为 Mate 30 | HarmonyOS 3.0.0 | Android 12 | 1.2.0 |
| 荣耀 Magic 4 | MagicOS 7.0 | Android 13 | 1.2.0 |
| 红米 K20 Pro | MIUI 12.5.6 | Android 11 | 1.2.0 |
| 红米 K60 Pro | MIUI 14.0.23 | Android 13 | 1.2.0 |
| Pixel 2 (模拟器) | Android 12 | Android 12 | 1.2.0 |

---

## 改进&贡献

**项目目前主要局限：**

- 仅支持`gpt-3.5-turbo`模型，未支持切换模型
- 未支持连续对话

如果你希望添加这些或其他功能，欢迎提交Issue或Pull Request

---

## 隐私说明

本程序不会以任何方式收集用户的个人信息，语音输入会直接发送给百度API，提问会直接发送给OpenAI API，不会经过任何中间服务器

---

## 引用的开源项目

- [Markwon](https://github.com/noties/Markwon): Android上的Markdown渲染器
- [chatgpt-java](https://github.com/PlexPt/chatgpt-java): OpenAI API的Java封装
