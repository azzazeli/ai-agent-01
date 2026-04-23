package com.alexm.agent;

import java.util.Scanner;

public class McpServer {
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
            }
        }
    }
}
