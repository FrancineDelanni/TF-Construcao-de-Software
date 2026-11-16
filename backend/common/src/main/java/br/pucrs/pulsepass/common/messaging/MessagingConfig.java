package br.pucrs.pulsepass.common.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(
    name = "pulsepass.messaging.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MessagingConfig {
  @Bean
  ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
      ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
      ConsumerFactory<Object, Object> consumerFactory,
      Quarantine quarantine) {
    var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
    configurer.configure(factory, consumerFactory);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    factory.setRecordInterceptor(
        (record, consumer) -> {
          @SuppressWarnings("unchecked")
          var typed =
              (org.apache.kafka.clients.consumer.ConsumerRecord<String, String>) (Object) record;
          quarantine.check(
              org.springframework.kafka.support.KafkaUtils.getConsumerGroupId(), typed);
          return record;
        });
    var handler =
        new DefaultErrorHandler(
            (record, ex) ->
                quarantine.recover(
                    org.springframework.kafka.support.KafkaUtils.getConsumerGroupId(), record, ex),
            new FixedBackOff(1000L, 3));
    handler.setAckAfterHandle(true);
    factory.setCommonErrorHandler(handler);
    return factory;
  }
}
