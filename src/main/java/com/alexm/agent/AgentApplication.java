package com.alexm.agent;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@SpringBootApplication
public class AgentApplication {

    public static void main(String[] args) {
        System.out.println("Starting application ...");
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Error: GEMINI_API_KEY environment variable not set.");
            return;
        }

        // 2. Set the API endpoint (Gemini passes the key in the URL)
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=" + apiKey;

        // 3. Construct the Gemini-specific JSON payload
        String jsonPayload = """
               {
                 "systemInstruction": {
                   "parts": [
                     {
                       "text": "You are a strict, terminal-only infrastructure assistant managing a home lab running Ubuntu. You never use markdown formatting. You only reply with the exact bash commands needed to solve the problem, followed by a one-sentence explanation."
                     }
                   ]
                 },
                 "contents": [{
                    "parts": [{"text": "How do I check the logs for my Immich docker container?"}]
                  }]
               }
               """;

        try {
            // 4. Build and send the HTTP request using native Java 11+ HttpClient
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(jsonPayload)).build();

            System.out.println("Sending request to Gemini...");
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 5. Print the raw JSON response
            System.out.println("\\nStatus Code: " + response.statusCode());
            System.out.println("Raw Response:\\n" + response.body());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
