package com.rfizzle.mercantile.reputation;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.rfizzle.mercantile.Mercantile;
import com.rfizzle.mercantile.api.ReputationTier;
import com.rfizzle.mercantile.config.MercantileConfig;
import com.rfizzle.mercantile.trade.OfferIdentityHash;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import com.rfizzle.mercantile.data.MercantileAttachments;
import com.rfizzle.mercantile.data.MercantileVillagerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ExclusiveTradesManager {

    private static final Gson GSON = new Gson();
    private static final String DATA_PATH = "exclusive_trades";
    private static final String CROSS_PROFESSION_KEY = "_mercantile";

    private static volatile Map<String, List<ExclusiveTrade>> PROFESSION_TRADES = Map.of();
    private static volatile List<ExclusiveTrade> CROSS_PROFESSION_TRADES = List.of();

    private static final Map<AbstractVillager, List<MerchantOffer>> INJECTED_OFFERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ExclusiveTradesManager() {
    }

    public static void init() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return Mercantile.id("exclusive_trades");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        loadTrades(manager);
                    }
                }
        );
    }

    public static void stripInjectedOffers(AbstractVillager merchant) {
        List<MerchantOffer> previouslyInjected = INJECTED_OFFERS.remove(merchant);
        if (previouslyInjected == null || previouslyInjected.isEmpty()) return;

        var offers = merchant.getOffers();
        for (MerchantOffer injected : previouslyInjected) {
            offers.remove(injected);
        }
    }

    /**
     * Injects exclusive offers without a player context. Advancement-gated trades
     * ({@code requires_advancement}) cannot be verified here and are skipped. Prefer
     * {@link #injectOffers(Villager, ServerPlayer, int)} whenever the opening player is known.
     */
    public static void injectOffers(Villager villager, int playerScore) {
        injectOffers(villager, null, playerScore);
    }

    public static void injectOffers(Villager villager, ServerPlayer player, int playerScore) {
        if (!MercantileConfig.get().enableReputation) return;

        Map<String, List<ExclusiveTrade>> profTrades = PROFESSION_TRADES;
        List<ExclusiveTrade> crossTrades = CROSS_PROFESSION_TRADES;

        String profession = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession()).getPath();

        List<MerchantOffer> toInject = new ArrayList<>();
        RegistryAccess registries = villager.level().registryAccess();
        RandomSource random = villager.getRandom();
        MercantileVillagerData villagerData = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);

        List<ExclusiveTrade> professionTrades = profTrades.get(profession);
        if (professionTrades != null) {
            for (ExclusiveTrade trade : professionTrades) {
                if (isAvailable(trade, player, playerScore)) {
                    toInject.add(resolveOffer(trade, registries, random, villagerData));
                }
            }
        }

        for (ExclusiveTrade trade : crossTrades) {
            if (isAvailable(trade, player, playerScore)) {
                toInject.add(resolveOffer(trade, registries, random, villagerData));
            }
        }

        if (toInject.isEmpty()) return;

        villager.getOffers().addAll(toInject);
        INJECTED_OFFERS.put(villager, toInject);
    }

    /**
     * Builds the offer for {@code trade}. Deterministic trades build fresh every time (their offer is a
     * pure function of the trade definition). A generative {@code enchant_randomly} trade is rolled once
     * and its result persisted on the villager under a stable per-template key, then restored verbatim on
     * later opens — so the drawn enchantment stays fixed (no free re-roll) and its {@link OfferIdentityHash}
     * is stable for the buy-lock, pin, and lock-eviction systems.
     */
    private static MerchantOffer resolveOffer(ExclusiveTrade trade, RegistryAccess registries,
                                              RandomSource random, MercantileVillagerData villagerData) {
        if (trade.enchantRandomly() == null) {
            return trade.createOffer(registries);
        }
        String key = trade.generativeKey();
        var ctx = registries.createSerializationContext(NbtOps.INSTANCE);
        CompoundTag stored = villagerData.getGenerativeOffer(key);
        if (stored != null) {
            MerchantOffer parsed = MerchantOffer.CODEC.parse(ctx, stored)
                    .resultOrPartial(Mercantile.LOGGER::error).orElse(null);
            if (parsed != null) return parsed;
        }
        MerchantOffer rolled = trade.createOffer(registries, random);
        CompoundTag encoded = (CompoundTag) MerchantOffer.CODEC.encodeStart(ctx, rolled)
                .resultOrPartial(Mercantile.LOGGER::error).orElse(null);
        if (encoded != null) villagerData.putGenerativeOffer(key, encoded);
        return rolled;
    }

    /**
     * Whether {@code trade} is offerable to a player at {@code playerScore}. Combines the reputation
     * gate with the optional {@code requires_advancement} gate. When the trade names an advancement but
     * no player is available (e.g. the wandering-trader path), the trade is treated as unavailable — it
     * is never surfaced to a buyer whose progression cannot be checked.
     */
    private static boolean isAvailable(ExclusiveTrade trade, ServerPlayer player, int playerScore) {
        if (playerScore < trade.minScore()) return false;
        return advancementSatisfied(trade, player);
    }

    private static boolean advancementSatisfied(ExclusiveTrade trade, ServerPlayer player) {
        if (trade.requiresAdvancement().isEmpty()) return true;
        if (player == null) return false;
        AdvancementHolder holder = player.server.getAdvancements().get(trade.requiresAdvancement().get());
        if (holder == null) return false;
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    public static void injectWanderingTraderOffer(WanderingTrader trader, int playerScore) {
        MercantileConfig config = MercantileConfig.get();
        if (!config.enableReputation || !config.enableWanderingTraderRep) return;

        // Requirement: at least TRUSTED (300)
        if (playerScore < ReputationTier.TRUSTED.minScore()) return;

        List<ExclusiveTrade> pool = PROFESSION_TRADES.get("wandering_trader");
        if (pool == null || pool.isEmpty()) return;

        MercantileVillagerData data = trader.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);
        CompoundTag storedOfferTag = data.getWanderingTraderOfferTag();
        MerchantOffer offer = null;

        if (storedOfferTag != null) {
            offer = MerchantOffer.CODEC.parse(trader.level().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), storedOfferTag)
                    .resultOrPartial(Mercantile.LOGGER::error).orElse(null);
        }

        if (offer == null) {
            List<ExclusiveTrade> qualifying = pool.stream()
                    .filter(t -> playerScore >= t.minScore())
                    .toList();
            if (qualifying.isEmpty()) return;

            ExclusiveTrade selected = qualifying.get(trader.getRandom().nextInt(qualifying.size()));
            offer = selected.createOffer(trader.level().registryAccess());
            data.setWanderingTraderOfferTag((CompoundTag) MerchantOffer.CODEC.encodeStart(trader.level().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), offer)
                    .resultOrPartial(Mercantile.LOGGER::error).orElse(null));
        }

        if (offer != null) {
            trader.getOffers().add(offer);
            INJECTED_OFFERS.put(trader, List.of(offer));
        }
    }

    /**
     * Computes the set of identity hashes for every exclusive trade available to {@code villager}'s
     * profession plus all cross-profession trades that the player CANNOT currently access
     * ({@code playerScore < trade.minScore()}). Used to evict stale lock-hash entries when the
     * player's reputation drops below an exclusive trade's threshold.
     */
    public static Set<String> getInaccessibleExclusiveHashes(Villager villager, int playerScore) {
        Map<String, List<ExclusiveTrade>> profTrades = PROFESSION_TRADES;
        List<ExclusiveTrade> crossTrades = CROSS_PROFESSION_TRADES;

        String profession = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession()).getPath();

        Set<String> inaccessible = new HashSet<>();
        RegistryAccess registries = villager.level().registryAccess();
        MercantileVillagerData villagerData = villager.getAttachedOrCreate(MercantileAttachments.VILLAGER_DATA);

        List<ExclusiveTrade> professionTrades = profTrades.get(profession);
        if (professionTrades != null) {
            for (ExclusiveTrade trade : professionTrades) {
                if (playerScore < trade.minScore()) {
                    addInaccessibleHash(inaccessible, trade, registries, villagerData);
                }
            }
        }

        for (ExclusiveTrade trade : crossTrades) {
            if (playerScore < trade.minScore()) {
                addInaccessibleHash(inaccessible, trade, registries, villagerData);
            }
        }

        return inaccessible;
    }

    /**
     * Adds {@code trade}'s identity hash to the eviction set. For a generative trade the hash must match
     * the offer the player actually locked, so it uses the persisted rolled offer; a generative trade with
     * no persisted roll (never offered to this player) has no lock to evict and is skipped.
     */
    private static void addInaccessibleHash(Set<String> inaccessible, ExclusiveTrade trade,
                                            RegistryAccess registries, MercantileVillagerData villagerData) {
        if (trade.enchantRandomly() != null) {
            CompoundTag stored = villagerData.getGenerativeOffer(trade.generativeKey());
            if (stored == null) return;
            MerchantOffer offer = MerchantOffer.CODEC
                    .parse(registries.createSerializationContext(NbtOps.INSTANCE), stored)
                    .resultOrPartial(Mercantile.LOGGER::error).orElse(null);
            if (offer != null) inaccessible.add(OfferIdentityHash.compute(offer));
            return;
        }
        inaccessible.add(OfferIdentityHash.compute(trade.createOffer(registries)));
    }

    // @VisibleForTesting
    public static void loadTrades(ResourceManager manager) {
        Map<String, List<ExclusiveTrade>> nextProf = new HashMap<>();
        List<ExclusiveTrade> nextCross = new ArrayList<>();

        Map<ResourceLocation, List<Resource>> found = manager.listResourceStacks(
                DATA_PATH, id -> id.getPath().endsWith(".json"));

        for (var entry : found.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            String path = fileId.getPath();
            String professionName = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);

            boolean isCrossProfession = CROSS_PROFESSION_KEY.equals(professionName);

            List<ExclusiveTrade> merged = isCrossProfession
                    ? nextCross
                    : nextProf.computeIfAbsent(professionName, k -> new ArrayList<>());

            for (Resource resource : entry.getValue()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json == null) continue;

                    // File-level Fabric resource conditions: a failing gate skips the whole file
                    // silently — the entries are intentionally absent, not malformed.
                    if (!conditionsMatch(json)) continue;

                    if (json.has("replace") && json.get("replace").getAsBoolean()) {
                        merged.clear();
                    }

                    int defaultMinScore = ReputationTier.fromName(
                            json.has("min_tier") ? json.get("min_tier").getAsString() : "trusted").minScore();

                    if (!json.has("trades")) continue;

                    JsonArray trades = json.getAsJsonArray("trades");
                    for (JsonElement tradeElem : trades) {
                        if (!tradeElem.isJsonObject()) continue;
                        JsonObject tradeJson = tradeElem.getAsJsonObject();

                        // Per-trade Fabric resource conditions: a failing gate skips this entry
                        // silently. It is not a malformed trade, so no warning and no error count.
                        if (!conditionsMatch(tradeJson)) continue;

                        try {
                            ExclusiveTrade trade = parseTrade(tradeJson, defaultMinScore);
                            if (trade != null) {
                                merged.add(trade);
                            }
                        } catch (Exception e) {
                            Mercantile.LOGGER.warn("Skipping malformed exclusive trade in {}: {}", fileId, e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    Mercantile.LOGGER.error("Failed to load exclusive trades: {}", fileId, e);
                }
            }
        }

        // Deep-copy list values before publishing immutable snapshot
        Map<String, List<ExclusiveTrade>> immutableProf = new HashMap<>();
        for (var e : nextProf.entrySet()) {
            immutableProf.put(e.getKey(), List.copyOf(e.getValue()));
        }
        // Compute summary counts from the local accumulators before publishing — never read back
        // the volatile fields we just wrote (mc-shared-state Rule 2: compute before publish).
        int profCount = immutableProf.values().stream().mapToInt(List::size).sum();
        int crossCount = nextCross.size();

        PROFESSION_TRADES = Map.copyOf(immutableProf);
        CROSS_PROFESSION_TRADES = List.copyOf(nextCross);

        Mercantile.LOGGER.info("Loaded {} exclusive trades ({} profession, {} cross-profession)",
                profCount + crossCount, profCount, crossCount);
    }

    /**
     * Evaluates a {@code fabric:load_conditions} array on a trade file root or an individual trade
     * entry. An object with no conditions is unconditionally present. A failed decode is treated as a
     * non-match — the entry is skipped silently rather than spamming the log.
     *
     * <p>Conditions are tested against {@link RegistryAccess#EMPTY} because this reload listener is not
     * threaded a live registry. Mod-presence gates ({@code fabric:all_mods_loaded}, {@code fabric:not},
     * …) never dereference the provider and work here; registry-dependent conditions
     * ({@code fabric:registry_contains}, {@code fabric:tags_populated}) are out of scope.
     */
    private static boolean conditionsMatch(JsonObject json) {
        JsonElement conditions = json.get(ResourceConditions.CONDITIONS_KEY);
        if (conditions == null) return true;
        return ResourceCondition.LIST_CODEC.parse(JsonOps.INSTANCE, conditions)
                .result()
                .map(list -> list.stream().allMatch(condition -> condition.test(RegistryAccess.EMPTY)))
                .orElse(false);
    }

    @VisibleForTesting
    static ExclusiveTrade parseTrade(JsonObject json, int defaultMinScore) {
        ItemCost input1 = parseItemCost(json.getAsJsonObject("input_1"));
        if (input1 == null) return null;

        ItemCost input2 = json.has("input_2") ? parseItemCost(json.getAsJsonObject("input_2")) : null;

        JsonObject outputJson = json.getAsJsonObject("output");
        ItemStack output = parseItemStack(outputJson);
        if (output == null || output.isEmpty()) return null;

        // Component specs (output-only). Resolved against the live registry in createOffer().
        List<EnchantmentSpec> enchantments = List.of();
        List<EnchantmentSpec> storedEnchantments = List.of();
        if (outputJson.has("components")) {
            JsonObject components = outputJson.getAsJsonObject("components");
            enchantments = parseEnchantmentSpecs(components, "enchantments");
            storedEnchantments = parseEnchantmentSpecs(components, "stored_enchantments");
        }

        // Generative enchant-book output: draw one enchantment from a tag at a level policy, resolved at
        // offer time. Mutually exclusive with fixed enchantment components — a trade that names both is
        // malformed and skipped with a warning rather than silently favouring one.
        EnchantRandomlySpec enchantRandomly = parseEnchantRandomly(outputJson);
        if (enchantRandomly != null && (!enchantments.isEmpty() || !storedEnchantments.isEmpty())) {
            Mercantile.LOGGER.warn("Exclusive trade output has both enchant_randomly and fixed enchantment "
                    + "components; skipping the entry (they are mutually exclusive)");
            return null;
        }

        int maxUses = json.has("max_uses") ? json.get("max_uses").getAsInt() : 12;
        int xpGain = json.has("xp_gain") ? json.get("xp_gain").getAsInt() : 1;
        float priceMultiplier = json.has("price_multiplier") ? json.get("price_multiplier").getAsFloat() : 0.05f;

        int minScore = json.has("min_tier_override")
                ? ReputationTier.fromName(json.get("min_tier_override").getAsString()).minScore()
                : defaultMinScore;

        Optional<ResourceLocation> requiresAdvancement = Optional.empty();
        if (json.has("requires_advancement")) {
            ResourceLocation advId = ResourceLocation.tryParse(json.get("requires_advancement").getAsString());
            if (advId == null) {
                Mercantile.LOGGER.warn("Invalid requires_advancement id in exclusive trade: {}",
                        json.get("requires_advancement").getAsString());
            } else {
                requiresAdvancement = Optional.of(advId);
            }
        }

        return new ExclusiveTrade(input1, input2, output, maxUses, xpGain, priceMultiplier, minScore,
                enchantments, storedEnchantments, requiresAdvancement, enchantRandomly);
    }

    /**
     * Parses the generative {@code enchant_randomly} + {@code level} fields off a trade {@code output}.
     * {@code enchant_randomly} is an enchantment tag id (a leading {@code #} is accepted and stripped);
     * {@code level} is a {@link LevelPolicy} name defaulting to {@code mid}. Returns {@code null} when no
     * generative output is declared or the tag id is unparseable (warned).
     */
    private static EnchantRandomlySpec parseEnchantRandomly(JsonObject outputJson) {
        if (outputJson == null || !outputJson.has("enchant_randomly")) return null;
        String raw = outputJson.get("enchant_randomly").getAsString();
        String stripped = raw.startsWith("#") ? raw.substring(1) : raw;
        ResourceLocation tagId = ResourceLocation.tryParse(stripped);
        if (tagId == null) {
            Mercantile.LOGGER.warn("Invalid enchant_randomly tag in exclusive trade: {}", raw);
            return null;
        }
        LevelPolicy policy = outputJson.has("level")
                ? LevelPolicy.fromName(outputJson.get("level").getAsString())
                : LevelPolicy.MID;
        return new EnchantRandomlySpec(TagKey.create(Registries.ENCHANTMENT, tagId), policy);
    }

    /**
     * Reads an array of {@code { "id": "minecraft:sharpness", "level": 5 }} entries under {@code key}.
     * Stores string IDs only; enchantment Holders are not resolved until offer construction, so this
     * needs no registry access. Returns an immutable, possibly empty list.
     */
    private static List<EnchantmentSpec> parseEnchantmentSpecs(JsonObject components, String key) {
        if (components == null || !components.has(key)) return List.of();
        List<EnchantmentSpec> specs = new ArrayList<>();
        for (JsonElement element : components.getAsJsonArray(key)) {
            JsonObject entry = element.getAsJsonObject();
            String id = entry.get("id").getAsString();
            int level = entry.has("level") ? entry.get("level").getAsInt() : 1;
            specs.add(new EnchantmentSpec(id, level));
        }
        return List.copyOf(specs);
    }

    private static ItemCost parseItemCost(JsonObject json) {
        if (json == null) return null;
        Item item = parseItem(json.get("item").getAsString());
        if (item == null) return null;
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        return new ItemCost(item, count);
    }

    private static ItemStack parseItemStack(JsonObject json) {
        if (json == null) return null;
        Item item = parseItem(json.get("item").getAsString());
        if (item == null) return null;
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        return new ItemStack(item, count);
    }

    private static Item parseItem(String id) {
        ResourceLocation itemId = ResourceLocation.tryParse(id);
        if (itemId == null) {
            Mercantile.LOGGER.warn("Invalid item ID in exclusive trade: {}", id);
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            Mercantile.LOGGER.warn("Unknown item in exclusive trade: {}", id);
            return null;
        }
        return item;
    }

    public static Map<String, List<ExclusiveTrade>> professionTradesSnapshot() {
        return Collections.unmodifiableMap(PROFESSION_TRADES);
    }

    public static List<ExclusiveTrade> crossProfessionTradesSnapshot() {
        return Collections.unmodifiableList(CROSS_PROFESSION_TRADES);
    }

    @VisibleForTesting
    public static void setSnapshotsForTesting(Map<String, List<ExclusiveTrade>> profession,
                                              List<ExclusiveTrade> crossProfession) {
        PROFESSION_TRADES = Map.copyOf(profession);
        CROSS_PROFESSION_TRADES = List.copyOf(crossProfession);
    }

    /** An enchantment to apply to a trade output, by registry ID and level. Resolved at offer time. */
    public record EnchantmentSpec(String id, int level) {
    }

    /**
     * Level chosen for a {@link EnchantRandomlySpec} draw, relative to the picked enchantment's own
     * {@code [min, max]} level range. {@code MID} is the rounded-up midpoint floored at min; {@code MAX}
     * is the enchantment's maximum level.
     */
    public enum LevelPolicy {
        MID,
        MAX;

        public int computeLevel(int min, int max) {
            return switch (this) {
                case MID -> Math.max(min, (max + 1) / 2);
                case MAX -> max;
            };
        }

        /** Parses a policy name, defaulting to {@link #MID} for absent/unknown values (warned). */
        public static LevelPolicy fromName(String name) {
            if (name == null) return MID;
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "max" -> MAX;
                case "mid" -> MID;
                default -> {
                    Mercantile.LOGGER.warn("Unknown level policy '{}' in exclusive trade; defaulting to mid", name);
                    yield MID;
                }
            };
        }
    }

    /** A generative enchant-book output: pick one enchantment from {@code tag} at {@code policy} level. */
    public record EnchantRandomlySpec(TagKey<Enchantment> tag, LevelPolicy policy) {
    }

    public record ExclusiveTrade(ItemCost input1, ItemCost input2, ItemStack output,
                                 int maxUses, int xpGain, float priceMultiplier, int minScore,
                                 List<EnchantmentSpec> enchantments, List<EnchantmentSpec> storedEnchantments,
                                 Optional<ResourceLocation> requiresAdvancement,
                                 EnchantRandomlySpec enchantRandomly) {
        public ExclusiveTrade {
            enchantments = enchantments == null ? List.of() : List.copyOf(enchantments);
            storedEnchantments = storedEnchantments == null ? List.of() : List.copyOf(storedEnchantments);
            requiresAdvancement = requiresAdvancement == null ? Optional.empty() : requiresAdvancement;
        }

        /** Enchantment-carrying trade with no advancement gate or generative output. */
        public ExclusiveTrade(ItemCost input1, ItemCost input2, ItemStack output,
                              int maxUses, int xpGain, float priceMultiplier, int minScore,
                              List<EnchantmentSpec> enchantments, List<EnchantmentSpec> storedEnchantments) {
            this(input1, input2, output, maxUses, xpGain, priceMultiplier, minScore,
                    enchantments, storedEnchantments, Optional.empty(), null);
        }

        /** Component-free trade — convenience for callers and tests that ship no enchantments. */
        public ExclusiveTrade(ItemCost input1, ItemCost input2, ItemStack output,
                              int maxUses, int xpGain, float priceMultiplier, int minScore) {
            this(input1, input2, output, maxUses, xpGain, priceMultiplier, minScore, List.of(), List.of());
        }

        @Override
        public ItemStack output() {
            return output.copy();
        }

        /**
         * A stable key identifying this generative trade's <em>template</em> — the inputs, output item,
         * tier, stock, and the enchant tag/policy — but NOT the rolled enchantment. Used to persist and
         * restore the drawn book per villager so it stays fixed across trade-screen re-opens. Only valid
         * for generative trades ({@code enchantRandomly != null}).
         */
        public String generativeKey() {
            return "gen|" + costKey(input1) + "|" + (input2 == null ? "-" : costKey(input2))
                    + "|" + BuiltInRegistries.ITEM.getKey(output.getItem()) + "x" + output.getCount()
                    + "|" + minScore + "|" + maxUses + "|" + xpGain + "|" + priceMultiplier
                    + "|" + enchantRandomly.tag().location() + "|" + enchantRandomly.policy();
        }

        private static String costKey(ItemCost cost) {
            return BuiltInRegistries.ITEM.getKey(cost.item().value()) + "x" + cost.count();
        }

        /**
         * Builds the offer without resolving enchantment components. Used where no {@link RegistryAccess}
         * is available (the recipe-viewer trade index, built on resource reload). Enchantments are dropped
         * from the displayed result; the live merchant offer carries them via {@link #createOffer(RegistryAccess)}.
         */
        public MerchantOffer createOffer() {
            return buildOffer(output.copy());
        }

        /**
         * Builds the offer with enchantment components resolved against {@code registries}. A generative
         * {@code enchant_randomly} output is rolled with a throwaway {@link RandomSource}; prefer
         * {@link #createOffer(RegistryAccess, RandomSource)} where a merchant's RNG is available so the
         * roll participates in world randomness.
         */
        public MerchantOffer createOffer(RegistryAccess registries) {
            return createOffer(registries, RandomSource.create());
        }

        /** Builds the offer, resolving fixed enchantments and rolling any generative output with {@code random}. */
        public MerchantOffer createOffer(RegistryAccess registries, RandomSource random) {
            ItemStack result = output.copy();
            applyEnchantments(result, registries);
            applyGenerativeEnchant(result, registries, random);
            return buildOffer(result);
        }

        private MerchantOffer buildOffer(ItemStack result) {
            if (input2 != null) {
                return new MerchantOffer(input1, Optional.of(input2), result, maxUses, xpGain, priceMultiplier);
            }
            return new MerchantOffer(input1, result, maxUses, xpGain, priceMultiplier);
        }

        private void applyEnchantments(ItemStack stack, RegistryAccess registries) {
            if (enchantments.isEmpty() && storedEnchantments.isEmpty()) return;
            HolderLookup.RegistryLookup<Enchantment> lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
            if (!enchantments.isEmpty()) {
                ItemEnchantments resolved = resolve(enchantments, lookup);
                if (!resolved.isEmpty()) stack.set(DataComponents.ENCHANTMENTS, resolved);
            }
            if (!storedEnchantments.isEmpty()) {
                ItemEnchantments resolved = resolve(storedEnchantments, lookup);
                if (!resolved.isEmpty()) stack.set(DataComponents.STORED_ENCHANTMENTS, resolved);
            }
        }

        /**
         * Rolls a generative {@code enchant_randomly} output onto {@code stack}. Resolves the tag against
         * the live enchantment registry, picks one holder uniformly with {@code random}, and stores it at
         * the policy level as a book enchantment. An unresolved or empty tag warns and leaves the stack a
         * plain item rather than failing the offer.
         */
        private void applyGenerativeEnchant(ItemStack stack, RegistryAccess registries, RandomSource random) {
            if (enchantRandomly == null) return;
            HolderLookup.RegistryLookup<Enchantment> lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
            Optional<HolderSet.Named<Enchantment>> holders = lookup.get(enchantRandomly.tag());
            if (holders.isEmpty() || holders.get().size() == 0) {
                Mercantile.LOGGER.warn("Empty or unknown enchantment tag in exclusive trade: {}",
                        enchantRandomly.tag().location());
                return;
            }
            HolderSet.Named<Enchantment> set = holders.get();
            Holder<Enchantment> picked = set.get(random.nextInt(set.size()));
            int level = enchantRandomly.policy().computeLevel(
                    picked.value().getMinLevel(), picked.value().getMaxLevel());
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            mutable.set(picked, level);
            stack.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        }

        private static ItemEnchantments resolve(List<EnchantmentSpec> specs,
                                                HolderLookup.RegistryLookup<Enchantment> lookup) {
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            for (EnchantmentSpec spec : specs) {
                ResourceLocation id = ResourceLocation.tryParse(spec.id());
                if (id == null) {
                    Mercantile.LOGGER.warn("Invalid enchantment ID in exclusive trade: {}", spec.id());
                    continue;
                }
                Optional<Holder.Reference<Enchantment>> holder =
                        lookup.get(ResourceKey.create(Registries.ENCHANTMENT, id));
                if (holder.isEmpty()) {
                    Mercantile.LOGGER.warn("Unknown enchantment in exclusive trade: {}", id);
                    continue;
                }
                mutable.set(holder.get(), spec.level());
            }
            return mutable.toImmutable();
        }
    }
}
