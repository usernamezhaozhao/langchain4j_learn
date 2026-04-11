package com.qi.langchain4j_learn_4_9;

import com.qi.langchain4j_learn_4_9.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class EmailServiceTest {

    @Autowired
    private EmailService emailService;

    @Test
    public void testSendSimpleEmail() {
        emailService.sendSimpleEmail("19273469840@163.com", "测试邮件", "这是一封测试邮件。");
    }
}