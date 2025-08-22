package io.github.haykam821.diceyheights.game.phase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import io.github.haykam821.diceyheights.game.DiceyHeightsConfig;
import io.github.haykam821.diceyheights.game.ItemSpawnStrategy;
import io.github.haykam821.diceyheights.game.map.DiceyHeightsMap;
import io.github.haykam821.diceyheights.game.player.PlayerEntry;
import io.github.haykam821.diceyheights.game.player.TeamEntry;
import io.github.haykam821.diceyheights.game.win.FreeForAllWinManager;
import io.github.haykam821.diceyheights.game.win.TeamWinManager;
import io.github.haykam821.diceyheights.game.win.WinManager;
import io.github.haykam821.diceyheights.game.win.WinResult;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.AirBlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.OperatorOnlyBlockItem;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;
import xyz.nucleoid.plasmid.api.game.GameActivity;
import xyz.nucleoid.plasmid.api.game.GameCloseReason;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeam;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeamConfig;
import xyz.nucleoid.plasmid.api.game.common.team.GameTeamKey;
import xyz.nucleoid.plasmid.api.game.common.team.TeamChat;
import xyz.nucleoid.plasmid.api.game.common.team.TeamManager;
import xyz.nucleoid.plasmid.api.game.common.team.TeamSelectionLobby;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptor;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptorResult;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.plasmid.api.game.player.PlayerSet;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

public class DiceyHeightsActivePhase implements GameActivityEvents.Enable, GameActivityEvents.Tick, GamePlayerEvents.Accept, GamePlayerEvents.Remove, PlayerDamageEvent, PlayerDeathEvent {
	private static final Text NO_DIRECT_ATTACKS_MESSAGE = Text.translatable("text.diceyheights.no_direct_attacks").formatted(Formatting.RED);

	private final GameSpace gameSpace;
	private final Random random;
	private final ServerWorld world;
	private final DiceyHeightsMap map;
	private final DiceyHeightsConfig config;

	private final List<PlayerEntry> players;
	private final boolean singleplayer;

	private final WinManager winManager;

	private final RegistryEntryList<Item> items;

	private boolean beforeFirstItem = true;
	private int ticksUntilNextItem;
	private int ticksPerBeat;

	private int ticksUntilClose = -1;

	public DiceyHeightsActivePhase(GameSpace gameSpace, ServerWorld world, DiceyHeightsMap map, DiceyHeightsConfig config, Optional<TeamSelectionLobby> maybeTeamSelection, Optional<TeamManager> maybeTeamManager) {
		this.gameSpace = gameSpace;
		this.world = world;
		this.random = world.getRandom();
		this.map = map;
		this.config = config;

		PlayerSet participants = this.gameSpace.getPlayers().participants();

		List<ServerPlayerEntity> shuffledPlayers = participants.stream().collect(Collectors.toCollection(ArrayList::new));
		Util.shuffle(shuffledPlayers, this.random);

		this.players = new ArrayList<>(shuffledPlayers.size());
		this.winManager = maybeTeamSelection.isPresent() ? new TeamWinManager(this) : new FreeForAllWinManager(this);

		int index = 0;

		Map<UUID, GameTeamKey> playersToTeams = new HashMap<>();
		Map<GameTeamKey, TeamEntry> keysToTeams = new HashMap<>();

		maybeTeamSelection.ifPresent(teamSelection -> {
			teamSelection.allocate(participants, (key, player) -> {
				playersToTeams.put(player.getUuid(), key);
				maybeTeamManager.get().addPlayerTo(player, key);
			});
		});

		for (ServerPlayerEntity player : shuffledPlayers) {
			float angle = (index / (float) shuffledPlayers.size()) * (MathHelper.PI * 2);

			Vec3d pillarPos = this.map.getPillarPos(this.random, angle);
			float pillarYaw = angle * MathHelper.DEGREES_PER_RADIAN + 90;

			GameTeamKey key = playersToTeams.get(player.getUuid());

			TeamEntry team = key == null ? null : keysToTeams.computeIfAbsent(key, k -> {
				return new TeamEntry(this.config.teams().orElseThrow().byKey(k));
			});

			this.players.add(new PlayerEntry(player, team, pillarPos, pillarYaw));

			index += 1;
		}

		this.singleplayer = players.size() == 1;

		this.items = this.config.items().orElseGet(() -> {
			List<RegistryEntry.Reference<Item>> items = this.world.getRegistryManager()
				.getOrThrow(RegistryKeys.ITEM)
				.streamEntries()
				.filter(this::isItemEnabled)
				.toList();

			return RegistryEntryList.of(items);
		});

		this.resetTicksUntilNextItem(true, this.config.ticksUntilFirstItem().orElse(this.config.ticksUntilNextItem()));
	}

	public static void setRules(GameActivity activity, boolean pvp) {
		activity.deny(GameRuleType.PORTALS);
		activity.deny(GameRuleType.SATURATED_REGENERATION);

		if (pvp) {
			activity.allow(GameRuleType.PLAYER_PROJECTILE_KNOCKBACK);
		} else {
			activity.deny(GameRuleType.CRAFTING);
			activity.deny(GameRuleType.FALL_DAMAGE);
			activity.deny(GameRuleType.HUNGER);
			activity.deny(GameRuleType.MODIFY_ARMOR);
			activity.deny(GameRuleType.MODIFY_INVENTORY);
			activity.deny(GameRuleType.PICKUP_ITEMS);
			activity.deny(GameRuleType.PVP);
			activity.deny(GameRuleType.THROW_ITEMS);
		}
	}

	public static void open(GameSpace gameSpace, ServerWorld world, DiceyHeightsMap map, DiceyHeightsConfig config, Optional<TeamSelectionLobby> teamSelection) {
		gameSpace.setActivity(activity -> {
			Optional<TeamManager> maybeTeamManager = config.teams().map(teams -> {
				TeamManager teamManager = TeamManager.addTo(activity);
				TeamChat.addTo(activity, teamManager);

				for (GameTeam team : config.teams().get()) {
					GameTeamConfig teamConfig = GameTeamConfig.builder(team.config())
						.setFriendlyFire(false)
						.build();

					teamManager.addTeam(team.key(), teamConfig);
				}

				return teamManager;
			});

			DiceyHeightsActivePhase phase = new DiceyHeightsActivePhase(gameSpace, world, map, config, teamSelection, maybeTeamManager);

			DiceyHeightsActivePhase.setRules(activity, true);

			// Listeners
			activity.listen(GameActivityEvents.ENABLE, phase);
			activity.listen(GameActivityEvents.TICK, phase);
			activity.listen(GamePlayerEvents.ACCEPT, phase);
			activity.listen(GamePlayerEvents.OFFER, JoinOffer::acceptSpectators);
			activity.listen(GamePlayerEvents.REMOVE, phase);
			activity.listen(PlayerDamageEvent.EVENT, phase);
			activity.listen(PlayerDeathEvent.EVENT, phase);
		});
	}

	// Listeners

	@Override
	public void onEnable() {
		this.map.removeWaitingPlatform(this.world);

		for (PlayerEntry player : this.players) {
			player.spawn(this.map, this.world, this.random, this.ticksUntilNextItem);
		}

		for (ServerPlayerEntity player : this.gameSpace.getPlayers().spectators()) {
			this.map.teleportToWaitingSpawn(player);
			player.changeGameMode(GameMode.SPECTATOR);
		}
	}

	@Override
	public void onTick() {
		// Decrease ticks until game end to zero
		if (this.isGameEnding()) {
			if (this.ticksUntilClose == 0) {
				this.gameSpace.close(GameCloseReason.FINISHED);
			}

			this.ticksUntilClose -= 1;
			return;
		}

		for (PlayerEntry entry : this.players) {
			entry.tick(this.world, this.config.itemSpawnStrategy(), this.ticksUntilNextItem, this.beforeFirstItem);

			ServerPlayerEntity player = entry.getAlivePlayer();

			if (player != null) {
				if (player.getY() > (DiceyHeightsMap.START_Y + this.config.mapConfig().maxHeight())) {
					player.damage(this.world, this.world.getDamageSources().outOfWorld(), 1);
				} else if (map.isOutOfBounds(player)) {
					this.eliminate(entry);
				}
			}
		}

		this.ticksUntilNextItem -= 1;

		if (this.ticksUntilNextItem <= 0) {
			this.resetTicksUntilNextItem(false, this.config.ticksUntilNextItem());

			this.gameSpace.getPlayers().playSound(SoundEvents.BLOCK_COPPER_BULB_TURN_ON, SoundCategory.PLAYERS, 1, 1);
			this.giveRandomItems();
		} else if (this.ticksPerBeat > 0 && this.ticksUntilNextItem % this.ticksPerBeat == 0) {
			this.gameSpace.getPlayers().playSound(SoundEvents.BLOCK_COPPER_BULB_TURN_OFF, SoundCategory.PLAYERS, 1, 1f);
		}

		WinResult win = this.winManager.checkWin();

		if (win != null) {
			this.gameSpace.getPlayers().sendMessage(win.message());
			this.ticksUntilClose = this.config.ticksUntilClose().get(this.random);
		}
	}

	@Override
	public JoinAcceptorResult onAcceptPlayers(JoinAcceptor acceptor) {
		return acceptor.teleport(this.world, this.map.getWaitingSpawnPos()).thenRunForEach(player -> {
			player.changeGameMode(GameMode.SPECTATOR);
		});
	}

	@Override
	public void onRemovePlayer(ServerPlayerEntity player) {
		this.eliminate(this.getPlayerEntry(player));
	}

	@Override
	public EventResult onDamage(ServerPlayerEntity player, DamageSource source, float amount) {
		if (this.config.preventDirectAttacks() && source.getSource() instanceof ServerPlayerEntity attacker) {
			attacker.sendMessage(NO_DIRECT_ATTACKS_MESSAGE, true);
			return EventResult.DENY;
		}

		return EventResult.PASS;
	}

	@Override
	public EventResult onDeath(ServerPlayerEntity player, DamageSource source) {
		this.eliminate(this.getPlayerEntry(player));
		return EventResult.DENY;
	}

	// Utilities

	private boolean isItemEnabled(RegistryEntry<Item> entry) {
		if (entry.getKey().isPresent() && !entry.getKey().get().getValue().getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
			return false;
		}

		Item item = entry.value();
		return !(item instanceof OperatorOnlyBlockItem) && !(item instanceof AirBlockItem) && item.isEnabled(this.world.getEnabledFeatures());
	}

	private void resetTicksUntilNextItem(boolean beforeFirstItem, IntProvider provider) {
		this.beforeFirstItem = beforeFirstItem;
		this.ticksUntilNextItem = provider.get(this.random);
		this.ticksPerBeat = this.ticksUntilNextItem / this.config.beats().get(this.random);
	}

	private void giveRandomItems() {
		ItemSpawnStrategy strategy = this.config.itemSpawnStrategy();
		int itemRolls = this.config.itemRolls().get(this.random);

		for (int roll = 0; roll < itemRolls; roll += 1) {
			if (this.config.separate()) {
				for (PlayerEntry player : this.players) {
					player.giveItemStack(this.world, strategy, () -> {
						return this.getRandomItem()
							.map(entry -> {
								int count = this.config.itemCount().get(this.random);
								return new ItemStack(entry, count);
							})
							.orElse(ItemStack.EMPTY);
					});
				}
			} else {
				this.getRandomItem().ifPresent(entry -> {
					for (PlayerEntry player : this.players) {
						player.giveItemStack(this.world, strategy, () -> {
							int count = this.config.itemCount().get(this.random);
							return new ItemStack(entry, count);
						});
					}
				});
			}
		}
	}

	private Optional<RegistryEntry<Item>> getRandomItem() {
		return this.items.getRandom(this.random);
	}

	/**
	 * Attempts to eliminate a player.
	 * @return whether an elimination has occurred
	 */
	public boolean eliminate(PlayerEntry player) {
		if (this.isGameEnding()) return false;

		if (player == null) return false;
		if (player.getAlivePlayer() == null) return false;

		// Send elimination message
		Text message = player.getEliminationMessage();
		this.gameSpace.getPlayers().sendMessage(message);

		// Perform removal operations
		player.reset(GameMode.SPECTATOR);
		player.clearAlivePlayer();

		return true;
	}

	public List<PlayerEntry> getPlayers() {
		return this.players;
	}

	public boolean isSingleplayer() {
		return this.singleplayer;
	}

	private boolean isGameEnding() {
		return this.ticksUntilClose >= 0;
	}

	private PlayerEntry getPlayerEntry(ServerPlayerEntity player) {
		if (player != null) {
			for (PlayerEntry entry : this.players) {
				if (player == entry.getAlivePlayer()) {
					return entry;
				}
			}
		}

		return null;
	}
}
