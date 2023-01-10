package com.hexagram2021.tpmaster.mixin;

import com.hexagram2021.tpmaster.server.commands.TPMCommands;
import com.hexagram2021.tpmaster.server.config.TPMServerConfig;
import com.hexagram2021.tpmaster.server.util.ITeleportable;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

import static com.hexagram2021.tpmaster.server.config.TPMServerConfig.*;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin implements ITeleportable {
	private Entity teleportMasterRequester = null;
	private RequestType requestType = null;

	private int teleportMasterAwayCoolDownTicks = 0;
	private int teleportMasterRequestCoolDownTicks = 0;
	private int teleportMasterAutoDenyTicks = 0;

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
		UUID uuid = nbt.getUUID("TeleportMasterRequester");
		if(uuid.equals(Util.NIL_UUID)) {
			this.teleportMasterRequester = null;
		} else {
			this.teleportMasterRequester = ((ServerPlayer)(Object)this).level.getPlayerByUUID(uuid);
		}
		byte req = nbt.getByte("RequestType");
		if(req <= 0 || req > RequestType.values().length) {
			this.requestType = null;
		} else {
			this.requestType = RequestType.values()[req - 1];
		}

		this.teleportMasterAwayCoolDownTicks = nbt.getInt("TeleportMasterAwayCoolDownTicks");
		this.teleportMasterRequestCoolDownTicks = nbt.getInt("TeleportMasterRequestCoolDownTicks");
	}

	@Inject(method = "addAdditionalSaveData", at = @At(value = "TAIL"))
	public void addTeleportMasterData(CompoundTag nbt, CallbackInfo ci) {
		nbt.putUUID("TeleportMasterRequester", this.teleportMasterRequester == null ? Util.NIL_UUID : this.teleportMasterRequester.getUUID());
		nbt.putByte("RequestType", (byte)(this.requestType == null ? 0 : this.requestType.ordinal() + 1));
		nbt.putInt("TeleportMasterAwayCoolDownTicks", this.teleportMasterAwayCoolDownTicks);
		nbt.putInt("TeleportMasterRequestCoolDownTicks", this.teleportMasterRequestCoolDownTicks);
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
}
