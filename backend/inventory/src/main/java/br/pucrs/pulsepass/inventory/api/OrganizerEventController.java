package br.pucrs.pulsepass.inventory.api;

import br.pucrs.pulsepass.inventory.application.OrganizerEventService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory/organizer/events")
@PreAuthorize("hasRole('Organizador')")
public class OrganizerEventController {
  private final OrganizerEventService service;
  OrganizerEventController(OrganizerEventService service) { this.service = service; }

  public record CreateEvent(String name, Instant startsAt, String venue) {}

  @GetMapping
  public List<Map<String, Object>> mine(@AuthenticationPrincipal Jwt jwt) {
    return service.listMine(jwt.getSubject());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateEvent body) {
    return service.createDraft(jwt.getSubject(), body.name(), body.startsAt(), body.venue());
  }
}