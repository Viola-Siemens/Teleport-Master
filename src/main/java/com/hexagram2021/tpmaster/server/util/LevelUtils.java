package com.hexagram2021.tpmaster.server.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class LevelUtils {
	public static int getTopBlock(Level level, BlockPos pos) {
		int y = -1;

		for (int j = level.getMaxBuildHeight() - 1; j > level.getMinBuildHeight(); j--) {
			BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(pos.getX(), 0, pos.getZ());
			if (!level.getBlockState(mutable.move(0, j, 0)).getBlock().defaultBlockState().isAir()) {
				y = j;
				break;
			}
		}

		return y + 1;
	}
}
