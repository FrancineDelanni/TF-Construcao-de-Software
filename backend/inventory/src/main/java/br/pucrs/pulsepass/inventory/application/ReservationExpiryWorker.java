package br.pucrs.pulsepass.inventory.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="pulsepass.inventory.expiry.enabled",havingValue="true",matchIfMissing=true)
public class ReservationExpiryWorker {
    private final InventoryService inventory;
    ReservationExpiryWorker(InventoryService inventory) {this.inventory=inventory;}
    @Scheduled(fixedDelayString="${pulsepass.inventory.expiry.delay-ms:1000}")
    public void expire() {for(var id:inventory.due()) inventory.expire(id);}
}
