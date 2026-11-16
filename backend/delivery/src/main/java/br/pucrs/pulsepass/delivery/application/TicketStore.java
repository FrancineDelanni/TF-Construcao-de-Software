package br.pucrs.pulsepass.delivery.application;

import java.util.*;

public interface TicketStore {
  void insert(
      UUID id,
      UUID itemId,
      UUID orderId,
      String customer,
      UUID eventId,
      String name,
      String starts,
      String venue,
      UUID seatId,
      String seat);

  Map<String, Object> itemIdentity(UUID itemId);

  List<Map<String, Object>> nextJob();

  void lease(UUID id, UUID lease);

  Map<String, Object> job(UUID id);

  List<Map<String, Object>> complete(UUID id, UUID lease, byte[] pdf);

  void failed(UUID id, UUID lease);

  List<Map<String, Object>> list(UUID orderId, String owner);

  List<Map<String, Object>> ownedPdf(UUID id, String owner);
}
