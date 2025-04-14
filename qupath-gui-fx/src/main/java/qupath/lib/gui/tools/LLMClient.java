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

import javafx.application.Platform;

public class LLMClient {
    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final String apiKey;

    public LLMClient(String apiKey) {
        this.apiKey = apiKey;
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
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        return conn;
    }

    private void writeRequestBody(HttpURLConnection conn, String json) throws IOException {
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String createRequestJson(String prompt) {
        return String.format("{\"model\":\"deepseek-chat\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}]}",
                escapeJson(prompt));
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
        int contentStart = json.indexOf("\"content\":\"") + 11;
        int contentEnd = json.indexOf("\"", contentStart);
        return unescapeJson(json.substring(contentStart, contentEnd));
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