package com.warehouse.swarm.service;

import com.warehouse.swarm.model.Models;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class WarehouseService {
    private final Map<String, BotState> bots = new LinkedHashMap<>();
    private final List<Models.Task> tasks = new CopyOnWriteArrayList<>();
    private final List<Models.Event> events = new CopyOnWriteArrayList<>();
    private final Map<String, Models.SecurityDevice> security = new LinkedHashMap<>();
    private final AtomicInteger taskCounter = new AtomicInteger(106);
    private int completedTasks = 17;
    private int obstaclesDetected = 31;
    private int reassignments = 3;
    private Models.AiEvent ai = new Models.AiEvent("CLEAR", 98, 85, "NO", "NONE");
    private boolean obstacleActive = false;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public WarehouseService() {
        bots.put("BOT-01", new BotState("BOT-01", "ONLINE", 82, "#104", 4, 2));
        bots.put("BOT-02", new BotState("BOT-02", "ONLINE", 61, "#105", 12, 7));
        bots.put("BOT-03", new BotState("BOT-03", "ONLINE", 91, "#106", 17, 4));

        tasks.add(new Models.Task("#104", "Shelf A", "Dock 2", "HIGH", "BOT-01", "RUNNING", "NORMAL"));
        tasks.add(new Models.Task("#105", "Shelf C", "Dock 1", "MEDIUM", "BOT-02", "RUNNING", "NORMAL"));
        tasks.add(new Models.Task("#106", "Shelf B", "Dock 3", "LOW", "BOT-03", "PENDING", "NORMAL"));

        security.put("BOT-01", new Models.SecurityDevice("BOT-01", "AUTHENTICATED", "Challenge + ECDH", "ACTIVE", now()));
        security.put("BOT-02", new Models.SecurityDevice("BOT-02", "AUTHENTICATED", "Challenge + ECDH", "ACTIVE", now()));
        security.put("BOT-03", new Models.SecurityDevice("BOT-03", "AUTHENTICATED", "Challenge + ECDH", "ACTIVE", now()));
        security.put("BOT-99", new Models.SecurityDevice("BOT-99", "REJECTED", "Challenge + ECDH", "BLOCKED", now()));

        log("SYSTEM", "SYSTEM_READY", "Warehouse control plane started", "INFO");
    }

    public synchronized Models.State state() {
        List<Models.Bot> botList = bots.values().stream().map(BotState::toModel).toList();
        int active = (int) tasks.stream().filter(t -> "RUNNING".equals(t.status())).count();
        return new Models.State(botList, List.copyOf(tasks), List.copyOf(events), ai,
                List.copyOf(security.values()), active, completedTasks, obstaclesDetected, reassignments);
    }

    public synchronized Models.State simulateObstacle() {
        obstacleActive = !obstacleActive;
        if (obstacleActive) {
            obstaclesDetected++;
            ai = new Models.AiEvent("OBSTACLE APPROACHING", 92, 24, "YES", "REPLAN ROUTE");
            log("EDGE-AI", "OBSTACLE_DETECTED", "Obstacle approaching · AI confidence 92%", "WARNING");
            tasks.replaceAll(t -> t.id().equals("#104")
                    ? new Models.Task(t.id(), t.source(), t.destination(), t.priority(), t.assignedBot(), t.status(), "BLOCKED") : t);
            log("A*", "ROUTE_RECALCULATED", "BOT-01 route blocked; new path calculated", "INFO");
            log("BOT-01", "ROUTE_UPDATED", "Swarm route updated via ESP-NOW", "INFO");
        } else {
            ai = new Models.AiEvent("CLEAR", 98, 85, "NO", "NONE");
            log("EDGE-AI", "PATH_CLEAR", "Obstacle cleared · normal route restored", "INFO");
            tasks.replaceAll(t -> t.id().equals("#104")
                    ? new Models.Task(t.id(), t.source(), t.destination(), t.priority(), t.assignedBot(), t.status(), "NORMAL") : t);
        }
        return state();
    }

    public synchronized Models.State failBot(String id) {
        BotState bot = bots.get(id);
        if (bot == null) return state();
        bot.status = "OFFLINE";
        bot.lastSeen = now();
        security.computeIfPresent(id, (k, v) -> new Models.SecurityDevice(v.id(), v.status(), v.authMethod(), "TERMINATED", now()));
        log(id, "BOT_OFFLINE", "Heartbeat timeout — bot marked offline", "ERROR");

        for (int i = 0; i < tasks.size(); i++) {
            Models.Task t = tasks.get(i);
            if (id.equals(t.assignedBot()) && !"COMPLETED".equals(t.status())) {
                Optional<BotState> replacement = bots.values().stream()
                        .filter(b -> "ONLINE".equals(b.status) && !b.id.equals(id))
                        .min(Comparator.comparingInt(b -> score(b, t)));
                if (replacement.isPresent()) {
                    BotState r = replacement.get();
                    tasks.set(i, new Models.Task(t.id(), t.source(), t.destination(), t.priority(), r.id, "RUNNING", "REASSIGNED"));
                    r.task = t.id();
                    reassignments++;
                    log("SYSTEM", "TASK_REASSIGNED", t.id() + " reassigned from " + id + " → " + r.id, "WARNING");
                }
            }
        }
        return state();
    }

    public synchronized Models.State reset() {
        bots.get("BOT-01").reset("#104", 4, 2, 82);
        bots.get("BOT-02").reset("#105", 12, 7, 61);
        bots.get("BOT-03").reset("#106", 17, 4, 91);
        tasks.clear();
        tasks.add(new Models.Task("#104", "Shelf A", "Dock 2", "HIGH", "BOT-01", "RUNNING", "NORMAL"));
        tasks.add(new Models.Task("#105", "Shelf C", "Dock 1", "MEDIUM", "BOT-02", "RUNNING", "NORMAL"));
        tasks.add(new Models.Task("#106", "Shelf B", "Dock 3", "LOW", "BOT-03", "PENDING", "NORMAL"));
        ai = new Models.AiEvent("CLEAR", 98, 85, "NO", "NONE");
        obstacleActive = false;
        log("SYSTEM", "RESET", "Simulation reset", "INFO");
        return state();
    }

    public synchronized Models.State processNavigationEvent(String botId, Models.NavigationEventRequest request) {
        String normalizedBotId = normalizeBotId(botId);
        BotState bot = bots.get(normalizedBotId);
        if (bot == null || request == null || request.action() == null) return state();

        String action = request.action().toUpperCase(Locale.ROOT);
        String reason = request.reason() == null || request.reason().isBlank()
                ? "Navigation event received" : request.reason();
        int confidence = request.confidence() == null
                ? bot.aiConfidence : (int) Math.round(request.confidence() * 100);

        switch (action) {
            case "CONTINUE" -> {
                obstacleActive = false;
                ai = new Models.AiEvent("CLEAR", confidence, 85, "NO", "NONE");
                bot.aiState = "CLEAR";
                bot.aiConfidence = confidence;
                log(normalizedBotId, "PATH_CLEAR", "Vision module reports clear path", "INFO");
            }
            case "WAIT" -> {
                obstacleActive = true;
                ai = new Models.AiEvent("OBSTACLE WAITING", confidence, 24, "YES", "WAIT");
                bot.aiState = "OBSTACLE WAITING";
                bot.aiConfidence = confidence;
                log(normalizedBotId, "NAVIGATION_WAIT", reason, "WARNING");
            }
            case "STOP" -> {
                obstacleActive = true;
                ai = new Models.AiEvent("BLOCKED", confidence, 24, "YES", "STOP");
                bot.aiState = "BLOCKED";
                bot.aiConfidence = confidence;
                log(normalizedBotId, "NAVIGATION_STOP", reason, "WARNING");
            }
            case "AVOID_LEFT", "AVOID_RIGHT" -> {
                obstacleActive = true;
                String direction = action.substring("AVOID_".length());
                ai = new Models.AiEvent("OBSTACLE AVOIDANCE", confidence, 24, "YES", action);
                bot.aiState = "OBSTACLE AVOIDANCE";
                bot.aiConfidence = confidence;
                log(normalizedBotId, "LOCAL_AVOIDANCE", reason + " — avoiding " + direction.toLowerCase(Locale.ROOT), "WARNING");
            }
            case "REPLAN" -> {
                obstacleActive = true;
                ai = new Models.AiEvent("ROUTE BLOCKED", confidence, 24, "YES", "REPLAN ROUTE");
                bot.aiState = "ROUTE BLOCKED";
                bot.aiConfidence = confidence;
                tasks.replaceAll(t -> normalizedBotId.equals(t.assignedBot()) && !"COMPLETED".equals(t.status())
                        ? new Models.Task(t.id(), t.source(), t.destination(), t.priority(), t.assignedBot(), t.status(), "BLOCKED")
                        : t);
                log(normalizedBotId, "ROUTE_REPLAN_REQUESTED",
                        "Static obstacle blocked route — new route requested", "WARNING");
            }
            default -> log(normalizedBotId, "NAVIGATION_EVENT", reason, "INFO");
        }
        return state();
    }

    public synchronized Models.State updateBotState(String botId, String status) {
        String normalizedBotId = normalizeBotId(botId);
        BotState bot = bots.get(normalizedBotId);
        if (bot == null || status == null || status.isBlank()) return state();

        bot.status = status;
        bot.lastSeen = now();
        log(normalizedBotId, "BOT_STATE_CHANGED", "Bot state changed to " + status, "INFO");
        return state();
    }

    public synchronized Models.Task createTask(Models.CreateTaskRequest req) {
        String id = "#" + taskCounter.incrementAndGet();
        Optional<BotState> bot = bots.values().stream().filter(b -> "ONLINE".equals(b.status))
                .min(Comparator.comparingInt(b -> loadScore(b, req.priority())));
        String botId = bot.map(b -> b.id).orElse("UNASSIGNED");
        Models.Task task = new Models.Task(id, req.source(), req.destination(), req.priority(), botId,
                "UNASSIGNED".equals(botId) ? "PENDING" : "RUNNING", "NORMAL");
        tasks.add(task);
        bot.ifPresent(b -> b.task = id);
        log("SYSTEM", "TASK_CREATED", id + " assigned to " + botId, "INFO");
        return task;
    }

    public synchronized void loginEvent(String username) {
        log("AUTH", "LOGIN_SUCCESS", "Dashboard user authenticated: " + username, "INFO");
    }

    private String normalizeBotId(String botId) {
        return botId == null ? null : botId.replace('_', '-');
    }

    private int score(BotState b, Models.Task t) {
        return (int) (Math.abs(b.x - 4) + Math.abs(b.y - 2) + (100 - b.battery) * 0.1);
    }

    private int loadScore(BotState b, String priority) {
        return (int) ((100 - b.battery) + Math.abs(b.x - 4) + Math.abs(b.y - 2));
    }

    private void log(String source, String type, String message, String severity) {
        events.add(0, new Models.Event(now(), source, type, message, severity));
        while (events.size() > 30) events.remove(events.size() - 1);
    }

    private String now() { return TIME.format(Instant.now()); }

    private static class BotState {
        final String id;
        String status;
        int battery;
        String task;
        double x, y;
        String lastSeen;
        String aiState = "CLEAR";
        int aiConfidence = 98;
        String connection = "ESP-NOW ✓";

        BotState(String id, String status, int battery, String task, double x, double y) {
            this.id = id; this.status = status; this.battery = battery; this.task = task; this.x = x; this.y = y;
            this.lastSeen = TIME.format(Instant.now());
        }

        void reset(String task, double x, double y, int battery) {
            this.status = "ONLINE"; this.task = task; this.x = x; this.y = y; this.battery = battery; this.lastSeen = TIME.format(Instant.now());
        }

        Models.Bot toModel() {
            return new Models.Bot(id, status, battery, task, x, y, aiState, aiConfidence, connection, lastSeen);
        }
    }
}
