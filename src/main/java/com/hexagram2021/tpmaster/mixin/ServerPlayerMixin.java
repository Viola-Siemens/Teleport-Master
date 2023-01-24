package com.hexagram2021.tpmaster.mixin;

import com.hexagram2021.tpmaster.server.commands.TPMCommands;
import com.hexagram2021.tpmaster.server.config.TPMServerConfig;
import com.hexagram2021.tpmaster.server.util.ITeleportable;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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

import static com.hexagram2021.tpmaster.server.config.TPMServerConfig.*;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin implements ITeleportable {
	@Shadow @Final
	public MinecraftServer server;
	private Entity teleportMasterRequester = null;
	private RequestType requestType = null;

	private int teleportMasterAwayCoolDownTicks = 0;
	private int teleportMasterRequestCoolDownTicks = 0;
	private int teleportMasterAutoDenyTicks = 0;

	private final GlobalPos[] homes = new GlobalPos[MAX_HOME_COUNT.get()];

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
					this.server.levelKeys().stream().filter(key -> key.location().toString().equals(curHome.getString("dimension"))).findFirst().ifPresentOrElse(
							key -> this.homes[finalI] = GlobalPos.of(key, BlockPos.of(curHome.getLong("pos"))),
							() -> this.homes[finalI] = null
					);
				} else {
					this.homes[i] = null;
				}
			}
		} else {
			for(int i = 0; i < MAX_HOME_COUNT.get(); ++i) {
				this.homes[i] = null;
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
			if(this.homes[i] != null) {
				CompoundTag curHome = new CompoundTag();
				curHome.putString("dimension", this.homes[i].dimension().location().toString());
				curHome.putLong("pos", this.homes[i].pos().asLong());
				homesTag.put(String.valueOf(i), curHome);
			}
		}
		nbt.put("TeleportMasterHomes", homesTag);
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

		this.teleportMasterAutoDenyTicks = TPMServerConfig.REQUEST_COMMAND_AUTO_DENY_TICK.get();
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
		this.homes[index] = pos;
	}

	@Override @Nullable
	public GlobalPos getTeleportMasterHome(int index) {
		return this.homes[index];
	}
}
