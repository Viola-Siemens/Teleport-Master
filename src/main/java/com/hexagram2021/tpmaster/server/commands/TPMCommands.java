package com.hexagram2021.tpmaster.server.commands;

import com.hexagram2021.tpmaster.server.config.TPMServerConfig;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Random;

public class TPMCommands {
	public static LiteralArgumentBuilder<CommandSourceStack> register() {
		return Commands.literal("tpmaster").then(
				Commands.literal("accept").requires((stack) -> stack.hasPermission(TPMServerConfig.ACCEPT_DENY_PERMISSION_LEVEL.get()))
						.executes(context -> accept(context.getSource().getEntityOrException()))
		).then(
				Commands.literal("deny").requires((stack) -> stack.hasPermission(TPMServerConfig.ACCEPT_DENY_PERMISSION_LEVEL.get()))
						.executes(context -> deny(context.getSource().getEntityOrException()))
		).then(
				Commands.literal("away").requires((stack) -> stack.hasPermission(TPMServerConfig.AWAY_PERMISSION_LEVEL.get()))
						.executes(context -> away(context.getSource(), context.getSource().getEntityOrException(), 0, true, null))
						.then(
								Commands.argument("distance", IntegerArgumentType.integer(0, 10000))
										.executes(context -> away(context.getSource(), context.getSource().getEntityOrException(), context.getArgument("distance", Integer.class), true, null))
										.then(
												Commands.argument("mustOnLand", BoolArgumentType.bool())
														.executes(context -> away(context.getSource(), context.getSource().getEntityOrException(), context.getArgument("distance", Integer.class), context.getArgument("mustOnLand", Boolean.class), null))
										)
						)
		).then(
				Commands.literal("request").requires((stack) -> stack.hasPermission(TPMServerConfig.REQUEST_PERMISSION_LEVEL.get())).then(
						Commands.argument("target", EntityArgument.entity()).executes(context -> request(context.getSource().getEntityOrException(), EntityArgument.getEntity(context, "target")))
				)
		);
	}

	private static int accept(Entity entity) {
		//TODO
		TeleportCommand.performTeleport();

		entity.sendMessage(new TextComponent(new TranslatableComponent("commands.tpmaster.accept.success", ).getString()), Util.NIL_UUID);
		return Command.SINGLE_SUCCESS;
	}

	private static int deny(Entity entity) {
		//TODO


		entity.sendMessage(new TextComponent(new TranslatableComponent("commands.tpmaster.deny.success", ).getString()), Util.NIL_UUID);
		return Command.SINGLE_SUCCESS;
	}

	private static final DynamicCommandExceptionType INVALID_AWAY_DISTANCE_PARAMETER = new DynamicCommandExceptionType(
			(d) -> () -> new TranslatableComponent("commands.tpmaster.away.invalid.distance", d).getString()
	);

	private static final SimpleCommandExceptionType CANNOT_FIND_POSITION = new SimpleCommandExceptionType(
			() -> new TranslatableComponent("commands.tpmaster.away.failed.no_position").getString()
	);

	private static int away(CommandSourceStack stack, Entity entity, int distance, boolean mustOnLand, @Nullable TeleportCommand.LookAt lookAt) throws CommandSyntaxException {
		if(distance == 0) {
			distance = entity.level.getRandom().nextInt(600) + 1000;
		} else if(distance < 0 || distance > 10000) {
			throw INVALID_AWAY_DISTANCE_PARAMETER.create(distance);
		}

		boolean flag = false;
		Random random = entity.level.getRandom();
		double x = entity.getX();
		double y = entity.getY();
		double z = entity.getZ();
		for(int i = 0; i < TPMServerConfig.AWAY_TRY_COUNT.get(); ++i) {
			double phi = random.nextDouble(2.0 * Math.acos(-1.0));
			x = entity.getX() + distance * Math.cos(phi) + random.nextDouble(TPMServerConfig.AWAY_NOISE_BOUND.get() * distance);
			z = entity.getZ() + distance * Math.sin(phi) + random.nextDouble(TPMServerConfig.AWAY_NOISE_BOUND.get() * distance);
			BlockPos blockPos = new BlockPos(x, 256.0, z);
			Biome biome = entity.level.getBiome(blockPos).value();
			boolean conti = false;
			if(mustOnLand) {
				for (String ocean : TPMServerConfig.OCEAN_BIOME_KEYS.get()) {
					ResourceLocation biomeId = biome.getRegistryName();
					if (biomeId != null && biomeId.toString().equals(ocean)) {
						conti = true;
						break;
					}
				}
			}
			if(!conti) {
				flag = true;
				y = entity.level.getBlockFloorHeight(blockPos);
				break;
			}
		}
		if(!flag) {
			throw CANNOT_FIND_POSITION.create();
		}
		TeleportCommand.performTeleport(stack, entity, (ServerLevel)entity.level, x, y, z, EnumSet.noneOf(ClientboundPlayerPositionPacket.RelativeArgument.class), entity.getYRot(), entity.getXRot(), lookAt);

		entity.sendMessage(new TextComponent(new TranslatableComponent("commands.tpmaster.away.success", distance).getString()), Util.NIL_UUID);
		return Command.SINGLE_SUCCESS;
	}

	private static int request(Entity entity, Entity target) {
		//TODO

	}
}
