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


        // --- DAY 20: Stream Wiring & The Handshake ---
        System.out.println("[SYSTEM] Wiring streams to MCP Server...");

        // The Agent writes to the Server's System.in
        java.io.BufferedWriter mcpWriter = new java.io.BufferedWriter(new java.io.OutputStreamWriter(mcpProcess.getOutputStream()));
        // The Agent reads from the Server's System.out
        java.io.BufferedReader mcpReader = new java.io.BufferedReader(new java.io.InputStreamReader(mcpProcess.getInputStream()));

        try {
            System.out.println("[SYSTEM] Sending 'initialize' handshake...");

            // 1. Build the initialize JSON-RPC request
            ObjectNode initRequest = mapper.createObjectNode();
            initRequest.put("jsonrpc", "2.0");
            initRequest.put("id", 1); // Track the request ID
            initRequest.put("method", "initialize");

            ObjectNode params = mapper.createObjectNode();
            params.put("protocolVersion", "2024-11-05");
            params.set("capabilities", mapper.createObjectNode());
            initRequest.set("params", params);

            mcpWriter.write(initRequest.toString() + "\n");
            mcpWriter.flush();

            // 3. Read the server's response
            String initResponseStr = mcpReader.readLine();

            // 4. Validate the handshake
            JsonNode initResponseNode = mapper.readTree(initResponseStr);
            if (initResponseNode.has("result") && initResponseNode.get("result").has("protocolVersion")) {
                System.out.println("[SYSTEM] Handshake successful! Connected to: " +
                        initResponseNode.get("result").path("serverInfo").path("name").asText());
            } else {
                System.err.println("[SYSTEM] Handshake failed. Unexpected response: " + initResponseStr);
            }
        } catch (Exception e) {
            System.err.println("[SYSTEM] Failed to communicate with MCP Server: " + e.getMessage());
            return;
        }

        // --- DAY 21: Dynamic Tool Discovery & Translation ---
        System.out.println("[SYSTEM] Requesting available tools from Server...");
        ArrayNode geminiToolsArray = mapper.createArrayNode();

        try {
            // 1. Ask the server for its tools
            ObjectNode toolsRequest = mapper.createObjectNode();
            toolsRequest.put("jsonrpc", "2.0");
            toolsRequest.put("id", 2);
            toolsRequest.put("method", "tools/list");

            mcpWriter.write(toolsRequest.toString() + "\n");
            mcpWriter.flush();

            // 2. Read the server's response
            String toolsResponseStr = mcpReader.readLine();
            JsonNode toolsResponseNode = mapper.readTree(toolsResponseStr);

            // 3. Translate MCP schema to Gemini schema
            if (toolsResponseNode.has("result") && toolsResponseNode.get("result").has("tools")) {
                JsonNode mcpTools = toolsResponseNode.get("result").get("tools");
                ArrayNode functionDeclarations = mapper.createArrayNode();

                for (JsonNode mcpTool : mcpTools) {
                    String toolName = mcpTool.get("name").asText();
                    System.out.println("[SYSTEM] Discovered tool: " + toolName);

                    ObjectNode geminiFunction = mapper.createObjectNode();
                    geminiFunction.put("name", toolName);

                    if (mcpTool.has("description")) {
                        geminiFunction.put("description", mcpTool.get("description").asText());
                    }
                    if (mcpTool.has("inputSchema")) {
                        // The beauty of this: Gemini's "parameters" exactly matches standard JSON Schema objects
                        geminiFunction.set("parameters", mcpTool.get("inputSchema"));
                    }

                    functionDeclarations.add(geminiFunction);
                }

                // Wrap in the Gemini root "tools" structure
                ObjectNode toolNode = mapper.createObjectNode();
                toolNode.set("functionDeclarations", functionDeclarations);
                geminiToolsArray.add(toolNode);

                System.out.println("[SYSTEM] Successfully translated tools for Gemini.");
            }

        } catch (Exception e) {
            System.err.println("[SYSTEM] Tool discovery failed: " + e.getMessage());
            return;
        }


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
                // --- Add this line back! ---
                if (!geminiToolsArray.isEmpty()) {
                    payload.set("tools", geminiToolsArray);
                }
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