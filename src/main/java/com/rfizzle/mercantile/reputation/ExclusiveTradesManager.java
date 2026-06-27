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

    public static void injectOffers(Villager villager, int playerScore) {
        if (!MercantileConfig.get().enableReputation) return;

        Map<String, List<ExclusiveTrade>> profTrades = PROFESSION_TRADES;
        List<ExclusiveTrade> crossTrades = CROSS_PROFESSION_TRADES;

        String profession = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession()).getPath();

        List<MerchantOffer> toInject = new ArrayList<>();
        RegistryAccess registries = villager.level().registryAccess();

        List<ExclusiveTrade> professionTrades = profTrades.get(profession);
        if (professionTrades != null) {
            for (ExclusiveTrade trade : professionTrades) {
                if (playerScore >= trade.minScore()) {
                    toInject.add(trade.createOffer(registries));
                }
            }
        }

        for (ExclusiveTrade trade : crossTrades) {
            if (playerScore >= trade.minScore()) {
                toInject.add(trade.createOffer(registries));
            }
        }

        if (toInject.isEmpty()) return;

        villager.getOffers().addAll(toInject);
        INJECTED_OFFERS.put(villager, toInject);
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

        List<ExclusiveTrade> professionTrades = profTrades.get(profession);
        if (professionTrades != null) {
            for (ExclusiveTrade trade : professionTrades) {
                if (playerScore < trade.minScore()) {
                    inaccessible.add(OfferIdentityHash.compute(trade.createOffer(registries)));
                }
            }
        }

        for (ExclusiveTrade trade : crossTrades) {
            if (playerScore < trade.minScore()) {
                inaccessible.add(OfferIdentityHash.compute(trade.createOffer(registries)));
            }
        }

        return inaccessible;
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

        int maxUses = json.has("max_uses") ? json.get("max_uses").getAsInt() : 12;
        int xpGain = json.has("xp_gain") ? json.get("xp_gain").getAsInt() : 1;
        float priceMultiplier = json.has("price_multiplier") ? json.get("price_multiplier").getAsFloat() : 0.05f;

        int minScore = json.has("min_tier_override")
                ? ReputationTier.fromName(json.get("min_tier_override").getAsString()).minScore()
                : defaultMinScore;

        return new ExclusiveTrade(input1, input2, output, maxUses, xpGain, priceMultiplier, minScore,
                enchantments, storedEnchantments);
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

    public record ExclusiveTrade(ItemCost input1, ItemCost input2, ItemStack output,
                                 int maxUses, int xpGain, float priceMultiplier, int minScore,
                                 List<EnchantmentSpec> enchantments, List<EnchantmentSpec> storedEnchantments) {
        public ExclusiveTrade {
            enchantments = enchantments == null ? List.of() : List.copyOf(enchantments);
            storedEnchantments = storedEnchantments == null ? List.of() : List.copyOf(storedEnchantments);
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
         * Builds the offer without resolving enchantment components. Used where no {@link RegistryAccess}
         * is available (the recipe-viewer trade index, built on resource reload). Enchantments are dropped
         * from the displayed result; the live merchant offer carries them via {@link #createOffer(RegistryAccess)}.
         */
        public MerchantOffer createOffer() {
            return buildOffer(output.copy());
        }

        /** Builds the offer with enchantment components resolved against {@code registries}. */
        public MerchantOffer createOffer(RegistryAccess registries) {
            ItemStack result = output.copy();
            applyEnchantments(result, registries);
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
