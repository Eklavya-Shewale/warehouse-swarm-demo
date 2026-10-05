package com.warehouse.swarm.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.warehouse.swarm.model.Models;
import com.warehouse.swarm.service.WarehouseService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class WarehouseController {
    private final WarehouseService service;

    public WarehouseController(WarehouseService service) {
        this.service = service;
    }

    @GetMapping("/state")
    public Models.State state() { return service.state(); }

    @PostMapping("/simulate/obstacle")
    public Models.State obstacle() { return service.simulateObstacle(); }

    @PostMapping("/simulate/failure/{id}")
    public Models.State failure(@PathVariable String id) { return service.failBot(id); }

    @PostMapping("/reset")
    public Models.State reset() { return service.reset(); }

    @PostMapping("/bots/{botId}/navigation-events")
    public Models.State navigationEvent(@PathVariable String botId,
                                        @RequestBody Models.NavigationEventRequest request) {
        return service.processNavigationEvent(botId, request);
    }

    @PutMapping("/bots/{botId}/state")
    public Models.State botState(@PathVariable String botId,
                                 @RequestBody Map<String, String> request) {
        return service.updateBotState(botId, request.get("status"));
    }

    @PostMapping("/tasks")
    public Models.Task createTask(@Valid @RequestBody Models.CreateTaskRequest request) {
        return service.createTask(request);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> request) {
        String username = request.getOrDefault("username", "admin");
        String password = request.getOrDefault("password", "admin");
        if (!"admin".equals(username) || !"admin".equals(password)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        service.loginEvent(username);
        return Map.of("token", "demo-jwt-token", "role", "ADMIN", "username", username);
    }

    
}
