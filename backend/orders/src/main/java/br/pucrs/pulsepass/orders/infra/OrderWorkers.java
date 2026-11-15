package br.pucrs.pulsepass.orders.infra;
import br.pucrs.pulsepass.orders.application.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;
import java.util.*;
@Component
public class OrderWorkers {
 private static final Logger log=LoggerFactory.getLogger(OrderWorkers.class);
 private final OrderService service;private final RemoteClients remote;
 @org.springframework.beans.factory.annotation.Value("${pulsepass.orders.workers:true}") private boolean enabled;
 public OrderWorkers(OrderService service,RemoteClients remote){this.service=service;this.remote=remote;}
 // Every HTTP request completes before entering the transactional service method.
 @Scheduled(fixedDelayString="${pulsepass.orders.reconcile-ms:1000}") public void reconcile(){if(!enabled)return;for(var row:service.workOrders())try{
  UUID id=(UUID)row.get("id"),reservation=(UUID)row.get("reservation_id");
  com.fasterxml.jackson.databind.JsonNode snapshot;
  if("CREATED".equals(row.get("state"))) {
   snapshot=bind(id,reservation,(String)row.get("customer_id"));
  } else snapshot=remote.reservation(reservation);
  service.snapshot(id,snapshot);
 }catch(RuntimeException failure){log.warn("Reconciliação de pedido pendente: {}",row.get("id"));}}
 public com.fasterxml.jackson.databind.JsonNode bind(UUID id,UUID reservation,String owner){
  com.fasterxml.jackson.databind.JsonNode snapshot;
  try {snapshot=remote.bind(reservation,id,owner);}
  catch(org.springframework.web.client.HttpClientErrorException rejected){
   try {snapshot=remote.reservation(reservation);}
   catch(org.springframework.web.client.HttpClientErrorException.NotFound missing){service.rejectIntent(id);throw new br.pucrs.pulsepass.common.ApiException(404,"Reserva não encontrada");}
  }
  if(!owner.equals(snapshot.path("customerId").asText())){service.rejectIntent(id);throw new br.pucrs.pulsepass.common.ApiException(404,"Reserva não encontrada");}
  return snapshot;
 }
 public void start(UUID id,UUID reservation,String owner){
  try {service.snapshot(id,bind(id,reservation,owner));}
  catch(br.pucrs.pulsepass.common.ApiException business){throw business;}
  catch(RuntimeException pending){log.warn("Vinculação de pedido pendente: {}",id);}
 }
 @Scheduled(fixedDelayString="${pulsepass.orders.payment-ms:1000}") public void payments(){if(!enabled)return;for(var attempt:service.workPayments())try{
  var result=remote.payment((UUID)attempt.get("id"),(String)attempt.get("outcome"));service.paymentResult((UUID)attempt.get("id"),result.path("status").asText());
 }catch(RuntimeException failure){log.warn("Pagamento pendente: {}",attempt.get("id"));}}
}
