package io.github.unknownassets.totemic.logic;

import java.util.List;

public record TotemicResolution(
	Outcome outcome,
	double rawPmd,
	double committedCapacity,
	List<Selection> selections
) {
	public enum Outcome {
		NOT_APPLICABLE,
		PROTECTED,
		INSUFFICIENT,
		ABORTED_CONFLICT
	}

	public record Selection(SemanticSlot slot, int units, double capacityPerUnit) {
		public Selection {
			if (slot == null || units < 1 || !Double.isFinite(capacityPerUnit) || capacityPerUnit < 0.0) {
				throw new IllegalArgumentException("selection");
			}
		}

		public double totalCapacity() {
			return capacityPerUnit * units;
		}
	}

	public TotemicResolution {
		if (outcome == null || !Double.isFinite(rawPmd) || rawPmd < 0.0 || !Double.isFinite(committedCapacity) || committedCapacity < 0.0) {
			throw new IllegalArgumentException("resolution");
		}
		selections = selections == null ? List.of() : List.copyOf(selections);
	}

	public static TotemicResolution notApplicable(double rawPmd) {
		return new TotemicResolution(Outcome.NOT_APPLICABLE, rawPmd, 0.0, List.of());
	}

	public static TotemicResolution aborted(double rawPmd) {
		return new TotemicResolution(Outcome.ABORTED_CONFLICT, rawPmd, 0.0, List.of());
	}
}
