package io.github.unknownassets.totemic.logic;

import java.util.List;

public record TotemicResolution(
	Outcome outcome,
	double rawPmd,
	double effectivePmd,
	boolean roundingEnabled,
	double committedCapacity,
	boolean includesUnlimited,
	List<Selection> selections
) {
	public enum Outcome {
		NOT_APPLICABLE,
		PROTECTED,
		INSUFFICIENT,
		VANILLA_DELEGATED,
		ABORTED_CONFLICT
	}

	public TotemicResolution(Outcome outcome, double rawPmd, double committedCapacity, List<Selection> selections) {
		this(outcome, rawPmd, rawPmd, false, committedCapacity, false, selections);
	}

	public record Selection(SemanticSlot slot, int units, Capacity capacityValue) {
		public Selection(SemanticSlot slot, int units, double capacityPerUnit) {
			this(slot, units, Capacity.finite(capacityPerUnit));
		}

		public Selection {
			if (slot == null || units < 1 || capacityValue == null) {
				throw new IllegalArgumentException("selection");
			}
		}

		public double totalCapacity() {
			return capacityValue instanceof Capacity.Finite finite ? finite.points() * units : 0.0;
		}

		public double capacityPerUnit() {
			return capacityValue instanceof Capacity.Finite finite ? finite.points() : 0.0;
		}

		public boolean unlimited() {
			return capacityValue.isUnlimited();
		}
	}

	public TotemicResolution {
		if (outcome == null || !Double.isFinite(rawPmd) || rawPmd < 0.0
			|| !Double.isFinite(effectivePmd) || effectivePmd < 0.0
			|| !Double.isFinite(committedCapacity) || committedCapacity < 0.0) {
			throw new IllegalArgumentException("resolution");
		}
		selections = selections == null ? List.of() : List.copyOf(selections);
	}

	public static TotemicResolution notApplicable(double rawPmd) {
		return new TotemicResolution(Outcome.NOT_APPLICABLE, rawPmd, rawPmd, false, 0.0, false, List.of());
	}

	public static TotemicResolution aborted(double rawPmd) {
		return new TotemicResolution(Outcome.ABORTED_CONFLICT, rawPmd, rawPmd, false, 0.0, false, List.of());
	}
}
