package com.hexagram2021.tpmaster.server.config;

import com.google.common.collect.ImmutableList;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

public class TPMServerConfig {
	public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
	public static final ForgeConfigSpec SPEC;

	public static final ForgeConfigSpec.IntValue ACCEPT_DENY_PERMISSION_LEVEL;
	public static final ForgeConfigSpec.IntValue AWAY_PERMISSION_LEVEL;
	public static final ForgeConfigSpec.IntValue REQUEST_PERMISSION_LEVEL;
	public static final ForgeConfigSpec.IntValue SPAWN_PERMISSION_LEVEL;
	public static final ForgeConfigSpec.IntValue HOME_PERMISSION_LEVEL;
	public static final ForgeConfigSpec.IntValue HELP_PERMISSION_LEVEL;

	public static final ForgeConfigSpec.IntValue AWAY_COMMAND_COOL_DOWN_TICK;
	public static final ForgeConfigSpec.IntValue REQUEST_COMMAND_COOL_DOWN_TICK;
	public static final ForgeConfigSpec.IntValue REQUEST_COMMAND_AUTO_DENY_TICK;

	public static final ForgeConfigSpec.IntValue AWAY_TRY_COUNT;
	public static final ForgeConfigSpec.DoubleValue AWAY_NOISE_BOUND;

	public static final ForgeConfigSpec.IntValue MAX_HOME_COUNT;

	public static final ForgeConfigSpec.ConfigValue<List<String>> OCEAN_BIOME_KEYS;

	static {
		BUILDER.push("tpmaster-common-config");

		ACCEPT_DENY_PERMISSION_LEVEL = BUILDER.comment("The permission level for accept and deny commands.")
				.defineInRange("ACCEPT_DENY_PERMISSION_LEVEL", 0, 0, 4);
		AWAY_PERMISSION_LEVEL = BUILDER.comment("The permission level for away (random tp).")
				.defineInRange("AWAY_PERMISSION_LEVEL", 0, 0, 4);
		REQUEST_PERMISSION_LEVEL = BUILDER.comment("The permission level for request.")
				.defineInRange("REQUEST_PERMISSION_LEVEL", 0, 0, 4);
		SPAWN_PERMISSION_LEVEL = BUILDER.comment("The permission level for spawn.")
				.defineInRange("SPAWN_PERMISSION_LEVEL", 0, 0, 4);
		HOME_PERMISSION_LEVEL = BUILDER.comment("The permission level for sethome and home.")
				.defineInRange("HOME_PERMISSION_LEVEL", 0, 0, 4);
		HELP_PERMISSION_LEVEL = BUILDER.comment("The permission level for help.")
				.defineInRange("HELP_PERMISSION_LEVEL", 0, 0, 4);

		AWAY_COMMAND_COOL_DOWN_TICK = BUILDER.comment("The cool down time in ticks for player to use away commands.")
				.defineInRange("TELEPORT_COMMAND_COOL_DOWN_TICK", 600, 0, 12000);
		REQUEST_COMMAND_COOL_DOWN_TICK = BUILDER.comment("The cool down time in ticks for player to use request commands.")
				.defineInRange("REQUEST_COMMAND_COOL_DOWN_TICK", 300, 0, 12000);
		REQUEST_COMMAND_AUTO_DENY_TICK = BUILDER.comment("How many tick with no acceptance will players auto deny a teleport request after.")
				.defineInRange("REQUEST_COMMAND_COOL_DOWN_TICK", 400, 0, 6000);

		AWAY_TRY_COUNT = BUILDER.comment("How many times will it try when player use `/tpmaster away` command.")
				.defineInRange("AWAY_TRY_COUNT", 128, 0, 256);
		AWAY_NOISE_BOUND = BUILDER.comment("The bound of noise when player use `/tpmaster away` command.")
				.defineInRange("AWAY_NOISE_BOUND", 0.1, 0.01, 1.0);

		MAX_HOME_COUNT = BUILDER.comment("How many homes at most will a player set.")
				.defineInRange("MAX_HOME_COUNT", 8, 1, 64);

		OCEAN_BIOME_KEYS = BUILDER.comment("The Resource Locations of ocean biomes (to ignore in random tp).")
				.define("OCEAN_BIOME_KEYS", new ArrayList<>(
						ImmutableList.of(
								Biomes.DEEP_FROZEN_OCEAN.location().toString(),
								Biomes.DEEP_COLD_OCEAN.location().toString(),
								Biomes.DEEP_OCEAN.location().toString(),
								Biomes.DEEP_LUKEWARM_OCEAN.location().toString(),
								Biomes.FROZEN_OCEAN.location().toString(),
								Biomes.COLD_OCEAN.location().toString(),
								Biomes.OCEAN.location().toString(),
								Biomes.LUKEWARM_OCEAN.location().toString(),
								Biomes.WARM_OCEAN.location().toString()
						)
				));

		BUILDER.pop();
		SPEC = BUILDER.build();
	}
}
