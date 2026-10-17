package com.khayaal.notify;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
class NotificationConfiguration {
    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    JavaMailSender javaMailSender(@Value("${spring.mail.host:localhost}") String host,
                                  @Value("${spring.mail.port:1025}") int port) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        return sender;
    }
}
