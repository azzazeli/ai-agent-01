package com.alexm.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
                    sendErrorResponse(null, -32700, "Internal error");
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
        } else if ("tools/call".equals(method)) {
            handleToolCall(request, id);
        } else {
            System.err.println("[McpServer] Unknown method: " + method);
            sendErrorResponse(id, -32601, "Method not found:" + method);
        }
    }

    private static void handleToolCall(JsonNode request, JsonNode id) {
        String toolName = request.path("params").path("name").asText("");
        JsonNode arguments = request.path("params").path("arguments");

        System.err.println("[McpServer] Tool call: " + toolName);

        String toolResult;
        if ("check_container_status".equals(toolName)) {
            String containerName = arguments.path("container_name").asText("immich");
            toolResult = executeLocalCommand(containerName);
        } else if ("list_immich_albums".equals(toolName)) {
            toolResult = listImmichAlbums();
        } else {
            sendErrorResponse(id, -32601, "Unknown tool: " + toolName);
            return;
        }
        sendToolResult(id, toolResult);
    }

    private static void sendToolResult(JsonNode id, String result) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);

        // MCP tool result format: content array with a text block
        ObjectNode textContent = mapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", result);

        var contentArray = mapper.createArrayNode();
        contentArray.add(textContent);

        ObjectNode resultNode = mapper.createObjectNode();
        resultNode.set("content", contentArray);

        response.set("result", resultNode);

        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
//        String json = mapper.writeValueAsString(response);
        System.out.println(json);
        System.out.flush();

        System.err.println("[McpServer] Sent tool result for: " + id);
    }

    private static String listImmichAlbums() {
        String apiKey = System.getenv("IMMICH_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return "Error: IMMICH_API_KEY environment variable is not set.";
        }

        // Adjust the host/port to match your local Immich Docker setup
        String immichUrl = System.getenv().getOrDefault("IMMICH_URL", "http://localhost:2283");

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(immichUrl + "/api/albums"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .header("x-api-key", apiKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "Immich API error: HTTP " + response.statusCode();
            }

            JsonNode albums = mapper.readTree(response.body());
            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(albums.size()).append(" album(s):\n");
            for (JsonNode album : albums) {
                String name = album.path("albumName").asText("(unnamed)");
                int count = album.path("assetCount").asInt(0);
                sb.append("  - ").append(name).append(" (").append(count).append(" assets)\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Error calling Immich API: " + e.getMessage();
        }
    }

    private static String executeLocalCommand(String containerName) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "ps", "--filter", "name=" + containerName, "--format", "{{.Names}}\t{{.Status}}"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            return output.isBlank() ? "Container '" + containerName + "' is not running." : output.trim();
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
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

//        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
        String json = mapper.writeValueAsString(response); // MCP host expect a single line
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

    private static void sendErrorResponse(JsonNode id, int code, String message) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");

        // id can be null if the request was so malformed we couldn't parse it
        if (id != null && !id.isNull()) {
            response.set("id", id);
        } else {
            response.putNull("id");
        }

        ObjectNode error = mapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        response.set("error", error);

        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
//        String json = mapper.writeValueAsString(response);
        System.out.println(json);
        System.out.flush();

        System.err.println("[McpServer] Sent error response: " + code + " " + message);
    }
}
