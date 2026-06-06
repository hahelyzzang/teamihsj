package net.sojeong.rescuecraft.animal;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Talks to a locally-running Ollama instance to generate a rescued animal's next
 * line. This mirrors the pig dialogue service: no external paid APIs are used,
 * the call is synchronous, and callers MUST run it off the server tick thread.
 *
 * The model can be overridden with the environment variable
 * {@code RESCUECRAFT_ANIMAL_MODEL} (falling back to {@code RESCUECRAFT_PIG_MODEL}
 * and finally a small default).
 */
public final class AnimalDialogueService {

    private static final String OLLAMA_URL = "http://localhost:11434/api/chat";
    private static final String DEFAULT_MODEL = "qwen2.5:3b-instruct";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private AnimalDialogueService() {}

    public static AnimalDialogueResponse generate(AnimalCompanion companion, String playerMessage) {
        String band = AnimalPrompt.currentBand();
        String system = AnimalPrompt.systemPrompt(band, companion);
        String user = AnimalPrompt.userPrompt(companion, playerMessage);

        try {
            String body = buildRequestJson(system, user);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_URL))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() != 200) {
                System.out.println("[RescueCraft][Animal] Ollama status " + response.statusCode()
                        + " body=" + response.body());
                return AnimalDialogueResponse.fallback();
            }

            String content = extractMessageContent(response.body());
            if (content == null) {
                return AnimalDialogueResponse.fallback();
            }
            AnimalDialogueResponse parsed = parseDialogueJson(content);
            return parsed.isUsable() ? parsed : AnimalDialogueResponse.fallback();

        } catch (ConnectException e) {
            System.out.println("[RescueCraft][Animal] Ollama is not running on localhost:11434.");
            return new AnimalDialogueResponse(
                    "*The animal stares at you silently.* (Ollama not running)",
                    "*동물이 말없이 당신을 바라본다.* (오라마 서버 실행 필요)"
            );
        } catch (Exception e) {
            System.out.println("[RescueCraft][Animal] Failed to call Ollama: " + e.getMessage());
            e.printStackTrace();
            return AnimalDialogueResponse.fallback();
        }
    }

    private static String modelName() {
        String override = System.getenv("RESCUECRAFT_ANIMAL_MODEL");
        if (override == null || override.isBlank()) {
            override = System.getenv("RESCUECRAFT_PIG_MODEL");
        }
        return (override == null || override.isBlank()) ? DEFAULT_MODEL : override;
    }

    private static String buildRequestJson(String system, String user) {
        return """
                {
                  "model": "%s",
                  "messages": [
                    {"role": "system", "content": "%s"},
                    {"role": "user", "content": "%s"}
                  ],
                  "stream": false,
                  "format": "json",
                  "options": {
                    "temperature": 0.6,
                    "num_predict": 300
                  }
                }
                """.formatted(modelName(), escapeJson(system), escapeJson(user));
    }

    private static String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /** Pulls the assistant message content string out of an Ollama /api/chat response. */
    private static String extractMessageContent(String json) {
        String key = "\"content\":\"";
        int start = json.indexOf(key);
        if (start == -1) {
            return null;
        }
        start += key.length();

        StringBuilder out = new StringBuilder();
        boolean escaping = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaping) {
                switch (c) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    default -> out.append(c);
                }
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else if (c == '"') {
                break;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Parses the inner JSON object the LLM was instructed to return, e.g.
     * {@code {"english": "I am hungry.", "korean": "나는 배가 고파."}}.
     * Tolerates minor formatting issues by extracting each field independently.
     */
    static AnimalDialogueResponse parseDialogueJson(String content) {
        if (content == null) {
            return AnimalDialogueResponse.fallback();
        }
        String english = extractStringField(content, "english");
        String korean = extractStringField(content, "korean");
        if (english == null) {
            english = stripCodeFences(content).trim();
        }
        if (korean == null) {
            korean = "";
        }
        return new AnimalDialogueResponse(english, korean);
    }

    private static String stripCodeFences(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline >= 0) t = t.substring(firstNewline + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t;
    }

    private static String extractStringField(String json, String field) {
        String key = "\"" + field + "\"";
        int k = json.indexOf(key);
        if (k == -1) return null;
        int colon = json.indexOf(':', k + key.length());
        if (colon == -1) return null;
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != '"') return null;
        i++;
        StringBuilder out = new StringBuilder();
        boolean esc = false;
        for (; i < json.length(); i++) {
            char c = json.charAt(i);
            if (esc) {
                switch (c) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    default -> out.append(c);
                }
                esc = false;
            } else if (c == '\\') {
                esc = true;
            } else if (c == '"') {
                return out.toString();
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
