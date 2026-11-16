package br.pucrs.pulsepass.delivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    scanBasePackages = {"br.pucrs.pulsepass.common", "br.pucrs.pulsepass.delivery"})
@EnableScheduling
public class DeliveryApplication {
  public static void main(String[] args) {
    SpringApplication.run(DeliveryApplication.class, args);
  }
}
