package com.khayaal.notify;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificationDispatcher {
    private final NotificationOutbox outbox;private final Map<String,NotificationChannel> channels;
    private final NotificationProperties properties;private final Clock clock;private final String workerId=UUID.randomUUID().toString();
    private final Counter sentCounter;private final Counter failedCounter;private final Timer deliveryTimer;
    public NotificationDispatcher(NotificationOutbox outbox,List<NotificationChannel> channels,NotificationProperties properties,Clock clock,MeterRegistry metrics){
        this.outbox=outbox;this.channels=channels.stream().collect(Collectors.toUnmodifiableMap(NotificationChannel::channelName, Function.identity()));
        this.properties=properties;this.clock=clock;
        this.sentCounter=metrics.counter("khayaal.notifications.sent");this.failedCounter=metrics.counter("khayaal.notifications.failed");
        this.deliveryTimer=metrics.timer("khayaal.notifications.delivery");
    }
    @Scheduled(fixedDelayString="${app.notifications.dispatch-delay-ms:5000}",initialDelayString="${app.notifications.initial-delay-ms:10000}")
    public int dispatch(){
        Instant now=clock.instant();var claimed=outbox.claim(workerId,properties.batchSize(),now);int sent=0;
        for(var notification:claimed){
            Timer.Sample sample=Timer.start();
            try{
                if(!outbox.isRelevant(notification)){outbox.markSent(notification.id(),clock.instant());continue;}
                NotificationChannel channel=channels.get(notification.channel());
                if(channel==null)throw new IllegalStateException("unsupported-channel");
                channel.send(new NotificationChannel.DeliveryMessage(outbox.destination(notification),notification.subject(),notification.body(),notification.deepLink()));
                outbox.markSent(notification.id(),clock.instant());sent++;sentCounter.increment();
            }catch(Exception exception){failedCounter.increment();outbox.markFailed(notification.id(),notification.attempts(),properties.maximumAttempts(),clock.instant(),exception.getClass().getSimpleName());}
            finally{sample.stop(deliveryTimer);}
        }
        return sent;
    }
}
