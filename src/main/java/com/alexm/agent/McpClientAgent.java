package com.alexm.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.*;

public class McpClientAgent {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        System.out.println("[Agent] Starting MCP Client Agent...");

        Process mcpServer = spawnMcpServer();

        BufferedWriter toServer = new BufferedWriter(new OutputStreamWriter(mcpServer.getOutputStream()));
        BufferedReader fromServer = new BufferedReader(new InputStreamReader(mcpServer.getInputStream()));

        // Verify the pipe works: send a raw initialize request and read back the response
        System.out.println("[Agent] Server spawned. Testing communication...");

        String initRequest = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","clientInfo":{"name":"mcp-client-agent","version":"1.0"}}}
            """.strip();

        sendRequest(toServer, initRequest);

        String response = readResponse(fromServer);

        System.out.println("[Agent] Server responded: " + response);

        JsonNode json = mapper.readTree(response);
        String serverName = json.path("result").path("serverInfo").path("name").asText("unknown");
        String version = json.path("result").path("protocolVersion").asText("unknown");

        System.out.println("[Agent] Handshake confirmed — server: " + serverName + ", protocol: " + version);

        // Clean up
        mcpServer.destroy();
        System.out.println("[Agent] Server process terminated.");

    }

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

        // Server's stderr flows to our stderr so we can see its debug logs
        pb.redirectErrorStream(false);
        pb.inheritIO().redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.PIPE);

        Process process = pb.start();
        System.out.println("[Agent] McpServer process started (pid: " + process.pid() + ")");
        return process;
    }

    private static void sendRequest(BufferedWriter writer, String json) throws IOException {
        writer.write(json);
        writer.newLine();
        writer.flush();
        System.out.println("[Agent] Sent: " + json);
    }

    static String readResponse(BufferedReader reader) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) {
                System.out.println("[Agent] Received: " + line);
                return line;
            }
        }
        throw new RuntimeException("Server closed the stream without sending a response.");
    }
}
