package io.github.unknownassets.totemic.logic.config;

import io.github.unknownassets.totemic.logic.Capacity;

import java.util.Optional;

public record UniqueTotemOverride(
	Optional<Capacity> capacity,
	Optional<Boolean> excluded
) {
	public static final UniqueTotemOverride EMPTY = new UniqueTotemOverride(Optional.empty(), Optional.empty());

	public UniqueTotemOverride {
		capacity = capacity == null ? Optional.empty() : capacity;
		excluded = excluded == null ? Optional.empty() : excluded;
	}

	TotemConfigPatch asPatch() {
		return new TotemConfigPatch(capacity.map(CapacityProfile::fixed), excluded);
	}
}
