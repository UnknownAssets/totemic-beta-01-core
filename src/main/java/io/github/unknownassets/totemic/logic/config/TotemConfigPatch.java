package io.github.unknownassets.totemic.logic.config;

import io.github.unknownassets.totemic.logic.Capacity;

import java.util.Optional;

public record TotemConfigPatch(
	Optional<CapacityProfile> capacity,
	Optional<Boolean> excluded
) {
	public static final TotemConfigPatch EMPTY = new TotemConfigPatch(Optional.empty(), Optional.empty());

	public TotemConfigPatch {
		capacity = capacity == null ? Optional.empty() : capacity;
		excluded = excluded == null ? Optional.empty() : excluded;
	}

	public static TotemConfigPatch capacity(CapacityProfile capacity) {
		return new TotemConfigPatch(Optional.of(capacity), Optional.empty());
	}

	public static TotemConfigPatch fixedCapacity(double capacity) {
		return capacity(CapacityProfile.fixed(capacity));
	}

	public static TotemConfigPatch fixedCapacity(Capacity capacity) {
		return capacity(CapacityProfile.fixed(capacity));
	}

	public static TotemConfigPatch excluded(boolean excluded) {
		return new TotemConfigPatch(Optional.empty(), Optional.of(excluded));
	}

	public TotemConfigPatch overlay(TotemConfigPatch higherPriority) {
		if (higherPriority == null) {
			throw new IllegalArgumentException("higherPriority");
		}
		return new TotemConfigPatch(
			higherPriority.capacity.isPresent() ? higherPriority.capacity : capacity,
			higherPriority.excluded.isPresent() ? higherPriority.excluded : excluded
		);
	}
}
