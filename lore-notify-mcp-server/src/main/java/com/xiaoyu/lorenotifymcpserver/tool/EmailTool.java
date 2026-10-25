package com.xiaoyu.lorenotifymcpserver.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件通知工具（步骤 3.2）。
 *
 * 收件人安全（方案 2.7 / 4.3）：
 * - to 由主项目（可信端）在调用前强制注入 arguments（= 当前登录用户邮箱），模型填写的值会被覆盖；
 * - 本 server 不连库、不查 users 表，保持单一职责与无状态，模型能决定的只有 subject/body。
 */
@Service
public class EmailTool {

    private final JavaMailSender mailSender;

    private final String from;

    public EmailTool(JavaMailSender mailSender,
                     @Value("${spring.mail.username:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Tool(name = "send_email",
          description = "发送邮件提醒。to 由系统自动注入（收件人=当前登录用户），请勿填写；" +
                        "subject/body 按需填写，如主题「连载更新提醒」、正文「您有 3 个伏笔待回收」。")
    public String sendEmail(
            @ToolParam(description = "收件人邮箱（系统已自动填充，无需填写）", required = false) String to,
            @ToolParam(description = "邮件主题") String subject,
            @ToolParam(description = "邮件正文") String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (from != null && !from.isBlank()) {
                message.setFrom(from);
            }
            message.setTo(to);
            message.setSubject(subject == null || subject.isBlank() ? "连载更新提醒" : subject);
            message.setText(body == null ? "" : body);
            mailSender.send(message);
            return "邮件已发送至 " + to;
        }
        catch (Exception e) {
            // 返回失败原因而非抛异常，让 Agent 能看到并自我纠正
            return "发送失败：" + e.getMessage();
        }
    }
}
