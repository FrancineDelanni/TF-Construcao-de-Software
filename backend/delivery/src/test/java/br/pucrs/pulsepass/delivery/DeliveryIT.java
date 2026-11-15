package br.pucrs.pulsepass.delivery;

import br.pucrs.pulsepass.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Testcontainers
@SpringBootTest(properties={"pulsepass.outbox.enabled=false","pulsepass.delivery.worker.enabled=false","spring.kafka.listener.auto-startup=false"})
@AutoConfigureMockMvc
class DeliveryIT {
 @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17.6");
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r) throws Exception {
  r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);r.add("spring.datasource.password",DB::getPassword);
  String dir=Files.createTempDirectory("pulsepass-ticket-keys-").toString();r.add("pulsepass.delivery.key-directory",()->dir);
 }
 @Autowired PaidOrderHandler handler;@Autowired TicketJobs jobs;@Autowired TicketPdf pdf;
 @Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired MockMvc mvc;
 UUID order=UUID.randomUUID(); UUID item=UUID.randomUUID();
 EventMessage paid(UUID messageId) {
  var p=Map.of("orderId",order,"reservationId",UUID.randomUUID(),"customerId","cliente-a","eventId",UUID.randomUUID(),"eventName","Festival Pulse","eventStartsAt","2026-11-28T22:00:00Z","venue","Arena Demo","items",List.of(Map.of("orderItemId",item,"seatId",UUID.randomUUID(),"sector","Pista","row","A","number","1","priceCents",15000)));
  return new EventMessage(messageId,"order.paid",1,"order",order,4,Instant.now(),"orders",messageId,null,json.valueToTree(p));
 }
 @BeforeEach void clean() {jdbc.execute("TRUNCATE ticket,inbox,outbox,consumer_quarantine RESTART IDENTITY");}
 @Test void duplicateDeliveryOneTicketAndPdfOwned() throws Exception {
  var event=paid(UUID.randomUUID());handler.accept(event);handler.accept(event);
  var redelivered=new EventMessage(UUID.randomUUID(),event.eventType(),1,event.aggregateType(),event.aggregateId(),4,event.occurredAt(),event.producer(),event.correlationId(),null,event.payload());
  handler.accept(redelivered);
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM ticket",Integer.class));
  var job=jobs.claim().orElseThrow();var id=(UUID)job.get("id");var lease=(UUID)job.get("lease_token");
  jobs.failed(id,lease);jdbc.update("UPDATE ticket SET next_attempt_at=clock_timestamp()");
  var retry=jobs.claim().orElseThrow();byte[] bytes=pdf.render(retry);
  jobs.complete(id,(UUID)retry.get("lease_token"),bytes);
  jobs.complete(id,(UUID)retry.get("lease_token"),bytes);
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM outbox",Integer.class));
  assertEquals(2,jdbc.queryForObject("SELECT attempts FROM ticket",Integer.class));
  try(var document=Loader.loadPDF(bytes)) {var text=new PDFTextStripper().getText(document);assertTrue(text.contains("Festival Pulse"));assertTrue(text.contains("Pista / A / 1"));assertEquals(1,document.getNumberOfPages());}
  mvc.perform(get("/api/delivery/tickets/"+id+"/pdf").with(jwt().jwt(j->j.subject("cliente-a")).authorities(new SimpleGrantedAuthority("ROLE_Cliente")))).andExpect(status().isOk());
  mvc.perform(get("/api/delivery/tickets/"+id+"/pdf").with(jwt().jwt(j->j.subject("cliente-b")).authorities(new SimpleGrantedAuthority("ROLE_Cliente")))).andExpect(status().isNotFound());
  mvc.perform(get("/api/delivery/tickets/"+id+"/pdf")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/delivery/tickets/"+id+"/pdf").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operador")))).andExpect(status().isForbidden());
  Files.write(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),"pulsepass-ticket-sample.pdf"),bytes);
  try(var document=Loader.loadPDF(bytes)) {javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(document).renderImageWithDPI(0,100),"png",java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),"pulsepass-ticket-sample.png").toFile());}
 }
 @Test void crashLeaseRecoversAndMalformedPayloadRollsBackInbox() {
  var event=paid(UUID.randomUUID());handler.accept(event);var first=jobs.claim().orElseThrow();assertTrue(jobs.claim().isEmpty());
  jdbc.update("UPDATE ticket SET lease_until=clock_timestamp()-interval '1 second'");
  var retry=jobs.claim().orElseThrow();assertNotEquals(first.get("lease_token"),retry.get("lease_token"));
  var invalid=event.payload().deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)invalid).put("orderId",UUID.randomUUID().toString());
  UUID bad=UUID.randomUUID();assertThrows(IllegalArgumentException.class,()->handler.accept(new EventMessage(bad,"order.paid",1,"order",order,4,Instant.now(),"orders",bad,null,invalid)));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM inbox WHERE event_id=?",Integer.class,bad));
 }
}
