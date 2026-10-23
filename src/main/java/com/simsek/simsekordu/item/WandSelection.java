package com.simsek.simsekordu.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simsek.simsekordu.data.MiningRegion;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** What a Commander's Wand currently has selected. Stored on the item as a data component. */
public record WandSelection(ResourceKey<Level> dimension, Optional<BlockPos> corner1, Optional<BlockPos> corner2,
                            Optional<BlockPos> storage) {
    public static final Codec<WandSelection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(WandSelection::dimension),
            BlockPos.CODEC.optionalFieldOf("corner1").forGetter(WandSelection::corner1),
            BlockPos.CODEC.optionalFieldOf("corner2").forGetter(WandSelection::corner2),
            BlockPos.CODEC.optionalFieldOf("storage").forGetter(WandSelection::storage)
    ).apply(instance, WandSelection::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, WandSelection> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), WandSelection::dimension,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), WandSelection::corner1,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), WandSelection::corner2,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), WandSelection::storage,
            WandSelection::new);

    public static WandSelection empty(ResourceKey<Level> dimension) {
        return new WandSelection(dimension, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public boolean isEmpty() {
        return this.corner1.isEmpty() && this.corner2.isEmpty() && this.storage.isEmpty();
    }

    /** This selection if it was made in {@code dimension}, otherwise an empty one: corners never span dimensions. */
    public WandSelection forDimension(ResourceKey<Level> dimension) {
        return this.dimension.equals(dimension) ? this : empty(dimension);
    }

    public WandSelection withCorner1(BlockPos pos) {
        return new WandSelection(this.dimension, Optional.of(pos), this.corner2, this.storage);
    }

    public WandSelection withCorner2(BlockPos pos) {
        return new WandSelection(this.dimension, this.corner1, Optional.of(pos), this.storage);
    }

    public WandSelection withStorage(BlockPos pos) {
        return new WandSelection(this.dimension, this.corner1, this.corner2, Optional.of(pos));
    }

    public Optional<MiningRegion> region() {
        if (this.corner1.isPresent() && this.corner2.isPresent()) {
            return Optional.of(MiningRegion.of(this.corner1.get(), this.corner2.get()));
        }
        return Optional.empty();
    }
}
