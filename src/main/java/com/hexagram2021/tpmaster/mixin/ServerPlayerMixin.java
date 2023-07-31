package com.hexagram2021.tpmaster.mixin;

import com.hexagram2021.tpmaster.TeleportMaster;
import com.hexagram2021.tpmaster.common.config.TPMCommonConfig;
import com.hexagram2021.tpmaster.server.commands.TPMCommands;
import com.hexagram2021.tpmaster.server.util.ITeleportable;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

import static com.hexagram2021.tpmaster.common.config.TPMCommonConfig.*;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin implements ITeleportable {
	@Shadow @Final
	public MinecraftServer server;
	
	private Entity teleportMasterRequester = null;
	private RequestType requestType = null;

	private int teleportMasterAwayCoolDownTicks = 0;
	private int teleportMasterRequestCoolDownTicks = 0;
	private int teleportMasterAutoDenyTicks = 0;

	private final GlobalPos[] teleportMasterHomes = new GlobalPos[MAX_HOME_COUNT.get()];

	private GlobalPos lastDeathPoint = null;

	@SuppressWarnings("ConstantConditions")
	@Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;tick()V", shift = At.Shift.AFTER))
	public void tickTeleportMaster(CallbackInfo ci) {
		if(this.teleportMasterAwayCoolDownTicks > 0) {
			--this.teleportMasterAwayCoolDownTicks;
		}
		if(this.teleportMasterRequestCoolDownTicks > 0) {
			--this.teleportMasterRequestCoolDownTicks;
		}
		if(this.teleportMasterAutoDenyTicks > 0) {
			--this.teleportMasterAutoDenyTicks;
			if(this.teleportMasterAutoDenyTicks == 0) {
				try {
					TPMCommands.deny((ServerPlayer) (Object) this);
				} catch(CommandSyntaxException ignored) {}
			}
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At(value = "TAIL"))
	public void readTeleportMasterData(CompoundTag nbt, CallbackInfo ci) {
		if(nbt.contains("TeleportMasterRequester", Tag.TAG_INT_ARRAY)) {
			UUID uuid = nbt.getUUID("TeleportMasterRequester");
			if (uuid.equals(Util.NIL_UUID)) {
				this.teleportMasterRequester = null;
			} else {
				this.teleportMasterRequester = ((ServerPlayer) (Object) this).level.getPlayerByUUID(uuid);
			}
		}
		if(nbt.contains("RequestType", Tag.TAG_BYTE)) {
			byte req = nbt.getByte("RequestType");
			if (req <= 0 || req > RequestType.values().length) {
				this.requestType = null;
			} else {
				this.requestType = RequestType.values()[req - 1];
			}
		}

		this.teleportMasterAwayCoolDownTicks = nbt.getInt("TeleportMasterAwayCoolDownTicks");
		this.teleportMasterRequestCoolDownTicks = nbt.getInt("TeleportMasterRequestCoolDownTicks");
		this.teleportMasterAutoDenyTicks = nbt.getInt("TeleportMasterAutoDenyTicks");

		if(nbt.contains("TeleportMasterHomes", Tag.TAG_COMPOUND)) {
			CompoundTag homesTag = nbt.getCompound("TeleportMasterHomes");
			for(int i = 0; i < MAX_HOME_COUNT.get(); ++i) {
				if(homesTag.contains(String.valueOf(i), Tag.TAG_COMPOUND)) {
					CompoundTag curHome = homesTag.getCompound(String.valueOf(i));

					int finalI = i;
					String dimension = curHome.getString("dimension");
					this.server.levelKeys().stream().filter(key -> key.location().toString().equals(dimension)).findFirst().ifPresentOrElse(
							key -> this.teleportMasterHomes[finalI] = GlobalPos.of(key, BlockPos.of(curHome.getLong("pos"))),
							() -> {
								TeleportMaster.LOGGER.error("No dimension named \"%s\".".formatted(dimension));
								this.teleportMasterHomes[finalI] = null;
							}
					);
				} else {
					this.teleportMasterHomes[i] = null;
				}
			}
		} else {
			for(int i = 0; i < MAX_HOME_COUNT.get(); ++i) {
				this.teleportMasterHomes[i] = null;
			}
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At(value = "TAIL"))
	public void addTeleportMasterData(CompoundTag nbt, CallbackInfo ci) {
		nbt.putUUID("TeleportMasterRequester", this.teleportMasterRequester == null ? Util.NIL_UUID : this.teleportMasterRequester.getUUID());
		nbt.putByte("RequestType", (byte)(this.requestType == null ? 0 : this.requestType.ordinal() + 1));
		nbt.putInt("TeleportMasterAwayCoolDownTicks", this.teleportMasterAwayCoolDownTicks);
		nbt.putInt("TeleportMasterRequestCoolDownTicks", this.teleportMasterRequestCoolDownTicks);
		nbt.putInt("TeleportMasterAutoDenyTicks", this.teleportMasterAutoDenyTicks);

		CompoundTag homesTag = new CompoundTag();
		for(int i = 0; i < MAX_HOME_COUNT.get(); ++i) {
			if(this.teleportMasterHomes[i] != null) {
				CompoundTag curHome = new CompoundTag();
				curHome.putString("dimension", this.teleportMasterHomes[i].dimension().location().toString());
				curHome.putLong("pos", this.teleportMasterHomes[i].pos().asLong());
				homesTag.put(String.valueOf(i), curHome);
			}
		}
		nbt.put("TeleportMasterHomes", homesTag);
	}
	
	@Inject(method = "die", at = @At(value = "TAIL"))
	public void recordDeathPoint(DamageSource damageSource, CallbackInfo ci) {
		ServerPlayer current = (ServerPlayer)(Object)this;
		BlockPos pos = current.blockPosition();
		this.setTeleportMasterLastDeathPoint(GlobalPos.of(current.level.dimension(), pos));
		current.sendSystemMessage(Component.translatable("info.tpmaster.death", current.level.dimension().location(), pos.getX(), pos.getY(), pos.getZ()));
	}

	@Inject(method = "restoreFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setLastDeathLocation(Ljava/util/Optional;)V", shift = At.Shift.AFTER))
	public void restoreTeleportMasterData(ServerPlayer source, boolean won, CallbackInfo ci) {
		if(source instanceof ITeleportable teleportable) {
			this.teleportMasterRequester = teleportable.getTeleportMasterRequester();
			this.requestType = teleportable.getRequestType();
			this.teleportMasterAwayCoolDownTicks = teleportable.getTeleportMasterAwayCoolDownTick();
			this.teleportMasterRequestCoolDownTicks = teleportable.getTeleportMasterRequestCoolDownTick();
			this.teleportMasterAutoDenyTicks = TPMCommonConfig.REQUEST_COMMAND_AUTO_DENY_TICK.get();
			for(int i = 0; i < MAX_HOME_COUNT.get(); ++i) {
				this.teleportMasterHomes[i] = teleportable.getTeleportMasterHome(i);
			}
			this.lastDeathPoint = teleportable.getTeleportMasterLastDeathPoint();
		}
	}

	@Override @Nullable
	public Entity getTeleportMasterRequester() {
		return this.teleportMasterRequester;
	}

	@Override @Nullable
	public RequestType getRequestType() {
		return this.requestType;
	}

	@Override
	public void setTeleportMasterRequest(@NotNull ITeleportable target, @NotNull RequestType type) {
		target.receiveTeleportMasterRequestFrom((Entity)(Object)this, type);
		if(!((ServerPlayer)(Object)this).getAbilities().instabuild) {
			this.teleportMasterRequestCoolDownTicks = REQUEST_COMMAND_COOL_DOWN_TICK.get();
		}
	}

	@Override
	public void receiveTeleportMasterRequestFrom(@NotNull Entity from, @NotNull RequestType type) {
		this.teleportMasterRequester = from;
		this.requestType = type;

		this.teleportMasterAutoDenyTicks = TPMCommonConfig.REQUEST_COMMAND_AUTO_DENY_TICK.get();
	}

	@Override
	public void setTeleportMasterAway() {
		if(!((ServerPlayer)(Object)this).getAbilities().instabuild) {
			this.teleportMasterAwayCoolDownTicks = AWAY_COMMAND_COOL_DOWN_TICK.get();
		}
	}

	@Override
	public void clearTeleportMasterRequest() {
		this.teleportMasterRequester = null;
		this.requestType = null;
	}

	@Override
	public boolean canUseTeleportMasterAway() {
		return this.teleportMasterAwayCoolDownTicks <= 0;
	}

	@Override
	public boolean canUseTeleportMasterRequest() {
		return this.teleportMasterRequestCoolDownTicks <= 0;
	}

	@Override
	public int getTeleportMasterAwayCoolDownTick() {
		return this.teleportMasterAwayCoolDownTicks;
	}

	@Override
	public int getTeleportMasterRequestCoolDownTick() {
		return this.teleportMasterRequestCoolDownTicks;
	}

	@Override
	public void setTeleportMasterHome(GlobalPos pos, int index) throws CommandSyntaxException {
		if(index < 0 || index >= MAX_HOME_COUNT.get()) {
			throw TPMCommands.INVALID_SETHOME_INDEX_PARAMETER.create(index, MAX_HOME_COUNT.get());
		}
		this.teleportMasterHomes[index] = pos;
	}

	@Override @Nullable
	public GlobalPos getTeleportMasterHome(int index) {
		return this.teleportMasterHomes[index];
	}

	@Override
	public GlobalPos[] getTeleportMasterHomes() {
		return this.teleportMasterHomes;
	}

	@Override
	public void setTeleportMasterLastDeathPoint(@Nullable GlobalPos pos) {
		this.lastDeathPoint = pos;
	}

	@Override @Nullable
	public GlobalPos getTeleportMasterLastDeathPoint() {
		return this.lastDeathPoint;
	}
}
