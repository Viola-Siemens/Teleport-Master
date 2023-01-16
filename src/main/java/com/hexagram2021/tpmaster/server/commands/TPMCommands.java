package com.hexagram2021.tpmaster.server.commands;

import com.hexagram2021.tpmaster.server.config.TPMServerConfig;
import com.hexagram2021.tpmaster.server.util.ITeleportable;
import com.hexagram2021.tpmaster.server.util.LevelUtils;
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
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.biome.Biome;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Random;

public class TPMCommands {
	public static LiteralArgumentBuilder<CommandSourceStack> register() {
		return Commands.literal("tpmaster").then(
				Commands.literal("accept").requires((stack) -> stack.hasPermission(TPMServerConfig.ACCEPT_DENY_PERMISSION_LEVEL.get()))
						.executes(context -> accept(context.getSource(), context.getSource().getEntityOrException()))
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
				Commands.literal("request").requires((stack) -> stack.hasPermission(TPMServerConfig.REQUEST_PERMISSION_LEVEL.get()))
						.then(
								Commands.argument("target", EntityArgument.entity())
										.executes(context -> request(context.getSource(), context.getSource().getEntityOrException(), EntityArgument.getEntity(context, "target"), ITeleportable.RequestType.ASK))
										.then(
												Commands.literal("ask")
														.executes(context -> request(context.getSource(), context.getSource().getEntityOrException(), EntityArgument.getEntity(context, "target"), ITeleportable.RequestType.ASK))
										)
										.then(
												Commands.literal("invite")
														.executes(context -> request(context.getSource(), context.getSource().getEntityOrException(), EntityArgument.getEntity(context, "target"), ITeleportable.RequestType.INVITE))
										)
						)
		).then(
				Commands.literal("spawn").requires((stack) -> stack.hasPermission(TPMServerConfig.SPAWN_PERMISSION_LEVEL.get()))
						.executes(context -> spawn(context.getSource(), context.getSource().getEntityOrException()))
		).then(
				Commands.literal("help").requires((stack) -> stack.hasPermission(TPMServerConfig.HELP_PERMISSION_LEVEL.get()))
						.executes(context -> help(context.getSource().getEntityOrException()))
		);
	}

	private static final SimpleCommandExceptionType NO_NEED_TO_ACCEPT = new SimpleCommandExceptionType(
			() -> new TranslatableComponent("commands.tpmaster.accept.failed.no_request").getString()
	);
	private static final SimpleCommandExceptionType NO_NEED_TO_DENY = new SimpleCommandExceptionType(
			() -> new TranslatableComponent("commands.tpmaster.deny.failed.no_request").getString()
	);
	private static final DynamicCommandExceptionType TARGET_UNHANDLED_RESERVATION = new DynamicCommandExceptionType(
			(name) -> () -> new TranslatableComponent("commands.tpmaster.request.failed.reserved", name).getString()
	);

	private static final DynamicCommandExceptionType INVALID_AWAY_DISTANCE_PARAMETER = new DynamicCommandExceptionType(
			(d) -> () -> new TranslatableComponent("commands.tpmaster.away.invalid.distance", d).getString()
	);
	private static final SimpleCommandExceptionType CANNOT_FIND_POSITION = new SimpleCommandExceptionType(
			() -> new TranslatableComponent("commands.tpmaster.away.failed.no_position").getString()
	);

	private static final DynamicCommandExceptionType COOL_DOWN_AWAY = new DynamicCommandExceptionType(
			(d) -> () -> new TranslatableComponent("commands.tpmaster.away.failed.cool_down", d).getString()
	);
	private static final DynamicCommandExceptionType COOL_DOWN_REQUEST = new DynamicCommandExceptionType(
			(d) -> () -> new TranslatableComponent("commands.tpmaster.request.failed.cool_down", d).getString()
	);

	private static int accept(CommandSourceStack stack, Entity entity) throws CommandSyntaxException {
		if(entity instanceof ITeleportable teleportable) {
			Entity requester = teleportable.getTeleportMasterRequester();
			ITeleportable.RequestType requestType = teleportable.getRequestType();
			if(requester == null || requestType == null) {
				throw NO_NEED_TO_ACCEPT.create();
			}

			switch(requestType) {
				case ASK -> TeleportCommand.performTeleport(
						stack, requester, (ServerLevel)entity.level,
						entity.getX(), entity.getY(), entity.getZ(),
						EnumSet.noneOf(ClientboundPlayerPositionPacket.RelativeArgument.class),
						requester.getYRot(), requester.getXRot(), null
				);
				case INVITE -> TeleportCommand.performTeleport(
						stack, entity, (ServerLevel)requester.level,
						requester.getX(), requester.getY(), requester.getZ(),
						EnumSet.noneOf(ClientboundPlayerPositionPacket.RelativeArgument.class),
						entity.getYRot(), entity.getXRot(), null
				);
			}

			entity.sendMessage(new TextComponent(new TranslatableComponent("commands.tpmaster.accept.success", requester.getName().getString()).getString()), Util.NIL_UUID);
			requester.sendMessage(new TextComponent(new TranslatableComponent("commands.tpmaster.request.accepted", entity.getName().getString()).getString()), Util.NIL_UUID);
			teleportable.clearTeleportMasterRequest();
		}
		return Command.SINGLE_SUCCESS;
	}

	public static int deny(Entity entity) throws CommandSyntaxException {
		if(entity instanceof ITeleportable teleportable) {
			Entity requester = teleportable.getTeleportMasterRequester();
			if(requester == null) {
				throw NO_NEED_TO_DENY.create();
			}

			entity.sendMessage(new TextComponent(new TranslatableComponent("commands.tpmaster.deny.success", requester.getName().getString()).getString()), Util.NIL_UUID);
			requester.sendMessage(new TextComponent(new TranslatableComponent("commands.tpmaster.request.denied", entity.getName().getString()).getString()), Util.NIL_UUID);
			teleportable.clearTeleportMasterRequest();
		}
		return Command.SINGLE_SUCCESS;
	}

	@SuppressWarnings("SameParameterValue")
	private static int away(CommandSourceStack stack, Entity entity, int distance, boolean mustOnLand, @Nullable TeleportCommand.LookAt lookAt) throws CommandSyntaxException {
		if(entity instanceof ITeleportable teleportable) {
			if(!teleportable.canUseTeleportMasterAway()) {
				throw COOL_DOWN_AWAY.create(teleportable.getTeleportMasterAwayCoolDownTick() / 20);
			}
			teleportable.setTeleportMasterAway();
		}
		if(distance == 0) {
			distance = entity.level.getRandom().nextInt(600) + 600;
		} else if(distance < 0 || distance > 10000) {
			throw INVALID_AWAY_DISTANCE_PARAMETER.create(distance);
		}

		boolean flag = false;
		Random random = entity.level.getRandom();
		double x = entity.getX();
		double y = entity.getY();
		double z = entity.getZ();
		for(int i = 0; i < TPMServerConfig.AWAY_TRY_COUNT.get(); ++i) {
			double phi = random.nextDouble(2.0D * Math.acos(-1.0D));
			x = entity.getX() + distance * Math.cos(phi) + random.nextDouble(TPMServerConfig.AWAY_NOISE_BOUND.get() * distance);
			z = entity.getZ() + distance * Math.sin(phi) + random.nextDouble(TPMServerConfig.AWAY_NOISE_BOUND.get() * distance);
			BlockPos blockPos = new BlockPos(x, 255.0D, z);
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
				y = LevelUtils.getTopBlock(entity.level, blockPos);
				if(y < 8) {
					continue;
				}
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

	private static int request(CommandSourceStack stack, Entity entity, Entity target, ITeleportable.RequestType type) throws CommandSyntaxException {
		if(entity instanceof ITeleportable teleportable) {
			if (!teleportable.canUseTeleportMasterRequest()) {
				throw COOL_DOWN_REQUEST.create(teleportable.getTeleportMasterRequestCoolDownTick() / 20);
			}
		}
		if(target instanceof ITeleportable teleportableTarget) {
			if(teleportableTarget.getTeleportMasterRequester() != null) {
				throw TARGET_UNHANDLED_RESERVATION.create(target.getName().getString());
			}
			if(entity instanceof ITeleportable teleportable) {
				teleportable.setTeleportMasterRequest(teleportableTarget, type);
			} else {
				teleportableTarget.receiveTeleportMasterRequestFrom(entity, type);
			}

			entity.sendMessage(new TextComponent(new TranslatableComponent("commands.tpmaster.request.success", target.getName().getString()).getString()), Util.NIL_UUID);
			target.sendMessage(new TextComponent(new TranslatableComponent(
					switch(type) {
						case ASK -> "commands.tpmaster.request.receive.ask";
						case INVITE -> "commands.tpmaster.request.receive.invite";
					},
					entity.getName().getString(), TPMServerConfig.REQUEST_COMMAND_AUTO_DENY_TICK.get() / 20
			).getString()), Util.NIL_UUID);
		} else {
			entity.sendMessage(new TextComponent(new TranslatableComponent("commands.tpmaster.request.success", target.getName().getString()).getString()), Util.NIL_UUID);
			boolean flag1 = entity instanceof Monster || (entity instanceof NeutralMob && !(entity instanceof TamableAnimal));
			boolean flag2 = target instanceof Monster || (target instanceof NeutralMob && !(target instanceof TamableAnimal));
			if(flag1 == flag2) {
				switch(type) {
					case ASK -> TeleportCommand.performTeleport(
							stack, entity, (ServerLevel)target.level,
							target.getX(), target.getY(), target.getZ(),
							EnumSet.noneOf(ClientboundPlayerPositionPacket.RelativeArgument.class),
							entity.getYRot(), entity.getXRot(), null
					);
					case INVITE -> TeleportCommand.performTeleport(
							stack, target, (ServerLevel)entity.level,
							entity.getX(), entity.getY(), entity.getZ(),
							EnumSet.noneOf(ClientboundPlayerPositionPacket.RelativeArgument.class),
							target.getYRot(), target.getXRot(), null
					);
				}
				entity.sendMessage(new TextComponent(new TranslatableComponent("commands.tpmaster.request.accepted", target.getName().getString()).getString()), Util.NIL_UUID);
			} else {
				entity.sendMessage(new TextComponent(new TranslatableComponent("commands.tpmaster.request.denied", target.getName().getString()).getString()), Util.NIL_UUID);
			}
		}

		return Command.SINGLE_SUCCESS;
	}

	private static int spawn(CommandSourceStack stack, Entity entity) throws CommandSyntaxException {
		ServerLevel overworld = stack.getServer().overworld();
		BlockPos spawnPoint = overworld.getSharedSpawnPos();
		TeleportCommand.performTeleport(
				stack, entity, overworld,
				spawnPoint.getX(), spawnPoint.getY() + 1.0D, spawnPoint.getZ(),
				EnumSet.noneOf(ClientboundPlayerPositionPacket.RelativeArgument.class),
				entity.getYRot(), entity.getXRot(), null
		);

		return Command.SINGLE_SUCCESS;
	}

	private static int help(Entity entity) {
		entity.sendMessage(new TextComponent(new TranslatableComponent("commands.tpmaster.help").getString()), Util.NIL_UUID);

		return Command.SINGLE_SUCCESS;
	}
}
