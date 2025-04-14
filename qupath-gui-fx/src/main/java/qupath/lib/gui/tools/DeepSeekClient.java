// package qupath.lib.gui.tools;

// import org.json.JSONObject;
// import java.io.*;
// import java.net.HttpURLConnection;
// import java.net.URL;
// import java.nio.charset.StandardCharsets;
// import java.util.concurrent.ExecutorService;
// import java.util.concurrent.Executors;

// public class DeepSeekClient {
//     private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
//     private final String apiKey;
//     private final ExecutorService executor = Executors.newCachedThreadPool();

//     public DeepSeekClient(String apiKey) {
//         this.apiKey = apiKey;
//     }

//     public void sendRequestAsync(String userMessage, 
//                                 ResponseCallback callback) {
//         executor.execute(() -> {
//             try {
//                 String response = sendRequest(userMessage);
//                 JSONObject jsonResponse = new JSONObject(response);
//                 String content = jsonResponse.getJSONArray("choices")
//                         .getJSONObject(0)
//                         .getJSONObject("message")
//                         .getString("content");
//                 Platform.runLater(() -> callback.onSuccess(content));
//             } catch (Exception e) {
//                 Platform.runLater(() -> callback.onError(e));
//             }
//         });
//     }

//     private String sendRequest(String userMessage) throws IOException {
//         HttpURLConnection connection = (HttpURLConnection) new URL(API_URL).openConnection();
//         connection.setRequestMethod("POST");
//         connection.setRequestProperty("Content-Type", "application/json");
//         connection.setRequestProperty("Authorization", "Bearer " + apiKey);
//         connection.setDoOutput(true);

//         JSONObject requestBody = new JSONObject();
//         requestBody.put("model", "deepseek-chat");
//         requestBody.put("messages", new JSONObject[]{
//             new JSONObject()
//                 .put("role", "user")
//                 .put("content", userMessage)
//         });

//         try (OutputStream os = connection.getOutputStream()) {
//             byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
//             os.write(input, 0, input.length);
//         }

//         int responseCode = connection.getResponseCode();
//         if (responseCode != 200) {
//             throw new IOException("API request failed with code: " + responseCode);
//         }

//         try (BufferedReader br = new BufferedReader(
//             new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
//             StringBuilder response = new StringBuilder();
//             String responseLine;
//             while ((responseLine = br.readLine()) != null) {
//                 response.append(responseLine.trim());
//             }
//             return response.toString();
//         }
//     }

//     public interface ResponseCallback {
//         void onSuccess(String response);
//         void onError(Throwable throwable);
//     }

//     public void shutdown() {
//         executor.shutdown();
//     }
// }