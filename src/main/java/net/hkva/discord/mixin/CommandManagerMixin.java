package net.hkva.discord.mixin;

import net.hkva.discord.DiscordIntegrationMod;
import net.minecraft.server.command.CommandManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CommandManager.class)
public abstract class CommandManagerMixin {
    @Inject(at = @At("RETURN"), method = "<init>")
    private void constructor(CommandManager.RegistrationEnvironment environment, CallbackInfo ci) {
        final CommandManager thisPtr = (CommandManager)(Object)this;
        DiscordIntegrationMod.registerCommands(thisPtr.getDispatcher());
    }
}
