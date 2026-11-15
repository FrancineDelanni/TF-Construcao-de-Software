package br.pucrs.pulsepass.common;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventMessageTest {
    @Test void rejectsUnsupportedVersionAndMissingIdentity() {
        var payload = new ObjectMapper().createObjectNode();
        var id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new EventMessage(id,"order.paid",2,"order",id,1,Instant.now(),"orders",id,null,payload).validate());
        assertThrows(IllegalArgumentException.class, () -> new EventMessage(null,"order.paid",1,"order",id,1,Instant.now(),"orders",id,null,payload).validate());
        assertDoesNotThrow(() -> new EventMessage(id,"order.paid",1,"order",id,1,Instant.now(),"orders",id,null,payload).validate());
    }
}
