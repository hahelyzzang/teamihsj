package net.sojeong.rescuecraft;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class OllamaEnglishEvaluator {
    private static final String OLLAMA_URL = "http://localhost:11434/api/chat";
    private static final String MODEL = "llama3.1";

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public static String evaluate(String playerAnswer) {
        try {
            String prompt = buildPrompt(playerAnswer);
            String requestBody = buildRequestJson(prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() != 200) {
                System.out.println("[RescueCraft] Ollama returned status: " + response.statusCode());
                System.out.println("[RescueCraft] Ollama response: " + response.body());
                return null;
            }

            String content = extractMessageContent(response.body());
            System.out.println("[RescueCraft] Ollama raw response: " + content);

            return parseLevel(content);

        } catch (ConnectException e) {
            System.out.println("[RescueCraft] Ollama is not running.");
            return null;

        } catch (Exception e) {
            System.out.println("[RescueCraft] Failed to call Ollama.");
            e.printStackTrace();
            return null;
        }
    }

    private static String buildPrompt(String playerAnswer) {
        return """
                You are a CEFR English level evaluator.

                Below is a transcript of a player's answers to several English questions
                of increasing difficulty (introduction, past events, opinions, and a
                step-by-step explanation). Judge the player's OVERALL English proficiency
                from ALL of the answers together, not just one. Weight the harder,
                open-ended answers (reasoning and explanation) most heavily, and look at
                grammar range, vocabulary, sentence complexity, coherence, and how well
                they handle the more abstract questions.

                Return only one of these labels:
                A1, A2, B1, B2, C1, C2

                Rules:
                - If the answers are mostly Korean or empty with little English evidence, return A1.
                - If they use only very simple words or memorized phrases, return A1.
                - If they use basic personal sentences but struggle to explain or give reasons, return A2.
                - If they connect sentences about familiar topics and give simple reasons, return B1.
                - If they give clear explanations and opinions with some complexity, return B2.
                - If they are fluent, well-structured, and flexible across all questions, return C1.
                - If they are highly fluent, nuanced, and precise, return C2.
                - Judge the writing itself; ignore the questions when scoring.
                - Do not explain. Do not output anything except the single CEFR label.

                Player answers:
                "%s"
                """.formatted(playerAnswer);
    }

    private static String buildRequestJson(String prompt) {
        String escapedPrompt = escapeJson(prompt);

        return """
                {
                  "model": "%s",
                  "messages": [
                    {
                      "role": "user",
                      "content": "%s"
                    }
                  ],
                  "stream": false,
                  "options": {
                    "temperature": 0
                  }
                }
                """.formatted(MODEL, escapedPrompt);
    }

    private static String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String extractMessageContent(String json) throws IOException {
        String key = "\"content\":\"";
        int start = json.indexOf(key);

        if (start == -1) {
            throw new IOException("Could not find content in Ollama response: " + json);
        }

        start += key.length();

        StringBuilder content = new StringBuilder();
        boolean escaping = false;

        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaping) {
                if (c == 'n') {
                    content.append('\n');
                } else if (c == 'r') {
                    content.append('\r');
                } else if (c == 't') {
                    content.append('\t');
                } else {
                    content.append(c);
                }
                escaping = false;
            } else {
                if (c == '\\') {
                    escaping = true;
                } else if (c == '"') {
                    break;
                } else {
                    content.append(c);
                }
            }
        }

        return content.toString();
    }

    private static String parseLevel(String raw) {
        String text = raw.trim().toUpperCase();

        if (text.contains("C2")) return "C2";
        if (text.contains("C1")) return "C1";
        if (text.contains("B2")) return "B2";
        if (text.contains("B1")) return "B1";
        if (text.contains("A2")) return "A2";
        if (text.contains("A1")) return "A1";

        System.out.println("[RescueCraft] Could not parse CEFR level from Ollama response: " + raw);
        return null;
    }
}