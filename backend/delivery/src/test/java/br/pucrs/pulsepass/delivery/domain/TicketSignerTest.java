package br.pucrs.pulsepass.delivery.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TicketSignerTest {
  @TempDir Path keys;

  @Test
  void signatureSurvivesRestartAndRejectsTampering() throws Exception {
    var signer = new TicketSigner(keys.toString());
    String signed = signer.sign("{\"ticketId\":\"ficticio\"}");
    assertTrue(signer.verify(signed));
    assertFalse(signer.verify("e30." + signed.split("\\.")[1]));
    assertFalse(signer.verify("invalid"));
    assertTrue(new TicketSigner(keys.toString()).verify(signed));
  }
}
