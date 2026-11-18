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