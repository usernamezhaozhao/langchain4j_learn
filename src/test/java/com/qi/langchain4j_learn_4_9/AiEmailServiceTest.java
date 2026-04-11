package com.qi.langchain4j_learn_4_9;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@SpringBootTest
public class AiEmailServiceTest {

    @Autowired
    private JavaMailSender mailSender;

    // 工具定义：发送邮件（自动附加当前测试类的源代码文件作为附件）
    private final ToolSpecification toolSpecification = ToolSpecification.builder()
            .name("sendEmail")
            .description("发送邮件到指定收件人，并自动附加当前测试类的Java源代码文件作为附件。")
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("to", "收件人的邮箱地址")
                    .addStringProperty("subject", "邮件的主题")
                    .addStringProperty("text", "邮件的正文内容")
                    .required("to", "subject", "text")
                    .build())
            .build();

    /**
     * 获取当前测试类的 .java 源文件
     */
    public File getCurrentJavaSourceFile() {
        String className = this.getClass().getSimpleName() + ".java";
        String packagePath = this.getClass().getPackage().getName().replace('.', File.separatorChar);
        String projectRoot = System.getProperty("user.dir");
        Path candidate = Paths.get(projectRoot, "src", "test", "java", packagePath, className);
        if (candidate.toFile().exists()) {
            log.info("找到源文件: {}", candidate.toFile());
            return candidate.toFile();
        } else {
            log.warn("未找到当前类的源文件: {}", className);
            return null;
        }
    }

    /**
     * 发送带附件的邮件（同步方法，返回发送结果）
     * 附件固定为当前测试类的源代码文件
     */
    private String sendAttachmentEmailSync(String to, String subject, String text) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom("2662480975@qq.com");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, true);

            File attachmentFile = getCurrentJavaSourceFile();
            if (attachmentFile != null && attachmentFile.exists() && attachmentFile.isFile()) {
                FileSystemResource resource = new FileSystemResource(attachmentFile);
                helper.addAttachment(resource.getFilename(), resource);
                log.info("已添加附件: {}", attachmentFile.getAbsolutePath());
            } else {
                log.warn("未找到源代码附件，邮件将不包含附件");
            }

            mailSender.send(message);
            log.info("邮件发送成功 to: {}", to);
            return "SUCCESS";
        } catch (MessagingException e) {
            log.error("邮件发送失败", e);
            return "FAILURE: " + e.getMessage();
        }
    }

    @Test
    void testEmailWithSourceCodeAttachment() throws ExecutionException, InterruptedException {
        StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();

        // 用户自然语言请求（模型会自行判断是否需要调用邮件工具）
        String userQuery = "请帮我发送邮件，收件人：11111111@qq.com，主题：超哥,我是鸿伟，内容：超哥,我是鸿伟,在学langchain的工具调用。又看了天气的查询api和发送邮箱逻辑。索性发你封邮件。哈哈";
        // 第一轮请求：包含用户消息和工具定义
        ChatRequest firstRequest = ChatRequest.builder()
                .messages(UserMessage.from(userQuery))
                .toolSpecifications(toolSpecification)
                .build();

        CompletableFuture<ChatResponse> firstResponseFuture = new CompletableFuture<>();
        chatModel.chat(firstRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                // 第一轮不输出
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                firstResponseFuture.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                firstResponseFuture.completeExceptionally(error);
            }
        });

        ChatResponse firstResponse = firstResponseFuture.get();
        AiMessage aiMessage = firstResponse.aiMessage();

        if (!aiMessage.hasToolExecutionRequests()) {
            System.out.println("模型直接回答：" + aiMessage.text());
            return;
        }

        System.out.println("模型请求调用工具，共 " + aiMessage.toolExecutionRequests().size() + " 个请求：");
        ToolExecutionRequest toolReq = aiMessage.toolExecutionRequests().get(0);
        System.out.println("工具名称：" + toolReq.name());
        System.out.println("参数：" + toolReq.arguments());

        JsonObject args = JsonParser.parseString(toolReq.arguments()).getAsJsonObject();
        String to = args.get("to").getAsString();
        String subject = args.get("subject").getAsString();
        String text = args.get("text").getAsString();

        // 执行邮件发送（自动附带源代码）
        String sendResult = sendAttachmentEmailSync(to, subject, text);
        ToolExecutionResultMessage toolResultMsg = ToolExecutionResultMessage.from(toolReq, sendResult);

        // 第二轮请求：将工具结果发给模型，流式输出最终回答
        ChatRequest secondRequest = ChatRequest.builder()
                .messages(
                        UserMessage.from(userQuery),
                        aiMessage,
                        toolResultMsg
                )
                .build();

        CompletableFuture<Void> finalOutputFuture = new CompletableFuture<>();
        chatModel.chat(secondRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                System.out.print(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                System.out.println();
                finalOutputFuture.complete(null);
            }

            @Override
            public void onError(Throwable error) {
                finalOutputFuture.completeExceptionally(error);
            }
        });

        finalOutputFuture.get();
    }
}