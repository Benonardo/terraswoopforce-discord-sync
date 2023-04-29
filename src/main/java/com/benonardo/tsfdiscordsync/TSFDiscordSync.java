package com.benonardo.tsfdiscordsync;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.server.MinecraftServer;

import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Comparator;
import java.util.Properties;

public class TSFDiscordSync implements DedicatedServerModInitializer {

    public static MinecraftServer server;

    public static String webhookURL;
    public static String messageID;
    public static int leaderboardCount;

    @Override
    public void onInitializeServer() {
        reloadConfig();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> TSFDiscordSync.server = server);

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
           UpdateLeaderboardCommand.register(dispatcher);
        });
    }

    public static void reloadConfig() {
        try (FileReader reader = new FileReader(FabricLoader.getInstance().getConfigDir().resolve("tsfdiscordsync.properties").toFile())) {
            var properties = new Properties();
            properties.load(reader);
            webhookURL = properties.getProperty("webhook_url");
            messageID = properties.getProperty("message_id");
            leaderboardCount = Integer.parseInt(properties.getProperty("leaderboard_count", "3"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        var tunnel1Scores = scoreboard.getAllPlayerScores(scoreboard.getObjective("tunnel1_best")).stream().filter(score -> score.getScore() != 0 && !score.getPlayerName().equals("$par")).sorted(Comparator.comparingInt(ScoreboardPlayerScore::getScore)).limit(leaderboardCount).toList();
        var tunnel2Scores = scoreboard.getAllPlayerScores(scoreboard.getObjective("tunnel2_best")).stream().filter(score -> score.getScore() != 0 && !score.getPlayerName().equals("$par")).sorted(Comparator.comparingInt(ScoreboardPlayerScore::getScore)).limit(leaderboardCount).toList();
        var tunnel3Scores = scoreboard.getAllPlayerScores(scoreboard.getObjective("tunnel3_best")).stream().filter(score -> score.getScore() != 0 && !score.getPlayerName().equals("$par")).sorted(Comparator.comparingInt(ScoreboardPlayerScore::getScore)).limit(leaderboardCount).toList();
        var tunnel4Scores = scoreboard.getAllPlayerScores(scoreboard.getObjective("tunnel4_best")).stream().filter(score -> score.getScore() != 0 && !score.getPlayerName().equals("$par")).sorted(Comparator.comparingInt(ScoreboardPlayerScore::getScore)).limit(leaderboardCount).toList();
        var tunnelallScores = scoreboard.getAllPlayerScores(scoreboard.getObjective("tunnelall_best")).stream().filter(score -> score.getScore() != 0 && !score.getPlayerName().equals("$par")).sorted(Comparator.comparingInt(ScoreboardPlayerScore::getScore)).limit(leaderboardCount).toList();
        StringBuilder builder = new StringBuilder();
        builder.append("**Tunnel 1 Scores**\\n");
        for (var i = 0; i < tunnel1Scores.size(); i++) {
            var score = tunnel1Scores.get(i);
            builder.append(i + 1).append(": `").append(formatTime(score.getScore())).append("` by ").append(score.getPlayerName()).append("\\n");
        }
        builder.append("**Tunnel 2 Scores**\\n");
        for (var i = 0; i < tunnel2Scores.size(); i++) {
            var score = tunnel2Scores.get(i);
            builder.append(i + 1).append(": `").append(formatTime(score.getScore())).append("` by ").append(score.getPlayerName()).append("\\n");
        }
        builder.append("**Tunnel 3 Scores**\\n");
        for (var i = 0; i < tunnel3Scores.size(); i++) {
            var score = tunnel3Scores.get(i);
            builder.append(i + 1).append(": `").append(formatTime(score.getScore())).append("` by ").append(score.getPlayerName()).append("\\n");
        }
        builder.append("**Frozen Tunnel Scores**\\n");
        for (var i = 0; i < tunnel4Scores.size(); i++) {
            var score = tunnel4Scores.get(i);
            builder.append(i + 1).append(": `").append(formatTime(score.getScore())).append("` by ").append(score.getPlayerName()).append("\\n");
        }
        builder.append("**Full Run Scores**\\n");
        for (var i = 0; i < tunnelallScores.size(); i++) {
            var score = tunnelallScores.get(i);
            builder.append(i + 1).append(": `").append(formatTime(score.getScore())).append("` by ").append(score.getPlayerName()).append("\\n");
        }

        return builder.toString();
    }

}
