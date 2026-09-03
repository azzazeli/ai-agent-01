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
                    String functionName = functionCall.path("name").asText();
                    JsonNode functionArgs = functionCall.path("args");

                    System.out.println("\n>>> [SYSTEM: TOOL CALL DETECTED] <<<");
                    System.out.println("The LLM requested: " + functionName + " with args " + functionArgs.toString());

                    // --- DAY 22: Forwarding the Request to the MCP Server ---
                    System.out.println("[SYSTEM] Forwarding execution request to MCP Server...");

                    ObjectNode toolCallRequest = mapper.createObjectNode();
                    toolCallRequest.put("jsonrpc", "2.0");
                    toolCallRequest.put("id", 3); // Message ID
                    toolCallRequest.put("method", "tools/call");

                    ObjectNode mcpParams = mapper.createObjectNode();
                    mcpParams.put("name", functionName);
                    // The beauty of this: Gemini's "args" perfectly matches MCP's "arguments" schema
                    mcpParams.set("arguments", functionArgs);
                    toolCallRequest.set("params", mcpParams);

                    // Send the request down the pipe to the background server
                    mcpWriter.write(toolCallRequest.toString() + "\n");
                    mcpWriter.flush();

                    // Immediately read the execution result back from the server
                    String mcpResponseStr = mcpReader.readLine();
                    System.out.println("[SYSTEM] Raw Result from Server: " + mcpResponseStr);
                    System.out.println("------------------------------------\n");

                    // --- DAY 23: The Final Return Trip ---
                    System.out.println("[SYSTEM] Sending execution result back to Gemini...");

                    // 1. Extract the actual text from the MCP Server's JSON-RPC response
                    JsonNode mcpResponseNode = mapper.readTree(mcpResponseStr);
                    System.out.println("[DEBUG] The Server actually sent: " + mcpResponseStr);

                    String executionResultText;

                    if (mcpResponseNode.has("result")) {
                        // Success path: extract the text from the MCP content array
                        executionResultText = mcpResponseNode.get("result").path("content").get(0).path("text").asText();
                    } else if (mcpResponseNode.has("error")) {
                        // Error path: extract the JSON-RPC error message
                        executionResultText = mcpResponseNode.get("error").path("message").asText();
                    } else {
                        executionResultText = "Unknown error occurred during tool execution.";
                    }

                    // 2. Append the Model's ORIGINAL request to history (Gemini requires this)
                    ObjectNode modelToolCallMessage = mapper.createObjectNode();
                    modelToolCallMessage.put("role", "model");
                    modelToolCallMessage.putArray("parts").add(firstPart); // firstPart contains the functionCall
                    history.add(modelToolCallMessage);

                    // 3. Append the execution result to history (as a functionResponse)
                    ObjectNode functionMessage = mapper.createObjectNode();
                    functionMessage.put("role", "user");

                    ObjectNode functionResponseNode = mapper.createObjectNode();
                    functionResponseNode.put("name", functionName);

                    ObjectNode responseData = mapper.createObjectNode();
                    responseData.put("result", executionResultText);
                    functionResponseNode.set("response", responseData);

                    ObjectNode partNode = mapper.createObjectNode();
                    partNode.set("functionResponse", functionResponseNode);

                    functionMessage.putArray("parts").add(partNode);
                    history.add(functionMessage);

                    // 4. Fire the second HTTP request to let the LLM read the result
                    ObjectNode secondPayload = mapper.createObjectNode();
                    secondPayload.set("systemInstruction", systemInstruction);
                    if (!geminiToolsArray.isEmpty()) {
                        secondPayload.set("tools", geminiToolsArray);
                    }
                    secondPayload.set("contents", history);

                    HttpRequest secondRequest = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(secondPayload.toString()))
                            .build();
                    HttpResponse<String> secondResponse = client.send(secondRequest, HttpResponse.BodyHandlers.ofString());

                    // --- ADD THIS SAFETY CHECK ---
                    if (secondResponse.statusCode() != 200) {
                        System.err.println("[API ERROR] Gemini rejected the return trip: " + secondResponse.body());
                        continue; // Skip the rest of the loop so we don't corrupt history
                    }

                    // 5. Parse and print the final natural language answer!
                    JsonNode secondRootNode = mapper.readTree(secondResponse.body());
                    String finalAssistantText = secondRootNode.path("candidates").path(0)
                            .path("content").path("parts").path(0)
                            .path("text").asText();

                    System.out.println("Agent: " + finalAssistantText + "\n");

                    // 6. Append this final answer to history so the loop continues seamlessly
                    ObjectNode finalAssistantMessage = mapper.createObjectNode();
                    finalAssistantMessage.put("role", "model");
                    finalAssistantMessage.putArray("parts").addObject().put("text", finalAssistantText);
                    history.add(finalAssistantMessage);

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