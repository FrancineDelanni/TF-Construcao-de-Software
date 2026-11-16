package br.pucrs.pulsepass.gateway.application;

import java.util.*;

public interface GatewayStore {
  void insert(UUID id, String outcome);

  Map<String, Object> attempt(UUID id);

  List<Map<String, Object>> find(UUID id);
}
