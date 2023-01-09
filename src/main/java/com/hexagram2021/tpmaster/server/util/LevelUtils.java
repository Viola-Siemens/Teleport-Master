package com.hexagram2021.tpmaster.server.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public class LevelUtils {
	public static int getTopBlock(Level level, BlockPos pos) {
		int y = -1;

		for (int j = level.getMaxBuildHeight(); j > level.getMinBuildHeight(); j--) {
			BlockPos.MutableBlockPos mutable = pos.mutable();
			if (!level.getBlockState(mutable.move(0, j, 0)).getBlock().equals(Blocks.AIR)) {
				y = j;
				break;
			}
		}

		return y + 1;
	}
}
