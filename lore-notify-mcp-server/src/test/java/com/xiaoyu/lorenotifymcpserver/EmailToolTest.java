package com.xiaoyu.lorenotifymcpserver;

import com.xiaoyu.lorenotifymcpserver.tool.EmailTool;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmailToolTest {

    @Test
    void sendEmail_usesInjectedToAndDefaultsSubject() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailTool tool = new EmailTool(mailSender, "noreply@example.com");

        String result = tool.sendEmail("user@example.com", null, "您有 3 个伏笔待回收");

        assertEquals("邮件已发送至 user@example.com", result);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertEquals("user@example.com", sent.getTo()[0]);
        assertEquals("noreply@example.com", sent.getFrom());
        assertEquals("连载更新提醒", sent.getSubject());
        assertEquals("您有 3 个伏笔待回收", sent.getText());
    }

    @Test
    void sendEmail_returnsFailureOnException() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new RuntimeException("SMTP 连接失败"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        EmailTool tool = new EmailTool(mailSender, "noreply@example.com");

        String result = tool.sendEmail("user@example.com", "提醒", "正文");

        assertTrue(result.startsWith("发送失败："));
    }
}
