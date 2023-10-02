# GPT Assistant

---

## 简介

GPT Assistant 是一个基于ChatGPT的安卓端语音助手，允许用户通过手机音量键从任意界面唤起并直接进行语音交流，用最快捷的方式询问并获取回复

### 项目特性

- 支持用户预设**问题模板**，支持**连续对话**，支持`gpt-3.5-turbo`和`gpt-4`模型
- 通过无障碍功能捕获音量键事件，实现在**任意界面唤起**
- 支持从**全局上下文菜单**（选中文本后弹出的系统菜单）中直接唤起
- 支持通过状态栏**快捷按钮**唤起
- 支持对**Markdown**进行渲染
- 使用百度短语音识别API进行**语音输入**
- 调用系统TTS引擎**输出语音**

### 国内使用说明

本软件通过OpenAI API获取回复，在国内使用时可以用第三方转发服务，如[Chatanywhere](https://github.com/chatanywhere/GPT_API_free)，其目前提供免费和付费服务，具体使用方法见[下述说明](#使用方法)

> 注：Chatanywhere注册需要GitHub账号，因此注册时需要能够登录GitHub的网络环境

### 费用说明

本软件不会以任何方式收取费用，也不会在软件中嵌入任何广告，但使用的下述服务**可能**产生费用:

1. ChatGPT调用费用

	- 以Chatanywhere为例，目前其**免费服务**限制对`gpt-3.5-turbo`模型的调用频率不超过**60请求/小时/IP&Key**，足够个人使用，若需要更高的调用频率或`gpt-4`模型，可以选择付费服务

2. 百度语音识别接口费用

	- 目前，百度短语音识别为新用户提供**15万次 & 180天免费额度**，额度外收取￥0.0034/次的调用费用

	- 若不需要使用语音输入功能，可以不填写相关配置项，不会产生该项费用

---

## 效果展示

**一、基础使用：仅用音量键就可以操控**

1. 长按音量下键唤出界面

2. 按住音量键不放，开始语音输入

3. 松开后再次短按，发送问题

<div align="center">
	<img src="readme_img/usage.gif" height="400px">
</div>

**二、用状态栏快捷键也可触发**

下拉状态栏，点击“GPT”按钮，即可唤出界面，键盘会自动弹出，可以手动输入问题

<div align="center">
	<img src="readme_img/tile_btn.gif" height="400px">
</div>

**三、从全局上下文菜单唤起**

在选中文本后弹出的系统菜单中，点击GPTAssistant选项，即可直接唤起应用并将选中文本添加到输入框

<div align="center">
	<img src="readme_img/context_menu.gif" height="400px">
</div>

**四、支持连续对话**

激活右上角的对话图标，即可保留当前会话，进行连续对话

<div align="center">
	<img src="readme_img/multi_chat.gif" height="400px">
</div>

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

若设置项的“启用长语音”选项关闭，则使用的是百度短语音识别接口，若开启，则使用的是实时语音识别接口，需要用户根据需求在创建应用时勾选对应的服务

此外，在创建应用时，需要将“语音包名”设置为“Android”，并填入本软件包名`com.skythinker.gptassistant`

![设置语音包名](readme_img/asr_set_package.jpg)

### 4. 开始使用

1. 根据软件提示开启无障碍服务，并允许软件在后台运行

2. 对于部分厂商的较新系统，需要手动在设置中允许应用的“后台弹出界面”权限

	> 若发现长按音量下键后手机震动一下但没有弹出界面，大概率是因为缺少该权限

3. 开始正常使用，可参照[效果展示](#效果展示)中的操作步骤

---

## 测试环境

已测试的机型：

| 机型 | 系统版本 | Android 版本 | 本程序版本 |
| :--: | :-----: | :----------: | :-------: |
| 荣耀 7C | EMUI 8.0.0 | Android 8 | 1.4.0 |
| 荣耀 20 | HarmonyOS 3.0.0 | Android 10 | 1.4.0 |
| 华为 Mate 30 | HarmonyOS 3.0.0 | Android 12 | 1.2.0 |
| 荣耀 Magic 4 | MagicOS 7.0 | Android 13 | 1.2.0 |
| 红米 K20 Pro | MIUI 12.5.6 | Android 11 | 1.3.0 |
| 红米 K60 Pro | MIUI 14.0.23 | Android 13 | 1.3.0 |
| Pixel 2 (模拟器) | Android 12 | Android 12 | 1.2.0 |

---

## 改进&贡献

如果你有改进建议或希望参与贡献，欢迎提交Issue或Pull Request

---

## 隐私说明

本程序不会以任何方式收集用户的个人信息，语音输入会直接发送给百度API，提问会直接发送给OpenAI API，不会经过任何中间服务器

---

## 引用的开源项目

- [Markwon](https://github.com/noties/Markwon): Android上的Markdown渲染器
- [chatgpt-java](https://github.com/Grt1228/chatgpt-java): OpenAI API的Java封装
