package com.benonardo.tsfdiscordsync.mixin;

import com.benonardo.tsfdiscordsync.TSFDiscordSync;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScoreboardPlayerScore.class)
public class ScoreboardPlayerScoreMixin {

    @Shadow
    private @Final Scoreboard scoreboard;

    @Shadow
    @Nullable
    private @Final ScoreboardObjective objective;

    @Shadow @Final private String playerName;

    @Inject(method = "setScore", at = @At("HEAD"))
    private void checkForRecordOnScoreChange(int score, CallbackInfo ci) {
        if (TSFDiscordSync.server == null || score == 0 || this.objective == null || !(this.objective.getName().equals("tunnel1_best") || this.objective.getName().equals("tunnel2_best") || this.objective.getName().equals("tunnel3_best") || this.objective.getName().equals("tunnel4_best") || this.objective.getName().equals("tunnelall_best"))) return;
        TSFDiscordSync.checkForHighScore(score, this.playerName, this.scoreboard, this.objective);
    }

    @Inject(method = "setScore", at = @At("TAIL"))
    private void updateLeaderboardOnScoreChange(int score, CallbackInfo ci) {
        if (TSFDiscordSync.server == null || score == 0 || this.objective == null || !(this.objective.getName().equals("tunnel1_best") || this.objective.getName().equals("tunnel2_best") || this.objective.getName().equals("tunnel3_best") || this.objective.getName().equals("tunnel4_best") || this.objective.getName().equals("tunnelall_best"))) return;
        TSFDiscordSync.updateLeaderboardMessage(this.scoreboard);
    }

}
