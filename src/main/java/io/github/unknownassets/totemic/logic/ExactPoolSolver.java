package io.github.unknownassets.totemic.logic;

import io.github.unknownassets.totemic.logic.TotemicResolution.Selection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class ExactPoolSolver {
	private static final int MAX_EXPANDED_UNITS = 40;

	public Result solve(double requiredCapacity, List<TotemCandidate> candidates) {
		if (requiredCapacity <= 0.0) {
			return new Result(List.of(), 0.0, true);
		}

		List<TotemCandidate> ordered = candidates.stream()
			.filter(candidate -> candidate.capacity() > 0.0)
			.sorted(Comparator.comparing(TotemCandidate::slot))
			.toList();
		if (ordered.isEmpty()) {
			return new Result(List.of(), 0.0, false);
		}

		double totalCapacity = ordered.stream().mapToDouble(candidate -> candidate.capacity() * candidate.units()).sum();
		if (totalCapacity < requiredCapacity) {
			return new Result(selectAll(ordered), totalCapacity, false);
		}

		if (allCapacitiesEqual(ordered)) {
			return solveEqualCapacities(requiredCapacity, ordered);
		}

		List<Unit> units = expand(ordered);
		if (units.size() > MAX_EXPANDED_UNITS) {
			throw new IllegalStateException("expanded pool");
		}

		int leftSize = units.size() / 2;
		List<Subset> leftSubsets = enumerate(units, 0, leftSize);
		List<Subset> rightSubsets = enumerate(units, leftSize, units.size() - leftSize);
		NavigableMap<Double, Subset> bestRightBySum = new TreeMap<>();
		for (Subset subset : rightSubsets) {
			bestRightBySum.merge(subset.capacity, subset, ExactPoolSolver::betterRepresentation);
		}

		Subset best = null;
		for (Subset left : leftSubsets) {
			Map.Entry<Double, Subset> rightEntry = bestRightBySum.ceilingEntry(requiredCapacity - left.capacity);
			if (rightEntry == null) {
				continue;
			}
			Subset combined = left.combine(rightEntry.getValue());
			if (best == null || compareSolutions(combined, best) < 0) {
				best = combined;
			}
		}

		if (best == null) {
			return new Result(selectAll(ordered), totalCapacity, false);
		}
		return new Result(toSelections(best.unitIndexes, units), best.capacity, true);
	}

	private static boolean allCapacitiesEqual(List<TotemCandidate> candidates) {
		double capacity = candidates.getFirst().capacity();
		return candidates.stream().allMatch(candidate -> Double.compare(candidate.capacity(), capacity) == 0);
	}

	private static Result solveEqualCapacities(double requiredCapacity, List<TotemCandidate> candidates) {
		List<Selection> selections = new ArrayList<>();
		double selectedCapacity = 0.0;
		for (TotemCandidate candidate : candidates) {
			int units = 0;
			while (units < candidate.units() && selectedCapacity < requiredCapacity) {
				units++;
				selectedCapacity += candidate.capacity();
			}
			if (units > 0) {
				selections.add(new Selection(candidate.slot(), units, candidate.capacity()));
			}
			if (selectedCapacity >= requiredCapacity) {
				break;
			}
		}
		return new Result(selections, selectedCapacity, selectedCapacity >= requiredCapacity);
	}

	private static List<Unit> expand(List<TotemCandidate> candidates) {
		List<Unit> units = new ArrayList<>();
		for (TotemCandidate candidate : candidates) {
			for (int unit = 0; unit < candidate.units(); unit++) {
				units.add(new Unit(candidate));
			}
		}
		return units;
	}

	private static List<Subset> enumerate(List<Unit> units, int offset, int length) {
		int subsetCount = 1 << length;
		List<Subset> subsets = new ArrayList<>(subsetCount);
		for (int mask = 0; mask < subsetCount; mask++) {
			double capacity = 0.0;
			List<Integer> indexes = new ArrayList<>();
			for (int bit = 0; bit < length; bit++) {
				if ((mask & (1 << bit)) != 0) {
					int index = offset + bit;
					capacity += units.get(index).candidate.capacity();
					indexes.add(index);
				}
			}
			subsets.add(new Subset(capacity, indexes));
		}
		return subsets;
	}

	private static Subset betterRepresentation(Subset left, Subset right) {
		return compareRepresentation(left, right) <= 0 ? left : right;
	}

	private static int compareSolutions(Subset left, Subset right) {
		int capacityComparison = Double.compare(left.capacity, right.capacity);
		return capacityComparison != 0 ? capacityComparison : compareRepresentation(left, right);
	}

	private static int compareRepresentation(Subset left, Subset right) {
		int countComparison = Integer.compare(left.unitIndexes.size(), right.unitIndexes.size());
		if (countComparison != 0) {
			return countComparison;
		}
		for (int index = 0; index < left.unitIndexes.size(); index++) {
			int indexComparison = Integer.compare(left.unitIndexes.get(index), right.unitIndexes.get(index));
			if (indexComparison != 0) {
				return indexComparison;
			}
		}
		return 0;
	}

	private static List<Selection> toSelections(List<Integer> indexes, List<Unit> units) {
		Map<SemanticSlot, SelectionAccumulator> bySlot = new LinkedHashMap<>();
		for (int index : indexes) {
			TotemCandidate candidate = units.get(index).candidate;
			bySlot.computeIfAbsent(candidate.slot(), ignored -> new SelectionAccumulator(candidate.capacity())).units++;
		}
		return bySlot.entrySet().stream()
			.map(entry -> new Selection(entry.getKey(), entry.getValue().units, entry.getValue().capacity))
			.toList();
	}

	private static List<Selection> selectAll(List<TotemCandidate> candidates) {
		return candidates.stream().map(candidate -> new Selection(candidate.slot(), candidate.units(), candidate.capacity())).toList();
	}

	public record Result(List<Selection> selections, double totalCapacity, boolean sufficient) {
		public Result {
			selections = List.copyOf(selections);
		}
	}

	private record Unit(TotemCandidate candidate) {
	}

	private record Subset(double capacity, List<Integer> unitIndexes) {
		private Subset {
			unitIndexes = List.copyOf(unitIndexes);
		}

		private Subset combine(Subset other) {
			List<Integer> combined = new ArrayList<>(this.unitIndexes.size() + other.unitIndexes.size());
			combined.addAll(this.unitIndexes);
			combined.addAll(other.unitIndexes);
			return new Subset(this.capacity + other.capacity, combined);
		}
	}

	private static final class SelectionAccumulator {
		private final double capacity;
		private int units;

		private SelectionAccumulator(double capacity) {
			this.capacity = capacity;
		}
	}
}
