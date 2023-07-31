package com.hexagram2021.tpmaster;

import com.hexagram2021.tpmaster.common.config.TPMCommonConfig;
import com.hexagram2021.tpmaster.server.TPMContent;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(TeleportMaster.MODID)
public class TeleportMaster {
    public static final String MODID = "tpmaster";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TeleportMaster() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TPMCommonConfig.SPEC);

        MinecraftForge.EVENT_BUS.addListener(TPMContent::registerCommands);
        MinecraftForge.EVENT_BUS.register(this);
    }
}
