package com.benonardo.tsfdiscordsync;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.server.MinecraftServer;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

public class TSFDiscordSync implements DedicatedServerModInitializer {

    public static MinecraftServer server;

    public static String webhookURL;
    public static String messageID;

    @Override
    public void onInitializeServer() {
        try (FileReader reader = new FileReader(FabricLoader.getInstance().getConfigDir().resolve("tsfdiscordsync.properties").toFile())) {
            var properties = new Properties();
            properties.load(reader);
            webhookURL = properties.getProperty("webhook_url");
            messageID = properties.getProperty("message_id");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ServerLifecycleEvents.SERVER_STARTED.register(server -> TSFDiscordSync.server = server);

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
           UpdateLeaderboardCommand.register(dispatcher);
        });
    }

    public static void updateMessage(Scoreboard scoreboard) {
        if (messageID == null) {
            sendMessage(scoreboard);
        } else {
            try {
                var client = HttpClient.newHttpClient();
                var request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookURL + "/messages/" + messageID))
                        .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"content\": \"" + getMessage(scoreboard) + "\"}"))
                        .header("Content-Type", "application/json")
                        .build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() > 299) {
                    System.out.println(response.body());
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static void sendMessage(Scoreboard scoreboard) {
        try {
            var client = HttpClient.newHttpClient();
            var request = HttpRequest.newBuilder()
                            .uri(URI.create(webhookURL))
                            .method("POST", HttpRequest.BodyPublishers.ofString("{\"content\": \"" + getMessage(scoreboard) + "\"}"))
                            .header("Content-Type", "application/json")
                            .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() > 299) {
                System.out.println(response.body());
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static String formatTime(int score) {
        var builder = new StringBuilder();
        if (score / 60000 < 10) builder.append(0);
        builder.append(score / 60000);
        score -= (score / 60000) * 60000;
        builder.append(":");
        if (score / 1000 < 10) builder.append(0);
        builder.append(score / 1000);
        score -= (score / 1000) * 1000;
        builder.append(".");
        if (score < 100) builder.append(0);
        if (score < 10) builder.append(0);
        builder.append(score);
        return builder.toString();
    }

    private static String getMessage(Scoreboard scoreboard) {
        List<ScoreboardPlayerScore> tunnel1Scores = new ArrayList<>(scoreboard.getAllPlayerScores(scoreboard.getObjective("tunnel1_best")));
        List<ScoreboardPlayerScore> tunnel2Scores = new ArrayList<>(scoreboard.getAllPlayerScores(scoreboard.getObjective("tunnel2_best")));
        List<ScoreboardPlayerScore> tunnel3Scores = new ArrayList<>(scoreboard.getAllPlayerScores(scoreboard.getObjective("tunnel3_best")));
        List<ScoreboardPlayerScore> tunnel4Scores = new ArrayList<>(scoreboard.getAllPlayerScores(scoreboard.getObjective("tunnel4_best")));
        List<ScoreboardPlayerScore> tunnelallScores = new ArrayList<>(scoreboard.getAllPlayerScores(scoreboard.getObjective("tunnelall_best")));

        StringBuilder builder = new StringBuilder();
        builder.append("**Tunnel 1 Scores**\\n");
        tunnel1Scores = tunnel1Scores.stream().filter(score -> score.getScore() != 0 && !score.getPlayerName().equals("$par")).sorted(Comparator.comparingInt(ScoreboardPlayerScore::getScore)).collect(Collectors.toList());
        if (tunnel1Scores.size() > 0) builder.append("1: `").append(formatTime(tunnel1Scores.get(0).getScore())).append("` by ").append(tunnel1Scores.get(0).getPlayerName()).append("\\n");
        if (tunnel1Scores.size() > 1) builder.append("2: `").append(formatTime(tunnel1Scores.get(1).getScore())).append("` by ").append(tunnel1Scores.get(1).getPlayerName()).append("\\n");
        if (tunnel1Scores.size() > 2) builder.append("3: `").append(formatTime(tunnel1Scores.get(2).getScore())).append("` by ").append(tunnel1Scores.get(2).getPlayerName()).append("\\n");
        builder.append("**Tunnel 2 Scores**\\n");
        tunnel2Scores = tunnel2Scores.stream().filter(score -> score.getScore() != 0 && !score.getPlayerName().equals("$par")).sorted(Comparator.comparingInt(ScoreboardPlayerScore::getScore)).collect(Collectors.toList());
        if (tunnel2Scores.size() > 0) builder.append("1: `").append(formatTime(tunnel2Scores.get(0).getScore())).append("` by ").append(tunnel2Scores.get(0).getPlayerName()).append("\\n");
        if (tunnel2Scores.size() > 1) builder.append("2: `").append(formatTime(tunnel2Scores.get(1).getScore())).append("` by ").append(tunnel2Scores.get(1).getPlayerName()).append("\\n");
        if (tunnel2Scores.size() > 2) builder.append("3: `").append(formatTime(tunnel2Scores.get(2).getScore())).append("` by ").append(tunnel2Scores.get(2).getPlayerName()).append("\\n");
        builder.append("**Tunnel 3 Scores**\\n");
        tunnel3Scores = tunnel3Scores.stream().filter(score -> score.getScore() != 0 && !score.getPlayerName().equals("$par")).sorted(Comparator.comparingInt(ScoreboardPlayerScore::getScore)).collect(Collectors.toList());
        if (tunnel3Scores.size() > 0) builder.append("1: `").append(formatTime(tunnel3Scores.get(0).getScore())).append("` by ").append(tunnel3Scores.get(0).getPlayerName()).append("\\n");
        if (tunnel3Scores.size() > 1) builder.append("2: `").append(formatTime(tunnel3Scores.get(1).getScore())).append("` by ").append(tunnel3Scores.get(1).getPlayerName()).append("\\n");
        if (tunnel3Scores.size() > 2) builder.append("3: `").append(formatTime(tunnel3Scores.get(2).getScore())).append("` by ").append(tunnel3Scores.get(2).getPlayerName()).append("\\n");
        builder.append("**Frozen Tunnel Scores**\\n");
        tunnel4Scores = tunnel4Scores.stream().filter(score -> score.getScore() != 0 && !score.getPlayerName().equals("$par")).sorted(Comparator.comparingInt(ScoreboardPlayerScore::getScore)).collect(Collectors.toList());
        if (tunnel4Scores.size() > 0) builder.append("1: `").append(formatTime(tunnel4Scores.get(0).getScore())).append("` by ").append(tunnel4Scores.get(0).getPlayerName()).append("\\n");
        if (tunnel4Scores.size() > 1) builder.append("2: `").append(formatTime(tunnel4Scores.get(1).getScore())).append("` by ").append(tunnel4Scores.get(1).getPlayerName()).append("\\n");
        if (tunnel4Scores.size() > 2) builder.append("3: `").append(formatTime(tunnel4Scores.get(2).getScore())).append("` by ").append(tunnel4Scores.get(2).getPlayerName()).append("\\n");
        builder.append("**Full Run Scores**\\n");
        tunnelallScores = tunnelallScores.stream().filter(score -> score.getScore() != 0 && !score.getPlayerName().equals("$par")).sorted(Comparator.comparingInt(ScoreboardPlayerScore::getScore)).collect(Collectors.toList());
        if (tunnelallScores.size() > 0) builder.append("1: `").append(formatTime(tunnelallScores.get(0).getScore())).append("` by ").append(tunnelallScores.get(0).getPlayerName()).append("\\n");
        if (tunnelallScores.size() > 1) builder.append("2: `").append(formatTime(tunnelallScores.get(1).getScore())).append("` by ").append(tunnelallScores.get(1).getPlayerName()).append("\\n");
        if (tunnelallScores.size() > 2) builder.append("3: `").append(formatTime(tunnelallScores.get(2).getScore())).append("` by ").append(tunnelallScores.get(2).getPlayerName()).append("\\n");

        return builder.toString();
    }

}
