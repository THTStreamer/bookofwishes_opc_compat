package com.compat.bookofwishes_opc.mixin;

import com.compat.bookofwishes_opc.service.OPCClaimsService;
import com.google.gson.Gson;
import com.theforbiddenwishingbook.service.WorldContextScanner;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Mixin into WorldContextScanner to add OPC claim-aware context to the AI prompt.
 * <p>
 * Two-phase injection:
 * <ol>
 *   <li>{@code @Inject at HEAD}: Captures the ServerPlayer and stores in ThreadLocal</li>
 *   <li>{@code @Redirect on GSON.toJson}: Reads player from ThreadLocal, scans claims,
 *       modifies the context map, then calls original toJson</li>
 * </ol>
 */
@Mixin(value = WorldContextScanner.class, remap = false)
public abstract class WorldContextScannerMixin {

    @Shadow
    @Final
    private static Gson GSON;

    @Unique
    private static final ThreadLocal<ServerPlayer> bookofwishes_opc_compat$capturedPlayer = new ThreadLocal<>();

    /**
     * Phase 1: Capture the ServerPlayer at method HEAD so Phase 2 can use it.
     */
    @Inject(method = "scanAndSerialize", at = @At("HEAD"))
    private void bookofwishes_opc_compat$capturePlayer(
            ServerPlayer player,
            int cacheTtlSeconds,
            CallbackInfoReturnable<String> cir
    ) {
        bookofwishes_opc_compat$capturedPlayer.set(player);
    }

    /**
     * Phase 2: Redirect GSON.toJson(context) to add OPC claim data to the context map
     * before it gets serialized to JSON.
     */
    @SuppressWarnings("unchecked")
    @Redirect(
            method = "scanAndSerialize",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/gson/Gson;toJson(Ljava/lang/Object;)Ljava/lang/String;"
            )
    )
    private String bookofwishes_opc_compat$injectClaimContext(
            Gson instance,
            Object context
    ) {
        ServerPlayer player = bookofwishes_opc_compat$capturedPlayer.get();
        bookofwishes_opc_compat$capturedPlayer.remove();

        if (player != null && context instanceof Map<?, ?> map) {
            try {
                OPCClaimsService.ClaimScanResult result = OPCClaimsService.scanClaimsAndStorage(player);
                if (result != null) {
                    Map<String, Object> contextMap = (Map<String, Object>) map;
                    contextMap.put("player_base", result.baseContext());
                    contextMap.put("base_storage", result.baseStorage());
                    contextMap.put("base_storage_summary", result.storageSummary());
                }
            } catch (Exception e) {
                // Silently fail - don't break the Book of Wishes
            }
        }

        return instance.toJson(context);
    }
}
