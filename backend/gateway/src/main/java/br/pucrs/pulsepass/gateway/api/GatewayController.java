package br.pucrs.pulsepass.gateway.api;

import br.pucrs.pulsepass.gateway.application.GatewayService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class GatewayController {
  private final GatewayService service;

  public GatewayController(GatewayService service) {
    this.service = service;
  }

  public record Request(UUID attemptId, String outcome) {}

  @PostMapping("/payments")
  public Map<String, Object> create(@RequestBody Request request) {
    return service.create(request.attemptId(), request.outcome());
  }

  @GetMapping("/payments/{id}")
  public Map<String, Object> get(@PathVariable UUID id) {
    return service.get(id);
  }
}
