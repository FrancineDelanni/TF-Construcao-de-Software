package br.pucrs.pulsepass.orders;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication(scanBasePackages={"br.pucrs.pulsepass.common","br.pucrs.pulsepass.orders"})
@EnableScheduling
public class OrdersApplication {public static void main(String[] args){SpringApplication.run(OrdersApplication.class,args);}}
