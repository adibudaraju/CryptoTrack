package com.electriccapital;

import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.sqlite.core.*;

/**
 * Handles all things message-related, whether that is receiving a message in a server to add to a database,
 * or taking a command and giving the appropriate response.
 */

import java.sql.*;
import java.time.*;
import java.util.*;

public class MessageEvent extends ListenerAdapter {

    public static final int SECONDS_IN_WEEK = 604800;

    /**
     * Listener method - the brunt of the flow control in the class. Checks if the message is a command or not,
     * and takes appropriate action - whether that's executing a command, or
     * saving the message to the database.
     * @param e
     */
    public void onGuildMessageReceived(GuildMessageReceivedEvent e) {
        Message msg = e.getMessage();
        Member member = msg.getMember();
        if (msg.getAuthor().isBot()) return;
        String content = msg.getContentStripped().toLowerCase();
        if (content.startsWith(";")) {
            content = content.substring(1);
            if (isCommand(content, "help")) {
                sendHelp(msg);
            } else if (isCommand(content, "add")) {
                if (!member.hasPermission(Permission.ADMINISTRATOR)) {
                    (new MessageBuilder())
                            .append("Only administrators can use this command.")
                            .sendTo(msg.getChannel()).queue();
                    return;
                }
                addChannels(msg);
            } else if (isCommand(content, "add-full-server")) {
                if (!member.hasPermission(Permission.ADMINISTRATOR)) {
                    (new MessageBuilder())
                            .append("Only administrators can use this command.")
                            .sendTo(msg.getChannel()).queue();
                    return;
                }
                addServer(msg.getGuild(), msg.getChannel());
            } else if (isCommand(content, "remove-full-server")) {
                if (!member.hasPermission(Permission.ADMINISTRATOR)) {
                    (new MessageBuilder())
                            .append("Only administrators can use this command.")
                            .sendTo(msg.getChannel()).queue();
                    return;
                }
                removeAllChannels(msg.getGuild(), msg.getChannel());
            } else if (isCommand(content, "remove")) {
                if (!member.hasPermission(Permission.ADMINISTRATOR)) {
                    (new MessageBuilder())
                            .append("Only administrators can use this command.")
                            .sendTo(msg.getChannel()).queue();
                    return;
                }
                removeChannels(msg);
            } else if (isCommand(content, "channel-stats")) {
                getChannelStats(msg.getTextChannel(), msg.getChannel());
            } else if (isCommand(content, "server-stats")) {
                getServerStats(msg.getGuild(), msg.getChannel());
            } else if (isCommand(content, "user-stats")) {
                getUserStats(msg, msg.getChannel());
            } else if(isCommand(content, "show-channels")){
                showChannels(msg.getGuild(), msg.getChannel());
            } else {
                messageReceived(e);
            }
        } else {
            messageReceived(e);
        }
    }

    /**
     * Handles a non-command message to see if it should store it to the database or not. Performs the storage if so.
     * @param e
     */
    private void messageReceived(GuildMessageReceivedEvent e) {
        Message msg = e.getMessage();
        User user = msg.getAuthor();
        Member member = msg.getMember();
        if (!DBUtils.containsChannel(msg.getTextChannel()))
            return;
        if (!DBUtils.containsUser(user))
            DBUtils.executePrepared("INSERT INTO members (serverName, serverID, userName, userNickname, userID, messagesSent)" +
                            "VALUES (?, ?, ?, ?, ?, ?)", member.getGuild().getName(),
                    member.getGuild().getIdLong(), user.getName(), member.getNickname(), member.getIdLong(), 0);
        DBUtils.executePrepared("INSERT INTO messages (serverName, serverID, channelName, " +
                        "channelID, userName, userNickname, userID, content," +
                        "messageID, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                member.getGuild().getName(), member.getGuild().getIdLong(), msg.getTextChannel().getName(),
                msg.getTextChannel().getIdLong(), user.getName(), member.getNickname(),
                member.getIdLong(), msg.getContentStripped(), msg.getIdLong(),
                msg.getTimeCreated().toInstant().getEpochSecond());
        DBUtils.executePrepared("UPDATE members SET messagesSent = messagesSent + 1 WHERE serverID = ? AND userID = ?",
                member.getGuild().getIdLong(), user.getIdLong());
    }

    /**
     * Gets stats about a specific channel.
     * @param channel
     * @param toSend
     */
    private void getChannelStats(TextChannel channel, MessageChannel toSend) {
        MessageBuilder messageBuilder = new MessageBuilder();
        if (!DBUtils.containsChannel(channel)) {
            messageBuilder.append("Channel ");
            messageBuilder.append(channel);
            messageBuilder.append(" is not in the watchlist!");
            messageBuilder.sendTo(toSend).queue();
            return;
        }
        int totalNum = DBUtils.getIntResult("SELECT COUNT(*) FROM messages WHERE channelID = " +
                channel.getIdLong(), 1);
        messageBuilder.append("Total messages recorded in this channel: " + totalNum);
        messageBuilder.append("\nTotal messages recorded in this channel in the past week: ");
        int weekNum = DBUtils.getIntResult("SELECT COUNT(*) FROM messages WHERE channelID = " +
                        channel.getIdLong() + " AND timestamp >= " + (OffsetDateTime.now().toInstant().getEpochSecond() - SECONDS_IN_WEEK),
                1);
        messageBuilder.append(weekNum);
        messageBuilder.sendTo(toSend).queue();

    }

    /**
     * Gets stats about a specific server/guild.
     * @param guild
     * @param toSend
     */
    private void getServerStats(Guild guild, MessageChannel toSend) {
        MessageBuilder messageBuilder = new MessageBuilder();
        int totalNum = DBUtils.getIntResult("SELECT COUNT(*) FROM messages WHERE serverID = " +
                guild.getIdLong(), 1);
        messageBuilder.append("Total messages recorded in this server: " + totalNum);
        messageBuilder.append("\nTotal messages sent in this server in the past week: ");
        int weekNum = DBUtils.getIntResult("SELECT COUNT(*) FROM messages WHERE serverID = " +
                        guild.getIdLong() + " AND timestamp >= " + (OffsetDateTime.now().toInstant().getEpochSecond() - SECONDS_IN_WEEK),
                1);
        messageBuilder.append(weekNum);
        messageBuilder.sendTo(toSend).queue();
    }

    /**
     * Shows all channels currently tracked in the given server.
     * @param guild
     * @param toSend
     */
    private void showChannels(Guild guild, MessageChannel toSend){
        MessageBuilder messageBuilder = new MessageBuilder();
        try{
            Connection conn = DBUtils.getConnection();
            Statement statement = conn.createStatement();
            ResultSet rs = DBUtils.getResults(conn, statement, "select channelID from channels where serverID = " +
                    guild.getIdLong());
            if(!rs.next()){
                messageBuilder.append("I can't find any tracked channels in this server!");
            } else{
                messageBuilder.append("List of tracked channels in this server:\n");
                do{
                    messageBuilder.append(guild.getTextChannelById(rs.getLong("channelID")));
                    messageBuilder.append("\n");
                } while(rs.next());
            }

            messageBuilder.sendTo(toSend).queue();
            DBUtils.close(rs, statement, conn);
        }
        catch(SQLException e){
            DBUtils.logError(e);
        }
    }

    /**
     * Gets stats about a specific user.
     * @param msg
     * @param toSend
     */
    private void getUserStats(Message msg, MessageChannel toSend) {
        String[] split = msg.getContentStripped().toLowerCase().split(" ");
        MessageBuilder messageBuilder = new MessageBuilder();
        long userId = 0;
        try {
            userId = Long.parseLong(split[1]);
            if (!DBUtils.containsUser(userId)) {
                throw new Exception();
            }
        } catch (Exception e) {
            messageBuilder.append("You need to provide a valid and tracked user ID!");
            messageBuilder.sendTo(toSend).queue();
            return;
        }

        int totalServers = DBUtils.getIntResult("SELECT COUNT(*) FROM members " +
                        "WHERE userID = " + userId,
                1);
        messageBuilder.append("Number of distinct tracked servers that this user is in: " + totalServers);
        int messagesSentOverall = DBUtils.getIntResult("SELECT COUNT(*) FROM messages " +
                        "WHERE userID = " + userId,
                1);
        messageBuilder.append("\nNumber of tracked messages across all servers: " + messagesSentOverall);
        int messagesSentOverallLastWeek = DBUtils.getIntResult("SELECT COUNT(*) FROM messages " +
                        "WHERE userID = " + userId + " AND timestamp >= "
                        + (OffsetDateTime.now().toInstant().getEpochSecond() - SECONDS_IN_WEEK),
                1);
        messageBuilder.append("\nNumber of tracked messages across all servers in the past week: "
                + messagesSentOverallLastWeek);
        if (msg.getGuild().retrieveMemberById(userId).complete() == null) {
            messageBuilder.append("\nThis user is not in the server!");
            messageBuilder.sendTo(toSend).queue();
            return;
        }
        int messagesSentInServer = DBUtils.getIntResult("SELECT COUNT(*) from messages where userID = "
                + userId + " AND serverID = " + msg.getGuild().getIdLong(), 1);
        messageBuilder.append("\nNumber of tracked messages in this server: " + messagesSentInServer);
        int messagesSentInServerLastWeek = DBUtils.getIntResult("SELECT COUNT(*) from messages " +
                        "where userID = " + userId + " AND serverID = " + msg.getGuild().getIdLong()
                        + " AND timestamp >= " + (OffsetDateTime.now().toInstant().getEpochSecond() - SECONDS_IN_WEEK),
                1);
        messageBuilder.append("\nNumber of tracked messages in this server in the past week: "
                + messagesSentInServerLastWeek);
        messageBuilder.sendTo(toSend).queue();
    }

    /**
     * Displays a help message in the requester's DMs.
     * @param msg
     */
    private void sendHelp(Message msg) {
        MessageBuilder mBuilder = new MessageBuilder();

        try {
            PrivateChannel channel = msg.getAuthor().openPrivateChannel().complete(true);
            mBuilder.append("Hey, I'm CryptoBot! I track the activity of certain channels in cryptocurrency servers!\n")
                    .append("List of commands: \n")
                    .append(";help: Sends help through DMs.\n")
                    .append(";add <channel list>: Adds channels to the watchlist of channels to track. " +
                            "(Usable by administrators only)\n")
                    .append(";add-full-server: Adds all channels in the server to the watchlist of channels to track. " +
                            "(Usable by administrators only)\n")
                    .append(";remove <channel list>: Removes channels from the watchlist of channels to track. " +
                            "(Usable by administrators only)\n")
                    .append(";removes-full-server: Removes all channels in the server from the watchlist of channels to track. " +
                            "(Usable by administrators only)\n")
                    .append(";user-stats <userID>: Lists stats about a user.\n")
                    .append(";channel-stats: Lists stats about a channel.\n")
                    .append(";server-stats: Lists stats about a server.\n")
                    .append(";show-channels: Lists all channels tracked in a server.");
            mBuilder.sendTo(channel).queue();
        } catch (RateLimitedException e) {
            mBuilder.append("Oops - an error occurred. Please try again.");
            mBuilder.sendTo(msg.getChannel()).queue();
        }


    }

    /**
     * Adds all channels in a server to the watchlist.
     * @param guild
     * @param currentChannel
     */
    private void addServer(Guild guild, MessageChannel currentChannel) {
        List<TextChannel> channels = guild.getTextChannels();
        MessageBuilder mBuilder = new MessageBuilder();
        if (channels.size() == 0) {
            mBuilder.append("Your server has no text channels!");
            mBuilder.sendTo(currentChannel).queue();
            return;
        }

        for (TextChannel channel : channels) {
            addChannelToDB(channel, mBuilder);
        }
        mBuilder.sendTo(currentChannel).queue();
    }

    /**
     * Removes all channels in the server from the watchlist.
     * @param guild
     * @param currentChannel
     */
    private void removeAllChannels(Guild guild, MessageChannel currentChannel) {
        List<TextChannel> channels = guild.getTextChannels();
        MessageBuilder mBuilder = new MessageBuilder();
        if (channels.size() == 0) {
            mBuilder.append("Your server has no text channels!");
            mBuilder.sendTo(currentChannel).queue();
            return;
        }

        for (TextChannel channel : channels) {
            removeChannelFromDB(channel, mBuilder);
        }
        mBuilder.sendTo(currentChannel).queue();
    }

    /**
     * Adds a list of channels to the watchlist.
     * @param msg
     */
    private void addChannels(Message msg) {
        List<TextChannel> channels = msg.getMentionedChannels();
        MessageChannel currentChannel = msg.getChannel();
        MessageBuilder mBuilder = new MessageBuilder();
        if (channels.size() == 0) {
            mBuilder.append("Please specify at least one channel.");
            mBuilder.sendTo(currentChannel).queue();
            return;
        }

        for (TextChannel channel : channels) {
            addChannelToDB(channel, mBuilder);
        }

        mBuilder.sendTo(currentChannel).queue();
    }

    /**
     * Removes a list of channels from the watchlist.
     * @param msg
     */
    private void removeChannels(Message msg) {
        List<TextChannel> channels = msg.getMentionedChannels();
        MessageChannel currentChannel = msg.getChannel();
        MessageBuilder mBuilder = new MessageBuilder();
        if (channels.size() == 0) {
            mBuilder.append("Please specify at least one channel.");
            mBuilder.sendTo(currentChannel).queue();
            return;
        }
        for (TextChannel channel : channels) {
            removeChannelFromDB(channel, mBuilder);
        }
        mBuilder.sendTo(currentChannel).queue();
    }


    /**
     * Adds a single channel to the database and displays the appropriate message.
     * @param channel
     * @param mBuilder
     */
    private void addChannelToDB(TextChannel channel, MessageBuilder mBuilder) {
        if (DBUtils.containsChannel(channel)) {
            mBuilder.append("Channel ");
            mBuilder.append(channel);
            mBuilder.append(" is already in the watchlist!\n");
            return;
        }
        DBUtils.executePrepared("INSERT INTO channels (serverName, serverID, channelName, channelID)" +
                        "VALUES (?, ?, ?, ?)", channel.getGuild().getName(),
                channel.getGuild().getIdLong(), channel.getName(), channel.getIdLong());
        mBuilder.append("Channel ");
        mBuilder.append(channel);
        mBuilder.append(" has been successfully added to the watchlist!\n");

    }

    /**
     * Removes a single channel from the database and displays the appropriate message.
     * @param channel
     * @param mBuilder
     */
    private void removeChannelFromDB(TextChannel channel, MessageBuilder mBuilder) {
        if (!DBUtils.containsChannel(channel)) {
            mBuilder.append("Channel ");
            mBuilder.append(channel);
            mBuilder.append(" is not in the watchlist!\n");
            return;
        }
        DBUtils.executeLocalOnly("DELETE FROM channels WHERE channelID = " + channel.getIdLong());
        mBuilder.append("Channel ");
        mBuilder.append(channel);
        mBuilder.append(" has been successfully removed from the watchlist!\n");
    }

    /**
     * Utility method to check if a given string is a command of a certain type.
     * @param text
     * @param command
     * @return
     */
    private boolean isCommand(String text, String command) {
        return text.startsWith(command + " ") || text.equals(command);
    }

}
