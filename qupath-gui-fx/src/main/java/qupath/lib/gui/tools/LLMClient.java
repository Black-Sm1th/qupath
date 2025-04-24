package qupath.lib.gui.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.application.Platform;

public class LLMClient {
    private static final Logger logger = LoggerFactory.getLogger(LLMClient.class);
    private String API_URL = "";
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final String apiKey;
    private final LLMType llmType;
    public enum LLMType {
        PATHOLOGY,
        DEEP_SEEK
    }
    public LLMClient(String apiKey, LLMType llmType) {
        this.apiKey = apiKey;
        this.llmType = llmType;
        switch (llmType) {
            case PATHOLOGY:
                API_URL = "http://111.6.178.34:25423/chat";
                break;
            case DEEP_SEEK:
                API_URL = "https://api.deepseek.com/v1/chat/completions";
                break;
            default:
                API_URL = "http://111.6.178.34:25423/chat";
                break;
        }
    }

    public void getCompletionAsync(String prompt, Consumer<String> onSuccess, Consumer<Exception> onError) {
        executor.execute(() -> {
            try {
                String result = sendRequest(prompt);
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Exception e) {
                Platform.runLater(() -> onError.accept(e));
            }
        });
    }

    public void getStreamingCompletionAsync(String prompt, Consumer<String> onChunk, Consumer<Exception> onError) {
        executor.execute(() -> {
            try {
                streamRequest(prompt, onChunk);
            } catch (Exception e) {
                Platform.runLater(() -> onError.accept(e));
            }
        });
    }

    private void streamRequest(String prompt, Consumer<String> onChunk) throws IOException {
        HttpURLConnection conn = createConnection();
        writeRequestBody(conn, createStreamingRequestJson(prompt));
        validateResponse(conn);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    if (data.equals("[DONE]")) {
                        break;
                    }
                    String content = parseContent(data);
                    if (content != null && !content.isEmpty()) {
                        Platform.runLater(() -> onChunk.accept(content));
                    }
                }
            }
        }
    }

    private String sendRequest(String prompt) throws IOException {
        HttpURLConnection conn = createConnection();
        writeRequestBody(conn, createRequestJson(prompt));
        validateResponse(conn);
        return parseContent(readResponse(conn));
    }

    private HttpURLConnection createConnection() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        if (llmType == LLMType.DEEP_SEEK) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        } else {
            conn.setRequestProperty("X-API-Key", apiKey);
        }
        conn.setDoOutput(true);
        return conn;
    }

    private void writeRequestBody(HttpURLConnection conn, String json) throws IOException {
        logger.info("Sending {} request to URL: {}", llmType, API_URL);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String createRequestJson(String prompt) {
        if (llmType == LLMType.DEEP_SEEK) {
            return String.format("{\"model\":\"deepseek-chat\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}]}",
                    escapeJson(prompt));
        } else {
            return String.format("{\"prompt\":\"%s\",\"temperature\":0.6}",
                    escapeJson(prompt));
        }
    }

    private String createStreamingRequestJson(String prompt) {
        if (llmType == LLMType.DEEP_SEEK) {
            return String.format("{\"model\":\"deepseek-chat\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}],\"stream\":true}",
                    escapeJson(prompt));
        } else {
            return String.format("{\"prompt\":\"%s\",\"temperature\":0.6,\"stream\":true}",
                    escapeJson(prompt));
        }
    }

    private String escapeJson(String input) {
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void validateResponse(HttpURLConnection conn) throws IOException {
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP error: " + responseCode);
        }
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    private String parseContent(String json) {
        int responseStart;
        int responseEnd;
        if (llmType == LLMType.DEEP_SEEK) {
            responseStart = json.indexOf("\"content\":\"") + 11;
            responseEnd = json.indexOf("\"", responseStart);
        } else {
            responseStart = json.indexOf("\"response\":\"") + 12;
            responseEnd = json.indexOf("\"", responseStart);
        }
        return unescapeJson(json.substring(responseStart, responseEnd));
    }

    private String unescapeJson(String input) {
        return input.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    @FunctionalInterface
    public interface Consumer<T> {
        void accept(T t);
    }
}