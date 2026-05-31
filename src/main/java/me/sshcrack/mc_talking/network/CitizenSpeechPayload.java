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
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
/*?}*/

import java.util.UUID;

public class CitizenSpeechPayload /*? if neoforge {*/ implements CustomPacketPayload/*?}*/ {
    /*? if forge {*/
    /*public static final String PROTOCOL_VERSION = "1";
    public static final ResourceLocation CHANNEL_ID = new ResourceLocation(McTalking.MODID, "citizen_speech");
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CHANNEL_ID,
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    private static int id = 0;
    *//*?}*/

    /*? if neoforge {*/
    public static final CustomPacketPayload.Type<CitizenSpeechPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(McTalking.MODID, "citizen_speech"));
    public static final StreamCodec<ByteBuf, CitizenSpeechPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            CitizenSpeechPayload::citizenId,
            ByteBufCodecs.STRING_UTF8,
            CitizenSpeechPayload::text,
            ByteBufCodecs.FLOAT,
            CitizenSpeechPayload::speed,
            ByteBufCodecs.BOOL,
            CitizenSpeechPayload::isFemale,
            CitizenSpeechPayload::new
    );
    /*?}*/

    private final UUID citizenId;
    private final String text;
    private final float speed;
    private final boolean isFemale;

    public CitizenSpeechPayload(UUID citizenId, String text, float speed, boolean isFemale) {
        this.citizenId = citizenId;
        this.text = text;
        this.speed = speed;
        this.isFemale = isFemale;
    }

    public UUID citizenId() {
        return citizenId;
    }

    public String text() {
        return text;
    }

    public float speed() {
        return speed;
    }

    public boolean isFemale() {
        return isFemale;
    }

    /*? if forge {*/
    /*public static void encode(CitizenSpeechPayload msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.citizenId);
        buf.writeUtf(msg.text);
        buf.writeFloat(msg.speed);
        buf.writeBoolean(msg.isFemale);
    }

    public static CitizenSpeechPayload decode(FriendlyByteBuf buf) {
        return new CitizenSpeechPayload(buf.readUUID(), buf.readUtf(), buf.readFloat(), buf.readBoolean());
    }

    public static void handle(CitizenSpeechPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> me.sshcrack.mc_talking.client.ClientSpeechHandler.onCitizenSpeech(msg));
        ctx.get().setPacketHandled(true);
    }

    public static void registerMessages() {
        CHANNEL.registerMessage(id++, CitizenSpeechPayload.class, CitizenSpeechPayload::encode, CitizenSpeechPayload::decode, CitizenSpeechPayload::handle);
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
