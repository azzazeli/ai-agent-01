package com.alexm.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;

public class StatefulAgent {

    public static void main(String[] args) {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Error: GEMINI_API_KEY environment variable not set.");
            return;
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=" + apiKey;

        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();
        Scanner scanner = new Scanner(System.in);

        // --- DAY 19: Booting the MCP Server ---
        System.out.println("[SYSTEM] Booting MCP Server in the background...");
        Process mcpProcess = null;
        try {
            String currentClasspath = System.getProperty("java.class.path");
            // Launch the compiled McpServer class from the Gradle output directory
            ProcessBuilder pb = new ProcessBuilder("java",
                    "-cp", currentClasspath,
                    "com.alexm.agent.mcp.McpServer");

            // Inherit the error stream so the Server's System.err logs show up in our terminal
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);

            mcpProcess = pb.start();
            System.out.println("[SYSTEM] MCP Server running.");

        } catch (Exception e) {
            System.err.println("Failed to start MCP Server: " + e.getMessage());
            return;
        }

        // Graceful shutdown hook to avoid zombie processes
        final Process finalMcpProcess = mcpProcess;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[SYSTEM] Shutting down MCP Server...");
            if (finalMcpProcess != null) {
                finalMcpProcess.destroy();
            }
        }));

        // --- Memory & Persona Initialization ---
        ArrayNode history = mapper.createArrayNode();

        ObjectNode systemInstruction = mapper.createObjectNode();
        systemInstruction.putArray("parts")
                .addObject()
                .put("text", "You are a strict, terminal-only infrastructure assistant managing a home lab running Ubuntu. Keep answers concise.");

        System.out.println("System initialized. Chat session started. Type 'exit' to quit.\n");

        // --- The Execution Loop ---
        while (true) {
            System.out.print("You: ");
            String userInput = scanner.nextLine();

            if ("exit".equalsIgnoreCase(userInput)) {
                System.out.println("Terminating session.");
                break;
            }

            try {
                // 1. Append User Input
                ObjectNode userMessage = mapper.createObjectNode();
                userMessage.put("role", "user");
                userMessage.putArray("parts").addObject().put("text", userInput);
                history.add(userMessage);

                // 2. Build Payload (Notice: toolsArray is temporarily gone!)
                ObjectNode payload = mapper.createObjectNode();
                payload.set("systemInstruction", systemInstruction);
                payload.set("contents", history);

                // 3. Send HTTP Request
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                // 4. Parse Response
                JsonNode rootNode = mapper.readTree(response.body());
                JsonNode firstPart = rootNode.path("candidates").path(0).path("content").path("parts").path(0);

                // 5. Handle Intent
                if (firstPart.has("functionCall")) {
                    JsonNode functionCall = firstPart.path("functionCall");
                    System.out.println("\n>>> [SYSTEM: TOOL CALL DETECTED] <<<");
                    System.out.println("The agent wants to run: " + functionCall.path("name").asText());

                    // The old executeLocalCommand() is gone. We will wire this to the MCP Server in Days 22-23.
                    System.out.println("[TODO] Days 22/23: Forward this execution request to the MCP Server via stdout!");
                    System.out.println("------------------------------------\n");

                }
                else if (firstPart.has("text")) {
                    String assistantText = firstPart.path("text").asText();
                    System.out.println("Agent: " + assistantText + "\n");

                    ObjectNode assistantMessage = mapper.createObjectNode();
                    assistantMessage.put("role", "model");
                    assistantMessage.putArray("parts").addObject().put("text", assistantText);
                    history.add(assistantMessage);
                } else {
                    System.out.println("Received an unknown response format.");
                }

            } catch (Exception e) {
                System.err.println("Error communicating with API: " + e.getMessage());
            }
        }

        scanner.close();
    }
}