package com.hexagram2021.tpmaster.server.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;

public class LevelUtils {
	public static int getTopBlock(Level level, BlockPos pos) {
		int y = -1;

		if(level.dimension() == Level.NETHER) {
			int air = 0;
			for(int j = 127; j > 0; j--) {
				BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(pos.getX(), 0, pos.getZ());
				if (level.getBlockState(mutable.move(0, j, 0)).getBlock().defaultBlockState().isAir()) {
					air += 1;
				} else {
					if(air >= 2 && !level.getFluidState(mutable.move(0, j, 0)).is(Fluids.LAVA)) {
						y = j;
						break;
					}
					air = 0;
				}
			}
		} else {
			for (int j = level.getMaxBuildHeight() - 1; j > level.getMinBuildHeight(); j--) {
				BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(pos.getX(), 0, pos.getZ());
				if (!level.getBlockState(mutable.move(0, j, 0)).getBlock().defaultBlockState().isAir()) {
					y = j;
					break;
				}
			}
		}

		return y + 1;
	}
}
