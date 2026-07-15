package com.compat.bookofwishes_opc.mixin;

import com.compat.bookofwishes_opc.service.ChestFillerService;
import com.compat.bookofwishes_opc.service.InventoryBookService;
import com.theforbiddenwishingbook.service.WishExecutor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Mixin into WishExecutor to add custom OPC compat action types:
 * - scan_player_storage: Creates a written book cataloging all items in claimed chests
 * - fill_chests: Fills all empty slots in claimed chests with a specified item
 */
@Mixin(value = WishExecutor.class, remap = false)
public abstract class WishExecutorMixin {

    @Inject(
            method = "execute(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/Map;)Lcom/theforbiddenwishingbook/service/WishExecutor$ExecutionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void bookofwishes_opc_compat$interceptActions(
            ServerPlayer player,
            Map<String, Object> action,
            CallbackInfoReturnable<WishExecutor.ExecutionResult> cir
    ) {
        String type = (String) action.get("type");
        if (type == null) return;

        switch (type) {
            case "scan_player_storage" -> {
                try {
                    var bookStack = InventoryBookService.createInventoryBook(player);
                    if (bookStack != null) {
                        player.getInventory().add(bookStack);
                        cir.setReturnValue(WishExecutor.ExecutionResult.ok("Created inventory catalog book"));
                    } else {
                        cir.setReturnValue(WishExecutor.ExecutionResult.fail(
                                "No claimed storage found. You must have claims with chests to catalog."
                        ));
                    }
                } catch (Exception e) {
                    cir.setReturnValue(WishExecutor.ExecutionResult.fail(
                            "Failed to scan storage: " + e.getMessage()
                    ));
                }
            }
            case "fill_chests" -> {
                try {
                    String itemStr = (String) action.get("item");
                    if (itemStr == null || itemStr.isEmpty()) {
                        cir.setReturnValue(WishExecutor.ExecutionResult.fail("No item specified for fill_chests"));
                        return;
                    }

                    int countPerSlot = action.containsKey("count_per_slot")
                            ? ((Number) action.get("count_per_slot")).intValue()
                            : 64;

                    countPerSlot = Math.max(1, Math.min(64, countPerSlot));

                    ResourceLocation itemId = ResourceLocation.parse(itemStr);
                    var item = BuiltInRegistries.ITEM.get(itemId);
                    if (item == null || item == net.minecraft.world.item.Items.AIR) {
                        cir.setReturnValue(WishExecutor.ExecutionResult.fail("Unknown item: " + itemStr));
                        return;
                    }

                    var result = ChestFillerService.fillClaimedChests(player, item, countPerSlot);
                    if (result != null && result.slotsFilled() > 0) {
                        cir.setReturnValue(WishExecutor.ExecutionResult.ok(
                                "Filled " + result.slotsFilled() + " slots with " + result.itemsPlaced() + " "
                                        + item.getDescription().getString() + " across " + result.chestsFound() + " chests"
                        ));
                    } else {
                        cir.setReturnValue(WishExecutor.ExecutionResult.fail(
                                "No empty slots found in claimed chests, or no claimed chests exist."
                        ));
                    }
                } catch (Exception e) {
                    cir.setReturnValue(WishExecutor.ExecutionResult.fail(
                            "Failed to fill chests: " + e.getMessage()
                    ));
                }
            }
        }
    }
}
