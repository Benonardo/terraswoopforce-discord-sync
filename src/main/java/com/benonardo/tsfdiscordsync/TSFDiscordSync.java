package com.benonardo.tsfdiscordsync;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class TSFDiscordSync implements DedicatedServerModInitializer {

    public static MinecraftServer server;

    public static String leaderboardWebhookURL;
    public static String highScoreWebhookURL;
    public static String leaderboardMessageID;
    public static int leaderboardCount;

    private static final Logger LOGGER = LoggerFactory.getLogger("TSFDiscordSync");

    @Override
    public void onInitializeServer() {
        reloadConfig();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> TSFDiscordSync.server = server);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
           UpdateLeaderboardCommand.register(dispatcher);
        });
    }

    public static void checkForHighScore(int newScore, String player, Scoreboard scoreboard, ScoreboardObjective objective) {
        Supplier<Stream<ScoreboardPlayerScore>> oldScores = () -> scoreboard.getAllPlayerScores(objective)
                .stream()
                .filter(score -> score.getScore() != 0 && !score.getPlayerName().equals("$par"));
        var higherScore = oldScores
                .get()
                .filter(oldScore -> oldScore.getScore() > newScore)
                .findFirst();
        if (higherScore.isPresent()) return;
        var previousHighScore = oldScores
                .get()
                .max(Comparator.comparingInt(ScoreboardPlayerScore::getScore));
        if (previousHighScore.isEmpty()) {
            sendWebhookMessage(highScoreWebhookURL, player + " has gotten the first record in " + formatTunnel(objective.getName()) + " with a time of `" + formatTime(newScore) + '`');
        } else if (previousHighScore.get().getPlayerName().equals(player)) {
            sendWebhookMessage(highScoreWebhookURL, player + " has beaten his previous record in " + formatTunnel(objective.getName()) + " with a time of `" + formatTime(newScore) + '`');
        } else  {
            sendWebhookMessage(highScoreWebhookURL, player + " has beaten " + previousHighScore.get().getPlayerName() + "'s record in " + formatTunnel(objective.getName()) + " with a time of `" + formatTime(newScore) + '`');
        }
    }

    public static void reloadConfig() {
        try (FileReader reader = new FileReader(FabricLoader.getInstance().getConfigDir().resolve("tsfdiscordsync.properties").toFile())) {
            var properties = new Properties();
            properties.load(reader);
            leaderboardWebhookURL = properties.getProperty("leaderboard_webhook_url");
            leaderboardMessageID = properties.getProperty("leaderboard_message_id");
            highScoreWebhookURL = properties.getProperty("high_score_webhook_url");
            leaderboardCount = Integer.parseInt(properties.getProperty("leaderboard_count", "3"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void updateLeaderboardMessage(Scoreboard scoreboard) {
        if (leaderboardMessageID == null) {
            sendLeaderboardMessage(scoreboard);
        } else {
            var client = HttpClient.newHttpClient();
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(leaderboardWebhookURL + "/messages/" + leaderboardMessageID))
                    .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"content\": \"" + constructLeaderboardMessage(scoreboard) + "\"}"))
                    .header("Content-Type", "application/json")
                    .build();
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, throwable) -> {
                if (throwable != null) {
                    LOGGER.error("Message edit failed", throwable);
                } else if (response.statusCode() > 299) {
                    LOGGER.error("Server responded with status code " + response.statusCode() + " and body " + response.body());
                }
            });
        }
    }

    public static void sendWebhookMessage(String url, String message) {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("POST", HttpRequest.BodyPublishers.ofString("{\"content\": \"" + message + "\"}"))
                .header("Content-Type", "application/json")
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, throwable) -> {
            if (throwable != null) {
                LOGGER.error("Message send failed", throwable);
            } else if (response.statusCode() > 299) {
                LOGGER.error("Server responded with status code " + response.statusCode() + " and body " + response.body());
            }
        });
    }

    private static void sendLeaderboardMessage(Scoreboard scoreboard) {
        sendWebhookMessage(leaderboardWebhookURL, constructLeaderboardMessage(scoreboard));
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

    private static String formatTunnel(String tunnel) {
        return switch (tunnel) {
            case "tunnel1_best" -> "Tunnel 1";
            case "tunnel2_best" -> "Tunnel 2";
            case "tunnel3_best" -> "Tunnel 3";
            case "tunnel4_best" -> "Frozen Tunnel";
            case "tunnelall_best" -> "Full Run";
            default -> throw new IllegalStateException("No tunnel with value: " + tunnel);
        };
    }

    private static List<ScoreboardPlayerScore> getScores(Scoreboard scoreboard, String objective) {
        return scoreboard.getAllPlayerScores(scoreboard.getNullableObjective("tunnel1_best"))
                .stream()
                .filter(score -> score.getScore() != 0 && !score.getPlayerName().equals("$par"))
                .sorted(Comparator.comparingInt(ScoreboardPlayerScore::getScore))
                .limit(leaderboardCount)
                .toList();
    }

    private static void constructMessageForTunnel(StringBuilder builder, Scoreboard scoreboard, String objective) {
        var tunnelScores = getScores(scoreboard, objective);
        builder.append("# ").append(formatTunnel(objective)).append(" Scores\\n");
        for (var i = 0; i < tunnelScores.size(); i++) {
            var score = tunnelScores.get(i);
            builder.append(i + 1).append(". `").append(formatTime(score.getScore())).append("` by ").append(score.getPlayerName()).append("\\n");
        }
    }

    private static String constructLeaderboardMessage(Scoreboard scoreboard) {
        var builder = new StringBuilder();
        constructMessageForTunnel(builder, scoreboard, "tunnel1_best");
        constructMessageForTunnel(builder, scoreboard, "tunnel2_best");
        constructMessageForTunnel(builder, scoreboard, "tunnel3_best");
        constructMessageForTunnel(builder, scoreboard, "tunnel4_best");
        constructMessageForTunnel(builder, scoreboard, "tunnelall_best");

        return builder.toString();
    }

}
