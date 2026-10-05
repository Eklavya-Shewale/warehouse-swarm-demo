package com.warehouse.swarm.controller;

import com.warehouse.swarm.model.Models;
import com.warehouse.swarm.service.WarehouseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
