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

        // 1. Initialize the shared memory structure (The "contents" array)
        ArrayNode history = mapper.createArrayNode();

        ObjectNode systemInstruction = mapper.createObjectNode();
        systemInstruction.putArray("parts")
                .addObject()
                .put("text", "You are a strict, terminal-only infrastructure assistant managing a home lab running Ubuntu. Keep answers concise.");

        // --- 2.5 Define the Tools Schema ---
        ArrayNode toolsArray = mapper.createArrayNode();
        ObjectNode toolNode = mapper.createObjectNode();
        ArrayNode functionDeclarations = mapper.createArrayNode();

        ObjectNode checkStatusFunction = mapper.createObjectNode();
        checkStatusFunction.put("name", "check_container_status");
        checkStatusFunction.put("description", "Checks if a specific local docker container is running.");

        // Define the parameters the LLM must provide to use this tool
        ObjectNode parameters = mapper.createObjectNode();
        parameters.put("type", "OBJECT");

        ObjectNode properties = mapper.createObjectNode();
        ObjectNode containerNameProp = mapper.createObjectNode();
        containerNameProp.put("type", "STRING");
        containerNameProp.put("description", "The name of the docker container to check (e.g., immich, paperless-ngx)");

        properties.set("container_name", containerNameProp);
        parameters.set("properties", properties);

        ArrayNode required = mapper.createArrayNode();
        required.add("container_name");
        parameters.set("required", required);

        checkStatusFunction.set("parameters", parameters);
        functionDeclarations.add(checkStatusFunction);
        toolNode.set("functionDeclarations", functionDeclarations);
        toolsArray.add(toolNode);

        System.out.println("System initialized. Chat session started. Type 'exit' to quit.\n");

        // 3. The Execution Loop
        while (true) {
            System.out.print("You: ");
            String userInput = scanner.nextLine();

            if ("exit".equalsIgnoreCase(userInput)) {
                System.out.println("Terminating session.");
                break;
            }

            try {
                // --- A. Append User Input to History ---
                ObjectNode userMessage = mapper.createObjectNode();
                userMessage.put("role", "user");
                userMessage.putArray("parts").addObject().put("text", userInput);
                history.add(userMessage);

                // --- B. Build the Final Payload ---
                ObjectNode payload = mapper.createObjectNode();
                payload.set("systemInstruction", systemInstruction);
                payload.set("tools", toolsArray);
                payload.set("contents", history); // The payload now grows with every loop

                // --- C. Execute the Network Call ---
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                // --- D. Parse the Response ---
                JsonNode rootNode = mapper.readTree(response.body());

                // Navigate to the first "part" of the response
                JsonNode firstPart = rootNode.path("candidates").path(0)
                        .path("content").path("parts").path(0);

                if (firstPart.has("functionCall")) {
                    JsonNode functionCall = firstPart.path("functionCall");
                    String functionName = functionCall.path("name").asText();

                    // Extract the specific argument we defined in our schema
                    String containerName = functionCall.path("args").path("container_name").asText();

                    System.out.println("\n>>> [SYSTEM: TOOL CALL DETECTED] <<<");
                    System.out.println("The agent wants to run: " + functionName);
                    System.out.println("Target Container: " + containerName);
                    System.out.println("------------------------------------\n");

                    // Note: We are NOT appending this to history yet.
                    // We will handle the complex history logic for tools on Day 11.

                }
                // 2. Otherwise, handle it as a standard text conversation
                else if (firstPart.has("text")) {
                    String assistantText = firstPart.path("text").asText();
                    System.out.println("Agent: " + assistantText + "\n");

                    // Append Assistant Response to History
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

    }
}
