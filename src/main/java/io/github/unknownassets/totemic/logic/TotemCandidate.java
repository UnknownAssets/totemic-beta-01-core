package io.github.unknownassets.totemic.logic;

public record TotemCandidate(SemanticSlot slot, Capacity capacityValue, int units) {
	public TotemCandidate(SemanticSlot slot, double capacity, int units) {
		this(slot, Capacity.finite(capacity), units);
	}

	public static TotemCandidate unlimited(SemanticSlot slot, int units) {
		return new TotemCandidate(slot, Capacity.unlimited(), units);
	}

	public TotemCandidate {
		if (slot == null) {
			throw new IllegalArgumentException("slot");
		}
		if (capacityValue == null) {
			throw new IllegalArgumentException("capacityValue");
		}
		if (units < 1) {
			throw new IllegalArgumentException("units");
		}
	}

	public double capacity() {
		return capacityValue instanceof Capacity.Finite finite ? finite.points() : 0.0;
	}

	public boolean unlimited() {
		return capacityValue.isUnlimited();
	}
}
