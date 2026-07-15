package com.compat.bookofwishes_opc.service;

import com.compat.bookofwishes_opc.config.CompatConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.player.api.IPlayerClaimPosListAPI;
import xaero.pac.common.claims.player.api.IPlayerDimensionClaimsAPI;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.player.api.IServerPlayerClaimInfoAPI;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import java.util.*;

public class OPCClaimsService {

    public record ClaimScanResult(
            Map<String, Object> baseContext,
            List<Map<String, Object>> baseStorage,
            Map<String, Object> storageSummary
    ) {}

    public static ClaimScanResult scanClaimsAndStorage(ServerPlayer player) {
        if (!CompatConfig.ENABLE_CLAIM_SCANNING.get()) {
            return null;
        }

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

        // Collect all claimed chunks for this player
        Set<ChunkPos> playerChunks = new HashSet<>();
        collectClaimedChunks(playerInfo, dimensionId, playerChunks);

        // Collect party members' chunks if enabled
        Map<String, Set<ChunkPos>> partyChunkMap = new LinkedHashMap<>();
        if (CompatConfig.INCLUDE_PARTY_CLAIMS.get()) {
            collectPartyClaims(opcApi, playerUUID, dimensionId, partyChunkMap);
        }

        int totalChunks = playerChunks.size();
        for (Set<ChunkPos> partyChunks : partyChunkMap.values()) {
            totalChunks += partyChunks.size();
        }

        if (totalChunks == 0) {
            return null;
        }

        // Limit scan count
        int maxChunks = CompatConfig.MAX_CLAIM_SCAN_CHUNKS.get();
        if (totalChunks > maxChunks) {
            totalChunks = maxChunks;
        }

        // Calculate base center from all player chunks
        Map<String, Object> baseContext = calculateBaseContext(
                player, playerChunks, partyChunkMap, dimensionId
        );

        // Scan all claimed chunks for storage
        int step = CompatConfig.STORAGE_SCAN_STEP.get();
        List<Map<String, Object>> baseStorage = new ArrayList<>();

        // Scan player's own chunks
        int scanned = 0;
        for (ChunkPos chunk : playerChunks) {
            if (scanned >= maxChunks) break;
            scanChunkForStorage(player, chunk, step, baseStorage, true);
            scanned++;
        }

        // Scan party members' chunks
        for (Map.Entry<String, Set<ChunkPos>> entry : partyChunkMap.entrySet()) {
            String memberName = entry.getKey();
            for (ChunkPos chunk : entry.getValue()) {
                if (scanned >= maxChunks) break;
                scanChunkForStorage(player, chunk, step, baseStorage, false, memberName);
                scanned++;
            }
        }

        // Build storage summary
        Map<String, Object> storageSummary = buildStorageSummary(baseStorage);

        return new ClaimScanResult(baseContext, baseStorage, storageSummary);
    }

    private static void collectClaimedChunks(
            IServerPlayerClaimInfoAPI playerInfo,
            ResourceLocation dimensionId,
            Set<ChunkPos> chunks
    ) {
        IPlayerDimensionClaimsAPI dimClaims;
        try {
            dimClaims = playerInfo.getDimension(dimensionId);
        } catch (Exception e) {
            return;
        }

        if (dimClaims == null) return;

        try {
            dimClaims.getStream().forEach(posList -> {
                posList.getStream().forEach(chunkPos -> {
                    chunks.add(chunkPos);
                });
            });
        } catch (Exception e) {
            // Skip on error
        }
    }

    private static void collectPartyClaims(
            OpenPACServerAPI opcApi,
            UUID playerUUID,
            ResourceLocation dimensionId,
            Map<String, Set<ChunkPos>> partyChunkMap
    ) {
        IPartyManagerAPI partyManager = opcApi.getPartyManager();
        if (partyManager == null) return;

        IServerPartyAPI party;
        try {
            party = partyManager.getPartyByMember(playerUUID);
        } catch (Exception e) {
            return;
        }

        if (party == null) return;

        IServerClaimsManagerAPI claimsManager = opcApi.getServerClaimsManager();
        if (claimsManager == null) return;

        party.getMemberInfoStream().forEach(member -> {
            UUID memberUUID = member.getUUID();
            if (memberUUID.equals(playerUUID)) return;

            IServerPlayerClaimInfoAPI memberInfo;
            try {
                memberInfo = claimsManager.getPlayerInfo(memberUUID);
            } catch (Exception e) {
                return;
            }

            if (memberInfo == null) return;

            Set<ChunkPos> memberChunks = new HashSet<>();
            collectClaimedChunks(memberInfo, dimensionId, memberChunks);

            if (!memberChunks.isEmpty()) {
                partyChunkMap.put(member.getUsername(), memberChunks);
            }
        });
    }

    private static Map<String, Object> calculateBaseContext(
            ServerPlayer player,
            Set<ChunkPos> playerChunks,
            Map<String, Set<ChunkPos>> partyChunkMap,
            ResourceLocation dimensionId
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("has_claims", true);
        context.put("dimension", dimensionId.toString());

        // Calculate bounding box of player's own chunks
        if (!playerChunks.isEmpty()) {
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

            for (ChunkPos chunk : playerChunks) {
                minX = Math.min(minX, chunk.x);
                maxX = Math.max(maxX, chunk.x);
                minZ = Math.min(minZ, chunk.z);
                maxZ = Math.max(maxZ, chunk.z);
            }

            int centerX = ((minX + maxX) / 2) * 16 + 8;
            int centerZ = ((minZ + maxZ) / 2) * 16 + 8;

            context.put("center_x", centerX);
            context.put("center_z", centerZ);
            context.put("claimed_chunks", playerChunks.size());
            context.put("claim_bounds", Map.of(
                    "min_x", minX * 16,
                    "max_x", maxX * 16 + 15,
                    "min_z", minZ * 16,
                    "max_z", maxZ * 16 + 15
            ));
        }

        // Party info
        if (!partyChunkMap.isEmpty()) {
            context.put("party_shared", true);
            context.put("party_members_with_claims", new ArrayList<>(partyChunkMap.keySet()));
            int partyChunkCount = partyChunkMap.values().stream().mapToInt(Set::size).sum();
            context.put("party_claimed_chunks", partyChunkCount);
        } else {
            context.put("party_shared", false);
        }

        return context;
    }

    private static void scanChunkForStorage(
            ServerPlayer player,
            ChunkPos chunk,
            int step,
            List<Map<String, Object>> storage,
            boolean isOwner
    ) {
        scanChunkForStorage(player, chunk, step, storage, isOwner, null);
    }

    private static void scanChunkForStorage(
            ServerPlayer player,
        ChunkPos chunk,
        int step,
        List<Map<String, Object>> storage,
        boolean isOwner,
        String ownerName
    ) {
        ServerLevel level = player.serverLevel();
        int chunkMinX = chunk.x * 16;
        int chunkMinZ = chunk.z * 16;

        for (int x = 0; x < 16; x += step) {
            for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y += step) {
                for (int z = 0; z < 16; z += step) {
                    BlockPos pos = new BlockPos(chunkMinX + x, y, chunkMinZ + z);
                    if (!level.isLoaded(pos)) continue;

                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof ChestBlockEntity || be instanceof ShulkerBoxBlockEntity) {
                        String type = be instanceof ShulkerBoxBlockEntity ? "shulker_box" : "chest";
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("type", type);
                        entry.put("x", pos.getX());
                        entry.put("y", pos.getY());
                        entry.put("z", pos.getZ());
                        entry.put("chunk_claimed", true);

                        if (isOwner) {
                            entry.put("owner", "self");
                        } else {
                            entry.put("owner", ownerName != null ? ownerName : "party");
                        }

                        storage.add(entry);
                    }
                }
            }
        }
    }

    private static Map<String, Object> buildStorageSummary(List<Map<String, Object>> storage) {
        Map<String, Object> summary = new LinkedHashMap<>();
        int chests = 0;
        int shulkerBoxes = 0;
        int claimedChests = 0;
        int partyChests = 0;

        for (Map<String, Object> entry : storage) {
            String type = (String) entry.get("type");
            String owner = (String) entry.get("owner");

            if ("chest".equals(type)) {
                chests++;
            } else if ("shulker_box".equals(type)) {
                shulkerBoxes++;
            }

            if ("self".equals(owner)) {
                claimedChests++;
            } else {
                partyChests++;
            }
        }

        summary.put("total_chests", chests);
        summary.put("total_shulker_boxes", shulkerBoxes);
        summary.put("claimed_chest_count", claimedChests);
        summary.put("party_chest_count", partyChests);
        summary.put("total_storage_in_base", storage.size());

        return summary;
    }
}
