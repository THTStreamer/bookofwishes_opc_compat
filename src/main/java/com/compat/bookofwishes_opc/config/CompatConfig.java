package com.compat.bookofwishes_opc.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.ModContainer;

public class CompatConfig {
    private static ModConfigSpec COMMON_CONFIG;

    public static ModConfigSpec.BooleanValue ENABLE_CLAIM_SCANNING;
    public static ModConfigSpec.BooleanValue INCLUDE_PARTY_CLAIMS;
    public static ModConfigSpec.IntValue MAX_CLAIM_SCAN_CHUNKS;
    public static ModConfigSpec.IntValue STORAGE_SCAN_STEP;

    public static void register(ModContainer modContainer) {
        buildConfig();
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, COMMON_CONFIG);
    }

    private static void buildConfig() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Open Parties and Claims Integration Settings").push("opc_compat");

        ENABLE_CLAIM_SCANNING = builder
                .comment(
                        "Enable scanning of player claimed chunks for storage.",
                        "When enabled, the Book of Wishes will know about chests in your claimed base."
                )
                .define("enable_claim_scanning", true);

        INCLUDE_PARTY_CLAIMS = builder
                .comment(
                        "Include party members' claimed chunks when scanning.",
                        "When enabled, chests in party members' claims will also be included in the wish context."
                )
                .define("include_party_claims", true);

        MAX_CLAIM_SCAN_CHUNKS = builder
                .comment(
                        "Maximum number of claimed chunks to scan for storage.",
                        "Lower this if players have extremely large claims to prevent lag."
                )
                .defineInRange("max_claim_scan_chunks", 100, 1, 1000);

        STORAGE_SCAN_STEP = builder
                .comment(
                        "Block step interval when scanning chunks for storage blocks.",
                        "Smaller values are more thorough but slower. 2 = every 2 blocks, 4 = every 4 blocks."
                )
                .defineInRange("storage_scan_step", 2, 1, 8);

        builder.pop();

        COMMON_CONFIG = builder.build();
    }
}
