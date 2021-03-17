package com.adibuda.bot;

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class TestEvent extends ListenerAdapter {
    public void onGuildMessageReceived(GuildMessageReceivedEvent e){
        String s = e.getMessage().getContentRaw().toLowerCase();

    }
}
