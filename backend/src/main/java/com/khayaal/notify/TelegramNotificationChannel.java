package com.khayaal.notify;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class TelegramNotificationChannel implements NotificationChannel {
    private final NotificationProperties properties;
    private final RestClient client=RestClient.create();
    public TelegramNotificationChannel(NotificationProperties properties){this.properties=properties;}
    @Override public String channelName(){return "TELEGRAM";}
    @Override public void send(DeliveryMessage message){
        if(properties.telegramBotToken()==null||properties.telegramBotToken().isBlank())throw new IllegalStateException("telegram-not-configured");
        client.post().uri("https://api.telegram.org/bot"+properties.telegramBotToken()+"/sendMessage")
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("chat_id",message.destination(),"text",message.body()+"\n"+message.deepLink()))
                .retrieve().toBodilessEntity();
    }
}
