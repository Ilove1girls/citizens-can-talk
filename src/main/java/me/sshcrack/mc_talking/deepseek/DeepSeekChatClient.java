package me.sshcrack.mc_talking.deepseek;

import com.google.gson.*;
import me.sshcrack.mc_talking.McTalking;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for DeepSeek Chat API (OpenAI-compatible).
 * Supports text chat and tool calling.
 */
public class DeepSeekChatClient {
    private static final String DEEPSEEK_URL = "https://api.deepseek.com/chat/completions";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private final String apiKey;
    private final String model;

    public DeepSeekChatClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * Sends a chat request to DeepSeek.
     *
     * @param messages List of messages (system, user, assistant, tool)
     * @param tools    List of tool definitions (nullable)
     * @return ChatResponse containing the assistant's message and any tool calls
     */
    public ChatResponse chat(List<Message> messages, List<Tool> tools) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("max_tokens", 300); // Hard cap: ~4 sentences max

        JsonArray msgs = new JsonArray();
        for (Message msg : messages) {
            msgs.add(msg.toJson());
        }
        requestBody.add("messages", msgs);

        if (tools != null && !tools.isEmpty()) {
            JsonArray toolsArray = new JsonArray();
            for (Tool tool : tools) {
                toolsArray.add(tool.toJson());
            }
            requestBody.add("tools", toolsArray);
            requestBody.addProperty("tool_choice", "auto");
        }

        String requestJson = requestBody.toString();
        if (me.sshcrack.mc_talking.config.McTalkingConfig.INSTANCE.instance().debugMode) {
            McTalking.LOGGER.debug("[DeepSeek] Request: {}", requestJson);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DEEPSEEK_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                McTalking.LOGGER.error("[DeepSeek] API error {}: {}", response.statusCode(), response.body());
                return new ChatResponse("", List.of());
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                return new ChatResponse("", List.of());
            }

            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            String content = message.has("content") && !message.get("content").isJsonNull()
                    ? message.get("content").getAsString()
                    : "";

            List<ToolCall> toolCalls = new ArrayList<>();
            if (message.has("tool_calls") && !message.get("tool_calls").isJsonNull()) {
                JsonArray tcArray = message.getAsJsonArray("tool_calls");
                for (JsonElement tc : tcArray) {
                    JsonObject tcObj = tc.getAsJsonObject();
                    String id = tcObj.get("id").getAsString();
                    JsonObject function = tcObj.getAsJsonObject("function");
                    String name = function.get("name").getAsString();
                    String arguments = function.get("arguments").getAsString();
                    toolCalls.add(new ToolCall(id, name, arguments));
                }
            }

            return new ChatResponse(content, toolCalls);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            McTalking.LOGGER.error("[DeepSeek] Request failed", e);
            return new ChatResponse("", List.of());
        }
    }

    // -------------------------------------------------------------------------
    // Data classes
    // -------------------------------------------------------------------------

    public static class Message {
        public final String role;
        public final String content;
        public final List<ToolCall> toolCalls;
        public final String toolCallId;

        public Message(String role, String content) {
            this(role, content, null, null);
        }

        public Message(String role, String content, List<ToolCall> toolCalls, String toolCallId) {
            this.role = role;
            this.content = content;
            this.toolCalls = toolCalls;
            this.toolCallId = toolCallId;
        }

        public static Message assistantWithTools(String content, List<ToolCall> toolCalls) {
            return new Message("assistant", content, toolCalls, null);
        }

        public static Message toolResult(String toolCallId, String content) {
            return new Message("tool", content, null, toolCallId);
        }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("role", role);
            if (content != null && !content.isEmpty()) {
                obj.addProperty("content", content);
            } else if (role.equals("assistant") && toolCalls != null && !toolCalls.isEmpty()) {
                obj.add("content", JsonNull.INSTANCE);
            } else {
                obj.addProperty("content", "");
            }

            if (toolCalls != null && !toolCalls.isEmpty()) {
                JsonArray arr = new JsonArray();
                for (ToolCall tc : toolCalls) {
                    arr.add(tc.toJson());
                }
                obj.add("tool_calls", arr);
            }

            if (toolCallId != null) {
                obj.addProperty("tool_call_id", toolCallId);
            }
            return obj;
        }
    }

    public record Tool(String name, String description, JsonObject parameters) {
        public JsonObject toJson() {
            JsonObject function = new JsonObject();
            function.addProperty("name", name);
            function.addProperty("description", description);
            if (parameters != null) {
                function.add("parameters", parameters);
            }
            JsonObject tool = new JsonObject();
            tool.addProperty("type", "function");
            tool.add("function", function);
            return tool;
        }
    }

    public record ToolCall(String id, String name, String arguments) {
        public JsonObject toJson() {
            JsonObject function = new JsonObject();
            function.addProperty("name", name);
            function.addProperty("arguments", arguments);
            JsonObject obj = new JsonObject();
            obj.addProperty("id", id);
            obj.addProperty("type", "function");
            obj.add("function", function);
            return obj;
        }
    }

    public record ChatResponse(String content, List<ToolCall> toolCalls) {
        public boolean hasToolCalls() {
            return !toolCalls.isEmpty();
        }
    }
}
