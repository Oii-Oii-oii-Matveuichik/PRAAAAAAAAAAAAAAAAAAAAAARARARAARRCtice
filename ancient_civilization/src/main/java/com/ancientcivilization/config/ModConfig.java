package com.ancientcivilization.config;

import net.minecraft.world.Difficulty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Root config. Loaded from {@code config/ancient_civilization.json} via Gson.
 * All fields are public so Gson can populate them; nested classes need no-arg constructors.
 */
public class ModConfig {
    public static ModConfig INSTANCE;

    public LocalDifficulty localDifficulty = new LocalDifficulty();
    public Squads squads = new Squads();
    public Animation animation = new Animation();
    public Reroll reroll = new Reroll();
    public ArmorRemap armorRemap = new ArmorRemap();
    public HpBuff hpBuff = new HpBuff();
    public Wanderers wanderers = new Wanderers();
    public RuinBuff ruinBuff = new RuinBuff();
    public Effects effects = new Effects();
    public March march = new March();
    public Warden warden = new Warden();

    // ---------------------------------------------------------------------
    // Local difficulty (per world difficulty level)
    // ---------------------------------------------------------------------

    public static class LocalDifficulty {
        /** Master switch for the whole local-difficulty subsystem. */
        public boolean enabled = true;
        /** Thresholds per world difficulty level. */
        public Levels levels = new Levels();
        /** Top-tier gear spawning (netherite / diamond / echo / siftite). */
        public TopTier topTier = new TopTier();
    }

    public static class Levels {
        /** Easy worlds: top tiers effectively never fire (local difficulty never reaches 3.5). */
        public LevelConfig easy = new LevelConfig(false, 3.5f);
        /** Normal worlds: fires near the top of the local-difficulty range (1.5–4.0). */
        public LevelConfig normal = new LevelConfig(true, 3.0f);
        /** Hard worlds: fires regularly (2.25–6.75). */
        public LevelConfig hard = new LevelConfig(true, 2.5f);

        public LevelConfig forDifficulty(Difficulty difficulty) {
            return switch (difficulty) {
                case EASY -> easy;
                case HARD -> hard;
                default -> normal;
            };
        }
    }

    public static class LevelConfig {
        public boolean enabled;
        public float threshold;

        public LevelConfig() {
        }

        public LevelConfig(boolean enabled, float threshold) {
            this.enabled = enabled;
            this.threshold = threshold;
        }
    }

    public static class TopTier {
        public boolean enabled = true;
        /** Per-slot chance per eligible mob. */
        public float chancePerSlot = 0.004f;
        /** Chance to roll a top-tier weapon. */
        public float chanceWeapon = 0.004f;
        /** Weighted items per armor slot. */
        public List<WeightedItem> helmet = defaultArmor("helmet");
        public List<WeightedItem> chestplate = defaultArmor("chestplate");
        public List<WeightedItem> leggings = defaultArmor("leggings");
        public List<WeightedItem> boots = defaultArmor("boots");
        /** Weighted top-tier weapons. */
        public List<WeightedItem> weapon = defaultWeapon();

        private static List<WeightedItem> defaultArmor(String slot) {
            List<WeightedItem> list = new ArrayList<>();
            list.add(new WeightedItem("minecraft:netherite_" + slot, 500));
            list.add(new WeightedItem("minecraft:diamond_" + slot, 200));
            list.add(new WeightedItem("create_deep_dark:echo_armor_" + slot, 200));
            list.add(new WeightedItem("the_sift:siftite_" + slot, 100));
            return list;
        }

        private static List<WeightedItem> defaultWeapon() {
            List<WeightedItem> list = new ArrayList<>();
            list.add(new WeightedItem("minecraft:netherite_sword", 60));
            list.add(new WeightedItem("minecraft:netherite_axe", 60));
            list.add(new WeightedItem("simplyswords:netherite_chakram", 10));
            list.add(new WeightedItem("simplyswords:netherite_claymore", 10));
            list.add(new WeightedItem("simplyswords:netherite_cutlass", 10));
            list.add(new WeightedItem("simplyswords:netherite_glaive", 10));
            list.add(new WeightedItem("simplyswords:netherite_greataxe", 10));
            list.add(new WeightedItem("simplyswords:netherite_greathammer", 10));
            list.add(new WeightedItem("simplyswords:netherite_katana", 10));
            list.add(new WeightedItem("simplyswords:netherite_rapier", 10));
            list.add(new WeightedItem("simplyswords:netherite_sai", 10));
            list.add(new WeightedItem("simplyswords:netherite_scythe", 10));
            return list;
        }
    }

    public static class WeightedItem {
        public String item;
        public int weight;

        public WeightedItem() {
        }

        public WeightedItem(String item, int weight) {
            this.item = item;
            this.weight = weight;
        }
    }

    // ---------------------------------------------------------------------
    // Squads
    // ---------------------------------------------------------------------

    public static class Squads {
        public boolean enabled = true;
        /** Squads spawn only on the surface (block light 15, open sky). Ancient-city squad overrides per type. */
        public boolean surfaceOnly = true;
        /** Global cooldown between squad events in a dimension, in ticks (600 = 5 min). */
        public long globalCooldownTicks = 600;
        public List<SquadType> types = defaultSquads();
    }

    public static class SquadType {
        public String id;
        public String description;
        /** Biome IDs the squad prefers (advisory; falls back to any surface spot). */
        public List<String> biomes = new ArrayList<>();
        /** Base interval between events, in ticks (5400 = 90 min). */
        public long baseTimerTicks = 5400;
        /** Shorter interval after a boosted (near-structure) event (1800 = 30 min). */
        public long boostedTimerTicks = 1800;
        /** Structure IDs that boost the squad (near-structure, boosted timer + F9). */
        public List<String> structures = new ArrayList<>();
        /** Bypasses the surface-only check (used by the ancient-city squad). */
        public boolean undergroundAllowed = false;
        public List<Member> members = new ArrayList<>();
    }

    public static class Member {
        public String role;
        public String entity;
        public String heldItem;
        /** slot -> item id: helmet / chestplate / leggings / boots. */
        public Map<String, String> armor = new LinkedHashMap<>();
    }

    private static List<SquadType> defaultSquads() {
        List<SquadType> list = new ArrayList<>();

        // Plains raiders
        SquadType plains = new SquadType();
        plains.id = "plains";
        plains.description = "Raiders of the plains";
        plains.biomes = List.of(
                "minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow",
                "minecraft:flower_plains", "minecraft:windswept_hills", "minecraft:savanna");
        plains.structures = List.of(
                "minecraft:ruined_portal", "nova_structures:hamlet", "nova_structures:wild_ruin",
                "nova_structures:well_oak", "terralith:fortified_village");
        plains.members = new ArrayList<>(List.of(
                hero("minecraft:zombie", "simplyswords:iron_katana", "iron"),
                soldier("minecraft:zombie", "simplyswords:iron_glaive", "iron", "helmet", "chestplate"),
                soldier("minecraft:zombie", "simplyswords:iron_claymore", "iron", "helmet", "leggings"),
                archer("minecraft:skeleton", "minecraft:bow"),
                peasant("minecraft:zombie", "minecraft:iron_sword")));
        list.add(plains);

        // Desert warriors
        SquadType desert = new SquadType();
        desert.id = "desert";
        desert.description = "Warriors of the desert";
        desert.biomes = List.of(
                "minecraft:desert", "minecraft:badlands", "minecraft:windswept_hills", "minecraft:savanna");
        desert.structures = List.of(
                "minecraft:desert_pyramid", "minecraft:ruined_portal_desert",
                "nova_structures:desert_ruins", "nova_structures:parched_camp",
                "nova_structures:badlands_miner_outpost", "nova_structures:well_desert",
                "nova_structures:tavern_desert", "terralith:desert_outpost",
                "terralith:fortified_desert_village", "terralith:rubble_desert");
        desert.members = new ArrayList<>(List.of(
                hero("minecraft:husk", "simplyswords:gold_katana", "golden"),
                soldier("minecraft:husk", "simplyswords:gold_glaive", "golden", "helmet", "chestplate"),
                soldier("minecraft:husk", "simplyswords:gold_claymore", "golden", "helmet", "leggings"),
                archer("variantsandventures:parched", "minecraft:bow"),
                peasant("minecraft:husk", "minecraft:iron_sword")));
        list.add(desert);

        // Drowned legion
        SquadType ocean = new SquadType();
        ocean.id = "ocean";
        ocean.description = "Legion of the drowned";
        ocean.biomes = List.of(
                "minecraft:ocean", "minecraft:warm_ocean", "minecraft:lukewarm_ocean",
                "minecraft:deep_ocean", "minecraft:river", "minecraft:beach");
        ocean.structures = List.of(
                "minecraft:shipwreck", "minecraft:ocean_ruin", "nova_structures:conduit_ruin");
        ocean.members = new ArrayList<>(List.of(
                hero("minecraft:drowned", "minecraft:trident", "copper"),
                soldier("minecraft:drowned", "minecraft:iron_sword", "copper", "helmet", "chestplate"),
                soldier("minecraft:drowned", "minecraft:iron_axe", "copper", "helmet", "leggings"),
                archer("minecraft:drowned", "minecraft:bow"),
                peasant("minecraft:drowned", "minecraft:iron_sword")));
        list.add(ocean);

        // Ancient city watch (spawns only inside minecraft:ancient_city)
        SquadType ancientCity = new SquadType();
        ancientCity.id = "ancient_city";
        ancientCity.description = "Watch of the ancient city";
        ancientCity.undergroundAllowed = true;
        ancientCity.baseTimerTicks = 2700;
        ancientCity.boostedTimerTicks = 1800;
        ancientCity.members = new ArrayList<>(List.of(
                hero("minecraft:zombie", "simplyswords:diamond_katana", "echo"),
                soldier("minecraft:zombie", "simplyswords:diamond_glaive", "echo", "helmet", "chestplate"),
                soldier("minecraft:zombie", "simplyswords:diamond_claymore", "echo", "helmet", "leggings"),
                archer("minecraft:skeleton", "minecraft:crossbow"),
                archer("minecraft:skeleton", "minecraft:bow")));
        list.add(ancientCity);

        return list;
    }

    private static Member hero(String entity, String weapon, String heavyMaterial) {
        Member m = new Member();
        m.role = "hero";
        m.entity = entity;
        m.heldItem = weapon;
        m.armor = fullArmor(heavyMaterial);
        return m;
    }

    private static Member soldier(String entity, String weapon, String heavyMaterial, String... slots) {
        Member m = new Member();
        m.role = "soldier";
        m.entity = entity;
        m.heldItem = weapon;
        m.armor = new LinkedHashMap<>();
        for (String slot : slots) {
            m.armor.put(slot, "luckysarmory:heavy_" + heavyMaterial + "_" + slot);
        }
        return m;
    }

    private static Member archer(String entity, String weapon) {
        Member m = new Member();
        m.role = "archer";
        m.entity = entity;
        m.heldItem = weapon;
        m.armor = new LinkedHashMap<>();
        m.armor.put("helmet", "chained_leather_armor:chainedleather_helmet");
        m.armor.put("chestplate", "chained_leather_armor:chainedleather_chestplate");
        m.armor.put("leggings", "chained_leather_armor:chainedleather_leggings");
        m.armor.put("boots", "chained_leather_armor:chainedleather_boots");
        return m;
    }

    private static Member peasant(String entity, String weapon) {
        Member m = new Member();
        m.role = "peasant";
        m.entity = entity;
        m.heldItem = weapon;
        return m;
    }

    private static Map<String, String> fullArmor(String heavyMaterial) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("helmet", "luckysarmory:heavy_" + heavyMaterial + "_helmet");
        map.put("chestplate", "luckysarmory:heavy_" + heavyMaterial + "_chestplate");
        map.put("leggings", "luckysarmory:heavy_" + heavyMaterial + "_leggings");
        map.put("boots", "luckysarmory:heavy_" + heavyMaterial + "_boots");
        return map;
    }

    // ---------------------------------------------------------------------
    // F2: skeleton-type -> melee-type reroll
    // ---------------------------------------------------------------------

    public static class Reroll {
        public boolean enabled = true;
        /** Chance that a skeleton-type cast spawns as its melee-type counterpart. */
        public float chance = 0.25f;
        /** Source entity -> replacement entity (wither_skeleton intentionally absent). */
        public Map<String, String> mapping = defaultMapping();

        private static Map<String, String> defaultMapping() {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("minecraft:skeleton", "minecraft:zombie");
            map.put("variantsandventures:parched", "minecraft:husk");
            map.put("variantsandventures:murk", "minecraft:drowned");
            map.put("minecraft:stray", "variantsandventures:gelid");
            map.put("variantsandventures:verdant", "variantsandventures:thicket");
            return map;
        }
    }

    // ---------------------------------------------------------------------
    // F3: heavy armor material remap by biome + copper oxidation by humidity
    // ---------------------------------------------------------------------

    public static class ArmorRemap {
        public boolean enabled = true;
        /** Material weights per biome category: copper / iron / golden. */
        public Map<String, Double> cold = weights(0.30, 0.60, 0.10);
        public Map<String, Double> temperate = weights(0.45, 0.40, 0.15);
        public Map<String, Double> hot = weights(0.30, 0.15, 0.55);
        public Map<String, Double> mesa = weights(0.25, 0.10, 0.65);
        public Map<String, Double> nether = weights(0.20, 0.15, 0.65);
        /** Copper oxidation stage weights: base / exposed / weathered / oxidized. */
        public Map<String, Double> oxidationWet = weights(0.10, 0.15, 0.30, 0.45);
        public Map<String, Double> oxidationTemperate = weights(0.35, 0.30, 0.25, 0.10);
        public Map<String, Double> oxidationDry = weights(0.70, 0.20, 0.10, 0.00);
        /** Biomes that are always fully oxidized. */
        public List<String> alwaysOxidizedBiomes = List.of(
                "minecraft:swamp", "minecraft:mangrove_swamp", "minecraft:old_growth_birch_forest",
                "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga",
                "minecraft:dark_forest", "minecraft:flower_forest", "minecraft:cherry_grove",
                "minecraft:lush_grove", "minecraft:jungle", "minecraft:jungle_hills",
                "minecraft:mangrove_swamps");
        /** Biomes treated as wet (higher oxidation chance). */
        public List<String> wetBiomes = List.of(
                "minecraft:jungle", "minecraft:jungle_hills", "minecraft:mangrove_swamp",
                "minecraft:mangrove_swamps", "minecraft:swamp", "minecraft:lush_grove",
                "minecraft:old_growth_birch_forest", "minecraft:old_growth_pine_taiga",
                "minecraft:old_growth_spruce_taiga", "minecraft:dark_forest",
                "minecraft:flower_forest", "minecraft:cherry_grove");
        /** Biomes treated as dry (low oxidation chance). */
        public List<String> dryBiomes = List.of(
                "minecraft:desert", "minecraft:badlands", "minecraft:windswept_hills",
                "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_savanna",
                "minecraft:windswept_desert", "minecraft:snowy_plains", "minecraft:snowy_taiga",
                "minecraft:snowy_mountains", "minecraft:ice_spikes", "minecraft:snowy_beach",
                "minecraft:snowy_savanna", "minecraft:snowy_savanna_plateau");

        private static Map<String, Double> weights(double... values) {
            Map<String, Double> map = new LinkedHashMap<>();
            String[] keys = {"copper", "iron", "golden", "base", "exposed", "weathered", "oxidized"};
            for (int i = 0; i < values.length; i++) {
                map.put(keys[i], values[i]);
            }
            return map;
        }
    }

    // ---------------------------------------------------------------------
    // F4: HP buff (undead family incl. V&V and squads)
    // ---------------------------------------------------------------------

    public static class HpBuff {
        public boolean enabled = true;
        /** Percent of max health added (confirmed: 10%). */
        public float percent = 0.10f;
        /** Minimum HP added regardless of percent. */
        public double min = 1.0;
    }

    // ---------------------------------------------------------------------
    // F8: stray wanderers at ruined portals
    // ---------------------------------------------------------------------

    public static class Wanderers {
        public boolean enabled = true;
        /** Scan interval per player, in ticks (200 = 10 s). */
        public int scanIntervalTicks = 200;
        /** Structure scan radius around each player. */
        public int detectionRadius = 64;
        /** One roll per portal structure instance (single chance for every portal). */
        public float chance = 0.10f;
        /** Optional biome whitelist for wanderer portals (empty = any biome). */
        public List<String> biomes = new ArrayList<>();
        /** Caste roll: soldier / hero only. */
        public List<WandererCaste> castes = defaultCastes();

        private static List<WandererCaste> defaultCastes() {
            List<WandererCaste> list = new ArrayList<>();

            WandererCaste soldier = new WandererCaste();
            soldier.role = "soldier";
            soldier.weight = 60;
            soldier.entity = "minecraft:zombie";
            soldier.heldItem = "simplyswords:iron_glaive";
            soldier.armor = new LinkedHashMap<>();
            soldier.armor.put("helmet", "luckysarmory:heavy_iron_helmet");
            soldier.armor.put("chestplate", "luckysarmory:heavy_iron_chestplate");
            list.add(soldier);

            WandererCaste hero = new WandererCaste();
            hero.role = "hero";
            hero.weight = 40;
            hero.entity = "minecraft:zombie";
            hero.heldItem = "simplyswords:iron_katana";
            hero.armor = fullArmor("iron");
            list.add(hero);

            return list;
        }
    }

    public static class WandererCaste {
        public String role;
        public int weight;
        public String entity;
        public String heldItem;
        public Map<String, String> armor = new LinkedHashMap<>();
    }

    // ---------------------------------------------------------------------
    // Ruin buff: squads spawning in ruins get a larger headcount
    // ---------------------------------------------------------------------

    public static class RuinBuff {
        public boolean enabled = true;
        /** Count multiplier for squads spawning in ruins (1.5 = 1.5x members). */
        public float countMultiplier = 1.5f;
        /** Structure IDs considered "ruins". */
        public List<String> ruinStructures = List.of(
                "minecraft:ruined_portal", "nova_structures:hamlet", "nova_structures:wild_ruin",
                "nova_structures:well_oak", "terralith:fortified_village");
    }

    // ---------------------------------------------------------------------
    // Generic mob effects (any status effect, any mob — config-driven)
    // ---------------------------------------------------------------------

    public static class Effects {
        public boolean enabled = true;
        public List<EffectEntry> entries = new ArrayList<>();
    }

    public static class EffectEntry {
        /** Status-effect ID, e.g. minecraft:strength or a modded effect. */
        public String effect;
        /** Amplifier (0 = first level). */
        public int amplifier = 0;
        /** Duration in ticks (0 = permanent). */
        public int durationTicks = 0;
        /** Per-mob chance (0..1). */
        public float chance = 1.0f;
        /** Entity-ID whitelist (empty = all entities). */
        public List<String> entities = new ArrayList<>();
        /** Roles the effect applies to (empty = any role): hero / soldier / archer / peasant. */
        public List<String> roles = new ArrayList<>();
        /** Squad IDs the effect applies to (empty = any squad). */
        public List<String> squads = new ArrayList<>();
    }

    // ---------------------------------------------------------------------
    // F10: squad march to village
    // ---------------------------------------------------------------------

    public static class March {
        public boolean enabled = true;
        /** Village trigger radius around the squad's target player. */
        public int triggerRadius = 128;
        /** Remote spawn distance beyond the village position. */
        public int spawnMin = 64;
        public int spawnMax = 96;
        /** Stop marching this close to the village. */
        public double approachDistance = 10.0;
        /** Squad types that can march (ground squads). */
        public List<String> appliesTo = List.of("plains", "desert");
    }

    // ---------------------------------------------------------------------
    // F7: warden-priority targeting for the ancient-city squad
    // ---------------------------------------------------------------------

    public static class Warden {
        public boolean enabled = true;
    }

    // ---------------------------------------------------------------------
    // Spawn animation (digging out)
    // ---------------------------------------------------------------------

    public static class Animation {
        public boolean enabled = true;
        /** Total animation length in ticks (45 = 2.25 s). */
        public int durationTicks = 45;
        /** Block/dust particles emitted per tick during the animation. */
        public int particleCount = 6;
        public float soundVolume = 0.6f;
        public float soundPitch = 1.0f;
    }
}
