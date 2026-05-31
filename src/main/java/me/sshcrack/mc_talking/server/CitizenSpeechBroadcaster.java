package me.sshcrack.mc_talking.server;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.network.CitizenInterruptPayload;
import me.sshcrack.mc_talking.network.CitizenSpeechPayload;
/*? if forge {*/
/*import net.minecraftforge.network.PacketDistributor;
*//*?}*/
/*? if neoforge {*/
import net.neoforged.neoforge.network.PacketDistributor;
/*?}*/
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side broadcaster that sends citizen speech text to nearby clients.
 */
public class CitizenSpeechBroadcaster {
    public static void broadcastSpeech(AbstractEntityCitizen citizen, String text, float speed) {
        if (citizen.level().isClientSide()) return;

        var server = citizen.level().getServer();
        if (server == null) return;

        int distance = McTalkingConfig.INSTANCE.instance().citizenVoiceDistance;
        if (distance <= 0) {
            distance = 32; // Default reasonable distance
        }

        double distSq = distance * distance;
        boolean isFemale = citizen.getCitizenData() != null && citizen.getCitizenData().isFemale();
        CitizenSpeechPayload payload = new CitizenSpeechPayload(citizen.getUUID(), text, speed, isFemale);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != citizen.level()) continue;
            if (player.distanceToSqr(citizen) <= distSq) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    public static void broadcastInterrupt(AbstractEntityCitizen citizen) {
        if (citizen.level().isClientSide()) return;

        var server = citizen.level().getServer();
        if (server == null) return;

        int distance = McTalkingConfig.INSTANCE.instance().citizenVoiceDistance;
        if (distance <= 0) {
            distance = 32;
        }

        double distSq = distance * distance;
        CitizenInterruptPayload payload = new CitizenInterruptPayload(citizen.getUUID());

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != citizen.level()) continue;
            if (player.distanceToSqr(citizen) <= distSq) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
