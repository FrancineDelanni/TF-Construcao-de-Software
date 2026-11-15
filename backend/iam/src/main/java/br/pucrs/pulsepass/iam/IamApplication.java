package br.pucrs.pulsepass.iam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(IamProperties.class)
public class IamApplication {
    public static void main(String[] args) { SpringApplication.run(IamApplication.class, args); }
}
