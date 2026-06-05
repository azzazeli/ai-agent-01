package com.alexm.agent.mcp;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Scanner;
import java.util.stream.Collectors;

public class McpServer {
    public static void main(String[] args) {
        System.err.println("[SERVER] Starting Bare Metal MCP Server...");
        System.err.println("[SERVER] Listening for JSON-RPC messages on stdin...");

        Scanner scanner = new Scanner(System.in);
        ObjectMapper mapper = new ObjectMapper();

        while (scanner.hasNextLine()) {
            String inputLine = scanner.nextLine();
            if (inputLine.trim().isEmpty()) {
                continue;
            }

            System.err.println("[SERVER] Received payload: " + inputLine);
            try {
                // 1. Parse the incoming string into a JSON tree
                JsonNode requestNode = mapper.readTree(inputLine);
                // 2. Ensure it has a "method" field
                if (requestNode.has("method")) {
                    String method = requestNode.get("method").asText();
                    JsonNode idNode = requestNode.get("id"); // We must echo this exact ID back
                    // 3. Handle the "initialize" handshake
                    if ("initialize".equals(method)) {
                        System.err.println("[SERVER] Handshake requested. Sending capabilities...");

                        // Build the JSON-RPC response
                        ObjectNode response = mapper.createObjectNode();
                        response.put("jsonrpc", "2.0");
                        if (idNode != null) {
                            response.set("id", idNode); // Match the client's request ID
                        }

                        // Build the "result" payload required by MCP
                        ObjectNode result = mapper.createObjectNode();
                        result.put("protocolVersion", "2024-11-05"); // The official MCP protocol version

                        ObjectNode capabilities = mapper.createObjectNode();
                        // An empty object for "tools" tells the client: "I support tools"
                        capabilities.set("tools", mapper.createObjectNode());
                        result.set("capabilities", capabilities);

                        ObjectNode serverInfo = mapper.createObjectNode();
                        serverInfo.put("name", "HomeLabMCP");
                        serverInfo.put("version", "1.0.0");
                        result.set("serverInfo", serverInfo);

                        response.set("result", result);

                        // CRITICAL: Send the JSON response to standard output!
                        System.out.println(response.toString());
                    }
                    // 4. Handle Tool Discovery
                    else if ("tools/list".equals(method)) {
                        System.err.println("[SERVER] Client requested available tools...");
                        ObjectNode response = mapper.createObjectNode();
                        response.put("jsonrpc", "2.0");
                        if (idNode != null) {
                            response.set("id", idNode);
                        }

                        // Build the result object containing the array of tools
                        ObjectNode result = mapper.createObjectNode();
                        ArrayNode toolsArray = mapper.createArrayNode();

                        ObjectNode checkStatusTool = mapper.createObjectNode();
                        checkStatusTool.put("name", "check_container_status");
                        checkStatusTool.put("description", "Checks if a specific local docker container is running.");

                        // MCP strictly uses JSON Schema for the input arguments
                        ObjectNode inputSchema = mapper.createObjectNode();
                        inputSchema.put("type", "object");

                        ObjectNode properties = mapper.createObjectNode();
                        ObjectNode containerNameProp = mapper.createObjectNode();
                        containerNameProp.put("type", "string");
                        containerNameProp.put("description", "The name of the docker container to check (e.g., immich, paperless-ngx)");
                        properties.set("container_name", containerNameProp);

                        inputSchema.set("properties", properties);

                        ArrayNode required = mapper.createArrayNode();
                        required.add("container_name");
                        inputSchema.set("required", required);

                        checkStatusTool.set("inputSchema", inputSchema);
                        toolsArray.add(checkStatusTool);

                        result.set("tools", toolsArray);
                        response.set("result", result);

                        // CRITICAL: Send the JSON response to standard output!
                        System.out.println(response.toString());
                    }
                    else if ("tools/call".equals(method)) {
                        System.err.println("[SERVER] Client requested tool execution...");

                        JsonNode params = requestNode.get("params");
                        String toolName = params.get("name").asText();
                        JsonNode arguments = params.get("arguments");

                        ObjectNode response = mapper.createObjectNode();
                        response.put("jsonrpc", "2.0");
                        if (idNode != null) {
                            response.set("id", idNode);
                        }

                        if ("check_container_status".equals(toolName)) {
                            // Extract the argument provided by the LLM
                            String containerName = arguments.get("container_name").asText();
                            System.err.println("[SERVER] Executing check for container: " + containerName);

                            // Call the local native method
                            String resultText = executeLocalCommand(containerName);

                            // Build the MCP expected result format (Array of content blocks)
                            ObjectNode result = mapper.createObjectNode();
                            ArrayNode contentArray = mapper.createArrayNode();

                            ObjectNode textContent = mapper.createObjectNode();
                            textContent.put("type", "text");
                            textContent.put("text", resultText); // The raw Docker output

                            contentArray.add(textContent);
                            result.set("content", contentArray);

                            response.set("result", result);
                        }
                        else {
                            // NEW: Official JSON-RPC Error Handling
                            System.err.println("[SERVER] Error: Unknown tool requested -> " + toolName);

                            ObjectNode errorNode = mapper.createObjectNode();
                            errorNode.put("code", -32601);
                            errorNode.put("message", "Method not found: Unknown tool '" + toolName + "'");

                            // Attach the error node instead of a result node
                            response.set("error", errorNode);
                        }
                        // CRITICAL: Send the JSON response to standard output!
                        System.out.println(response.toString());
                    }

                }

            } catch (Exception e) {
                // Log parsing errors to stderr so we don't break the stdout pipe
                System.err.println("[SERVER] Error parsing JSON: " + e.getMessage());
            }

        }
    }

    public static String executeLocalCommand(String containerName) {
        System.out.println("[SYSTEM] Executing local check for: " + containerName);
        try {
            // Using a list of arguments is safer than a raw bash string
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "docker", "ps", "--filter", "name=" + containerName, "--format", "{{.Names}} - Status: {{.Status}}"
            );
            // Redirect error stream so we can see if docker fails (e.g., permissions)
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Read the terminal output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = reader.lines().collect(Collectors.joining("\n"));

            int exitCode = process.waitFor();

            if (output.trim().isEmpty()) {
                return "Container '" + containerName + "' is not currently running or does not exist.";
            }
            System.out.println("Terminal output: " + output );

            return output;
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }

}
