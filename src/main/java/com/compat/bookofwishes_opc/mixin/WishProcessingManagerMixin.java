package com.compat.bookofwishes_opc.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.theforbiddenwishingbook.server.WishProcessingManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mixin into WishProcessingManager to intercept AI responses and convert
 * give_item actions to fill_chests when the wish text mentions chests/storage.
 * This is a safety net that forces correct behavior regardless of what the AI generates.
 */
@Mixin(value = WishProcessingManager.class, remap = false)
public abstract class WishProcessingManagerMixin {

    @Unique
    private static final java.util.Set<String> STORAGE_KEYWORDS = java.util.Set.of(
            "chest", "chests", "storage", "base", "shulker", "container",
            "fill my", "stock my", "store", "stored", "keeping", "keep in",
            "put in", "place in", "filled with", "fill with", "contain"
    );

    @Inject(
            method = "executeAIResponse",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/gson/JsonObject;has(Ljava/lang/String;)Z",
                    shift = At.Shift.AFTER,
                    ordinal = 0
            )
    )
    private void bookofwishes_opc_compat$convertActions(
            ServerPlayer player,
            int inventorySlot,
            JsonObject aiResponse,
            List<String> wishes,
            List<Integer> pageNumbers,
            CallbackInfo ci
    ) {
        try {
            // Check if any wish mentions chests/storage
            boolean wishesMentionStorage = false;
            for (String wish : wishes) {
                String lower = wish.toLowerCase();
                for (String keyword : STORAGE_KEYWORDS) {
                    if (lower.contains(keyword)) {
                        wishesMentionStorage = true;
                        break;
                    }
                }
                if (wishesMentionStorage) break;
            }

            if (!wishesMentionStorage) return;
            if (!aiResponse.has("actions")) return;

            JsonArray actions = aiResponse.getAsJsonArray("actions");
            JsonArray convertedActions = new JsonArray();

            for (JsonElement actionEl : actions) {
                if (!actionEl.isJsonObject()) {
                    convertedActions.add(actionEl);
                    continue;
                }

                JsonObject action = actionEl.getAsJsonObject();
                String type = action.has("type") ? action.get("type").getAsString() : "";

                if ("give_item".equals(type) || "give_block".equals(type)) {
                    // Convert to fill_chests
                    JsonObject fillAction = new JsonObject();
                    fillAction.addProperty("type", "fill_chests");

                    // Copy the item/block field
                    if (action.has("item")) {
                        fillAction.addProperty("item", action.get("item").getAsString());
                    } else if (action.has("block")) {
                        fillAction.addProperty("item", action.get("block").getAsString());
                    }

                    // Determine count_per_slot from count
                    int count = action.has("count") ? action.get("count").getAsInt() : 64;
                    fillAction.addProperty("count_per_slot", Math.min(count, 64));

                    convertedActions.add(fillAction);
                } else {
                    convertedActions.add(action);
                }
            }

            // Replace the actions array
            aiResponse.add("actions", convertedActions);

        } catch (Exception e) {
            // Silently fail - don't break wish processing
        }
    }
}
