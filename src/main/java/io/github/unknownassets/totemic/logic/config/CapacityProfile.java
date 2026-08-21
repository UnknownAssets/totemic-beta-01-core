package io.github.unknownassets.totemic.logic.config;

import io.github.unknownassets.totemic.logic.Capacity;

public record CapacityProfile(
	Capacity peaceful,
	Capacity easy,
	Capacity normal,
	Capacity hard
) {
	public CapacityProfile {
		if (peaceful == null || easy == null || normal == null || hard == null) {
			throw new IllegalArgumentException("capacity profile");
		}
	}

	public static CapacityProfile fixed(Capacity capacity) {
		if (capacity == null) {
			throw new IllegalArgumentException("capacity");
		}
		return new CapacityProfile(capacity, capacity, capacity, capacity);
	}

	public static CapacityProfile fixed(double capacity) {
		return fixed(Capacity.finite(capacity));
	}

	public Capacity resolve(DifficultyTier difficulty) {
		if (difficulty == null) {
			throw new IllegalArgumentException("difficulty");
		}
		return switch (difficulty) {
			case PEACEFUL -> peaceful;
			case EASY -> easy;
			case NORMAL -> normal;
			case HARD -> hard;
		};
	}
}
