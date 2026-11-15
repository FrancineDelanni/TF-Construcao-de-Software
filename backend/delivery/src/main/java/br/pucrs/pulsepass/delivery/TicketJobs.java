package br.pucrs.pulsepass.delivery;

import br.pucrs.pulsepass.common.Outbox;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketJobs {
 private final JdbcTemplate jdbc;private final Outbox outbox;
 public TicketJobs(JdbcTemplate jdbc,Outbox outbox) {this.jdbc=jdbc;this.outbox=outbox;}
 @Transactional
 public Optional<Map<String,Object>> claim() {
  var jobs=jdbc.queryForList("SELECT id FROM ticket WHERE status<>'ISSUED' AND next_attempt_at<=clock_timestamp() AND (lease_until IS NULL OR lease_until<clock_timestamp()) ORDER BY issued_at LIMIT 1 FOR UPDATE SKIP LOCKED");
  if(jobs.isEmpty()) return Optional.empty();
  UUID lease=UUID.randomUUID();Object id=jobs.getFirst().get("id");
  jdbc.update("UPDATE ticket SET status='PROCESSING',lease_token=?,lease_until=clock_timestamp()+interval '1 minute',attempts=attempts+1 WHERE id=?",lease,id);
  return Optional.of(jdbc.queryForMap("SELECT * FROM ticket WHERE id=?",id));
 }
 @Transactional
 public void complete(UUID id,UUID lease,byte[] pdf) {
  var rows=jdbc.queryForList("UPDATE ticket SET status='ISSUED',pdf=?,lease_until=NULL WHERE id=? AND lease_token=? AND status='PROCESSING' RETURNING order_id,order_item_id,event_id",pdf,id,lease);
  if(rows.isEmpty()) return;
  var t=rows.getFirst();
  outbox.add("pulsepass.delivery.events.v1","ticket.issued","order",(UUID)t.get("order_id"),1,
      Map.of("ticketId",id,"orderId",t.get("order_id"),"orderItemId",t.get("order_item_id"),"eventId",t.get("event_id")));
 }
 @Transactional
 public void failed(UUID id,UUID lease) {
  jdbc.update("UPDATE ticket SET status='PENDING',lease_until=NULL,next_attempt_at=clock_timestamp()+interval '5 seconds' WHERE id=? AND lease_token=? AND status='PROCESSING'",id,lease);
 }
}
