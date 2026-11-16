package br.pucrs.pulsepass.delivery.api;

import br.pucrs.pulsepass.delivery.application.TicketService;
import br.pucrs.pulsepass.delivery.domain.TicketSigner;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/delivery")
@PreAuthorize("hasRole('Cliente')")
public class TicketController {
  private final TicketService service;
  private final TicketSigner signer;

  public TicketController(TicketService service, TicketSigner signer) {
    this.service = service;
    this.signer = signer;
  }

  @GetMapping("/orders/{orderId}/tickets")
  public List<Map<String, Object>> list(
      @PathVariable UUID orderId, @AuthenticationPrincipal Jwt jwt) {
    return service.list(orderId, jwt.getSubject());
  }

  @GetMapping("/tickets/{id}/pdf")
  public ResponseEntity<byte[]> pdf(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pulsepass-" + id + ".pdf\"")
        .body(service.pdf(id, jwt.getSubject()));
  }

  @GetMapping("/public-key")
  public Map<String, String> publicKey() {
    return Map.of(
        "keyId",
        "delivery-ed25519-v1",
        "algorithm",
        "Ed25519",
        "encoding",
        "X.509-SPKI-base64",
        "publicKey",
        signer.publicKey());
  }
}
