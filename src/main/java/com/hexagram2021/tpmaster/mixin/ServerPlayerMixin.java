package com.hexagram2021.tpmaster.mixin;

import com.hexagram2021.tpmaster.server.util.ITeleportable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.hexagram2021.tpmaster.server.config.TPMServerConfig.REQUEST_COMMAND_COOL_DOWN_TICK;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin implements ITeleportable {
	private Entity teleportMasterRequester = null;
	private RequestType requestType = null;

	private int teleportMasterAwayCoolDownTicks = 0;
	private int teleportMasterRequestCoolDownTicks = 0;

	@Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;tick()V", shift = At.Shift.AFTER))
	public void tickTeleportMaster(CallbackInfo ci) {
		if(this.teleportMasterAwayCoolDownTicks > 0) {
			--this.teleportMasterAwayCoolDownTicks;
		}
		if(this.teleportMasterRequestCoolDownTicks > 0) {
			--this.teleportMasterRequestCoolDownTicks;
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
		this.teleportMasterRequestCoolDownTicks = REQUEST_COMMAND_COOL_DOWN_TICK.get();
	}

	@Override
	public void receiveTeleportMasterRequestFrom(@NotNull Entity from, @NotNull RequestType type) {
		this.teleportMasterRequester = from;
		this.requestType = type;
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
