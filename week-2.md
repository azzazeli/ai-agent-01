# Week 2 Action Plan: The Bare Metal Agent Loop & Tool Calling

**Goal:** Transform the minimal script into a continuous loop that maintains memory, recognizes when to use a tool, executes a local terminal command, and feeds the result back to the LLM.
**Time Commitment:** 20-30 minutes per day.

### Day 7: The Execution Loop & Memory
- [x] Wrap your native `HttpClient` logic inside a `while (true)` loop in your `main` method.
- [x] Use `java.util.Scanner` to read input from your terminal so you can chat continuously.
- [x] Implement a basic history state. Instead of hardcoding the `contents` array, use Jackson `ObjectMapper` and `ArrayNode` to maintain a growing list of messages. Append your new input to this list before sending the payload.

### Day 8: Defining the Tool Schema
- [x] Read the [Gemini API documentation on Function Calling](https://ai.google.dev/docs/function_calling).
- [x] Modify your root JSON payload construction to include a `tools` array alongside `systemInstruction` and `contents`.
- [x] Define a single `functionDeclaration` inside that array. 
  - **Name:** `check_container_status`
  - **Description:** "Checks if a specific local docker container is running."
  - **Parameters:** A required string property called `container_name`.

Define in clause a project for example with immich

### Day 9: Parsing the Intent (`functionCall`)
- [x] Run your loop and ask: "Is my immich container running?"
- [x] Modify your Jackson parsing logic for the response. Stop blindly printing the text content.
- [x] Write an `if/else` block: Check if the response `parts` array contains a `functionCall` object instead of a `text` object. 
- [x] If a `functionCall` is detected, extract and print the `name` (check_container_status) and the `args` (immich).

### Day 10: Local Execution (The "Hands" of the Agent)
- [x] Step away from the LLM logic for a day. Write a new, separate Java method: `public static String executeLocalCommand(String containerName)`.
- [x] Use `java.lang.ProcessBuilder` to execute a native Ubuntu command: `docker ps --filter "name=" + containerName` (ensure you handle the input securely to avoid command injection).
- [x] Read the process `InputStream`, capture the terminal output into a String, and return it.

### Day 11: The Return Trip (Closing the Loop)
- [x] Wire Day 9 and Day 10 together. When your `if/else` block detects `check_container_status`, pass the extracted argument to your `executeLocalCommand` method.
- [x] Take the resulting String (e.g., the raw output of `docker ps`) and format it into a Gemini `functionResponse` JSON object.
- [x] Append this `functionResponse` to your memory array.
- [x] **Crucial Step:** Automatically trigger a *second* `HttpClient` request sending the updated history back to Gemini so it can read the terminal output and generate a human-friendly answer.

### Day 12: Testing the Watchdog
- [x] Run the full application from your terminal.
- [x] Test the standard path: Ask it a general question (e.g., "What is Ubuntu?") and ensure it answers normally without breaking the loop.
- [x] Test the tool path: Ask it "Check on paperless-ngx and immich" and watch the terminal logs to verify it triggers the tool, runs the background process, and reports back the exact uptime status.
