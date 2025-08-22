package io.github.haykam821.diceyheights.game;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.haykam821.diceyheights.game.item.ItemSpawnStrategy;
import io.github.haykam821.diceyheights.game.map.DiceyHeightsMapConfig;
import net.minecraft.SharedConstants;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryCodecs;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.IntProvider;
import xyz.nucleoid.plasmid.api.game.common.config.WaitingLobbyConfig;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeamList;

public record DiceyHeightsConfig(
	WaitingLobbyConfig playerConfig,
	DiceyHeightsMapConfig mapConfig,
	Optional<GameTeamList> teams,
	Optional<IntProvider> ticksUntilFirstItem,
	IntProvider ticksUntilNextItem,
	IntProvider beats,
	Optional<RegistryEntryList<Item>> items,
	IntProvider itemRolls,
	IntProvider itemCount,
	ItemSpawnStrategy itemSpawnStrategy,
	boolean separate,
	boolean preventDirectAttacks,
	IntProvider ticksUntilClose
) {
	public static final MapCodec<DiceyHeightsConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> {
		return instance.group(
			WaitingLobbyConfig.CODEC.fieldOf("players").forGetter(DiceyHeightsConfig::playerConfig),
			DiceyHeightsMapConfig.CODEC.optionalFieldOf("map", DiceyHeightsMapConfig.DEFAULT).forGetter(DiceyHeightsConfig::mapConfig),
			GameTeamList.CODEC.optionalFieldOf("teams").forGetter(DiceyHeightsConfig::teams),
			IntProvider.POSITIVE_CODEC.optionalFieldOf("ticks_until_first_item").forGetter(DiceyHeightsConfig::ticksUntilFirstItem),
			IntProvider.POSITIVE_CODEC.optionalFieldOf("ticks_until_next_item", ConstantIntProvider.create(SharedConstants.TICKS_PER_SECOND * 3)).forGetter(DiceyHeightsConfig::ticksUntilNextItem),
			IntProvider.POSITIVE_CODEC.optionalFieldOf("beats", ConstantIntProvider.create(3)).forGetter(DiceyHeightsConfig::beats),
			RegistryCodecs.entryList(RegistryKeys.ITEM).optionalFieldOf("items").forGetter(DiceyHeightsConfig::items),
			IntProvider.POSITIVE_CODEC.optionalFieldOf("item_rolls", ConstantIntProvider.create(1)).forGetter(DiceyHeightsConfig::itemRolls),
			IntProvider.POSITIVE_CODEC.optionalFieldOf("item_count", ConstantIntProvider.create(1)).forGetter(DiceyHeightsConfig::itemCount),
			ItemSpawnStrategy.CODEC.optionalFieldOf("item_spawn_strategy", ItemSpawnStrategy.DIRECT).forGetter(DiceyHeightsConfig::itemSpawnStrategy),
			Codec.BOOL.optionalFieldOf("separate", false).forGetter(DiceyHeightsConfig::separate),
			Codec.BOOL.optionalFieldOf("prevent_direct_attacks", false).forGetter(DiceyHeightsConfig::preventDirectAttacks),
			IntProvider.NON_NEGATIVE_CODEC.optionalFieldOf("ticks_until_close", ConstantIntProvider.create(SharedConstants.TICKS_PER_SECOND * 5)).forGetter(DiceyHeightsConfig::ticksUntilClose)
		).apply(instance, DiceyHeightsConfig::new);
	});
}
