package net.hkva.discord.callback;

import net.dv8tion.jda.api.entities.Message;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public interface DiscordMessageCallback {

    Event<DiscordMessageCallback> EVENT = EventFactory.createArrayBacked(DiscordMessageCallback.class,
            (listeners) -> (message) -> {
                for (DiscordMessageCallback listener : listeners) {
                    listener.dispatch(message);
                }
            }
    );

    void dispatch(Message message);

}
