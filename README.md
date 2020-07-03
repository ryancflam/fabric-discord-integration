# fabric-discord-integration

**fabric-discord-integration** is a [Fabric](https://fabricmc.net/) mod for dedicated Minecraft servers allowing bidirectional [Discord](https://discord.com) integration.
The mod is still fairly early in development, so please understand there may be occasional bugs.

## Features:
* Minecraft -> Discord chat forwarding (including join messages, deaths, etc.)
* Discord -> Minecraft chat forwarding
* Display player count

## Quick start:
1. Install [Fabric](https://fabricmc.net/use/?page=server) and the [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api) on your Minecraft server
2. Place `fabric-discord-integration-*.jar` in `mods/`
3. Start the server
4. Edit `config/discord.json` in a text editor ([example](https://gist.github.com/hkva/50f368241f65a877d26a3546f979a7f7))
5. Run `/discord loadConfig` followed by `/discord reconnect`
6. Check that the bot connected with `/discord status`

## Minecraft commands
These commands are only available to server operators
* `/discord loadConfig` Reload the `discord.json` config file
* `/discord reconnect` Disconnect and reconnect the Discord bot
* `/discord status` Check the connection status of the Discord bot

## License
[MIT](/LICENSE)
