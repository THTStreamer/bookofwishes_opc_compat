package com.compat.bookofwishes_opc.mixin;

import com.theforbiddenwishingbook.service.AIPromptBuilder;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Mixin into AIPromptBuilder to inject the "scan_player_storage" action type
 * into the AI system prompt, so the AI knows it can create an inventory catalog book.
 */
@Mixin(value = AIPromptBuilder.class, remap = false)
public abstract class AIPromptBuilderMixin {

    @Unique
    private static final String OPC_COMPAT_ACTIONS = """

            ======================================
            COMPAT MOD - OPEN PARTIES AND CLAIMS:
            ======================================
            The player has CLAIMED CHUNKS via Open Parties and Claims. The world context includes
            "player_base" and "base_storage" fields with their claimed storage information.

            IMPORTANT: When the player's wish involves placing items INTO their chests, storage,
            or base, you MUST use the fill_chests action. Do NOT use give_item for these wishes.
            give_item puts items in the PLAYER'S INVENTORY. fill_chests puts items IN THEIR CHESTS.

            --- ACTION: scan_player_storage ---
            Syntax: { "type": "scan_player_storage" }
            Scans ALL chests/shulker boxes in claimed chunks. Creates a Written Book catalog.
            Use when the player wants to know what items they have stored.

            --- ACTION: fill_chests ---
            Syntax: { "type": "fill_chests", "item": "minecraft:item_name", "count_per_slot": N }
            Fills empty slots in claimed chests with the specified item.
            count_per_slot = how many items per slot (default 64, max 64).

            HOW TO INTERPRET THE PLAYER'S WISH for fill_chests:
            - "ALL my chests" / "EVERY chest" / "each chest" / "all my storage"
              → fill_chests with count_per_slot: 64 (fill ALL empty slots in ALL chests)
            - "A chest" / "one chest" / "find me a chest"
              → fill_chests with count_per_slot: 64 (scan all chests, find one with space, fill one slot)
            - "Fill my chests" / "stock my storage" / "fill my base with"
              → fill_chests with count_per_slot: 64 (fill ALL empty slots)
            - "Put X in a chest" / "store X" / "keep X in my chests"
              → fill_chests with count_per_slot matching the requested amount
            - "Top up" / "replenish" / "refill"
              → fill_chests (tops up partial stacks to 64)

            EXAMPLES OF CORRECT BEHAVIOR:
            Player: "I wish for my chests to be filled with diamonds"
            → { "type": "fill_chests", "item": "minecraft:diamond", "count_per_slot": 64 }
            Payment: Heavy — this fills potentially hundreds of slots

            Player: "I wish for ALL my chests to contain diamond blocks"
            → { "type": "fill_chests", "item": "minecraft:diamond_block", "count_per_slot": 64 }
            Payment: Extremely heavy — every empty slot across all claimed chests

            Player: "I wish for a chest to have a stack of diamonds"
            → { "type": "fill_chests", "item": "minecraft:diamond", "count_per_slot": 64 }
            Payment: Moderate — one slot in one chest

            Player: "I wish for my storage to be stocked with iron"
            → { "type": "fill_chests", "item": "minecraft:iron_ingot", "count_per_slot": 64 }
            Payment: Heavy — all empty slots

            Player: "Give me diamonds"
            → { "type": "give_item", "item": "minecraft:diamond", "count": 64 }
            Payment: Light — this goes in INVENTORY, not chests. Use give_item ONLY when the
            player wants items in their personal inventory, NOT when they mention chests/storage/base.
            """;

    /**
     * Inject at RETURN of buildWishPrompt to append our action to the prompt.
     */
    @Inject(
            method = "buildWishPrompt(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            at = @At("RETURN")
    )
    private static void bookofwishes_opc_compat$addActionToSinglePrompt(
            ServerPlayer player,
            String wishText,
            String worldContext,
            CallbackInfoReturnable<String> cir
    ) {
        bookofwishes_opc_compat$injectActions(cir);
    }

    /**
     * Inject at RETURN of buildMultiWishPrompt to append our action to the prompt.
     */
    @Inject(
            method = "buildMultiWishPrompt(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/List;Ljava/lang/String;)Ljava/lang/String;",
            at = @At("RETURN")
    )
    private static void bookofwishes_opc_compat$addActionToMultiPrompt(
            ServerPlayer player,
            List<String> wishes,
            String worldContext,
            CallbackInfoReturnable<String> cir
    ) {
        bookofwishes_opc_compat$injectActions(cir);
    }

    @Unique
    private static void bookofwishes_opc_compat$injectActions(CallbackInfoReturnable<String> cir) {
        String original = cir.getReturnValue();
        if (original != null && !original.contains("scan_player_storage")) {
            String modified = original.replace(
                    "RULES:",
                    OPC_COMPAT_ACTIONS + "\n            RULES:"
            );
            cir.setReturnValue(modified);
        }
    }
}
