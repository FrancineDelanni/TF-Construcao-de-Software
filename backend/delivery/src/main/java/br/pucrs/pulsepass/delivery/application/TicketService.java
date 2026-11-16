package br.pucrs.pulsepass.delivery.application;

import br.pucrs.pulsepass.common.web.ApiException;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class TicketService {
  private final TicketStore store;

  public TicketService(TicketStore store) {
    this.store = store;
  }

  public List<Map<String, Object>> list(UUID orderId, String owner) {
    return store.list(orderId, owner);
  }

  public byte[] pdf(UUID id, String owner) {
    var records = store.ownedPdf(id, owner);
    if (records.isEmpty()) throw new ApiException(404, "Ingresso não encontrado");
    var ticket = records.getFirst();
    if (!"ISSUED".equals(ticket.get("status")))
      throw new ApiException(409, "Emissão pendente; consulte novamente");
    return (byte[]) ticket.get("pdf");
  }
}
