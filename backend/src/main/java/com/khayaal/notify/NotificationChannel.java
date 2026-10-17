package com.khayaal.notify;

public interface NotificationChannel {
    String channelName();
    void send(DeliveryMessage message);

    record DeliveryMessage(String destination,String subject,String body,String deepLink) { }
}
