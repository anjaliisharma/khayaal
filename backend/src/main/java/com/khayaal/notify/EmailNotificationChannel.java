package com.khayaal.notify;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationChannel implements NotificationChannel {
    private final JavaMailSender mailSender;
    private final NotificationProperties properties;
    public EmailNotificationChannel(JavaMailSender mailSender,NotificationProperties properties){this.mailSender=mailSender;this.properties=properties;}
    @Override public String channelName(){return "EMAIL";}
    @Override public void send(DeliveryMessage message){
        SimpleMailMessage email=new SimpleMailMessage();email.setFrom(properties.fromAddress());email.setTo(message.destination());
        email.setSubject(message.subject());email.setText(message.body()+(message.deepLink()==null?"":"\n\n"+message.deepLink()));mailSender.send(email);
    }
}
