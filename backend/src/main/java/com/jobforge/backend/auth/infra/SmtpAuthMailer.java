package com.jobforge.backend.auth.infra;

import com.jobforge.backend.auth.app.AuthMailer;
import com.jobforge.backend.auth.app.AuthProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Interim SMTP delivery (Mailpit in dev). Link targets are frontend routes (Dev 2) — see REQ-20261002. */
@Component
public class SmtpAuthMailer implements AuthMailer {

    private final JavaMailSender sender;
    private final AuthProperties props;

    public SmtpAuthMailer(JavaMailSender sender, AuthProperties props) {
        this.sender = sender;
        this.props = props;
    }

    @Override
    public void sendVerification(String toEmail, String firstName, String token) {
        send(toEmail, "Verify your JobForge email",
                "Hi " + firstName + ",\n\nConfirm your email address:\n" + link("/verify-email", token)
                        + "\n\nThis link expires in " + props.verificationTtlHours() + " hours.");
    }

    @Override
    public void sendPasswordReset(String toEmail, String firstName, String token) {
        send(toEmail, "Reset your JobForge password",
                "Hi " + firstName + ",\n\nReset your password:\n" + link("/reset-password", token)
                        + "\n\nThis link expires in " + props.passwordResetTtlMinutes()
                        + " minutes. If you did not ask for this, ignore this email.");
    }

    private String link(String path, String token) {
        return props.appBaseUrl() + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(props.mailFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        sender.send(message);
    }
}
