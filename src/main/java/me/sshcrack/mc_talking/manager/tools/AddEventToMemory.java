package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import me.sshcrack.mc_talking.schema.JsonSchemaBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddEventToMemory extends FunctionAction {
    public AddEventToMemory() {
        super("add_event_to_memory", """
                        Adds a message to your memory, so you remember it in ongoing conversations.
                        Use for important events only and be concise.
                        """,
                new JsonSchemaBuilder()
                        .string("event", "The event to remember", true)
                        .build());
    }

    @NotNull
    @Override
    public JsonObject execute(AbstractEntityCitizen citizen, IColony colony, @Nullable JsonObject parameters) {
        var obj = new JsonObject();
        if (parameters == null || !parameters.has("event")) {
            obj.addProperty("success", false);
            obj.addProperty("error", "Missing or invalid parameters.");
            return obj;
        }

        var event = parameters.get("event").getAsString();
        var memory = ((CitizenDataMemoryExtended) citizen.getCitizenData()).mc_talking$getOrInitializeMemory();

        memory.addEvent(event);
        obj.addProperty("success", true);

        return obj;
    }
}
