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
    private static final String API_URL = "http://localhost:10000/process";
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public LLMClient() {
    }

    public void getCompletionAsync(String question, String image, Consumer<String> onSuccess, Consumer<Exception> onError) {
        executor.execute(() -> {
            try {
                String result = sendRequest(question, image);
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Exception e) {
                Platform.runLater(() -> onError.accept(e));
            }
        });
    }

    private String sendRequest(String question, String image) throws IOException {
        HttpURLConnection conn = createConnection();
        writeRequestBody(conn, createRequestJson(question, image));
        validateResponse(conn);
        return readResponse(conn);
    }

    private HttpURLConnection createConnection() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        return conn;
    }

    private void writeRequestBody(HttpURLConnection conn, String json) throws IOException {
        logger.info("Sending request to URL: {}", API_URL);
        logger.info("Request body: {}", json);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String createRequestJson(String question, String image) {
        String json = String.format("{\"question\":\"%s\",\"image\":\"%s\"}",
                escapeJson(question),
                escapeJson(image));
        return json;
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

    @FunctionalInterface
    public interface Consumer<T> {
        void accept(T t);
    }
}