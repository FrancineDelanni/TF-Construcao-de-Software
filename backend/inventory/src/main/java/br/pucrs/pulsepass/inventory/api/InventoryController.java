package br.pucrs.pulsepass.inventory.api;

import br.pucrs.pulsepass.inventory.application.InventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService inventory;
    InventoryController(InventoryService inventory) {this.inventory=inventory;}
    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('Cliente','Organizador','Operador','Admin')")
    public List<Map<String,Object>> events() {return inventory.catalog();}
    @GetMapping("/events/{id}")
    @PreAuthorize("hasAnyRole('Cliente','Organizador','Operador','Admin')")
    public Map<String,Object> event(@PathVariable UUID id) {return inventory.catalogEvent(id);}
    public record ReserveRequest(List<UUID> seatIds) { }
    public record BindRequest(UUID orderId,String customerId) { }
    @PostMapping("/events/{id}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('Cliente')")
    public ReservationSnapshot reserve(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name="Idempotency-Key",required=false) String key,@RequestBody ReserveRequest body) {
        return inventory.reserve(id,jwt.getSubject(),key,body.seatIds());
    }
    @GetMapping("/reservations/{id}")
    @PreAuthorize("hasRole('Cliente')")
    public ReservationSnapshot reservation(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) {return inventory.get(id,jwt.getSubject());}
    @GetMapping("/internal/reservations/{id}")
    @PreAuthorize("hasRole('ServiceOrders')")
    public ReservationSnapshot internal(@PathVariable UUID id) {return inventory.get(id,null);}
    @PostMapping("/internal/reservations/{id}/bind")
    @PreAuthorize("hasRole('ServiceOrders')")
    public ReservationSnapshot bind(@PathVariable UUID id,@RequestBody BindRequest body) {return inventory.bind(id,body.orderId(),body.customerId());}
}
