package me.sshcrack.mc_talking.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for standard OpenAI/DeepSeek JSON Schema parameter objects.
 *
 * <p>Usage:
 * <pre>
 * JsonObject params = new JsonSchemaBuilder()
 *     .string("name", "The citizen's name", true)
 *     .integer("count", "How many items", true)
 *     .enum_("mood", "Current mood", List.of("happy", "sad", "angry"), true)
 *     .build();
 * </pre>
 */
public class JsonSchemaBuilder {
    private final JsonObject properties = new JsonObject();
    private final List<String> required = new ArrayList<>();

    public JsonSchemaBuilder string(String name, String description, boolean isRequired) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "string");
        prop.addProperty("description", description);
        properties.add(name, prop);
        if (isRequired) required.add(name);
        return this;
    }

    public JsonSchemaBuilder integer(String name, String description, boolean isRequired) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "integer");
        prop.addProperty("description", description);
        properties.add(name, prop);
        if (isRequired) required.add(name);
        return this;
    }

    public JsonSchemaBuilder number(String name, String description, boolean isRequired) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "number");
        prop.addProperty("description", description);
        properties.add(name, prop);
        if (isRequired) required.add(name);
        return this;
    }

    public JsonSchemaBuilder bool(String name, String description, boolean isRequired) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "boolean");
        prop.addProperty("description", description);
        properties.add(name, prop);
        if (isRequired) required.add(name);
        return this;
    }

    public JsonSchemaBuilder enum_(String name, String description, List<String> values, boolean isRequired) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "string");
        prop.addProperty("description", description);
        JsonArray arr = new JsonArray();
        for (String v : values) arr.add(v);
        prop.add("enum", arr);
        properties.add(name, prop);
        if (isRequired) required.add(name);
        return this;
    }

    public JsonObject build() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        if (!required.isEmpty()) {
            JsonArray req = new JsonArray();
            for (String r : required) req.add(r);
            schema.add("required", req);
        }
        return schema;
    }
}
