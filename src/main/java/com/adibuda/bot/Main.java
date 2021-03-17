package com.adibuda.bot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

import javax.security.auth.login.LoginException;
import java.util.Scanner;

public class Main{
    public static void main(String[] args) throws LoginException {
        Scanner sc = new Scanner("src/main/java/com/adibuda/bot/token.txt");
        String token = sc.nextLine();
        JDA jda = JDABuilder.createDefault(token).build();
        jda.addEventListener(new TestEvent());
    }
}
