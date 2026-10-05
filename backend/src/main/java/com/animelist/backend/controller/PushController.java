package com.animelist.backend.controller;

import com.animelist.backend.dto.push.*;
import com.animelist.backend.service.PushDeliveryService;
import com.animelist.backend.service.PushSubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/push")
public class PushController {
    private final PushSubscriptionService subscriptions;
    private final PushDeliveryService delivery;

    public PushController(PushSubscriptionService subscriptions, PushDeliveryService delivery) {
        this.subscriptions = subscriptions;
        this.delivery = delivery;
    }

    @GetMapping("/config") public PushConfigResponse config() { return subscriptions.config(); }
    @PostMapping("/subscriptions") public PushSubscriptionResponse save(@Valid @RequestBody PushSubscriptionRequest body) { return subscriptions.save(body); }
    @PostMapping("/subscriptions/status") public PushSubscriptionResponse status(@Valid @RequestBody PushEndpointRequest body) { return subscriptions.status(body); }
    @PatchMapping("/subscriptions") public PushSubscriptionResponse preferences(@Valid @RequestBody PushSubscriptionRequest body) { return subscriptions.preferences(body); }
    @DeleteMapping("/subscriptions") public ResponseEntity<Void> delete(@Valid @RequestBody PushEndpointRequest body) { subscriptions.delete(body); return ResponseEntity.noContent().build(); }
    @PostMapping("/test") public ResponseEntity<Void> test(@Valid @RequestBody PushEndpointRequest body) { delivery.sendTest(body.endpoint()); return ResponseEntity.noContent().build(); }
}
