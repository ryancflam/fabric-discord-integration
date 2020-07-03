package net.hkva.discord.mixin;

import net.hkva.discord.callback.ServerChatCallback;
import net.minecraft.network.MessageType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
    @Inject(at = @At("HEAD"), method = "broadcastChatMessage (Lnet/minecraft/text/Text;Lnet/minecraft/network/MessageType;Ljava/util/UUID;)V")
    private void broadcastMessage(Text text, MessageType type, UUID senderUUID, CallbackInfo ci) {
        MinecraftServer server = ((PlayerManager)(Object)this).getServer();
        ServerChatCallback.EVENT.invoker().dispatch(server, text, type, senderUUID);
    }
}
