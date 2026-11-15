package br.pucrs.pulsepass.common;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name={"pulsepass.messaging.enabled","pulsepass.outbox.enabled"},havingValue="true",matchIfMissing=true)
public class OutboxRelay {
 private final OutboxPublisher publisher;
 public OutboxRelay(OutboxPublisher publisher) { this.publisher=publisher; }
 @Scheduled(fixedDelayString="${pulsepass.outbox.delay:500}")
 public void relay() { for(int i=0;i<50 && publisher.publishNext();i++) { /* bounded drain */ } }
}
