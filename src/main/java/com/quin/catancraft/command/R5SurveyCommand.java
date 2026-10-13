package com.quin.catancraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.quin.catancraft.world.R5ReadOnlyPreflight;
import com.quin.catancraft.world.R5PilotInstaller;
import com.quin.catancraft.world.R5CivicStagedPreview;
import com.quin.catancraft.world.R5CivicConstructionAudit;
import com.quin.catancraft.world.R5CommercialTestInstaller;
import com.quin.catancraft.world.R5LogisticsTestInstaller;
import com.quin.catancraft.world.R5CivicJournalDryRun;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Operator diagnostics only. All R5 edits/purchases remain disabled. */
public final class R5SurveyCommand {
    private R5SurveyCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("catan")
                .then(Commands.literal("r5")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status")
                                .executes(ctx -> status(ctx.getSource())))
                        .then(Commands.literal("apply")
                                .then(Commands.argument("territory", StringArgumentType.word())
                                        .then(Commands.argument("group", StringArgumentType.word())
                                                .then(Commands.literal("confirm")
                                                        .executes(ctx -> pilotResult(ctx.getSource(),
                                                                R5PilotInstaller.apply(ctx.getSource().getServer(),
                                                                        StringArgumentType.getString(ctx,"territory"),
                                                                        StringArgumentType.getString(ctx,"group"))))))))
                        .then(Commands.literal("rollback")
                                .then(Commands.argument("territory", StringArgumentType.word())
                                        .then(Commands.argument("group", StringArgumentType.word())
                                                .then(Commands.literal("confirm")
                                                        .executes(ctx -> pilotResult(ctx.getSource(),
                                                                R5PilotInstaller.rollback(ctx.getSource().getServer(),
                                                                        StringArgumentType.getString(ctx,"territory"),
                                                                        StringArgumentType.getString(ctx,"group"))))))))
                        .then(Commands.literal("plan")
                                .then(Commands.argument("territory", StringArgumentType.word())
                                        .then(Commands.argument("kind", StringArgumentType.word())
                                                .executes(ctx -> stagedPlan(ctx.getSource(),
                                                        StringArgumentType.getString(ctx,"territory"),
                                                        StringArgumentType.getString(ctx,"kind"))))))
                        .then(Commands.literal("constructioncheck")
                                .then(Commands.argument("territory", StringArgumentType.word())
                                        .then(Commands.argument("kind", StringArgumentType.word())
                                                .executes(ctx -> constructionAudit(ctx.getSource(),
                                                        StringArgumentType.getString(ctx,"territory"),
                                                        StringArgumentType.getString(ctx,"kind"))))))
                        .then(Commands.literal("commercialpilot")
                                .then(Commands.literal("diagnose")
                                        .executes(ctx -> commercialDiagnose(ctx.getSource())))
                                .then(Commands.literal("apply")
                                        .then(Commands.literal("confirm")
                                                .executes(ctx -> commercialPilot(ctx.getSource(),true))))
                                .then(Commands.literal("rollback")
                                        .then(Commands.literal("confirm")
                                                .executes(ctx -> commercialPilot(ctx.getSource(),false)))))
                        .then(Commands.literal("logisticspilot")
                                .then(Commands.literal("diagnose")
                                        .executes(ctx -> logisticsDiagnose(ctx.getSource())))
                                .then(Commands.literal("apply")
                                        .then(Commands.literal("confirm")
                                                .executes(ctx -> logisticsPilot(ctx.getSource(),true))))
                                .then(Commands.literal("rollback")
                                        .then(Commands.literal("confirm")
                                                .executes(ctx -> logisticsPilot(ctx.getSource(),false)))))
                        .then(Commands.literal("journalplan")
                                .then(Commands.argument("territory", StringArgumentType.word())
                                        .then(Commands.argument("kind", StringArgumentType.word())
                                                .executes(ctx -> journalModel(ctx.getSource(),
                                                        StringArgumentType.getString(ctx,"territory"),
                                                        StringArgumentType.getString(ctx,"kind"))))))
                        .then(Commands.literal("city")
                                .then(Commands.argument("territory", StringArgumentType.word())
                                        .executes(ctx -> overview(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "territory")))))
                        .then(Commands.literal("civic")
                                .then(Commands.argument("territory", StringArgumentType.word())
                                        .then(Commands.argument("kind", StringArgumentType.word())
                                                .executes(ctx -> civic(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "territory"),
                                                        StringArgumentType.getString(ctx, "kind"))))))
                        .then(Commands.literal("group")
                                .then(Commands.argument("territory", StringArgumentType.word())
                                        .then(Commands.argument("group", StringArgumentType.word())
                                                .executes(ctx -> group(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "territory"),
                                                        StringArgumentType.getString(ctx, "group"))))))));
    }

    private static int logisticsDiagnose(CommandSourceStack source) {
        R5LogisticsTestInstaller.Result result=
                R5LogisticsTestInstaller.diagnose(source.getServer());
        source.sendSuccess(() -> Component.literal(result.text()),false);
        return result.success()?1:0;
    }

    private static int logisticsPilot(CommandSourceStack source,boolean apply) {
        R5LogisticsTestInstaller.Result result=apply
                ?R5LogisticsTestInstaller.apply(source.getServer())
                :R5LogisticsTestInstaller.rollback(source.getServer());
        if(result.success()) {
            source.sendSuccess(() -> Component.literal(result.text()),true);
            return 1;
        }
        source.sendFailure(Component.literal(result.text()));
        return 0;
    }

    private static int commercialDiagnose(CommandSourceStack source) {
        R5CommercialTestInstaller.Result result=
                R5CommercialTestInstaller.diagnose(source.getServer());
        source.sendSuccess(() -> Component.literal(result.text()),false);
        return result.success()?1:0;
    }

    private static int commercialPilot(CommandSourceStack source,boolean apply) {
        R5CommercialTestInstaller.Result result=apply
                ?R5CommercialTestInstaller.apply(source.getServer())
                :R5CommercialTestInstaller.rollback(source.getServer());
        if(result.success()) {
            source.sendSuccess(() -> Component.literal(result.text()),true);
            return 1;
        }
        source.sendFailure(Component.literal(result.text()));
        return 0;
    }

    private static int journalModel(CommandSourceStack source,String city,String kind) {
        R5CivicJournalDryRun.Result plan=R5CivicJournalDryRun.preview(
                source.getServer(),city,kind);
        source.sendSuccess(() -> Component.literal(plan.headline()),false);
        for(String message:plan.messages())
            source.sendSuccess(() -> Component.literal("  "+message),false);
        return plan.modeled()?1:0;
    }

    private static int constructionAudit(CommandSourceStack source,String city,String kind) {
        R5CivicConstructionAudit.Report report=R5CivicConstructionAudit.inspect(
                source.getServer(),city,kind);
        source.sendSuccess(() -> Component.literal(report.headline()),false);
        for(String line:report.details())
            source.sendSuccess(() -> Component.literal("  "+line),false);
        // A read-only report is never a world-building approval.
        return report.staticReady()?1:0;
    }

    private static int stagedPlan(CommandSourceStack source,String city,String kind) {
        R5CivicStagedPreview.Report plan=R5CivicStagedPreview.preview(
                source.getServer(),city,kind);
        source.sendSuccess(() -> Component.literal(plan.headline()),false);
        for(String line:plan.details())
            source.sendSuccess(() -> Component.literal("  "+line),false);
        return plan.matched()?1:0;
    }

    private static int pilotResult(CommandSourceStack source,R5PilotInstaller.Result result) {
        if(result.success()) {
            source.sendSuccess(() -> Component.literal(result.message()),true);
            return 1;
        }
        source.sendFailure(Component.literal(result.message()));
        return 0;
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "R5-2: Civic builds and house migration DISABLED; " +
                "only three opt-in test-world streetscape groups can be applied. READ-ONLY /catan r5 plan a commercial|logistics simulates prerequisite writes. " +
                "Use /catan r5 city a, /catan r5 civic a commercial, or " +
                "/catan r5 group a front_public_park."),false);
        return 1;
    }

    private static int overview(CommandSourceStack source, String territory) {
        try {
            R5ReadOnlyPreflight.Overview summary=R5ReadOnlyPreflight.overview(territory);
            source.sendSuccess(() -> Component.literal(
                    "R5 " + territory.toUpperCase() + ": " + summary.groups() +
                    " streetscape groups, " + summary.civicSlots() +
                    " civic proposals. NO PURCHASE OR PLACEMENT AUTHORIZED."),false);
            List<String> names=summary.groupNames();
            int first=Math.min(names.size(),12);
            source.sendSuccess(() -> Component.literal(
                    "Example group IDs: " + String.join(", ",names.subList(0,first)) +
                    (names.size()>first?" (additional groups in R5 handoff)":"")),false);
            return 1;
        } catch(Exception error) {
            source.sendFailure(Component.literal(
                    "R5 overview refused: "+error.getMessage()));
            return 0;
        }
    }

    private static int civic(CommandSourceStack source, String territory, String kind) {
        return report(source,R5ReadOnlyPreflight.civic(
                source.getServer(),territory,kind));
    }

    private static int group(CommandSourceStack source, String territory, String groupId) {
        return report(source,R5ReadOnlyPreflight.group(
                source.getServer(),territory,groupId));
    }

    private static int report(CommandSourceStack source, R5ReadOnlyPreflight.Report report) {
        source.sendSuccess(() -> Component.literal(report.headline()),false);
        for (String issue : report.issues())
            source.sendSuccess(() -> Component.literal("  REVIEW: "+issue),false);
        if (report.matched())
            source.sendSuccess(() -> Component.literal(
                    "All sampled/live expected-before cells currently match. " +
                    "R5 world changes remain disabled pending saved-state migration, " +
                    "terrain/entity checks and durable rollback."),false);
        return report.matched()?1:0;
    }
}
