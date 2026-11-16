package br.pucrs.pulsepass.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.pucrs.pulsepass.gateway.api.GatewayController;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(classes = GatewayApplication.class)
@AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class GatewayDurabilityIT {
  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:17.6");

  @DynamicPropertySource
  static void config(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
  }

  @Autowired JdbcTemplate db;
  @Autowired MockMvc http;

  @Test
  void durableTimeoutIsQueriedWithSameAttemptAfterControllerRestart() throws Exception {
    UUID id = UUID.randomUUID();
    String body = "{\"attemptId\":\"" + id + "\",\"outcome\":\"TIMEOUT\"}";
    http.perform(
            post("/payments")
                .header("X-Gateway-Key", "local-gateway-secret")
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"));
    db.update(
        "UPDATE gateway_attempt SET created_at=clock_timestamp()-interval '9 seconds' WHERE attempt_id=?",
        id);
    assertEquals(
        "APPROVED",
        new GatewayController(
                new br.pucrs.pulsepass.gateway.application.GatewayService(
                    new br.pucrs.pulsepass.gateway.infrastructure.persistence.JdbcGatewayStore(db)))
            .get(id)
            .get("status"));
    http.perform(
            post("/payments")
                .header("X-Gateway-Key", "local-gateway-secret")
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT count(*) FROM gateway_attempt WHERE attempt_id=?", Integer.class, id));
    http.perform(
            post("/payments")
                .header("X-Gateway-Key", "local-gateway-secret")
                .contentType("application/json")
                .content(body.replace("TIMEOUT", "DECLINE")))
        .andExpect(status().isConflict());
  }

  @Test
  void gatewayRequiresOwnSecret() throws Exception {
    http.perform(get("/payments/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    http.perform(get("/payments/" + UUID.randomUUID()).header("X-Gateway-Key", "incorrect"))
        .andExpect(status().isUnauthorized());
  }
}
