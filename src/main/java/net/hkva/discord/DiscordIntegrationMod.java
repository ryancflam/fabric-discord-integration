package net.hkva.discord;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.server.ServerStopCallback;
import net.fabricmc.fabric.api.event.server.ServerTickCallback;
import net.hkva.discord.callback.ServerChatCallback;
import net.minecraft.network.MessageType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
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
	private static Optional<DiscordBot> bot = Optional.empty();
	private static int lastPlayerCount = -1;
	private static final int PLAYER_COUNT_UPDATE_INTERVAL = 20 * 5;

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

		ServerStopCallback.EVENT.register(DiscordIntegrationMod::onServerStop);
		ServerTickCallback.EVENT.register(DiscordIntegrationMod::onServerTick);
		ServerChatCallback.EVENT.register(DiscordIntegrationMod::onServerChat);
	}

	/**
	 * Server stop callback
	 */
	public static void onServerStop(MinecraftServer server) {
		if (bot.isPresent()) {
			disconnectBot();
		}
	}

	/**
	 * Server tick callback
	 */
	public static void onServerTick(MinecraftServer server) {
		if (!bot.isPresent()) {
			return;
		}

		synchronized (messageBufferIn) {
			for (String message : messageBufferIn) {
				server.getPlayerManager().broadcastChatMessage(
						new LiteralText(message), MessageType.CHAT, Util.NIL_UUID);
			}
			messageBufferIn.clear();
		}

		// TODO: Replace with player join/leave listeners
		if (server.getTicks() % PLAYER_COUNT_UPDATE_INTERVAL == 0) {
			final int playerCount = server.getCurrentPlayerCount();
			if (playerCount != lastPlayerCount) {
				lastPlayerCount = playerCount;
				bot.get().updatePlayerCount(playerCount, server.getMaxPlayerCount());
			}
		}
	}

	/**
	 * Chat message callback
	 */
	public static void onServerChat(MinecraftServer server, Text message, MessageType type, UUID sender) {
		// LOGGER.info(String.format("onServerChat(message = ..., type = %s, sender = %s", type, sender));
		if (bot.isPresent() && !(type == MessageType.CHAT && sender == Util.NIL_UUID)) {
			bot.get().relayOutgoing(message.getString());
		}
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
		if (!bot.isPresent() || !bot.get().isConnected()) {
			source.sendFeedback(new LiteralText("Discord: Not connected"), false);
		} else {
			source.sendFeedback(new LiteralText("Discord: Connected"), false);
		}

		return 0;
	}

	/**
	 * In-game command to reconnect to discord
	 */
	public static int reconnectCommand(CommandContext<ServerCommandSource> context) {
		final ServerCommandSource source = context.getSource();
		if (bot.isPresent()) {
			disconnectBot();
			source.sendFeedback(new LiteralText("Discord: Disconnected"), true);
		}
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
		// Notify bot of config change
		if (bot.isPresent()) {
			bot.get().updateConfig(config);
		}
		return result;
	}

	/**
	 * Connect to discord
	 */
	public static boolean connectBot() {
		if (bot.isPresent()) {
			throw new IllegalStateException("Trying to create new bot when bot already exists");
		}

		bot = Optional.of(new DiscordBot(config));
		try {
			bot.get().login();
		} catch (LoginException | InterruptedException e) {
			e.printStackTrace();
			LOGGER.warn("Could not log in with the supplied token");
			bot = Optional.empty();
			return false;
		}
		LOGGER.info("Logged in to Discord");
		return true;
	}

	/**
	 * Disconnect from discord
	 */
	public static void disconnectBot() {
		if (!bot.isPresent()) {
			throw new IllegalStateException("Trying to destroy bot when no bot exists");
		}

		bot.get().logout();
		LOGGER.info("Logged out of Discord");

		bot = Optional.empty();
	}
}
