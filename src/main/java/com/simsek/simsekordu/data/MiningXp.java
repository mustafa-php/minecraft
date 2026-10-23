package com.simsek.simsekordu.data;

import java.util.List;
import java.util.Map;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/** XP a miner earns per block broken. Ores are recognised through block tags (vanilla and the c: convention). */
public final class MiningXp {
    public static final int DEFAULT_XP = 1;

    /** Checked in order, so the most valuable tags come first. Any other ore is worth {@code 3}. */
    private static final List<Map.Entry<TagKey<Block>, Integer>> ORE_XP = List.of(
            Map.entry(Tags.Blocks.ORES_NETHERITE_SCRAP, 10),
            Map.entry(BlockTags.DIAMOND_ORES, 10),
            Map.entry(Tags.Blocks.ORES_DIAMOND, 10),
            Map.entry(BlockTags.EMERALD_ORES, 10),
            Map.entry(Tags.Blocks.ORES_EMERALD, 10),
            Map.entry(BlockTags.IRON_ORES, 5),
            Map.entry(Tags.Blocks.ORES_IRON, 5),
            Map.entry(BlockTags.GOLD_ORES, 5),
            Map.entry(Tags.Blocks.ORES_GOLD, 5),
            Map.entry(BlockTags.COAL_ORES, 3),
            Map.entry(Tags.Blocks.ORES_COAL, 3),
            Map.entry(BlockTags.COPPER_ORES, 3),
            Map.entry(Tags.Blocks.ORES_COPPER, 3),
            Map.entry(Tags.Blocks.ORES, 3));

    private MiningXp() {
    }

    public static int xpFor(BlockState state) {
        for (Map.Entry<TagKey<Block>, Integer> entry : ORE_XP) {
            if (state.is(entry.getKey())) {
                return entry.getValue();
            }
        }
        return DEFAULT_XP;
    }
}
