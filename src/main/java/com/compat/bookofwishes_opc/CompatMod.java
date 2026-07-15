package com.compat.bookofwishes_opc;

import com.compat.bookofwishes_opc.config.CompatConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(CompatMod.MOD_ID)
public class CompatMod {
    public static final String MOD_ID = "bookofwishes_opc_compat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CompatMod(IEventBus modEventBus, ModContainer modContainer) {
        CompatConfig.register(modContainer);
        LOGGER.info("Book of Wishes - OPC Compat loaded. Claim-aware wish context enabled.");
    }
}
