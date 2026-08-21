package io.github.unknownassets.totemic.logic.config;

import io.github.unknownassets.totemic.logic.Capacity;
import io.github.unknownassets.totemic.logic.PmdRoundingPolicy;

public record EffectiveTotemConfig(
	Capacity capacity,
	boolean excluded,
	boolean consumeOnFailure,
	PmdRoundingPolicy roundingPolicy,
	long configurationRevision
) {
	public EffectiveTotemConfig {
		if (capacity == null || roundingPolicy == null || configurationRevision < 0) {
			throw new IllegalArgumentException("effective config");
		}
	}
}
