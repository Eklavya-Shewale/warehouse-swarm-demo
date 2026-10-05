package com.warehouse.swarm.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class Models {
    private Models() {}

    public record Bot(String id, String status, int battery, String task, double x, double y,
                      String aiState, int aiConfidence, String connection, String lastSeen) {}

    public record Task(String id, String source, String destination, String priority,
                       String assignedBot, String status, String routeStatus) {}

    public record Event(String time, String source, String type, String message, String severity) {}

    public record AiEvent(String state, int confidence, int distanceCm, String irDetected, String action) {}

    public record SecurityDevice(String id, String status, String authMethod, String session, String lastEvent) {}

    public record State(List<Bot> bots, List<Task> tasks, List<Event> events,
                        AiEvent ai, List<SecurityDevice> security, int activeTasks,
                        int completedTasks, int obstaclesDetected, int reassignments) {}

    public record CreateTaskRequest(String source, String destination, String priority) {}

    public record NavigationEventRequest(String action, String reason, String timestamp,
                                         Integer priorityObjectId, Double confidence,
                                         Map<String, Object> object,
                                         Map<String, Double> clearance) {}
}
