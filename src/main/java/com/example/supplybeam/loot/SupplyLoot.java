package com.example.supplybeam.loot;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 空投奖池：四档稀有度各自的加权物品池（独立模式开箱时按档位条数结算）。
 * 安装 Manhunt 时开箱改为触发对应档位的资源抽奖，本奖池作为无 Manhunt 时的独立奖池。
 * 物品与权重在此调整；档位越高越靠近"毕业物资"，传奇档低概率开出鞘翅。
 */
public final class SupplyLoot {
    private SupplyLoot() {}

    private static final RandomSource RNG = RandomSource.create();

    /** 随机附魔池（原版命名空间 id）。 */
    private static final String[] ENCHANT_POOL = {
        "sharpness", "smite", "bane_of_arthropods", "efficiency", "unbreaking", "protection",
        "projectile_protection", "fire_protection", "blast_protection", "thorns", "power", "punch",
        "flame", "infinity", "looting", "fortune", "silk_touch", "mending", "feather_falling",
        "depth_strider", "soul_speed", "swift_sneak", "quick_charge", "impaling", "loyalty",
        "riptide", "channeling", "respiration", "aqua_affinity", "frost_walker", "knockback",
        "fire_aspect", "sweeping_edge", "lunge", "wind_burst", "density", "breach"
    };
    private static final List<String> SINGLE_LEVEL = List.of(
        "silk_touch", "mending", "infinity", "aqua_affinity", "channeling", "wind_burst");

    public interface Entry {
        ItemStack roll(HolderLookup.Provider registries);

        int weight();
    }

    /** 普通物品堆：物品 + 数量区间 + 权重。 */
    private record Simple(Item item, int min, int max, int weight) implements Entry {
        @Override
        public ItemStack roll(HolderLookup.Provider registries) {
            return new ItemStack(item, min + RNG.nextInt(max - min + 1));
        }
    }

    /** 附魔装备：随机选一条适用附魔（等级受档位上限约束）。 */
    private record Enchanted(Item item, int weight, int levelCap) implements Entry {
        @Override
        public ItemStack roll(HolderLookup.Provider registries) {
            return enchantItem(registries, new ItemStack(item), levelCap);
        }
    }

    /** 随机附魔书。 */
    private record Book(int weight, int levelCap) implements Entry {
        @Override
        public ItemStack roll(HolderLookup.Provider registries) {
            return randomBook(registries, levelCap);
        }
    }

    // ==================== 稀有补给（蓝，档0）：生存基础物资 ====================
    private static final List<Entry> TIER_POOL_0 = List.of(
        new Simple(Items.BREAD, 2, 4, 6), new Simple(Items.COOKED_BEEF, 2, 3, 5),
        new Simple(Items.BAKED_POTATO, 3, 6, 3), new Simple(Items.TORCH, 8, 16, 5),
        new Simple(Items.IRON_INGOT, 2, 4, 5), new Simple(Items.COAL, 4, 8, 4),
        new Simple(Items.ARROW, 8, 16, 4), new Simple(Items.OAK_LOG, 4, 8, 3),
        new Simple(Items.STRING, 2, 5, 3), new Simple(Items.LEATHER, 2, 4, 2),
        new Simple(Items.SWEET_BERRIES, 3, 6, 3), new Simple(Items.IRON_PICKAXE, 1, 1, 1),
        new Simple(Items.IRON_AXE, 1, 1, 1), new Simple(Items.CAMPFIRE, 1, 1, 1)
    );

    // ==================== 罕见补给（绿，档1）：成型的装备与资源 ====================
    private static final List<Entry> TIER_POOL_1 = List.of(
        new Simple(Items.GOLD_INGOT, 2, 5, 5), new Simple(Items.ARROW, 16, 32, 3),
        new Simple(Items.IRON_CHESTPLATE, 1, 1, 2), new Simple(Items.IRON_HELMET, 1, 1, 2),
        new Simple(Items.IRON_LEGGINGS, 1, 1, 2), new Simple(Items.IRON_BOOTS, 1, 1, 2),
        new Simple(Items.BOW, 1, 1, 2), new Simple(Items.SHIELD, 1, 1, 2),
        new Simple(Items.GOLDEN_CARROT, 3, 6, 3), new Simple(Items.AMETHYST_SHARD, 3, 6, 3),
        new Simple(Items.BOOK, 2, 4, 3), new Simple(Items.IRON_BLOCK, 1, 2, 2),
        new Simple(Items.OBSIDIAN, 2, 4, 2), new Simple(Items.TNT, 1, 3, 2),
        new Simple(Items.COMPASS, 1, 1, 1), new Simple(Items.EXPERIENCE_BOTTLE, 4, 8, 2),
        new Book(1, 2)
    );

    // ==================== 史诗补给（紫，档2）：钻石级跃迁 ====================
    private static final List<Entry> TIER_POOL_2 = List.of(
        new Simple(Items.DIAMOND, 1, 3, 5), new Simple(Items.GOLDEN_APPLE, 1, 2, 4),
        new Simple(Items.ENDER_PEARL, 2, 4, 3), new Simple(Items.EMERALD, 2, 5, 3),
        new Simple(Items.GOLD_BLOCK, 1, 2, 2), new Simple(Items.NAME_TAG, 1, 1, 2),
        new Simple(Items.SADDLE, 1, 1, 2), new Simple(Items.ANVIL, 1, 1, 1),
        new Enchanted(Items.IRON_SWORD, 2, 3), new Enchanted(Items.IRON_CHESTPLATE, 2, 3),
        new Enchanted(Items.IRON_PICKAXE, 1, 3), new Enchanted(Items.CROSSBOW, 1, 3),
        new Book(3, 3)
    );

    // ==================== 传奇补给（红，档3）：下界合金与毕业物资 ====================
    private static final List<Entry> TIER_POOL_3 = List.of(
        new Simple(Items.NETHERITE_SCRAP, 1, 2, 3), new Simple(Items.NETHERITE_INGOT, 1, 1, 1),
        new Simple(Items.ENCHANTED_GOLDEN_APPLE, 1, 1, 2), new Simple(Items.TOTEM_OF_UNDYING, 1, 1, 2),
        new Simple(Items.DIAMOND_BLOCK, 1, 2, 2), new Simple(Items.TRIDENT, 1, 1, 1),
        new Enchanted(Items.DIAMOND_SWORD, 2, 5), new Enchanted(Items.DIAMOND_CHESTPLATE, 2, 5),
        new Enchanted(Items.DIAMOND_PICKAXE, 1, 5), new Enchanted(Items.DIAMOND_BOOTS, 1, 5),
        new Book(3, 5),
        // 原神话档精华并入传奇：毕业物资以更低权重出现
        new Simple(Items.ELYTRA, 1, 1, 1), new Simple(Items.NETHERITE_INGOT, 1, 1, 2),
        new Simple(Items.TOTEM_OF_UNDYING, 1, 1, 2), new Simple(Items.BEACON, 1, 1, 1),
        new Simple(Items.SHULKER_BOX, 1, 1, 1),
        new Simple(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1, 1, 2),
        new Enchanted(Items.NETHERITE_SWORD, 1, 5), new Enchanted(Items.NETHERITE_CHESTPLATE, 1, 5),
        new Book(2, 255)
    );

    private static final List<List<Entry>> POOLS = List.of(
        TIER_POOL_0, TIER_POOL_1, TIER_POOL_2, TIER_POOL_3);

    /** 按稀有度开箱：条数由 {@link SupplyRarity#rolls()} 决定。 */
    public static List<ItemStack> rollLoot(SupplyRarity rarity, HolderLookup.Provider registries) {
        List<Entry> pool = POOLS.get(rarity.ordinal());
        List<ItemStack> out = new ArrayList<>(rarity.rolls());
        for (int i = 0; i < rarity.rolls(); i++) {
            out.add(weightedPick(pool).roll(registries));
        }
        return out;
    }

    public static Entry weightedPick(List<Entry> pool) {
        int total = 0;
        for (Entry e : pool) {
            total += Math.max(1, e.weight());
        }
        int roll = RNG.nextInt(total);
        for (Entry e : pool) {
            roll -= Math.max(1, e.weight());
            if (roll < 0) {
                return e;
            }
        }
        return pool.getLast();
    }

    /** 给装备随机附魔（附魔是数据包注册表，必须使用活动注册表的 Holder）。 */
    private static ItemStack enchantItem(HolderLookup.Provider registries, ItemStack stack, int maxLevelCap) {
        var registry = registries.lookupOrThrow(Registries.ENCHANTMENT);
        for (int attempt = 0; attempt < 12; attempt++) {
            String id = ENCHANT_POOL[RNG.nextInt(ENCHANT_POOL.length)];
            var key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace(id));
            Optional<Holder.Reference<Enchantment>> holder = registry.get(key);
            if (holder.isEmpty() || !holder.get().value().canEnchant(stack)) {
                continue;
            }
            Enchantment enchantment = holder.get().value();
            int max = enchantment.getMaxLevel();
            int level = SINGLE_LEVEL.contains(id) ? 1 : 1 + RNG.nextInt(Math.min(max, Math.max(1, maxLevelCap)));
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(
                stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
            mutable.set(holder.get(), level);
            stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
            return stack;
        }
        return stack;
    }

    /** 随机附魔书（levelCap = 255 表示直接取满级）。 */
    private static ItemStack randomBook(HolderLookup.Provider registries, int maxLevelCap) {
        String id = ENCHANT_POOL[RNG.nextInt(ENCHANT_POOL.length)];
        var registry = registries.lookupOrThrow(Registries.ENCHANTMENT);
        var key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace(id));
        Optional<Holder.Reference<Enchantment>> holder = registry.get(key);
        if (holder.isEmpty()) {
            return new ItemStack(Items.PAPER);
        }
        int max = holder.get().value().getMaxLevel();
        int level = maxLevelCap >= 255 ? max
            : SINGLE_LEVEL.contains(id) ? 1 : 1 + RNG.nextInt(Math.min(max, Math.max(1, maxLevelCap)));
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(holder.get(), level);
        book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        return book;
    }
}
