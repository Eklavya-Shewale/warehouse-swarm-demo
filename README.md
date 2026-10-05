# Warehouse Swarm Demo MVP

A 2–3 hour hackathon dashboard MVP for an intelligent warehouse swarm system.

## What is included

- Spring Boot backend
- Browser-based digital twin dashboard
- 3 simulated warehouse bots
- Edge-AI event simulation
- Dynamic task allocation
- Bot heartbeat/failure simulation
- Automatic task reassignment
- A* route-replanning event simulation
- Security panel showing authenticated/rejected devices
- Live event stream
- REST API ready to receive ESP32 data later

## Run

Requirements:
- Java 21
- Maven 3.9+

From this directory:

```bash
mvn spring-boot:run
```

Then open:

http://localhost:8080

## Demo flow

1. Show the Overview and Digital Twin.
2. Click **Simulate Obstacle**.
3. Point out Edge-AI classification → route blocked → route recalculated → swarm route updated.
4. Click **Simulate BOT-01 Failure**.
5. Point out heartbeat timeout → BOT-01 offline → task reassigned to another online bot.
6. Click **Create Demo Task**.
7. Show the new task being allocated automatically.
8. Show the Security panel and explain that the current UI simulates the device-authentication state; real fuzzy-extractor/ECDH firmware can be connected to the same backend later.

## Important scope note

This MVP intentionally keeps state in memory and does not yet connect physical ESP32 boards, ESP-NOW, TinyML, MySQL, or production JWT validation. Those are integration layers to add after the dashboard demo is stable.
