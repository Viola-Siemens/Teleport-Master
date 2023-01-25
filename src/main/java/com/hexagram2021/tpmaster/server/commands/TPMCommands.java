package com.hexagram2021.tpmaster.server.commands;

import com.hexagram2021.tpmaster.server.config.TPMServerConfig;
import com.hexagram2021.tpmaster.server.util.ITeleportable;
import com.hexagram2021.tpmaster.server.util.LevelUtils;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class TPMCommands {
	public static LiteralArgumentBuilder<CommandSourceStack> register() {
		return Commands.literal("tpmaster").then(
				Commands.literal("accept").requires(stack -> stack.hasPermission(TPMServerConfig.ACCEPT_DENY_PERMISSION_LEVEL.get()))
						.executes(context -> accept(context.getSource(), context.getSource().getEntityOrException()))
		).then(
				Commands.literal("deny").requires(stack -> stack.hasPermission(TPMServerConfig.ACCEPT_DENY_PERMISSION_LEVEL.get()))
						.executes(context -> deny(context.getSource().getEntityOrException()))
		).then(
				Commands.literal("away").requires(stack -> stack.hasPermission(TPMServerConfig.AWAY_PERMISSION_LEVEL.get()))
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
				Commands.literal("request").requires(stack -> stack.hasPermission(TPMServerConfig.REQUEST_PERMISSION_LEVEL.get()))
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
				Commands.literal("spawn").requires(stack -> stack.hasPermission(TPMServerConfig.SPAWN_PERMISSION_LEVEL.get()))
						.executes(context -> spawn(context.getSource(), context.getSource().getEntityOrException()))
		).then(
				Commands.literal("sethome").requires(stack -> stack.hasPermission(TPMServerConfig.HOME_PERMISSION_LEVEL.get()))
						.executes(context -> sethome(context.getSource().getEntityOrException(), 0))
						.then(
								Commands.argument("index", IntegerArgumentType.integer(0, TPMServerConfig.MAX_HOME_COUNT.get() - 1))
										.executes(context -> sethome(context.getSource().getEntityOrException(), IntegerArgumentType.getInteger(context, "index")))
						)
		).then(
				Commands.literal("home").requires(stack -> stack.hasPermission(TPMServerConfig.HOME_PERMISSION_LEVEL.get()))
						.executes(context -> home(context.getSource(), context.getSource().getEntityOrException(), 0))
						.then(
								Commands.argument("index", IntegerArgumentType.integer(0, TPMServerConfig.MAX_HOME_COUNT.get() - 1))
										.executes(context -> home(context.getSource(), context.getSource().getEntityOrException(), IntegerArgumentType.getInteger(context, "index")))
						)
		).then(
				Commands.literal("remove").requires(stack -> stack.hasPermission(TPMServerConfig.BACK_PERMISSION_LEVEL.get()))
						.then(
								Commands.literal("home").then(
										Commands.argument("index", IntegerArgumentType.integer(0, TPMServerConfig.MAX_HOME_COUNT.get() - 1))
												.executes(context -> removeHome(context.getSource().getEntityOrException(), IntegerArgumentType.getInteger(context, "index")))
								)
						)
						.then(
								Commands.literal("back").executes(context -> removeBack(context.getSource().getEntityOrException()))
						)
		).then(
				Commands.literal("help").requires(stack -> stack.hasPermission(TPMServerConfig.HELP_PERMISSION_LEVEL.get()))
						.executes(context -> help(context.getSource().getEntityOrException()))
		);
	}//TODO: remove home

	private static final SimpleCommandExceptionType NO_NEED_TO_ACCEPT = new SimpleCommandExceptionType(
			Component.translatable("commands.tpmaster.accept.failed.no_request")
	);
	private static final SimpleCommandExceptionType NO_NEED_TO_DENY = new SimpleCommandExceptionType(
			Component.translatable("commands.tpmaster.deny.failed.no_request")
	);
	private static final DynamicCommandExceptionType TARGET_UNHANDLED_RESERVATION = new DynamicCommandExceptionType(
			(name) -> Component.translatable("commands.tpmaster.request.failed.reserved", name)
	);

	private static final DynamicCommandExceptionType INVALID_AWAY_DISTANCE_PARAMETER = new DynamicCommandExceptionType(
			(d) -> Component.translatable("commands.tpmaster.away.invalid.distance", d)
	);
	private static final SimpleCommandExceptionType CANNOT_FIND_POSITION = new SimpleCommandExceptionType(
			Component.translatable("commands.tpmaster.away.failed.no_position")
	);
	public static final Dynamic2CommandExceptionType INVALID_SETHOME_INDEX_PARAMETER = new Dynamic2CommandExceptionType(
			(i, max) -> Component.translatable("commands.tpmaster.sethome.invalid.index", i, max)
	);
	private static final DynamicCommandExceptionType NO_HOME_TO_BACK = new DynamicCommandExceptionType(
			(d) -> Component.translatable("commands.tpmaster.home.failed.no_home", d)
	);
	private static final DynamicCommandExceptionType NO_LEVEL_FOUNDED = new DynamicCommandExceptionType(
			(level) -> Component.translatable("commands.tpmaster.home.failed.no_level", level)
	);

	private static final DynamicCommandExceptionType COOL_DOWN_AWAY = new DynamicCommandExceptionType(
			(d) -> Component.translatable("commands.tpmaster.away.failed.cool_down", d)
	);
	private static final DynamicCommandExceptionType COOL_DOWN_REQUEST = new DynamicCommandExceptionType(
			(d) -> Component.translatable("commands.tpmaster.request.failed.cool_down", d)
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

			entity.sendSystemMessage(Component.translatable("commands.tpmaster.accept.success", requester.getName().getString()));
			requester.sendSystemMessage(Component.translatable("commands.tpmaster.request.accepted", entity.getName().getString()));
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

			entity.sendSystemMessage(Component.translatable("commands.tpmaster.deny.success", requester.getName().getString()));
			requester.sendSystemMessage(Component.translatable("commands.tpmaster.request.denied", entity.getName().getString()));
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
		RandomSource random = entity.level.getRandom();
		double x = entity.getX();
		double y = entity.getY();
		double z = entity.getZ();
		for(int i = 0; i < TPMServerConfig.AWAY_TRY_COUNT.get(); ++i) {
			double phi = random.nextDouble() * 2.0D * Math.acos(-1.0D);
			x = entity.getX() + distance * Math.cos(phi) + random.nextDouble() * TPMServerConfig.AWAY_NOISE_BOUND.get() * distance;
			z = entity.getZ() + distance * Math.sin(phi) + random.nextDouble() * TPMServerConfig.AWAY_NOISE_BOUND.get() * distance;
			BlockPos blockPos = new BlockPos(x, 255.0D, z);
			Biome biome = entity.level.getBiome(blockPos).value();
			boolean conti = false;
			if(mustOnLand) {
				for (String ocean : TPMServerConfig.OCEAN_BIOME_KEYS.get()) {
					ResourceLocation biomeId = ForgeRegistries.BIOMES.getKey(biome);
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

		entity.sendSystemMessage(Component.translatable("commands.tpmaster.away.success", distance));

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

			entity.sendSystemMessage(Component.translatable("commands.tpmaster.request.success", target.getName().getString()));
			target.sendSystemMessage(Component.translatable(
					switch(type) {
						case ASK -> "commands.tpmaster.request.receive.ask";
						case INVITE -> "commands.tpmaster.request.receive.invite";
					},
					entity.getName().getString(), TPMServerConfig.REQUEST_COMMAND_AUTO_DENY_TICK.get() / 20
			));
		} else {
			entity.sendSystemMessage(Component.translatable("commands.tpmaster.request.success", target.getName().getString()));
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
				entity.sendSystemMessage(Component.translatable("commands.tpmaster.request.accepted", target.getName().getString()));
			} else {
				entity.sendSystemMessage(Component.translatable("commands.tpmaster.request.denied", target.getName().getString()));
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

	private static int sethome(Entity entity, int index) throws  CommandSyntaxException {
		if(entity instanceof ITeleportable teleportable) {
			BlockPos pos = entity.getOnPos();
			GlobalPos globalPos = GlobalPos.of(entity.level.dimension(), entity.getOnPos());
			teleportable.setTeleportMasterHome(globalPos, index);
			entity.sendSystemMessage(Component.translatable("commands.tpmaster.sethome.success", pos.getX(), pos.getY(), pos.getZ(), index));
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int home(CommandSourceStack stack, Entity entity, int index) throws CommandSyntaxException {
		if(entity instanceof ITeleportable teleportable) {
			GlobalPos globalPos = teleportable.getTeleportMasterHome(index);
			if(globalPos == null) {
				throw NO_HOME_TO_BACK.create(index);
			}
			ServerLevel level = stack.getServer().getLevel(globalPos.dimension());
			if(level == null) {
				throw NO_LEVEL_FOUNDED.create(globalPos.dimension().toString());
			}
			BlockPos pos = globalPos.pos();
			TeleportCommand.performTeleport(
					stack, entity, level,
					pos.getX(), pos.getY() + 1.0D, pos.getZ(),
					EnumSet.noneOf(ClientboundPlayerPositionPacket.RelativeArgument.class),
					entity.getYRot(), entity.getXRot(), null
			);
		}

		return Command.SINGLE_SUCCESS;
	}

	private static int back(CommandSourceStack stack, Entity entity) {
		if(entity instanceof ITeleportable teleportable) {
			//TODO
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int removeHome(Entity entity, int index) throws CommandSyntaxException {
		if(entity instanceof ITeleportable teleportable) {
			teleportable.setTeleportMasterHome(null, index);
			entity.sendSystemMessage(Component.translatable("commands.tpmaster.remove.home.success", index));
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int removeBack(Entity entity) {
		if(entity instanceof ITeleportable teleportable) {
			//TODO
			entity.sendSystemMessage(Component.translatable("commands.tpmaster.remove.back.success"));
		}
		return Command.SINGLE_SUCCESS;
	}

	private static int help(Entity entity) {
		entity.sendSystemMessage(Component.translatable("commands.tpmaster.help"));

		return Command.SINGLE_SUCCESS;
	}
}
