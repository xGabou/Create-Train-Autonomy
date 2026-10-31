package net.Gabou.createtrainmining.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.Gabou.createtrainmining.block.TrainAutomationControllerBlockEntity;
import net.Gabou.createtrainmining.core.NamedControllerSavedData;
import net.Gabou.createtrainmining.core.TrainController;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import static net.minecraft.commands.Commands.*;

public final class ControllerCommands {
    private ControllerCommands() {}

    public static void register(RegisterCommandsEvent event) {
        var station = argument("station", StringArgumentType.string())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        NamedControllerSavedData.get(ctx.getSource().getServer()).stations().keySet()
                                .stream().map(StringArgumentType::escapeIfRequired), builder));
        for (String action : new String[]{"status", "start", "retry", "cancel", "stop", "return"})
            station.then(literal(action).executes(ctx -> control(ctx, action)));
        event.getDispatcher().register(literal("trainautomation")
                .requires(source -> source.hasPermission(2))
                .then(literal("name").then(argument("name", StringArgumentType.string())
                        .executes(ctx -> name(ctx, false))
                        .then(literal("at").then(argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> name(ctx, true))))))
                .then(literal("list").executes(ControllerCommands::list))
                .then(literal("station").then(station)));
    }

    private static int name(CommandContext<CommandSourceStack> ctx, boolean atPosition)
            throws CommandSyntaxException {
        var source = ctx.getSource();
        var level = source.getLevel();
        net.minecraft.core.BlockPos pos;
        if (atPosition) pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        else {
            var hit = source.getPlayerOrException().pick(8, 0, false);
            if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK)
                throw error("Look at a Train Automation Controller within 8 blocks");
            pos = blockHit.getBlockPos();
        }
        if (!(level.getBlockEntity(pos) instanceof TrainAutomationControllerBlockEntity entity))
            throw error("The targeted block is not a Train Automation Controller");
        String name = StringArgumentType.getString(ctx, "name");
        try {
            NamedControllerSavedData.get(source.getServer()).name(name, level, entity);
        } catch (RuntimeException e) {
            throw error(e.getMessage());
        }
        source.sendSuccess(() -> Component.literal("Controller named " + name + " at "
                + pos.toShortString() + " (" + level.dimension().location() + ")"), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var stations = NamedControllerSavedData.get(source.getServer()).stations();
        if (stations.isEmpty()) source.sendSuccess(() -> Component.literal("No named controllers"), false);
        stations.forEach((name, address) -> source.sendSuccess(() -> Component.literal(
                name + ": " + address.dimension().location() + " " + address.pos().toShortString()), false));
        return stations.size();
    }

    private static int control(CommandContext<CommandSourceStack> ctx, String action)
            throws CommandSyntaxException {
        var source = ctx.getSource();
        String name = StringArgumentType.getString(ctx, "station");
        try {
            TrainController controller = NamedControllerSavedData.get(source.getServer())
                    .resolve(source.getServer(), name).controller();
            switch (action) {
                case "start" -> controller.start();
                case "retry" -> controller.retry();
                case "cancel", "stop" -> controller.stop();
                case "return" -> controller.forceReturn();
                default -> {}
            }
            String train = controller.getSelectedTrainId() == null ? "none"
                    : controller.getSelectedTrainId().toString();
            String message = name + ": " + controller.getStatus() + " | " + controller.getControlMode()
                    + " | enabled=" + controller.isEnabled() + " | train=" + train
                    + (controller.getLastError().isEmpty() ? "" : " | " + controller.getLastError());
            source.sendSuccess(() -> Component.literal(message), !action.equals("status"));
            return 1;
        } catch (RuntimeException e) {
            throw error(e.getMessage());
        }
    }

    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Component.literal(
                message == null ? "Controller command failed" : message)).create();
    }
}
