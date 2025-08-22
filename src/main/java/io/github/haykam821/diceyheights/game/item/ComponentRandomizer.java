package io.github.haykam821.diceyheights.game.item;

import java.util.List;
import java.util.Optional;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.featuretoggle.ToggleableFeature;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Util;
import net.minecraft.util.math.random.Random;

/**
 * Utilities to assign random component values to item stacks.
 */
public final class ComponentRandomizer {
	private ComponentRandomizer() {
		return;
	}

	private static <T extends ToggleableFeature> Optional<? extends RegistryEntry<T>> getRandom(RegistryKey<Registry<T>> registry, ServerWorld world, Random random) {
		List<? extends RegistryEntry<T>> entries = world.getRegistryManager()
			.getOrThrow(registry)
			.streamEntries()
			.filter(entry -> entry.value().isEnabled(world.getEnabledFeatures()))
			.toList();
		
		return Util.getRandomOrEmpty(entries, random);
	}

	public static void applyTo(ItemStack stack, ServerWorld world, Random random) {
		if (stack.contains(DataComponentTypes.POTION_CONTENTS)) {
			getRandom(RegistryKeys.POTION, world, random).ifPresent(potion -> {
				stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(potion));
			});
		}
	}
}