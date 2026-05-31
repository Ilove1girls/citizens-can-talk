package me.sshcrack.mc_talking.network;

import me.sshcrack.mc_talking.McTalking;
/*? if forge {*/
/*import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.function.Supplier;
*//*?}*/
/*? if neoforge {*/
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
/*?}*/

import java.util.UUID;

/**
 * Server→client packet that tells the client to immediately stop all speech
 * and clear the audio queue for a specific citizen.
 * Sent when the player interrupts the citizen with new speech.
 */
public class CitizenInterruptPayload /*? if neoforge {*/ implements CustomPacketPayload/*?}*/ {
    /*? if forge {*/
    /*public static final String PROTOCOL_VERSION = "1";
    public static final ResourceLocation CHANNEL_ID = new ResourceLocation(McTalking.MODID, "citizen_interrupt");
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CHANNEL_ID,
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    private static int id = 0;
    *//*?}*/

    /*? if neoforge {*/
    public static final CustomPacketPayload.Type<CitizenInterruptPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(McTalking.MODID, "citizen_interrupt"));
    public static final StreamCodec<ByteBuf, CitizenInterruptPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            CitizenInterruptPayload::citizenId,
            CitizenInterruptPayload::new
    );
    /*?}*/

    private final UUID citizenId;

    public CitizenInterruptPayload(UUID citizenId) {
        this.citizenId = citizenId;
    }

    public UUID citizenId() {
        return citizenId;
    }

    /*? if forge {*/
    /*public static void encode(CitizenInterruptPayload msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.citizenId);
    }

    public static CitizenInterruptPayload decode(FriendlyByteBuf buf) {
        return new CitizenInterruptPayload(buf.readUUID());
    }

    public static void handle(CitizenInterruptPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> me.sshcrack.mc_talking.client.ClientSpeechHandler.onCitizenInterrupt(msg));
        ctx.get().setPacketHandled(true);
    }

    public static void registerMessages() {
        CHANNEL.registerMessage(id++, CitizenInterruptPayload.class, CitizenInterruptPayload::encode, CitizenInterruptPayload::decode, CitizenInterruptPayload::handle);
    }
    *//*?}*/

    /*? if neoforge {*/
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void registerMessages() {
    }
    /*?}*/
}
