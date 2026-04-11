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

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    /**
     * 1. 发送普通文本邮件
     */
    @Async
    public void sendSimpleEmail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("2662480975@qq.com"); // 发件人必须与配置一致
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    /**
     * 2. 发送HTML邮件
     */
    @Async
    public void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        // 创建MimeMessage对象，用于支持复杂格式
        MimeMessage message = mailSender.createMimeMessage();
        // 第二个参数true表示这是一个MIME消息，支持HTML和附件；第三个参数指定编码UTF-8
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom("2662480975@qq.com");
        helper.setTo(to);
        helper.setSubject(subject);
        // 第二个参数true表示邮件内容为HTML格式
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }

    /**
     * 3. 发送带附件的邮件
     */
    @Async
    public void sendAttachmentEmail(String to, String subject, String text, String filePath) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        // 必须传入true，否则无法添加附件
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom("2662480975@qq.com");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(text, true);

        // 添加附件：通过文件路径创建附件资源
        FileSystemResource file = new FileSystemResource(new File(filePath));
        helper.addAttachment(file.getFilename(), file);

        mailSender.send(message);
    }
}