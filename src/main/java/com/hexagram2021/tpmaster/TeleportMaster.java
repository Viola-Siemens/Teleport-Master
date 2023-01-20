package com.hexagram2021.tpmaster;

import com.hexagram2021.tpmaster.server.TPMContent;
import com.hexagram2021.tpmaster.server.config.TPMServerConfig;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TeleportMaster.MODID)
public class TeleportMaster {
    public static final String MODID = "tpmaster";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TeleportMaster() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, TPMServerConfig.SPEC);

        MinecraftForge.EVENT_BUS.addListener(TPMContent::registerCommands);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        //preinit
    }
}
