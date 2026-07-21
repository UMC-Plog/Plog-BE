package com.plog.infrastructure.mail;

/**
 * 메일 발송 추상화. 구현체(Gmail/SES 등)를 갈아끼울 수 있게 한다.
 * 발송은 외부 I/O이므로 호출부는 트랜잭션 밖에서 부른다.
 */
public interface MailSender {

    void send(String to, String subject, String body);
}
