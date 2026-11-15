package br.pucrs.pulsepass.inventory;

import br.pucrs.pulsepass.common.EventMessage;
import br.pucrs.pulsepass.inventory.application.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/** Real broker + real PostgreSQL: no mocks stand in for delivery, deduplication or outbox publication. */
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties={"pulsepass.inventory.expiry.enabled=false"})
class InventoryKafkaIT {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Container static final KafkaContainer kafka=new KafkaContainer("apache/kafka:3.9.1");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
        r.add("spring.kafka.bootstrap-servers",kafka::getBootstrapServers);
    }
    @Autowired InventoryService inventory;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired KafkaTemplate<String,String> producer;
    @Test void commandIsCommittedBeforeAcknowledgementAndResultArrivesThroughOutbox() throws Exception {
        try(var admin=AdminClient.create(Map.of("bootstrap.servers",kafka.getBootstrapServers()))) {
            for(var name:List.of("pulsepass.inventory.commands.v1","pulsepass.inventory.reservations.v1")) {
                if(!admin.listTopics().names().get().contains(name)) admin.createTopics(List.of(new NewTopic(name,1,(short)1))).all().get();
            }
        }
        var event=jdbc.queryForObject("select id from inventory_events order by id limit 1",UUID.class);
        var seat=jdbc.queryForObject("select id from inventory_seats where event_id=? order by id limit 1",UUID.class,event);
        var customer=UUID.randomUUID().toString();var order=UUID.randomUUID();
        var reservation=inventory.reserve(event,customer,"broker",List.of(seat));inventory.bind(reservation.reservationId(),order,customer);
        var command=new EventMessage(UUID.randomUUID(),"reservation.confirm.requested",1,"reservation",reservation.reservationId(),1,Instant.now(),"orders",null,null,json.valueToTree(Map.of("reservationId",reservation.reservationId(),"orderId",order,"customerId",customer,"paymentAttemptId",UUID.randomUUID())));
        var raw=json.writeValueAsString(command);
        producer.send("pulsepass.inventory.commands.v1",reservation.reservationId().toString(),raw).get();
        producer.send("pulsepass.inventory.commands.v1",reservation.reservationId().toString(),raw).get();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(()->assertEquals("CONFIRMED",jdbc.queryForObject("select status from inventory_reservations where id=?",String.class,reservation.reservationId())));
        assertEquals(1,jdbc.queryForObject("select count(*) from inbox where consumer='inventory-commands-v1' and event_id=?",Integer.class,command.eventId()));
        var config=Map.<String,Object>of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,kafka.getBootstrapServers(),ConsumerConfig.GROUP_ID_CONFIG,"inventory-test-"+UUID.randomUUID(),ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest",ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class,ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class);
        try(var consumer=new KafkaConsumer<String,String>(config)) {
            consumer.subscribe(List.of("pulsepass.inventory.reservations.v1"));
            await().atMost(Duration.ofSeconds(30)).until(()->{
                for(var record:consumer.poll(Duration.ofMillis(250))) {
                    var result=json.readValue(record.value(),EventMessage.class);
                    if(result.eventType().equals("reservation.confirmed")) {
                        assertEquals(command.eventId().toString(),result.payload().path("commandId").asText());
                        assertEquals("CONFIRMED",result.payload().path("status").asText());return true;
                    }
                }
                return false;
            });
        }
        assertEquals(1,jdbc.queryForObject("select count(*) from outbox where payload::jsonb->>'eventType'='reservation.confirmed'",Integer.class));
    }
}
