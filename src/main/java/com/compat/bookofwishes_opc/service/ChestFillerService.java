package com.compat.bookofwishes_opc.service;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import xaero.pac.common.claims.player.api.IPlayerDimensionClaimsAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.player.api.IServerPlayerClaimInfoAPI;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ChestFillerService {

    public record FillResult(int chestsFound, int slotsFilled, int itemsPlaced) {}

    public static FillResult fillClaimedChests(ServerPlayer player, Item item, int countPerSlot) {
        MinecraftServer server = player.getServer();
        if (server == null) return null;

        OpenPACServerAPI opcApi;
        try {
            opcApi = OpenPACServerAPI.get(server);
        } catch (Exception e) {
            return null;
        }
        if (opcApi == null) return null;

        IServerClaimsManagerAPI claimsManager = opcApi.getServerClaimsManager();
        if (claimsManager == null) return null;

        UUID playerUUID = player.getUUID();
        ResourceLocation dimensionId = player.level().dimension().location();

        IServerPlayerClaimInfoAPI playerInfo;
        try {
            playerInfo = claimsManager.getPlayerInfo(playerUUID);
        } catch (Exception e) {
            return null;
        }
        if (playerInfo == null) return null;

        Set<ChunkPos> allChunks = new HashSet<>();
        collectChunks(playerInfo, dimensionId, allChunks);

        IPartyManagerAPI partyManager = opcApi.getPartyManager();
        if (partyManager != null) {
            try {
                IServerPartyAPI party = partyManager.getPartyByMember(playerUUID);
                if (party != null) {
                    party.getMemberInfoStream().forEach(member -> {
                        UUID memberUUID = member.getUUID();
                        if (memberUUID.equals(playerUUID)) return;
                        try {
                            IServerPlayerClaimInfoAPI memberInfo = claimsManager.getPlayerInfo(memberUUID);
                            if (memberInfo != null) {
                                collectChunks(memberInfo, dimensionId, allChunks);
                            }
                        } catch (Exception e) {
                            // Skip
                        }
                    });
                }
            } catch (Exception e) {
                // Skip party scanning
            }
        }

        if (allChunks.isEmpty()) return null;

        ServerLevel level = player.serverLevel();
        int chestsFound = 0;
        int totalSlotsFilled = 0;
        int totalItemsPlaced = 0;

        for (ChunkPos chunk : allChunks) {
            FillResult result = scanAndFillChunk(level, chunk, item, countPerSlot);
            if (result != null) {
                chestsFound += result.chestsFound();
                totalSlotsFilled += result.slotsFilled();
                totalItemsPlaced += result.itemsPlaced();
            }
        }

        return new FillResult(chestsFound, totalSlotsFilled, totalItemsPlaced);
    }

    private static void collectChunks(
            IServerPlayerClaimInfoAPI playerInfo,
            ResourceLocation dimensionId,
            Set<ChunkPos> chunks
    ) {
        try {
            IPlayerDimensionClaimsAPI dimClaims = playerInfo.getDimension(dimensionId);
            if (dimClaims == null) return;
            dimClaims.getStream().forEach(posList -> {
                posList.getStream().forEach(chunks::add);
            });
        } catch (Exception e) {
            // Skip
        }
    }

    private static FillResult scanAndFillChunk(ServerLevel level, ChunkPos chunk, Item item, int countPerSlot) {
        int chestsFound = 0;
        int slotsFilled = 0;
        int itemsPlaced = 0;
        int chunkMinX = chunk.x * 16;
        int chunkMinZ = chunk.z * 16;

        for (int x = 0; x < 16; x += 2) {
            for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y += 2) {
                for (int z = 0; z < 16; z += 2) {
                    BlockPos pos = new BlockPos(chunkMinX + x, y, chunkMinZ + z);
                    if (!level.isLoaded(pos)) continue;

                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof ChestBlockEntity || be instanceof ShulkerBoxBlockEntity) {
                        chestsFound++;
                        FillResult result = fillContainer(be, item, countPerSlot);
                        if (result != null) {
                            slotsFilled += result.slotsFilled();
                            itemsPlaced += result.itemsPlaced();
                        }
                    }
                }
            }
        }

        if (chestsFound == 0) return null;
        return new FillResult(chestsFound, slotsFilled, itemsPlaced);
    }

    private static FillResult fillContainer(BlockEntity be, Item item, int countPerSlot) {
        net.minecraft.world.Container container = null;
        if (be instanceof ChestBlockEntity chest) {
            container = chest;
        } else if (be instanceof ShulkerBoxBlockEntity shulker) {
            container = shulker;
        }
        if (container == null) return null;

        int slotsFilled = 0;
        int itemsPlaced = 0;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);

            if (stack.isEmpty()) {
                // Empty slot — fill it
                ItemStack toPlace = new ItemStack(item, countPerSlot);
                container.setItem(i, toPlace);
                slotsFilled++;
                itemsPlaced += countPerSlot;
            } else if (stack.is(item) && stack.getCount() < stack.getMaxStackSize()) {
                // Partial stack of the same item — top it up
                int canAdd = stack.getMaxStackSize() - stack.getCount();
                int toAdd = Math.min(canAdd, countPerSlot);
                if (toAdd > 0) {
                    stack.grow(toAdd);
                    itemsPlaced += toAdd;
                    slotsFilled++;
                }
            }
        }

        return new FillResult(0, slotsFilled, itemsPlaced);
    }
}
