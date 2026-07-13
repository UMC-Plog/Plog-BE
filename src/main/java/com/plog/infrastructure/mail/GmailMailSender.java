package com.plog.infrastructure.mail;

import com.plog.global.api.code.ErrorCode;
import com.plog.global.api.exception.ApiException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Gmail SMTP 구현체. 나중에 SES 등으로 교체할 때 이 클래스만 갈아끼우면 된다.
 */
@Component
public class GmailMailSender implements MailSender {

    private final JavaMailSender javaMailSender;

    public GmailMailSender(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    @Override
    public void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            javaMailSender.send(message);
        } catch (MailException e) {
            throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }
}
