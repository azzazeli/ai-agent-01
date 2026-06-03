package com.alexm.agent.mcp;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Scanner;

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
                }

            } catch (Exception e) {
                // Log parsing errors to stderr so we don't break the stdout pipe
                System.err.println("[SERVER] Error parsing JSON: " + e.getMessage());
            }

        }

    }
}
