package com.hexagram2021.tpmaster.server;

import com.hexagram2021.tpmaster.TeleportMaster;
import com.hexagram2021.tpmaster.server.commands.TPMCommands;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class TPMContent {
	public static void registerCommands(RegisterCommandsEvent event) {
		final CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
		dispatcher.register(TPMCommands.register());
	}
}
