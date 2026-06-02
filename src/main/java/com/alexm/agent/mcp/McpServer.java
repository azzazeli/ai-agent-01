package com.alexm.agent.mcp;

import java.util.Scanner;

public class McpServer {
    public static void main(String[] args) {
        System.err.println("[SERVER] Starting Bare Metal MCP Server...");
        System.err.println("[SERVER] Listening for JSON-RPC messages on stdin...");

        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String inputLine = scanner.nextLine();
            if (inputLine.trim().isEmpty()) {
                continue;
            }

            System.err.println("[SERVER] Received payload: " + inputLine);

        }

    }
}
