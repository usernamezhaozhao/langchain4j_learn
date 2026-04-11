# LangChain4j 项目完整文档

## 目录
1. [项目概述](#项目概述)
2. [核心概念](#核心概念)
3. [代码详解](#代码详解)
4. [LangChain4j 知识补充](#langchain4j-知识补充)

---

## 项目概述

本项目是一个基于 **LangChain4j** 框架的学习项目,展示了如何在 Java 环境中集成大语言模型(LLM)并实现各种高级功能。

### 技术栈
- **Spring Boot 3.5.13**: 应用框架
- **LangChain4j 1.12.2**: LLM 集成框架
- **Java 17**: 编程语言
- **Lombok**: 简化 Java 代码
- **Apache POI**: Excel 文件处理
- **OkHttp**: HTTP 客户端
- **Gson**: JSON 处理

### 项目结构
```
back_langchain4j_4_9/
├── src/main/java/
│   └── com/qi/langchain4j_learn_4_9/
│       ├── Langchain4jLearn49Application.java  # 主启动类
│       └── service/
│           └── EmailService.java               # 邮件服务
├── src/test/java/
│   ├── com/qi/langchain4j_learn_4_9/
│   │   ├── AssistantTest.java                  # AI助手测试
│   │   ├── AssistantChain.java                 # AI服务链测试
│   │   ├── MemoryTest.java                     # 记忆功能测试
│   │   ├── ToolChainTest.java                  # 工具调用测试
│   │   ├── ToolChainTestWithAnnotation.java    # 注解工具测试
│   │   ├── RagChainTest.java                   # RAG测试
│   │   ├── AiEmailServiceTest.java             # AI邮件服务测试
│   │   ├── EmailServiceTest.java               # 邮件服务测试
│   │   └── SimpleTextCleaner.java              # 文本清洗器
│   └── tool/
│       └── WeatherTools.java                   # 天气工具类
└── md/
    └── learn.md                                # 学习笔记
```

---

## 核心概念

### 1. LangChain4j 是什么?

**LangChain4j** 是一个用于在 Java 应用中集成大语言模型(LLM)的框架。它提供了:

- **统一的 API 接口**: 支持多种 LLM 提供商(OpenAI、Anthropic、Google 等)
- **对话记忆管理**: 自动管理多轮对话的上下文
- **工具调用(Function Calling)**: 让 AI 能够调用外部函数和 API
- **RAG(检索增强生成)**: 结合向量数据库实现知识库问答
- **流式输出**: 支持实时流式返回 AI 响应
- **结构化输出**: 将 AI 响应解析为 Java 对象

### 2. 核心组件

#### 2.1 ChatModel (聊天模型)
- **作用**: 与 LLM 进行对话的核心接口
- **类型**:
  - `ChatModel`: 同步模型,一次性返回完整响应
  - `StreamingChatModel`: 流式模型,逐字返回响应(类似打字机效果)

#### 2.2 ChatMemory (对话记忆)
- **作用**: 存储和管理对话历史,使 AI 能够记住之前的对话内容
- **类型**:
  - `MessageWindowChatMemory`: 基于消息数量的窗口记忆(保留最近 N 条消息)
  - `TokenWindowChatMemory`: 基于 Token 数量的窗口记忆(保留最近 N 个 Token)

#### 2.3 AiServices (AI 服务)
- **作用**: 通过接口定义的方式简化 AI 集成,自动处理消息转换、记忆管理等
- **特点**: 声明式编程,类似 Spring Data JPA

#### 2.4 Tools (工具)
- **作用**: 让 AI 能够调用 Java 方法来获取实时数据或执行操作
- **实现方式**: 使用 `@Tool` 注解标记方法

#### 2.5 RAG (检索增强生成)
- **作用**: 将外部知识库(如文档、数据库)与 LLM 结合,提供更准确的答案
- **核心组件**:
  - `EmbeddingModel`: 将文本转换为向量
  - `EmbeddingStore`: 存储向量数据
  - `ContentRetriever`: 检索相关内容

---

## 代码详解

### 1. 主启动类 - Langchain4jLearn49Application.java

```java
package com.qi.langchain4j_learn_4_9;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * LangChain4j 学习项目的主启动类
 * 
 * 功能说明:
 * 1. @SpringBootApplication: Spring Boot 应用的核心注解,包含了组件扫描、自动配置等功能
 * 2. @EnableAsync: 启用 Spring 的异步方法执行能力,允许使用 @Async 注解实现异步调用
 *    - 这对于邮件发送等耗时操作非常重要,可以避免阻塞主线程
 *    - 异步方法会在独立的线程池中执行
 */
@SpringBootApplication
@EnableAsync  // 启用异步支持,EmailService 中的异步方法需要此注解
public class Langchain4jLearn49Application {

    /**
     * 应用程序入口点
     * 
     * @param args 命令行参数
     * 
     * 执行流程:
     * 1. SpringApplication.run() 会启动 Spring 容器
     * 2. 自动扫描并注册所有带 @Component、@Service 等注解的 Bean
     * 3. 初始化所有配置的组件(如 JavaMailSender)
     * 4. 启动内嵌的 Web 服务器(如果有 Web 依赖)
     */
    public static void main(String[] args) {
        SpringApplication.run(Langchain4jLearn49Application.class, args);
    }

}
```

**知识点补充**:
- **@EnableAsync 的工作原理**: 
  - Spring 会为标记了 `@Async` 的方法创建代理
  - 当调用异步方法时,实际上是调用代理对象
  - 代理会将方法提交到线程池执行,立即返回
  - 默认使用 `SimpleAsyncTaskExecutor`,可以自定义线程池

---

### 2. 邮件服务 - EmailService.java

```java
package com.qi.langchain4j_learn_4_9.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.core.io.FileSystemResource;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.File;

/**
 * 邮件服务类
 * 
 * 功能概述:
 * 提供三种邮件发送方式:
 * 1. 简单文本邮件
 * 2. HTML 格式邮件
 * 3. 带附件的邮件
 * 
 * 所有方法都使用 @Async 异步执行,避免阻塞主线程
 * 
 * 技术要点:
 * - JavaMailSender: Spring 提供的邮件发送抽象接口
 * - SimpleMailMessage: 用于简单文本邮件
 * - MimeMessage: 用于复杂邮件(HTML、附件等)
 * - MimeMessageHelper: 简化 MimeMessage 的构建过程
 */
@Service  // 标记为 Spring 服务组件,会被自动扫描并注册为 Bean
public class EmailService {

    /**
     * JavaMailSender 是 Spring 提供的邮件发送核心接口
     * 
     * 工作原理:
     * 1. Spring Boot 会根据 application.properties 中的配置自动创建 JavaMailSender Bean
     * 2. 配置项包括: SMTP 服务器地址、端口、用户名、密码、SSL/TLS 设置等
     * 3. 底层使用 Jakarta Mail API (原 JavaMail API) 实现邮件发送
     * 
     * 常见配置示例:
     * spring.mail.host=smtp.qq.com
     * spring.mail.port=587
     * spring.mail.username=your-email@qq.com
     * spring.mail.password=your-authorization-code
     * spring.mail.properties.mail.smtp.auth=true
     * spring.mail.properties.mail.smtp.starttls.enable=true
     */
    @Autowired
    private JavaMailSender mailSender;

    /**
     * 1. 发送普通文本邮件
     * 
     * @param to      收件人邮箱地址
     * @param subject 邮件主题
     * @param text    邮件正文(纯文本)
     * 
     * 使用场景:
     * - 简单的通知邮件
     * - 验证码邮件
     * - 系统告警邮件
     * 
     * 技术细节:
     * - SimpleMailMessage 是最简单的邮件类型,只支持纯文本
     * - 不支持 HTML 格式、图片嵌入、附件等高级功能
     * - 性能最好,适合大批量发送
     * 
     * @Async 注解说明:
     * - 方法会在独立线程中执行,不会阻塞调用者
     * - 调用此方法会立即返回,邮件在后台发送
     * - 如果发送失败,异常会在异步线程中抛出,不会影响主流程
     */
    @Async  // 异步执行,避免邮件发送耗时阻塞主线程
    public void sendSimpleEmail(String to, String subject, String text) {
        // 创建简单邮件消息对象
        SimpleMailMessage message = new SimpleMailMessage();
        
        // 设置发件人地址,必须与配置文件中的 spring.mail.username 一致
        // 否则会被 SMTP 服务器拒绝(防止伪造发件人)
        message.setFrom("2662480975@qq.com"); // 发件人必须与配置一致
        
        // 设置收件人地址,可以是单个地址或数组(多个收件人)
        message.setTo(to);
        
        // 设置邮件主题
        message.setSubject(subject);
        
        // 设置邮件正文(纯文本)
        message.setText(text);
        
        // 发送邮件
        // 底层会连接 SMTP 服务器,进行身份验证,然后发送邮件
        mailSender.send(message);
    }

    /**
     * 2. 发送 HTML 邮件
     * 
     * @param to          收件人邮箱地址
     * @param subject     邮件主题
     * @param htmlContent HTML 格式的邮件内容
     * @throws MessagingException 邮件发送异常
     * 
     * 使用场景:
     * - 营销邮件(需要精美排版)
     * - 带样式的通知邮件
     * - 包含图片、链接的邮件
     * 
     * HTML 邮件示例:
     * String html = """
     *     <html>
     *     <body>
     *         <h1 style="color: blue;">欢迎使用我们的服务</h1>
     *         <p>这是一封 <strong>HTML</strong> 邮件</p>
     *         <img src="https://example.com/logo.png" alt="Logo"/>
     *     </body>
     *     </html>
     *     """;
     * 
     * 技术细节:
     * - MimeMessage 支持 MIME 协议,可以发送复杂格式的邮件
     * - MimeMessageHelper 简化了 MimeMessage 的构建过程
     * - HTML 邮件可以包含 CSS 样式、图片(外链或内嵌)、链接等
     * - 注意: 不同邮件客户端对 HTML/CSS 的支持程度不同,需要测试兼容性
     */
    @Async  // 异步执行
    public void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        // 创建 MimeMessage 对象,用于支持复杂格式
        MimeMessage message = mailSender.createMimeMessage();
        
        // 创建 MimeMessageHelper 辅助类
        // 参数说明:
        // - message: 要构建的 MimeMessage 对象
        // - true: 表示这是一个 multipart 消息,支持 HTML 和附件
        // - "UTF-8": 字符编码,确保中文等非 ASCII 字符正确显示
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        // 设置发件人
        helper.setFrom("2662480975@qq.com");
        
        // 设置收件人
        helper.setTo(to);
        
        // 设置邮件主题
        helper.setSubject(subject);
        
        // 设置邮件内容
        // 第二个参数 true 表示邮件内容为 HTML 格式
        // 如果为 false,则按纯文本处理(HTML 标签会被显示为文本)
        helper.setText(htmlContent, true);

        // 发送邮件
        mailSender.send(message);
    }

    /**
     * 3. 发送带附件的邮件
     * 
     * @param to       收件人邮箱地址
     * @param subject  邮件主题
     * @param text     邮件正文(可以是 HTML)
     * @param filePath 附件文件的完整路径
     * @throws MessagingException 邮件发送异常
     * 
     * 使用场景:
     * - 发送报表文件(Excel、PDF)
     * - 发送合同、发票等文档
     * - 发送日志文件
     * 
     * 技术细节:
     * - FileSystemResource 用于读取本地文件系统中的文件
     * - 附件会被编码为 Base64 格式嵌入邮件中
     * - 大附件会显著增加邮件大小和发送时间
     * - 建议: 单个附件不超过 10MB,总附件不超过 25MB
     * 
     * 高级用法:
     * - 可以添加多个附件: helper.addAttachment("file1.pdf", resource1);
     *                      helper.addAttachment("file2.xlsx", resource2);
     * - 可以自定义附件名称: helper.addAttachment("自定义名称.pdf", resource);
     * - 可以内嵌图片: helper.addInline("logo", resource); 然后在 HTML 中引用 <img src="cid:logo"/>
     */
    @Async  // 异步执行
    public void sendAttachmentEmail(String to, String subject, String text, String filePath) throws MessagingException {
        // 创建 MimeMessage 对象
        MimeMessage message = mailSender.createMimeMessage();
        
        // 创建 MimeMessageHelper
        // 必须传入 true,否则无法添加附件
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        // 设置发件人
        helper.setFrom("2662480975@qq.com");
        
        // 设置收件人
        helper.setTo(to);
        
        // 设置邮件主题
        helper.setSubject(subject);
        
        // 设置邮件正文
        // 第二个参数 true 表示支持 HTML 格式
        helper.setText(text, true);

        // 添加附件
        // 步骤1: 通过文件路径创建 FileSystemResource 对象
        FileSystemResource file = new FileSystemResource(new File(filePath));
        
        // 步骤2: 将文件作为附件添加到邮件中
        // - file.getFilename(): 获取文件名(不含路径),作为附件显示名称
        // - file: 文件资源对象
        // 
        // 注意事项:
        // - 如果文件不存在,会抛出异常
        // - 如果文件路径包含中文,需要确保编码正确
        // - 附件名称会显示在收件人的邮件客户端中
        helper.addAttachment(file.getFilename(), file);

        // 发送邮件
        mailSender.send(message);
    }
}
```

**EmailService 知识点总结**:

1. **SimpleMailMessage vs MimeMessage**:
   - `SimpleMailMessage`: 只能发送纯文本,性能好,适合简单场景
   - `MimeMessage`: 支持 HTML、附件、内嵌图片等,功能强大但复杂

2. **异步发送的优势**:
   - 不阻塞主线程,提升用户体验
   - 适合批量发送场景
   - 发送失败不影响主流程

3. **常见问题**:
   - **发送失败**: 检查 SMTP 配置、网络连接、授权码是否正确
   - **中文乱码**: 确保使用 UTF-8 编码
   - **附件过大**: 压缩文件或使用云存储链接代替

---

### 3. 记忆功能测试 - MemoryTest.java

这个类展示了 LangChain4j 中对话记忆(ChatMemory)的各种使用方式。

```java
package com.qi.langchain4j_learn_4_9;

import ch.qos.logback.core.subst.Tokenizer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 对话记忆功能测试类
 * 
 * 核心概念:
 * ChatMemory 是 LangChain4j 中用于管理对话历史的组件,它解决了一个关键问题:
 * LLM 本身是无状态的,每次请求都是独立的,不会记住之前的对话。
 * 通过 ChatMemory,我们可以在多轮对话中保持上下文连贯性。
 * 
 * 记忆类型:
 * 1. MessageWindowChatMemory: 基于消息数量的窗口记忆
 * 2. TokenWindowChatMemory: 基于 Token 数量的窗口记忆
 * 3. 持久化记忆: 通过 ChatMemoryStore 实现跨会话的记忆持久化
 */
@SpringBootTest
public class MemoryTest {

    /**
     * Test2: 基础记忆功能演示
     * 
     * 功能说明:
     * 演示如何手动管理对话记忆,实现多轮对话的上下文保持
     * 
     * 工作流程:
     * 1. 创建 ChatModel 和 ChatMemory
     * 2. 第一轮对话: 用户说"我是七七"
     * 3. 将用户消息和 AI 回复存入记忆
     * 4. 第二轮对话: 用户问"我叫什么"
     * 5. 从记忆中获取历史消息,与新消息一起发送给 AI
     * 6. AI 能够根据历史记忆回答"你叫七七"
     * 
     * 关键点:
     * - 必须手动将每轮对话存入记忆
     * - 必须手动从记忆中获取历史消息并发送给 AI
     * - 这种方式比较底层,适合需要精细控制的场景
     */
    @Test
    void Test2() {
        // 1. 创建 ChatModel (聊天模型)
        // OpenAiChatModel 是 LangChain4j 提供的 OpenAI 兼容模型实现
        // 这里使用的是硅基流动(SiliconFlow)的 API,它兼容 OpenAI 的接口格式
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")  // API 密钥
                .modelName("Qwen/Qwen3-Omni-30B-A3B-Instruct")  // 模型名称(通义千问)
                .baseUrl("https://api.siliconflow.cn/v1")  // API 基础 URL
                .build();

        // 2. 创建 ChatMemory (对话记忆)
        // MessageWindowChatMemory.withMaxMessages(10) 表示:
        // - 最多保留最近 10 条消息(包括用户消息和 AI 回复)
        // - 当消息数量超过 10 条时,最早的消息会被自动删除
        // - 这是一种滑动窗口机制,防止上下文过长导致 Token 超限
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        // 3. 第一轮对话
        // 创建用户消息对象
        UserMessage userMessage1 = UserMessage.from("你好我是七七");
        
        // 发送消息给 AI 并获取回复
        // chatModel.chat(userMessage1) 返回 ChatResponse 对象
        // .aiMessage() 提取其中的 AiMessage(AI 的回复消息)
        AiMessage message1 = chatModel.chat(userMessage1).aiMessage();
        
        // 将本轮对话(用户消息 + AI 回复)存入记忆
        // 这一步非常重要!如果不存入记忆,AI 在下一轮对话中就不会记得这次对话
        chatMemory.add(userMessage1, message1);
        
        // 打印 AI 的回复
        System.out.println("AI: " + message1.text());

        // 4. 第二轮对话:需要带上历史
        // 从记忆中获取所有历史消息
        // 返回的是 List<ChatMessage>,包含之前所有的用户消息和 AI 回复
        List<ChatMessage> history = chatMemory.messages();
        
        // 创建新的用户消息
        UserMessage userMessage2 = UserMessage.from("你还记得我是谁吗,请说出你的记忆,我叫什么");
        
        // 将历史消息和新消息合并
        // 这样 AI 就能看到完整的对话历史,从而给出正确的回答
        List<ChatMessage> allMessages = new ArrayList<>(history);
        allMessages.add(userMessage2);

        // 发送所有消息(历史 + 新消息)给 AI
        // AI 会根据历史记忆回答"你叫七七"
        AiMessage message2 = chatModel.chat(allMessages).aiMessage();
        System.out.println("AI: " + message2.text());

        // 将本轮对话也存入记忆(可选)
        // 如果后续还有第三轮、第四轮对话,就需要存入记忆
        chatMemory.add(userMessage2, message2);

        // 清空记忆(测试结束后清理)
        chatMemory.clear();
    }

    /**
     * 持久化存储记忆的实现
     * 
     * 问题背景:
     * MessageWindowChatMemory 默认将消息存储在内存中,应用重启后记忆会丢失。
     * 在实际应用中,我们需要将记忆持久化到数据库、Redis 等存储中。
     * 
     * 解决方案:
     * 实现 ChatMemoryStore 接口,自定义记忆的存储和读取逻辑。
     * 
     * 这里使用 ConcurrentHashMap 作为示例(实际应用中应该用数据库)
     */
    // 模拟持久化存储(实际应用中应该是数据库)
    // Key: 会话 ID(Object 类型,可以是 String、Long 等)
    // Value: 该会话的所有消息列表
    public final Map<Object, List<ChatMessage>> store = new ConcurrentHashMap<>();
    
    /**
     * 自定义的持久化记忆存储实现
     * 
     * ChatMemoryStore 接口定义了三个方法:
     * 1. getMessages: 根据会话 ID 获取消息列表
     * 2. updateMessages: 更新会话的消息列表
     * 3. deleteMessages: 删除会话的所有消息
     * 
     * 实际应用中的实现方式:
     * - MySQL: 创建 chat_messages 表,存储 session_id、role、content、timestamp 等字段
     * - Redis: 使用 List 数据结构,key 为 "chat:session:{id}",value 为消息列表
     * - MongoDB: 存储为文档,每个会话一个文档,包含消息数组
     */
    class PersistentChatMemoryStore implements ChatMemoryStore {
        /**
         * 获取指定会话的所有消息
         * 
         * @param o 会话 ID(可以是 String、Long、UUID 等)
         * @return 消息列表,如果会话不存在则返回空列表
         * 
         * 实际应用示例(MySQL):
         * SELECT role, content, timestamp 
         * FROM chat_messages 
         * WHERE session_id = ? 
         * ORDER BY timestamp ASC
         */
        @Override
        public List<ChatMessage> getMessages(Object o) {
            return store.getOrDefault(o, List.of());
        }
        
        /**
         * 更新指定会话的消息列表
         * 
         * @param o    会话 ID
         * @param list 新的消息列表(会完全替换旧的列表)
         * 
         * 注意:
         * - 这个方法会完全替换旧的消息列表,不是追加
         * - LangChain4j 会在内存中管理消息窗口,然后调用此方法持久化
         * 
         * 实际应用示例(MySQL):
         * 1. DELETE FROM chat_messages WHERE session_id = ?
         * 2. INSERT INTO chat_messages (session_id, role, content, timestamp) VALUES (?, ?, ?, ?)
         */
        @Override
        public void updateMessages(Object o, List<ChatMessage> list) {
            store.put(o, list);
        }
        
        /**
         * 删除指定会话的所有消息
         * 
         * @param o 会话 ID
         * 
         * 使用场景:
         * - 用户主动清空对话历史
         * - 会话过期自动清理
         * - 用户注销账号时清理数据
         * 
         * 实际应用示例(MySQL):
         * DELETE FROM chat_messages WHERE session_id = ?
         */
        @Override
        public void deleteMessages(Object o) {
            store.remove(o);
        }
    }
    
    /**
     * Test3: 持久化记忆测试
     * 
     * 功能说明:
     * 演示如何使用自定义的 ChatMemoryStore 实现记忆持久化
     * 
     * 与 Test2 的区别:
     * - Test2: 记忆存储在内存中,应用重启后丢失
     * - Test3: 记忆存储在自定义的 store 中,可以持久化到数据库
     * 
     * 实际应用场景:
     * - 多轮对话的客服系统
     * - 需要记住用户偏好的 AI 助手
     * - 跨会话的上下文保持
     */
    @Test
    void Test3(){
        // 1. 创建 ChatModel
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("Qwen/Qwen3-Omni-30B-A3B-Instruct")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        
        // 2. 创建带持久化存储的 ChatMemory
        // 使用 builder 模式配置:
        // - chatMemoryStore: 自定义的持久化存储实现
        // - maxMessages: 最多保留 10 条消息
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryStore(new PersistentChatMemoryStore())  // 使用自定义存储
                .maxMessages(10)  // 消息窗口大小
                .build();
        
        // 3. 第一轮对话
        UserMessage userMessage1 = UserMessage.from("你好我是七七");
        AiMessage message1 = chatModel.chat(userMessage1).aiMessage();
        chatMemory.add(userMessage1, message1);
        System.out.println("AI: " + message1.text());
        
        // 4. 第二轮对话:需要带上历史
        List<ChatMessage> history = chatMemory.messages();
        UserMessage userMessage2 = UserMessage.from("你还记得我是谁吗,请说出你的记忆,我叫什么");
        List<ChatMessage> allMessages = new ArrayList<>(history);
        allMessages.add(userMessage2);
        AiMessage message2 = chatModel.chat(allMessages).aiMessage();
        System.out.println("AI: " + message2.text());
        
        // 5. 将本轮对话也存入记忆
        chatMemory.add(userMessage2, message2);
        
        // 6. 输出记忆内容(验证持久化)
        List<ChatMessage> messages = chatMemory.messages();
        messages.forEach(System.out::println);
        
        // 7. 遍历 store 查看所有会话的记忆
        for (Map.Entry<Object, List<ChatMessage>> entry : store.entrySet()) {
            Object sessionId = entry.getKey();
            List<ChatMessage> messages1 = entry.getValue();
            System.out.println("会话: " + sessionId);
            for (ChatMessage msg : messages1) {
                System.out.println("  " + msg);
            }
        }
        
        // 8. 清理(测试结束)
        chatMemory.clear();
        store.clear();
    }
    
    /**
     * Test4: 占位方法
     * 实际实现已移至 AssistantTest.java
     * 参见 AssistantTest 中的多用户记忆管理示例
     */
    @Test
    void Test4() {
        //移步Assistant
    }
    
    /**
     * Test5: 消息数量窗口演示
     * 
     * 功能说明:
     * 演示 MessageWindowChatMemory 的滑动窗口机制
     * 
     * 工作原理:
     * - 设置 maxMessages = 3,表示最多保留 3 条消息
     * - 当添加第 4 条消息时,第 1 条消息会被自动删除
     * - 这是一个 FIFO(先进先出)队列
     * 
     * 使用场景:
     * - 限制上下文长度,避免 Token 超限
     * - 只关注最近的对话,忽略久远的历史
     * - 节省存储空间和计算成本
     */
    @Test
    void Test5() {
        // 创建只保留最近 3 条消息的记忆
        MessageWindowChatMemory memory = MessageWindowChatMemory.withMaxMessages(3);
        
        // 添加 4 条消息
        memory.add(UserMessage.from("1"));  // 第 1 条
        memory.add(UserMessage.from("2"));  // 第 2 条
        memory.add(UserMessage.from("3"));  // 第 3 条
        memory.add(UserMessage.from("4"));  // 第 4 条,此时第 1 条会被删除
        
        // 验证: 当前只有 3 条消息
        System.out.println("当前消息数量:" + memory.messages().size()); // 输出: 3
        
        // 输出消息内容: 2, 3, 4 (第 1 条已被删除)
        memory.messages().forEach(System.out::println);
    }
    
    /**
     * Test6: Token 窗口演示
     * 
     * 功能说明:
     * 演示 TokenWindowChatMemory 的基于 Token 数量的窗口机制
     * 
     * MessageWindowChatMemory vs TokenWindowChatMemory:
     * - MessageWindowChatMemory: 按消息数量限制(如最多 10 条消息)
     * - TokenWindowChatMemory: 按 Token 数量限制(如最多 100 个 Token)
     * 
     * 为什么需要 TokenWindowChatMemory?
     * - LLM 的上下文限制是基于 Token 数量,不是消息数量
     * - 不同消息的 Token 数量差异很大(短消息可能只有几个 Token,长消息可能有几百个)
     * - 使用 TokenWindowChatMemory 可以更精确地控制上下文长度
     * 
     * Token 是什么?
     * - Token 是 LLM 处理文本的基本单位
     * - 英文: 1 个单词 ≈ 1-2 个 Token
     * - 中文: 1 个汉字 ≈ 1-2 个 Token
     * - 例如: "你好" ≈ 2 个 Token, "Hello" ≈ 1 个 Token
     */
    @Test
    void Test6() {
        // 1. 创建 ChatModel
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("Qwen/Qwen3-Omni-30B-A3B-Instruct")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        
        // 2. 创建 TokenWindowChatMemory
        // 参数说明:
        // - maxTokens(100): 最多保留 100 个 Token 的消息
        // - OpenAiTokenCountEstimator: Token 计数器,用于估算消息的 Token 数量
        //   - 不同模型的 Token 计算方式不同,需要指定模型名称
        //   - 这里使用 "gpt-3.5-turbo" 的计数方式(通用性较好)
        TokenWindowChatMemory memory = TokenWindowChatMemory
                .builder()
                .maxTokens(100, new OpenAiTokenCountEstimator("gpt-3.5-turbo"))
                .build();
        
        // 3. 添加消息到记忆
        memory.add(UserMessage.from("我是七七"));
        
        // 4. 获取历史消息
        List<ChatMessage> messages = memory.messages();
        
        // 5. 创建新的用户消息
        UserMessage userMessage = UserMessage.from("你还记得我是谁吗");
        messages.add(userMessage);
        
        // 6. 发送消息给 AI
        AiMessage aiMessage = chatModel.chat(messages).aiMessage();
        System.out.println(aiMessage.text());
        
        // 7. 输出当前记忆中的消息数量和内容
        System.out.println("当前token数:" + memory.messages().size());
        System.out.println("消息:" + memory.messages());
    }

}
```

**MemoryTest 知识点总结**:

1. **为什么需要对话记忆?**
   - LLM 本身是无状态的,每次请求都是独立的
   - 没有记忆,AI 无法进行多轮对话
   - 记忆使 AI 能够理解上下文,提供连贯的回答

2. **两种窗口机制的选择**:
   - **MessageWindowChatMemory**: 简单直观,适合消息长度相对均匀的场景
   - **TokenWindowChatMemory**: 更精确,适合需要严格控制上下文长度的场景

3. **持久化的重要性**:
   - 内存记忆会在应用重启后丢失
   - 持久化到数据库可以实现跨会话的记忆保持
   - 实际应用中应该使用 MySQL、Redis、MongoDB 等存储

4. **记忆管理的最佳实践**:
   - 合理设置窗口大小,避免 Token 超限
   - 定期清理过期会话的记忆,节省存储空间
   - 敏感信息不要存入记忆,注意数据安全

---

### 4. AI 助手测试 - AssistantTest.java

这个类展示了如何使用 AiServices 简化 AI 集成,实现多用户记忆管理。

```java
package com.qi.langchain4j_learn_4_9;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 助手接口定义
 * 
 * 核心概念:
 * AiServices 是 LangChain4j 提供的高级抽象,通过接口定义的方式简化 AI 集成。
 * 它类似于 Spring Data JPA,你只需要定义接口,框架会自动生成实现。
 * 
 * 优势:
 * 1. 声明式编程: 不需要手动管理消息、记忆等
 * 2. 自动记忆管理: 框架自动处理对话历史的存储和检索
 * 3. 多用户支持: 通过 @MemoryId 轻松实现多用户隔离
 * 4. 类型安全: 编译时检查,减少运行时错误
 */
interface MyAssistant {
    /**
     * 聊天方法
     * 
     * @param userId  用户 ID,用于区分不同用户的对话记忆
     *                @MemoryId 注解告诉框架使用这个参数作为记忆的标识符
     * @param message 用户消息
     *                @UserMessage 注解表示这是用户发送的消息
     * @return AI 的回复(String 类型)
     * 
     * 工作原理:
     * 1. 框架根据 userId 查找对应的 ChatMemory
     * 2. 从 ChatMemory 中获取历史消息
     * 3. 将历史消息 + 新消息一起发送给 LLM
     * 4. 将 LLM 的回复存入 ChatMemory
     * 5. 返回 AI 的回复文本
     * 
     * 注意:
     * - userId 可以是任意类型(String、Long、UUID 等)
     * - 不同 userId 的对话记忆是完全隔离的
     * - 框架会自动管理记忆的生命周期
     */
    String chat(@MemoryId String userId, @UserMessage String message);
}

/**
 * 多用户助手接口
 * 
 * 与 MyAssistant 的区别:
 * - userId 类型为 Long(而不是 String)
 * - 参数名为 msg(而不是 message)
 * 
 * 这个例子说明:
 * - @MemoryId 和 @UserMessage 可以用于任意类型的参数
 * - 参数名称可以自定义
 * - 框架会根据注解识别参数的作用
 */
interface MultiUserAssistant {
    String chat(@MemoryId Long userId, @UserMessage String msg);
}


@SpringBootTest(classes = {AssistantTest.class})
public class AssistantTest {
    
    /**
     * Test4: 单用户记忆管理
     * 
     * 功能说明:
     * 演示如何使用 AiServices 实现自动记忆管理
     * 
     * 与 MemoryTest.Test2 的对比:
     * - MemoryTest.Test2: 手动管理记忆,需要手动添加消息、获取历史等
     * - AssistantTest.Test4: 自动管理记忆,只需调用 chat() 方法
     * 
     * 优势:
     * - 代码更简洁,不需要手动处理 ChatMemory
     * - 自动处理消息的存储和检索
     * - 更符合面向对象的编程风格
     */
    @Test
    void Test4(){
        // 1. 创建 ChatModel
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("Qwen/Qwen3-Omni-30B-A3B-Instruct")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        
        // 2. 构建 AI 服务
        // AiServices.builder() 使用构建器模式创建 AI 服务
        // 参数说明:
        // - MyAssistant.class: 要实现的接口
        // - chatModel: 使用的聊天模型
        // - chatMemoryProvider: 记忆提供器,根据 memoryId 创建对应的 ChatMemory
        //   - memoryId 就是 chat() 方法中的 userId 参数
        //   - MessageWindowChatMemory.withMaxMessages(10): 为每个用户创建一个最多保留 10 条消息的记忆
        MyAssistant assistant = AiServices.builder(MyAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build();
        
        // 3. 第一轮对话
        // 调用 chat() 方法,框架会自动:
        // 1. 根据 "user_001" 查找或创建 ChatMemory
        // 2. 从 ChatMemory 中获取历史消息(第一次为空)
        // 3. 将 "我叫小明" 发送给 LLM
        // 4. 将用户消息和 AI 回复存入 ChatMemory
        // 5. 返回 AI 的回复
        String ans1 = assistant.chat("user_001", "我叫小明");
        System.out.println("AI: " + ans1);

        // 4. 第二轮对话(自动带记忆)
        // 框架会自动:
        // 1. 根据 "user_001" 获取之前的 ChatMemory
        // 2. 从 ChatMemory 中获取历史消息(包含第一轮的对话)
        // 3. 将历史消息 + "我叫什么?" 一起发送给 LLM
        // 4. LLM 根据历史记忆回答"你叫小明"
        // 5. 将本轮对话存入 ChatMemory
        String ans2 = assistant.chat("user_001", "我叫什么?");
        System.out.println("AI: " + ans2);
        
        // 注意:
        // - 整个过程中,我们不需要手动管理 ChatMemory
        // - 框架自动处理了所有的记忆管理逻辑
        // - 代码更简洁、更易维护
    }
    
    /**
     * Test5: 多用户记忆隔离
     * 
     * 功能说明:
     * 演示如何使用 @MemoryId 实现多用户的记忆隔离
     * 
     * 应用场景:
     * - 多用户聊天系统
     * - 客服系统(每个客户独立的对话历史)
     * - 多租户 SaaS 应用
     * 
     * 核心机制:
     * - 每个 userId 对应一个独立的 ChatMemory
     * - 不同用户的对话记忆完全隔离,互不干扰
     * - 框架自动管理多个 ChatMemory 实例
     */
    @Test
    void Test5(){
        // 1. 创建 ChatModel
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("Qwen/Qwen3-Omni-30B-A3B-Instruct")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        
        // 2. 构建 AI 服务
        // chatMemoryProvider 会为每个不同的 memoryId 创建独立的 ChatMemory
        // 例如:
        // - memoryId = 1L 时,创建 ChatMemory_1
        // - memoryId = 2L 时,创建 ChatMemory_2
        // - 两个 ChatMemory 完全独立,互不影响
        MultiUserAssistant assistant = AiServices.builder(MultiUserAssistant.class)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .chatModel(chatModel)
                .build();
        
        // 3. 用户 1 的对话
        // 第一轮: 用户 1 说"我叫张三"
        System.out.println(assistant.chat(1L, "我叫张三"));
        // 第二轮: 用户 1 问"我是谁?"
        // AI 会根据用户 1 的记忆回答"你叫张三"
        System.out.println(assistant.chat(1L, "我是谁?"));

        // 4. 用户 2 的对话(互不干扰)
        // 第一轮: 用户 2 说"我叫李四"
        System.out.println(assistant.chat(2L, "我叫李四"));
        // 第二轮: 用户 2 问"我是谁?"
        // AI 会根据用户 2 的记忆回答"你叫李四"
        // 注意: 用户 2 的记忆中没有"张三",完全独立
        System.out.println(assistant.chat(2L, "我是谁?"));
        
        // 验证记忆隔离:
        // - 用户 1 的记忆: "我叫张三" -> "我是谁?" -> "你叫张三"
        // - 用户 2 的记忆: "我叫李四" -> "我是谁?" -> "你叫李四"
        // - 两个用户的记忆完全独立,互不干扰
    }

}
```

**AssistantTest 知识点总结**:

1. **AiServices 的优势**:
   - **声明式编程**: 只需定义接口,框架自动生成实现
   - **自动记忆管理**: 不需要手动处理 ChatMemory
   - **类型安全**: 编译时检查,减少错误
   - **代码简洁**: 相比手动管理,代码量减少 50% 以上

2. **@MemoryId 的作用**:
   - 标识不同用户的对话记忆
   - 实现多用户记忆隔离
   - 可以是任意类型(String、Long、UUID 等)

3. **chatMemoryProvider 的工作原理**:
   ```java
   .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
   ```
   - 这是一个 Lambda 表达式,接收 memoryId,返回 ChatMemory
   - 框架会为每个不同的 memoryId 调用一次这个函数
   - 返回的 ChatMemory 会被缓存,同一个 memoryId 会复用同一个 ChatMemory

4. **实际应用建议**:
   - 小型应用: 使用内存记忆(MessageWindowChatMemory)
   - 生产环境: 使用持久化记忆(实现 ChatMemoryStore,存储到数据库)
   - 高并发场景: 使用 Redis 作为记忆存储,提升性能

---

### 5. 工具调用测试 - ToolChainTest.java

这个类展示了 LangChain4j 中最强大的功能之一: **工具调用(Tool/Function Calling)**。

**什么是工具调用?**
- LLM 本身只能生成文本,无法获取实时数据(如天气、股票价格)
- 工具调用让 LLM 能够"调用"外部函数来获取实时数据
- 工作流程: 用户提问 → LLM 判断需要调用工具 → 执行工具函数 → 将结果返回给 LLM → LLM 生成最终回答

由于这个文件代码较长,我将分段详细注释。让我继续添加: