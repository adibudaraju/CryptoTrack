package com.electriccapital;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import org.apache.log4j.*;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.sql.*;
import java.util.Scanner;

public class Main {

    /**
     * Main method, logs bot in and checks if database exists - if not, creates it. Adds listeners and sets status.
     * @param args
     * @throws LoginException
     * @throws SQLException
     * @throws FileNotFoundException
     */
    public static void main(String[] args) throws LoginException, SQLException, FileNotFoundException {
        BasicConfigurator.configure();
        // token file
        Scanner sc = new Scanner(new File("src/main/java/com/electriccapital/token.txt"));
        String token = sc.nextLine();
        System.out.println(token);
        JDA jda = JDABuilder.createDefault(token).build();
        boolean hasTable = DBUtils.executeLocalOnly("SELECT * from members");
        if (!hasTable) {
            createDatabase();
        }
        jda.addEventListener(new MessageEvent());
        jda.getPresence().setActivity(Activity.playing("Type ;help for help!"));
    }

    /**
     * Runs some simple SQL queries to create the DB.
     * @throws SQLException
     */
    private static void createDatabase() throws SQLException{
        String q1, q2, q3;
         /* messages table stores server, channel, user, and message info.
         channels table stores server and channel info.
         members table stores server and member info. separate entries are stores for the same Discord user in
         different servers. */
        q1 = "CREATE TABLE messages (serverName VARCHAR(25), serverID BIGINT, channelName VARCHAR(25)," +
                " channelID BIGINT, userName VARCHAR(25), userNickname VARCHAR(25), userID BIGINT, content VARCHAR(100), " +
                "messageID BIGINT, timestamp BIGINT)";
        q2 = "CREATE TABLE channels (serverName VARCHAR(25), serverID BIGINT," +
                " channelName VARCHAR(25), channelID BIGINT)";
        q3 = "CREATE TABLE members (serverName VARCHAR(25), serverID BIGINT," +
                " userName VARCHAR(25), userNickname VARCHAR(25), userID BIGINT, messagesSent BIGINT)";
        DBUtils.executeLocalOnly(q1, q2, q3);
    }

}
