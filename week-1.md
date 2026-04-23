# Week 1 Action Plan: Theory & The Minimal Agent

**Time Commitment:** 20-30 minutes per day.

### Day 1: Theory Pt. 1
- [x] Go to [Google Agent Fundamentals](https://www.skills.google/course_templates/1562).
- [x] Complete Module 1.
- [x] Note the architectural differences between a standard LLM and an Agent.

### Day 2: Theory Pt. 2
- [x] Complete Module 2 of the Google course.
- [x] Focus on the interaction between Models, Tools, and Orchestration.

### Day 3: Theory Pt. 3
- [x] Complete the final module of the Google course.
- [x] Pass the course quiz.

### Day 4: Environment Setup
- [x] Initialize a clean Maven project.
- [x] Add a JSON parser dependency (e.g., Jackson `jackson-databind`).
- [x] Export your LLM API key as a system environment variable in your Ubuntu terminal (e.g., `export LLM_API_KEY="your-key-here"`). *Do not hardcode it in the Java files.*

### Day 5: The Minimal Agent
- [x] Write a `main` method using native `java.net.http.HttpClient`.
- [x] Construct a hardcoded JSON string: `{"role": "user", "content": "Hello"}` (format depends on your specific LLM provider).
- [x] Send the POST request to the API.
- [x] Print the raw string response to the console.

### Day 6: Persona Injection
- [x] Modify your Day 5 JSON payload.
- [x] Add a system message (e.g., "You are an infrastructure assistant managing a home lab running Ubuntu. Keep answers under 2 sentences.").
- [x] Run the application again.
- [x] Verify that the prompt constraints successfully change the output style.
