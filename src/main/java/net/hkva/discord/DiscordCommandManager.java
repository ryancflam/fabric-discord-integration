package net.hkva.discord;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.hkva.discord.discordcommand.PlayersCommand;

public class DiscordCommandManager {

    private final CommandDispatcher<Message> dispatcher = new CommandDispatcher<>();

    public DiscordCommandManager() {
        PlayersCommand.register(dispatcher);
    }

    public static LiteralArgumentBuilder<Message> literal(String literal) {
        return LiteralArgumentBuilder.literal(literal);
    }

    public CommandDispatcher<Message> getDispatcher() {
        return dispatcher;
    }

}
