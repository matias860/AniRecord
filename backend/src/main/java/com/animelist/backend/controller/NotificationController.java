package com.animelist.backend.controller;

import com.animelist.backend.dto.notification.NotificationResponse;
import com.animelist.backend.dto.notification.NotificationSyncResponse;
import com.animelist.backend.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/notifications") @Validated
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service = service; }
    @GetMapping public List<NotificationResponse> recent(@RequestParam(defaultValue="30") @Min(1) @Max(100) int limit) { return service.findRecent(limit); }
    @GetMapping("/unread-count") public Map<String, Long> unread() { return Map.of("count", service.unreadCount()); }
    @PostMapping("/sync") public NotificationSyncResponse sync(@RequestParam(defaultValue="30") @Min(1) @Max(100) int limit) {
        var result = service.syncSafely();
        return new NotificationSyncResponse(result.added(), result.successful(), service.findRecent(limit));
    }
    @PatchMapping("/{id}/read") public void read(@PathVariable Long id) { service.read(id); }
    @PatchMapping("/read") public void read(@RequestBody List<Long> ids) { service.read(ids); }
    @PatchMapping("/read-all") public void readAll() { service.readAll(); }
}
