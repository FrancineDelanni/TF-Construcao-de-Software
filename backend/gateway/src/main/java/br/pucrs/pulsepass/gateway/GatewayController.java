package br.pucrs.pulsepass.gateway;
import br.pucrs.pulsepass.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.*;
@RestController
public class GatewayController {
 private final JdbcTemplate db;
 public GatewayController(JdbcTemplate db){this.db=db;}
 public record Request(UUID attemptId,String outcome){}
 @PostMapping("/payments") @Transactional
 public Map<String,Object> create(@RequestBody Request request){
  if(request.attemptId()==null || !Set.of("APPROVE","DECLINE","TIMEOUT").contains(Objects.toString(request.outcome(),""))) throw new ApiException(400,"Tentativa e resultado válidos são obrigatórios");
  db.update("INSERT INTO gateway_attempt(attempt_id,outcome) VALUES (?,?) ON CONFLICT DO NOTHING",request.attemptId(),request.outcome());
  Map<String,Object> row=db.queryForMap("SELECT *,clock_timestamp() AS now FROM gateway_attempt WHERE attempt_id=?",request.attemptId());
  if(!request.outcome().equals(row.get("outcome"))) throw new ApiException(409,"Tentativa já utilizada com outro resultado");
  return response(row);
 }
 @GetMapping("/payments/{id}") public Map<String,Object> get(@PathVariable UUID id){
  var rows=db.queryForList("SELECT *,clock_timestamp() AS now FROM gateway_attempt WHERE attempt_id=?",id);
  if(rows.isEmpty()) throw new ApiException(404,"Tentativa não encontrada");
  return response(rows.getFirst());
 }
 private Map<String,Object> response(Map<String,Object> row){return Map.of("attemptId",row.get("attempt_id"),"status",GatewayDecision.status((String)row.get("outcome"),((java.sql.Timestamp)row.get("created_at")).toInstant(),((java.sql.Timestamp)row.get("now")).toInstant()));}
}
