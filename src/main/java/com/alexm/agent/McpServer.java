package com.alexm.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Scanner;

public class McpServer {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        // RULE: System.out is reserved exclusively for JSON-RPC protocol messages.
        // ALL debug and logging goes to System.err. Always. No exceptions.
        System.err.println("[McpServer] Server starting...");

        Scanner scanner = new Scanner(System.in);

        System.err.println("[McpServer] Stdio loop ready. Waiting for input.");

        while (true) {
            if (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                System.err.println("[McpServer] Received: " + line);
                try {
                    JsonNode request = mapper.readTree(line);
                    handleRequest(request);
                } catch (Exception e) {
                    System.err.println("[McpServer] Failed to parse JSON: " + e.getMessage());
                }
            }
        }
    }

    private static void handleRequest(JsonNode request) {
        String method = request.path("method").asText("");
        JsonNode id = request.path("id");  // may be a number or string
        System.err.println("[McpServer] Method: " + method);

        if ("initialize".equals(method)) {
            sendInitializeResponse(id);
        } else if ("tools/list".equals(method)) {
            sendToolsListResponse(id);
        } else {
            // Day 15+ will handle other methods here
            System.err.println("[McpServer] Unknown method: " + method);
        }
    }

    private static void sendToolsListResponse(JsonNode id) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);

        // --- Tool 1: check_container_status (from Day 8) ---
        ObjectNode tool1 = mapper.createObjectNode();
        tool1.put("name", "check_container_status");
        tool1.put("description", "Checks if a specific local Docker container is running.");

        ObjectNode tool1Params = mapper.createObjectNode();
        tool1Params.put("type", "object");

        ObjectNode tool1Properties = mapper.createObjectNode();
        ObjectNode containerNameProp = mapper.createObjectNode();
        containerNameProp.put("type", "string");
        containerNameProp.put("description", "The name of the Docker container to check, e.g. immich_server");
        tool1Properties.set("container_name", containerNameProp);

        tool1Params.set("properties", tool1Properties);
        tool1Params.set("required", mapper.createArrayNode().add("container_name"));
        tool1.set("inputSchema", tool1Params);

        // --- Tool 2: list_immich_albums ---
        ObjectNode tool2 = mapper.createObjectNode();
        tool2.put("name", "list_immich_albums");
        tool2.put("description", "Returns a list of albums from the local Immich instance.");

        ObjectNode tool2Params = mapper.createObjectNode();
        tool2Params.put("type", "object");
        tool2Params.set("properties", mapper.createObjectNode()); // no parameters required
        tool2Params.set("required", mapper.createArrayNode());
        tool2.set("inputSchema", tool2Params);

        var toolsArray = mapper.createArrayNode();
        toolsArray.add(tool1);
        toolsArray.add(tool2);

        ObjectNode result = mapper.createObjectNode();
        result.set("tools", toolsArray);

        response.set("result", result);

        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
//        String json = mapper.writeValueAsString(response); // MCP host expect a single line
        System.out.println(json);
        System.out.flush();

        System.err.println("[McpServer] Sent tools/list response with " + toolsArray.size() + " tools.");

    }

    private static void sendInitializeResponse(JsonNode id) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);

        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");

        // Server identity
        ObjectNode serverInfo = mapper.createObjectNode();
        serverInfo.put("name", "immich-mcp-server");
        serverInfo.put("version", "1.0.0");
        result.set("serverInfo", serverInfo);

        // Capabilities: we support tools (more can be added later)
        ObjectNode capabilities = mapper.createObjectNode();
        capabilities.set("tools", mapper.createObjectNode());
        result.set("capabilities", capabilities);

        response.set("result", result);

        // THIS is the only place stdout is used — the protocol message
        String json = mapper.writeValueAsString(response);
        System.out.println(json);
        System.out.flush();

        System.err.println("[McpServer] Sent initialize response.");
    }
}
