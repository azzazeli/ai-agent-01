package com.alexm.agent;


import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class McpClientAgent {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static int requestId = 1;

    public static void main(String[] args) throws Exception {
        System.out.println("[Agent] Starting MCP Client Agent...");

        Process mcpServer = spawnMcpServer();

        BufferedWriter toServer = new BufferedWriter(new OutputStreamWriter(mcpServer.getOutputStream()));
        BufferedReader fromServer = new BufferedReader(new InputStreamReader(mcpServer.getInputStream()));

        // Step 1: Handshake
        performHandshake(toServer, fromServer);

        // Step 2: Discover tools and convert to Gemini format
        ArrayNode geminiTools = discoverTools(toServer, fromServer);
        System.out.println("[Agent] Gemini-ready tool declarations:");
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(geminiTools));

        // Clean up
        mcpServer.destroy();
        System.out.println("[Agent] Done.");
    }

    // -------------------------------------------------------------------------
    // Protocol steps
    // -------------------------------------------------------------------------

    private static void performHandshake(BufferedWriter toServer, BufferedReader fromServer) throws Exception {
        ObjectNode request = buildRequest("initialize");
        ObjectNode params = mapper.createObjectNode();
        params.put("protocolVersion", "2024-11-05");
        ObjectNode clientInfo = mapper.createObjectNode();
        clientInfo.put("name", "mcp-client-agent");
        clientInfo.put("version", "1.0");
        params.set("clientInfo", clientInfo);
        request.set("params", params);

        sendRequest(toServer, mapper.writeValueAsString(request));
        JsonNode response = mapper.readTree(readResponse(fromServer));

        String serverName = response.path("result").path("serverInfo").path("name").asText("unknown");
        String protocol = response.path("result").path("protocolVersion").asText("unknown");
        System.out.println("[Agent] Handshake OK — server: " + serverName + ", protocol: " + protocol);
    }

    private static ArrayNode discoverTools(BufferedWriter toServer, BufferedReader fromServer) throws Exception {
        ObjectNode request = buildRequest("tools/list");

        sendRequest(toServer, mapper.writeValueAsString(request));
        JsonNode response = mapper.readTree(readResponse(fromServer));

        JsonNode mcpTools = response.path("result").path("tools");
        System.out.println("[Agent] Discovered " + mcpTools.size() + " tool(s) from MCP server.");

        return convertToGeminiDeclarations(mcpTools);
    }

    // -------------------------------------------------------------------------
    // Schema conversion: MCP inputSchema → Gemini functionDeclaration
    // -------------------------------------------------------------------------

    static ArrayNode convertToGeminiDeclarations(JsonNode mcpTools) {
        ArrayNode declarations = mapper.createArrayNode();

        for (JsonNode tool : mcpTools) {
            ObjectNode declaration = mapper.createObjectNode();
            declaration.put("name", tool.path("name").asText());
            declaration.put("description", tool.path("description").asText());

            JsonNode inputSchema = tool.path("inputSchema");
            declaration.set("parameters", convertSchema(inputSchema));

            declarations.add(declaration);
        }

        return declarations;
    }

    private static ObjectNode convertSchema(JsonNode mcpSchema) {
        ObjectNode geminiSchema = mapper.createObjectNode();

        // Gemini requires uppercase type names
        String type = mcpSchema.path("type").asText("object").toUpperCase();
        geminiSchema.put("type", type);

        // Recursively convert properties
        if (mcpSchema.has("properties")) {
            ObjectNode geminiProperties = mapper.createObjectNode();
            mcpSchema.path("properties").properties().forEach(entry -> {
                ObjectNode prop = mapper.createObjectNode();
                prop.put("type", entry.getValue().path("type").asText("string").toUpperCase());
                if (entry.getValue().has("description")) {
                    prop.put("description", entry.getValue().path("description").asText());
                }
                geminiProperties.set(entry.getKey(), prop);
            });
            geminiSchema.set("properties", geminiProperties);
        }

        // Preserve required array as-is
        if (mcpSchema.has("required")) {
            geminiSchema.set("required", mcpSchema.path("required"));
        }

        return geminiSchema;
    }

    // -------------------------------------------------------------------------
    // Process management
    // -------------------------------------------------------------------------

    private static Process spawnMcpServer() throws Exception {
        String javaExecutable = ProcessHandle.current()
                .info()
                .command()
                .orElse("java");

        String classpath = System.getProperty("java.class.path");

        ProcessBuilder pb = new ProcessBuilder(
                javaExecutable,
                "-cp", classpath,
                "com.alexm.agent.McpServer"
        );

        pb.redirectErrorStream(false);
        pb.inheritIO()
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.PIPE);

        Process process = pb.start();
        System.out.println("[Agent] McpServer process started (pid: " + process.pid() + ")");
        return process;
    }

    // -------------------------------------------------------------------------
    // Communication helpers
    // -------------------------------------------------------------------------

    private static ObjectNode buildRequest(String method) {
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", requestId++);
        request.put("method", method);
        return request;
    }

    static void sendRequest(BufferedWriter writer, String json) throws Exception {
        writer.write(json);
        writer.newLine();
        writer.flush();
        System.out.println("[Agent] → " + json);
    }

    static String readResponse(BufferedReader reader) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) {
                System.out.println("[Agent] ← " + line);
                return line;
            }
        }
        throw new RuntimeException("Server closed the stream without sending a response.");
    }
}