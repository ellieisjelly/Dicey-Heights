package io.github.haykam821.diceyheights.game.item;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringIdentifiable;

public enum ItemSpawnStrategy implements StringIdentifiable {
	/**
	 * The item is inserted directly into the player's inventory.
	 */
	DIRECT("direct"),

	/**
	 * The item is spawned in the world at the top of the player's pillar.
	 */
	AT_PILLAR("at_pillar"),

	/**
	 * While the player is alive, the item is spawned in the world
	 * at the top of the player's pillar.
	 */
	AT_PILLAR_WHEN_ALIVE("at_pillar_when_alive");

	public static final Codec<ItemSpawnStrategy> CODEC = StringIdentifiable.createCodec(ItemSpawnStrategy::values);

	private final String name;

	private ItemSpawnStrategy(String name) {
		this.name = name;
	}

	@Override
	public String asString() {
		return this.name;
	}
}
