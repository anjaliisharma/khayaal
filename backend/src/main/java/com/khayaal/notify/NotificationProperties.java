package com.khayaal.notify;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.notifications")
public record NotificationProperties(int followUpMinutes,int maximumAttempts,int batchSize,String fromAddress,
                                     String applicationUrl,String telegramBotToken) {
    public NotificationProperties {
        if(followUpMinutes<1)followUpMinutes=15;
        if(maximumAttempts<1)maximumAttempts=5;
        if(batchSize<1)batchSize=50;
    }
}
