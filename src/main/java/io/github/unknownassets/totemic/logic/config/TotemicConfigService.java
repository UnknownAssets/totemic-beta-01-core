package io.github.unknownassets.totemic.logic.config;

import io.github.unknownassets.totemic.logic.PmdRoundingPolicy;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class TotemicConfigService {
	private final Defaults defaults;
	private final AtomicReference<State> state = new AtomicReference<>(
		new State(Optional.empty(), TotemicConfigLayer.EMPTY, 0, SnapshotStatus.DEFAULTS)
	);

	public TotemicConfigService(Defaults defaults) {
		if (defaults == null) {
			throw new IllegalArgumentException("defaults");
		}
		this.defaults = defaults;
	}

	public SnapshotUpdate activate(TotemicConfigSnapshot snapshot) {
		if (snapshot == null) {
			return reject();
		}
		State updated = state.updateAndGet(current -> new State(
			Optional.of(snapshot), current.overrides, Math.addExact(current.configurationRevision, 1), SnapshotStatus.ACTIVE
		));
		return update(updated);
	}

	public SnapshotUpdate reject() {
		State updated = state.updateAndGet(current -> current.withStatus(SnapshotStatus.REJECTED));
		return update(updated);
	}

	public SnapshotUpdate sourceMissing() {
		State updated = state.updateAndGet(current -> current.withStatus(SnapshotStatus.SOURCE_MISSING));
		return update(updated);
	}

	public SnapshotUpdate clearSnapshot() {
		State updated = state.updateAndGet(current -> new State(
			Optional.empty(), current.overrides, Math.addExact(current.configurationRevision, 1), SnapshotStatus.DEFAULTS
		));
		return update(updated);
	}

	public long replaceOverrides(TotemicConfigLayer overrides) {
		if (overrides == null) {
			throw new IllegalArgumentException("overrides");
		}
		return state.updateAndGet(current -> new State(
			current.snapshot, overrides, Math.addExact(current.configurationRevision, 1), current.status
		)).configurationRevision;
	}

	public SnapshotUpdate snapshotState() {
		return update(state.get());
	}

	public EffectiveTotemConfig resolve(
		DifficultyTier difficulty,
		List<String> matchingDefinitionIds,
		UniqueTotemOverride uniqueOverride
	) {
		return view().resolve(difficulty, matchingDefinitionIds, uniqueOverride);
	}

	public ConfigurationView view() {
		State current = state.get();
		return new ConfigurationView(defaults, current);
	}

	public static final class ConfigurationView {
		private final Defaults defaults;
		private final TotemicConfigLayer snapshot;
		private final TotemicConfigLayer overrides;
		private final long configurationRevision;
		private final SnapshotStatus snapshotStatus;

		private ConfigurationView(Defaults defaults, State state) {
			this.defaults = defaults;
			this.snapshot = state.snapshot
				.map(TotemicConfigSnapshot::configuration)
				.orElse(TotemicConfigLayer.EMPTY);
			this.overrides = state.overrides;
			this.configurationRevision = state.configurationRevision;
			this.snapshotStatus = state.status;
		}

		public long configurationRevision() {
			return configurationRevision;
		}

		public SnapshotStatus snapshotStatus() {
			return snapshotStatus;
		}

		public EffectiveTotemConfig resolve(
			DifficultyTier difficulty,
			List<String> matchingDefinitionIds,
			UniqueTotemOverride uniqueOverride
		) {
			if (difficulty == null || matchingDefinitionIds == null) {
				throw new IllegalArgumentException("resolution input");
			}
			uniqueOverride = uniqueOverride == null ? UniqueTotemOverride.EMPTY : uniqueOverride;

			boolean consumeOnFailure = snapshot.consumeOnFailure().orElse(defaults.consumeOnFailure);
			consumeOnFailure = overrides.consumeOnFailure().orElse(consumeOnFailure);
			PmdRoundingPolicy roundingPolicy = snapshot.roundingPolicy().orElse(defaults.roundingPolicy);
			roundingPolicy = overrides.roundingPolicy().orElse(roundingPolicy);

			TotemConfigPatch effective = TotemConfigPatch.EMPTY
				.overlay(snapshot.combineMatching(matchingDefinitionIds))
				.overlay(overrides.combineMatching(matchingDefinitionIds))
				.overlay(uniqueOverride.asPatch());

			CapacityProfile capacity = effective.capacity().orElse(defaults.fallbackCapacity);
			boolean excluded = effective.excluded().orElse(false);
			return new EffectiveTotemConfig(
				capacity.resolve(difficulty), excluded, consumeOnFailure, roundingPolicy, configurationRevision
			);
		}
	}

	private static SnapshotUpdate update(State state) {
		return new SnapshotUpdate(state.status, state.snapshot, state.configurationRevision);
	}

	public record Defaults(
		CapacityProfile fallbackCapacity,
		boolean consumeOnFailure,
		PmdRoundingPolicy roundingPolicy
	) {
		public Defaults {
			if (fallbackCapacity == null || roundingPolicy == null) {
				throw new IllegalArgumentException("defaults");
			}
		}
	}

	public enum SnapshotStatus {
		DEFAULTS,
		ACTIVE,
		REJECTED,
		SOURCE_MISSING
	}

	public record SnapshotUpdate(
		SnapshotStatus status,
		Optional<TotemicConfigSnapshot> activeSnapshot,
		long configurationRevision
	) {
		public SnapshotUpdate {
			if (status == null || activeSnapshot == null || configurationRevision < 0) {
				throw new IllegalArgumentException("snapshot update");
			}
		}
	}

	private record State(
		Optional<TotemicConfigSnapshot> snapshot,
		TotemicConfigLayer overrides,
		long configurationRevision,
		SnapshotStatus status
	) {
		private State withStatus(SnapshotStatus nextStatus) {
			return new State(snapshot, overrides, configurationRevision, nextStatus);
		}
	}
}
