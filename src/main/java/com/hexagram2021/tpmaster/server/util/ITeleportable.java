package com.hexagram2021.tpmaster.server.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public interface ITeleportable {
	@Nullable
	Entity getTeleportMasterRequester();
	@Nullable
	RequestType getRequestType();
	void clearTeleportMasterRequest();
	void setTeleportMasterRequest(@NotNull ITeleportable target, @NotNull RequestType type);
	void receiveTeleportMasterRequestFrom(@NotNull Entity from, @NotNull RequestType type);
	void setTeleportMasterAway();

	boolean canUseTeleportMasterAway();
	boolean canUseTeleportMasterRequest();
	int getTeleportMasterAwayCoolDownTick();
	int getTeleportMasterRequestCoolDownTick();

	void setTeleportMasterHome(GlobalPos pos, int index) throws CommandSyntaxException;
	@Nullable
	GlobalPos getTeleportMasterHome(int index);
	GlobalPos[] getTeleportMasterHomes();

	void setTeleportMasterLastDeathPoint(@Nullable GlobalPos pos);
	@Nullable
	GlobalPos getTeleportMasterLastDeathPoint();

	enum RequestType {
		ASK,		//Ask if requester can teleport to where requestee is.
		INVITE		//Invite requestee to come to where requester is.
	}
}
