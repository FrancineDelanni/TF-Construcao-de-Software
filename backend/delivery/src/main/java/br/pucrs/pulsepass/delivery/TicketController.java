package br.pucrs.pulsepass.delivery;

import br.pucrs.pulsepass.common.ApiException;
import br.pucrs.pulsepass.delivery.domain.TicketSigner;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/delivery")
@PreAuthorize("hasRole('Cliente')")
public class TicketController {
 private final JdbcTemplate jdbc;private final TicketSigner signer;
 public TicketController(JdbcTemplate jdbc,TicketSigner signer) {this.jdbc=jdbc;this.signer=signer;}
 @GetMapping("/orders/{orderId}/tickets")
 public List<Map<String,Object>> list(@PathVariable UUID orderId,@AuthenticationPrincipal Jwt jwt) {
  return jdbc.query("SELECT id,order_id,status,seat_description FROM ticket WHERE order_id=? AND customer_id=? ORDER BY seat_description",
    (rs,n)->Map.<String,Object>of("id",rs.getObject("id"),"orderId",rs.getObject("order_id"),"status",rs.getString("status"),"seatDescription",rs.getString("seat_description")),orderId,jwt.getSubject());
 }
 @GetMapping("/tickets/{id}/pdf")
 public ResponseEntity<byte[]> pdf(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) {
  var records=jdbc.queryForList("SELECT status,pdf FROM ticket WHERE id=? AND customer_id=?",id,jwt.getSubject());
  if(records.isEmpty()) throw new ApiException(404,"Ingresso não encontrado");
  var t=records.getFirst();if(!"ISSUED".equals(t.get("status"))) throw new ApiException(409,"Emissão pendente; consulte novamente");
  return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
     .header(HttpHeaders.CACHE_CONTROL,"no-store")
     .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"pulsepass-"+id+".pdf\"").body((byte[])t.get("pdf"));
 }
 @GetMapping("/public-key")
 public Map<String,String> publicKey() {return Map.of("keyId","delivery-ed25519-v1","algorithm","Ed25519","encoding","X.509-SPKI-base64","publicKey",signer.publicKey());}
}
