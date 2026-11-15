package br.pucrs.pulsepass.orders.api;
import br.pucrs.pulsepass.orders.application.OrderService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/orders") @PreAuthorize("hasRole('Cliente')")
public class OrderController {
 private final OrderService service;private final br.pucrs.pulsepass.orders.infra.OrderWorkers workers;
 public OrderController(OrderService service,br.pucrs.pulsepass.orders.infra.OrderWorkers workers){this.service=service;this.workers=workers;}
 public record Create(UUID reservationId){} public record Payment(String outcome){}
 @PostMapping public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key") String key,@RequestBody Create body){UUID id=service.create(jwt.getSubject(),key,body.reservationId());workers.start(id,body.reservationId(),jwt.getSubject());return accepted(jwt.getSubject(),id);}
 @GetMapping public Object list(@AuthenticationPrincipal Jwt jwt){return service.list(jwt.getSubject());}
 @GetMapping("/{id}") public Object get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return service.get(jwt.getSubject(),id);}
 @PostMapping("/{id}/payments") public ResponseEntity<?> pay(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestHeader("Idempotency-Key")String key,@RequestBody Payment body){return accepted(jwt.getSubject(),service.pay(jwt.getSubject(),id,key,body.outcome()));}
 @PostMapping("/{id}/cancel") public ResponseEntity<?> cancel(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestHeader("Idempotency-Key")String key,@RequestBody com.fasterxml.jackson.databind.JsonNode body){if(!body.isObject() || !body.isEmpty())throw new br.pucrs.pulsepass.common.ApiException(400,"Cancelamento exige objeto vazio");return accepted(jwt.getSubject(),service.cancel(jwt.getSubject(),id,key));}
 private ResponseEntity<?> accepted(String owner,UUID id){return ResponseEntity.accepted().header(HttpHeaders.LOCATION,"/api/orders/"+id).body(service.get(owner,id));}
}
