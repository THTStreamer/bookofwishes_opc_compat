# Book of Wishes - OPC Compat

A Minecraft 1.21.1 NeoForge compatibility mod that integrates [The Book of Wishes](https://github.com/THTStreamer/bookofwishes) with [Open Parties and Claims](https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims) (OPC).

## What It Does

The Book of Wishes lets players write wishes in a magical book, which an AI entity reads and grants (for a price). This compat mod makes that AI **claim-aware** — it knows about your claimed land, the chests inside it, and can interact with your stored items.

Without this mod, the Book of Wishes has no concept of player claims or storage. With it, the AI understands your base, your chests, and what's inside them.

## Features

- **Claim-aware AI context** — The AI receives information about which chunks you've claimed via OPC, including party members' claims
- **Chest scanning** — The AI can scan all chests, shulker boxes, and other containers within your claimed chunks and see what's inside
- **Storage catalog** — Generate a written book listing all items found in your claimed storage, sorted by quantity
- **Claim-aware wish actions** — New action types that interact with claimed storage:
  - `scan_player_storage` — Scans all containers in claimed chunks and creates an inventory catalog book
  - `fill_chests` — Fills empty slots in claimed chests with a specified item
- **Smart action conversion** — Automatically converts the AI's `give_item` responses to `fill_chests` when your wish mentions chests, storage, or your base — so items go IN your chests, not just into your inventory
- **Party claim support** — Includes claims from all party members when scanning (configurable)

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x
- Java 21
- [The Book of Wishes](https://github.com/THTStreamer/bookofwishes) `1.0.0+`
- [Open Parties and Claims](https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims) `0.27.0+`

## Installation

1. Install [The Book of Wishes](https://github.com/THTStreamer/bookofwishes) and its dependency [Ollama](https://ollama.com/)
2. Install [Open Parties and Claims](https://www.curseforge.com/minecraft/mc-mods/open-parties-and-claims)
3. Place `bookofwishes_opc_compat-1.0.0.jar` in your `mods/` folder alongside both base mods

## Configuration

On first launch, generates `config/bookofwishes_opc_compat.toml`:

```toml
[compat]
  # Enable scanning the player's OPC claimed chunks for storage info
  enable_claim_scanning = true

  # Include claims from all party members (not just the player's own)
  include_party_claims = true

  # Maximum number of chunks to scan for storage (higher = slower)
  max_claim_scan_chunks = 1024

  # Step size when scanning chunks for storage (smaller = more thorough)
  storage_scan_step = 2
```

## How It Works

### AI Context Enrichment

When a player submits a wish, the compat mod intercepts the world context data sent to the AI and adds:

- **`player_base`** — A summary of the player's claimed areas (chunk counts per dimension)
- **`base_storage`** — A list of all containers found in claimed chunks, with their contents
- **`base_storage_summary`** — A readable summary of stored items (e.g., "64x diamond, 128x iron_ingot")

This gives the AI full knowledge of your claimed base and what you have stored.

### New Action Types

The mod adds two custom action types that the AI can use:

#### `scan_player_storage`

Scans all containers in the player's claimed chunks and creates a written book listing everything found.

```json
{ "type": "scan_player_storage" }
```

#### `fill_chests`

Fills empty slots in claimed chests with a specified item.

```json
{ "type": "fill_chests", "item": "minecraft:diamond", "count_per_slot": 64 }
```

- `item` — The item ID to fill with
- `count_per_slot` — How many items per slot (1-64, defaults to 64)

### Smart Action Conversion

The mod monitors the AI's responses. If your wish mentions chests, storage, or your base (e.g., "fill my chests with diamonds"), but the AI responds with a `give_item` action (items go to inventory), the mod automatically converts it to `fill_chests` (items go in chests).

This works regardless of what the AI generates — it's a safety net at the code level.

### Example Wishes

| Wish | Action Used |
|------|-------------|
| "I wish for all my chests to contain diamonds" | `fill_chests` (all empty slots in all chests) |
| "Fill my storage with iron ingots" | `fill_chests` (all empty slots) |
| "Put a stack of diamonds in a chest" | `fill_chests` (one slot in one chest) |
| "What's in my base?" | `scan_player_storage` (creates catalog book) |
| "Give me 64 diamonds" | `give_item` (inventory — no chest mentioned) |
| "I wish for diamonds" | `give_item` (inventory — no chest mentioned) |

## Building from Source

```bash
git clone https://github.com/THTStreamer/bookofwishes_opc_compat.git
cd bookofwishes_opc_compat
./gradlew build
```

The built jar will be in `build/libs/`.

**Note:** You'll need the Book of Wishes and OPC mod JARs available as dependencies. See `build.gradle` for the expected paths.

## Compatibility

| Mod | Version |
|-----|---------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x |
| The Book of Wishes | 1.0.0+ |
| Open Parties and Claims | 0.27.0+ |

## License

MIT
