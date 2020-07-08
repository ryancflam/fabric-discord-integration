package net.hkva.discord;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.vdurmont.emoji.EmojiParser;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.server.ServerStartCallback;
import net.fabricmc.fabric.api.event.server.ServerStopCallback;
import net.fabricmc.fabric.api.event.server.ServerTickCallback;
import net.hkva.discord.callback.DiscordMessageCallback;
import net.hkva.discord.callback.ServerChatCallback;
import net.minecraft.network.MessageType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.security.auth.login.LoginException;
import java.util.*;

public class DiscordIntegrationMod implements DedicatedServerModInitializer {

	// Just let everything be accessed statically
	// Not a big deal since there is only one instance of this class

	public static Logger LOGGER = LogManager.getLogger("Discord Integration");

	// Incoming message buffer
	public static List<String> messageBufferIn = Collections.synchronizedList(new ArrayList<String>());

	private static final ConfigFile configFile = new ConfigFile("discord.json");
	private static Config config = ConfigFile.DEFAULT_CONFIG;

	private static DiscordBot bot = new DiscordBot();

	private static Optional<MinecraftServer> server = Optional.empty();

	/**
	 * Mod entry point
	 */
	@Override
	public void onInitializeServer() {
		if (!readConfig()) {
			LOGGER.warn("Not running since config was just created");
			LOGGER.warn("You can reload the config with /discord loadConfig");
			LOGGER.warn("Then, you can connect to Discord with /discord reconnect");
			return;
		}

		connectBot();

		ServerStartCallback.EVENT.register(DiscordIntegrationMod::onServerStart);
		ServerStopCallback.EVENT.register(DiscordIntegrationMod::onServerStop);
		ServerChatCallback.EVENT.register(DiscordIntegrationMod::onServerChat);
		DiscordMessageCallback.EVENT.register(DiscordIntegrationMod::onDiscordChat);
	}

	private static void onServerStart(MinecraftServer server) {
		DiscordIntegrationMod.server = Optional.of(server);
	}

	/**
	 * Server stop callback
	 */
	private static void onServerStop(MinecraftServer server) {
		DiscordIntegrationMod.server = Optional.empty();
		bot.disconnect();
	}

	/**
	 * On Minecraft chat message sent
	 */
	private static void onServerChat(MinecraftServer server, Text text, MessageType type, UUID senderUUID) {
		if (type == MessageType.CHAT && senderUUID == Util.NIL_UUID) {
			return;
		}

		System.out.println(text);
		System.out.println(bot.isConnected());

		bot.withConnection(c -> {
			for (Long channelID : config.relayChannelIDs) {
				final TextChannel relayChannel = c.getTextChannelById(channelID);
				if (relayChannel == null || !relayChannel.canTalk()) {
					LOGGER.warn("Relay channel " + channelID + " is invalid");
					continue;
				}
				relayChannel.sendMessage(formatOutgoing(text.getString())).queue();
			}
		});
	}

	/**
	 * On Discord chat message sent
	 */
	private static void onDiscordChat(Message message) {
		if (!server.isPresent()) {
			return;
		}

		final User author = message.getAuthor();

		if (author.isBot()) {
			return;
		}

		if (!config.relayChannelIDs.contains((Long)message.getChannel().getIdLong())) {
			return;
		}

		final String gameChatMessage = formatIncoming(message);

		server.get().getPlayerManager().broadcastChatMessage(
				new LiteralText(gameChatMessage),
				MessageType.CHAT,
				Util.NIL_UUID
		);
	}

	/**
	 * Register in-game commands
	 * @param dispatcher Server command dispatcher
	 */
	public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("discord")
				.requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(4))
				.then(CommandManager.literal("loadConfig")
						.executes(DiscordIntegrationMod::reloadConfigCommand)
				)
				.then(CommandManager.literal("status")
						.executes(DiscordIntegrationMod::statusCommand)
				)
				.then(CommandManager.literal("reconnect")
						.executes(DiscordIntegrationMod::reconnectCommand)
				)
		);
	}

	/**
	 * In-game command to reload the config
	 */
	public static int reloadConfigCommand(CommandContext<ServerCommandSource> context) {
		String response = "Discord: Loaded config";
		if (!readConfig()) {
			response = "Discord: Wrote default config";
		}
		context.getSource().sendFeedback(new LiteralText(response), true);
		return 0;
	}

	/**
	 * In-game command to check the connection status
	 */
	public static int statusCommand(CommandContext<ServerCommandSource> context) {
		final ServerCommandSource source = context.getSource();
		if (!bot.isConnected()) {
			source.sendFeedback(new LiteralText("Discord: Not connected"), false);
		} else {
			bot.withConnection(c -> {
				source.sendFeedback(new LiteralText("Discord: Connected"), false);
				source.sendFeedback(new LiteralText("Status: " + c.getStatus()), false);
			});
		}

		return 0;
	}

	/**
	 * In-game command to reconnect to discord
	 */
	public static int reconnectCommand(CommandContext<ServerCommandSource> context) {
		final ServerCommandSource source = context.getSource();
		bot.disconnect();
		source.sendFeedback(new LiteralText("Discord: Disconnected"), true);
		if (!connectBot()) {
			source.sendFeedback(new LiteralText("Discord: Could not log in"), true);
		} else {
			source.sendFeedback(new LiteralText("Discord: Connected"), true);
		}

		return 0;
	}

	/**
	 * Load the config from disk
	 * @return false if the config didn't exist
	 */
	public static boolean readConfig() {
		boolean result = true;
		// Write default config if required
		if (!configFile.exists()) {
			LOGGER.warn("Config file " + configFile + " does not exist, creating...");
			configFile.writeDefaultConfig();
			result = false;
		}
		// Read config
		Optional<Config> readResult = configFile.readConfig();
		if (!readResult.isPresent()) {
			LOGGER.error("Failed to read config file " + configFile);
		} else {
			LOGGER.info("Read config " + configFile);
			config = readResult.get();
		}

		return result;
	}

	/**
	 * Connect to discord
	 */
	public static boolean connectBot() {
		if (bot.isConnected()) {
			throw new IllegalStateException("Bot is already connected");
		}

		try {
			bot.connect(config.token);
		} catch (Exception e) {
			LOGGER.warn("Discord: Could not log in with the supplied token");
			return false;
		}

		LOGGER.info("Discord: Logged in");
		return true;
	}

	/**
	 * Disconnect from discord
	 */
	public static void disconnectBot() {
		if (!bot.isConnected()) {
			throw new IllegalStateException("Bot is already disconnected");
		}

		bot.disconnect();
		LOGGER.info("Logged out of Discord");
	}

	/**
	 * Format an outgoing message
	 */
	public static String formatOutgoing(String message) {
		// "@" -> "@ "
		return message.replace("@", "@ ");
	}

	/**
	 * Format an incoming message
	 */
	public static String formatIncoming(Message message) {
		String gameMessage = EmojiParser.parseToAliases(message.getContentDisplay());
		return String.format("[%s] %s", formatUsername(message.getAuthor()), gameMessage);
	}

	/**
	 * Format a user's name
	 */
	public static String formatUsername(User user) {
		return String.format("%s#%s", user.getName(), user.getDiscriminator());
	}
}
