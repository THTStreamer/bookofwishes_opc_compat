package com.compat.bookofwishes_opc.service;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.item.component.WrittenBookContent;
import xaero.pac.common.claims.player.api.IPlayerDimensionClaimsAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.player.api.IServerPlayerClaimInfoAPI;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import java.util.*;
import java.util.stream.Collectors;

public class InventoryBookService {

    public record InventoryEntry(String itemId, String itemName, int count) {}

    public static ItemStack createInventoryBook(ServerPlayer player) {
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

        if (allChunks.isEmpty()) {
            return null;
        }

        ServerLevel level = player.serverLevel();
        Map<String, int[]> itemTally = new LinkedHashMap<>();
        int chestsScanned = 0;

        for (ChunkPos chunk : allChunks) {
            chestsScanned += scanChunk(level, chunk, itemTally);
        }

        if (itemTally.isEmpty() && chestsScanned == 0) {
            return null;
        }

        List<InventoryEntry> sorted = itemTally.entrySet().stream()
                .map(e -> new InventoryEntry(e.getKey(), friendlyName(e.getKey()), e.getValue()[0]))
                .sorted(Comparator.comparingInt(InventoryEntry::count).reversed())
                .collect(Collectors.toList());

        List<Filterable<Component>> pages = buildPages(sorted, chestsScanned, allChunks.size());
        return createBookItem(pages, player.getName().getString());
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

    private static int scanChunk(ServerLevel level, ChunkPos chunk, Map<String, int[]> itemTally) {
        int chestsFound = 0;
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
                        scanContainer(be, itemTally);
                    }
                }
            }
        }
        return chestsFound;
    }

    private static void scanContainer(BlockEntity be, Map<String, int[]> itemTally) {
        net.minecraft.world.Container container = null;
        if (be instanceof ChestBlockEntity chest) {
            container = chest;
        } else if (be instanceof ShulkerBoxBlockEntity shulker) {
            container = shulker;
        }
        if (container == null) return;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;

            String itemId = stack.getItem().builtInRegistryHolder().key().location().toString();
            int count = stack.getCount();

            itemTally.merge(itemId, new int[]{count}, (a, b) -> {
                a[0] += b[0];
                return a;
            });
        }
    }

    private static List<Filterable<Component>> buildPages(List<InventoryEntry> entries, int chestsScanned, int chunksScanned) {
        List<Filterable<Component>> pages = new ArrayList<>();

        // Page 1: Summary
        Component summary = Component.literal("Inventory Catalog").withStyle(s -> s.withColor(0x5500AA).withBold(true));
        summary = ((net.minecraft.network.chat.MutableComponent) summary).append(Component.literal("\n\nChests scanned: " + chestsScanned).withStyle(s -> s.withColor(0x666666)));
        summary = ((net.minecraft.network.chat.MutableComponent) summary).append(Component.literal("\nChunks scanned: " + chunksScanned).withStyle(s -> s.withColor(0x666666)));
        summary = ((net.minecraft.network.chat.MutableComponent) summary).append(Component.literal("\nUnique items: " + entries.size()).withStyle(s -> s.withColor(0x666666)));
        pages.add(Filterable.passThrough(summary));

        // Subsequent pages: items in groups of 14
        int itemsPerPage = 14;
        for (int i = 0; i < entries.size(); i += itemsPerPage) {
            Component page = Component.empty();
            int end = Math.min(i + itemsPerPage, entries.size());

            boolean first = true;
            for (int j = i; j < end; j++) {
                InventoryEntry entry = entries.get(j);
                if (!first) {
                    page = ((net.minecraft.network.chat.MutableComponent) page).append(Component.literal("\n"));
                }
                first = false;

                page = ((net.minecraft.network.chat.MutableComponent) page).append(Component.literal(entry.itemName() + ": ").withStyle(s -> s.withColor(0xFFFFFF)));
                page = ((net.minecraft.network.chat.MutableComponent) page).append(Component.literal(String.valueOf(entry.count())).withStyle(s -> s.withColor(0xFFFF55)));
            }
            pages.add(Filterable.passThrough(page));
        }

        return pages;
    }

    private static ItemStack createBookItem(List<Filterable<Component>> pages, String playerName) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);

        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough("Inventory Catalog"),
                playerName,
                0,
                pages,
                true
        );

        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        return book;
    }

    private static String friendlyName(String itemId) {
        String path = itemId.contains(":") ? itemId.split(":")[1] : itemId;
        return Arrays.stream(path.split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}
