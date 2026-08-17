package io.github.unknownassets.totemic.logic;

public record TotemCandidate(SemanticSlot slot, double capacity, int units) {
	public TotemCandidate {
		if (slot == null) {
			throw new IllegalArgumentException("slot");
		}
		if (!Double.isFinite(capacity) || capacity < 0.0) {
			throw new IllegalArgumentException("capacity");
		}
		if (units < 1) {
			throw new IllegalArgumentException("units");
		}
	}
}
