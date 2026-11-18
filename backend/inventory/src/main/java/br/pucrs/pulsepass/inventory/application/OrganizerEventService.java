package br.pucrs.pulsepass.inventory.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrganizerEventService {
  private final JdbcTemplate jdbc;

  OrganizerEventService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> listMine(String organizerId) {
    return jdbc.query(
        "SELECT id, name, starts_at, venue, status FROM inventory_events "
            + "WHERE organizer_id = ? ORDER BY starts_at",
        (rs, i) -> toMap(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getTimestamp("starts_at").toInstant(),
            rs.getString("venue"),
            rs.getString("status")),
        organizerId);
  }

  public Map<String, Object> createDraft(String organizerId, String name, Instant startsAt, String venue) {
    if (name == null || name.isBlank() || startsAt == null || venue == null || venue.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name, startsAt e venue são obrigatórios");
    }
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO inventory_events(id, name, starts_at, venue, status, organizer_id) "
            + "VALUES (?, ?, ?, ?, 'DRAFT', ?)",
        id, name, Timestamp.from(startsAt), venue, organizerId);
    return toMap(id, name, startsAt, venue, "DRAFT");
  }

  private static Map<String, Object> toMap(UUID id, String name, Instant startsAt, String venue, String status) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", id);
    m.put("name", name);
    m.put("startsAt", startsAt);
    m.put("venue", venue);
    m.put("status", status);
    return m;
  }
}
