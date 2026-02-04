package com.ainovel.app.settings;

import com.ainovel.app.settings.model.GlobalSettings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
public class SmtpService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final SettingsService settingsService;

    @Value("${spring.mail.username:}")
    private String defaultFromAddress;

    public SmtpService(ObjectProvider<JavaMailSender> mailSenderProvider, SettingsService settingsService) {
        this.mailSenderProvider = mailSenderProvider;
        this.settingsService = settingsService;
    }

    public void sendTestEmail(String toEmail) {
        SimpleMailMessage message = new SimpleMailMessage();
        JavaMailSender sender = resolveMailSender();
        String from = resolveFromAddress();
        if (from != null && !from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(toEmail);
        message.setSubject("AiNovel SMTP 测试邮件");
        message.setText("这是一封测试邮件，说明 SMTP 配置可用。");
        sender.send(message);
    }

    private String resolveFromAddress() {
        GlobalSettings g = settingsService.getGlobalSettings();
        if (g.getSmtpUsername() != null && !g.getSmtpUsername().isBlank()) {
            return g.getSmtpUsername();
        }
        return defaultFromAddress;
    }

    private JavaMailSender resolveMailSender() {
        GlobalSettings g = settingsService.getGlobalSettings();
        if (g.getSmtpHost() == null || g.getSmtpHost().isBlank()) {
            JavaMailSender sender = mailSenderProvider.getIfAvailable();
            if (sender != null) {
                return sender;
            }
            return new JavaMailSenderImpl();
        }
        JavaMailSenderImpl impl = new JavaMailSenderImpl();
        impl.setHost(g.getSmtpHost());
        impl.setPort(g.getSmtpPort() != null ? g.getSmtpPort() : 587);
        impl.setUsername(g.getSmtpUsername());
        impl.setPassword(g.getSmtpPassword());
        Properties props = impl.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        return impl;
    }
}
