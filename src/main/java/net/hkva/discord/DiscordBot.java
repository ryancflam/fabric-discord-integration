package net.hkva.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;

import com.vdurmont.emoji.EmojiParser;

import java.util.Optional;

/**
 * Represents a connection to Discord
 */
public class DiscordBot extends ListenerAdapter {

    private Config config;
    private final JDABuilder builder;
    private Optional<JDA> discord = Optional.empty();

    public DiscordBot(Config config) {
        this.config = config;

        builder = JDABuilder.createDefault(config.token)
                .setStatus(OnlineStatus.ONLINE)
                .setActivity(Activity.playing(config.initialActivity))
                .addEventListeners(this);
    }

    /**
     * On Discord message received
     */
    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        final Message message = event.getMessage();
        final User author = message.getAuthor();

        if (author.isBot()) {
            return;
        }

        // TODO: Blacklist users
        // TODO: Attachment support
        
        if (!config.relayChannelIDs.contains((Long)message.getChannel().getIdLong())) {
        	return;
        }

        String messageContent = formatIncoming(message);

        synchronized (DiscordIntegrationMod.messageBufferIn) {
            DiscordIntegrationMod.messageBufferIn.add(messageContent);
        }
    }

    /**
     * Connect to discord
     */
    public void login() throws LoginException, InterruptedException {
        discord = Optional.of(builder.build());
        discord.get().awaitReady();
    }

    /**
     * Disconnect from discord
     */
    public void logout() {
        if (discord.isPresent()) {
            discord.get().shutdownNow();
        }
    }

    /**
     * Update the config
     */
    public void updateConfig(Config config) {
        this.config = config;
    }

    /**
     * Check if the bot is connected
     */
    public boolean isConnected() {
        return discord.isPresent();
    }

    /**
     * Relay game chat message
     */
    public void relayOutgoing(String message) {
        for (Long channelID : config.relayChannelIDs) {
            final TextChannel channel = discord.get().getTextChannelById(channelID);
            if (channel == null || !channel.canTalk()) {
                DiscordIntegrationMod.LOGGER.warn("Relay channel " + channelID + " is invalid");
                continue;
            }
            channel.sendMessage(formatOutgoing(message)).queue();
        }
    }

    /**
     * Format a username
     */
    public static String formatUsername(User user) {
        return String.format("%s#%s", user.getName(), user.getDiscriminator());
    }
    
    /**
     * Format a message going from Discord to Minecraft
     */
    public static String formatIncoming(Message message) {
    	final String contentNoEmoji = EmojiParser.parseToAliases(message.getContentDisplay());
    	return String.format("[%s] %s", formatUsername(message.getAuthor()), contentNoEmoji);
    }

    /**
     * Format a message going from Minecraft to Discord
     */
    public static String formatOutgoing(String message) {
        // "@" -> "@ "
        return message.replace("@", "@ ");
    }

    /**
     * Update the player count status
     */
    public void updatePlayerCount(int players, int max) {
        final String activityString = String.format("%d/%d players", players, max);
        discord.get().getPresence().setActivity(Activity.playing(activityString));
    }

}
