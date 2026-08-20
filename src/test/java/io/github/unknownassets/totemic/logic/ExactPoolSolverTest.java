package io.github.unknownassets.totemic.logic;

import io.github.unknownassets.totemic.logic.TotemicResolution.Selection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExactPoolSolverTest {
	private final ExactPoolSolver solver = new ExactPoolSolver();

	@Test
	void handlesMixedPoolsFarBeyondTheOldFortyUnitLimit() {
		List<TotemCandidate> candidates = List.of(
			candidate(9, 5, 64),
			candidate(10, 7, 64),
			candidate(11, 11, 64)
		);

		ExactPoolSolver.Result result = assertDoesNotThrow(() -> solver.solve(1_000, candidates));

		assertTrue(result.sufficient());
		assertEquals(1_000.0, result.totalCapacity());
	}

	@Test
	void emptiesTheSmallerStackBeforeTakingUnitsFromTheLargerStack() {
		TotemCandidate large = candidate(9, 5, 64);
		TotemCandidate small = candidate(10, 5, 3);

		ExactPoolSolver.Result result = solver.solve(25, List.of(large, small));

		assertEquals(25.0, result.totalCapacity());
		assertEquals(2, selectedUnits(result, large.slot()));
		assertEquals(3, selectedUnits(result, small.slot()));
	}

	@Test
	void stillMinimizesCapacityBeforeUnitsAndStackConsolidation() {
		ExactPoolSolver.Result result = solver.solve(12, List.of(
			candidate(9, 5, 64),
			candidate(10, 5, 3),
			candidate(11, 13, 1)
		));

		assertEquals(13.0, result.totalCapacity());
		assertEquals(1, result.selections().stream().mapToInt(Selection::units).sum());
		assertEquals(SemanticSlot.storage(11), result.selections().getFirst().slot());
	}

	@Test
	void usesExactThreeDecimalCapacityUnits() {
		ExactPoolSolver.Result exact = solver.solve(0.3, List.of(
			candidate(9, 0.1, 3),
			candidate(10, 0.301, 1)
		));
		ExactPoolSolver.Result above = solver.solve(0.300_000_1, List.of(
			candidate(9, 0.1, 3),
			candidate(10, 0.301, 1)
		));

		assertEquals(0.3, exact.totalCapacity());
		assertEquals(3, exact.selections().getFirst().units());
		assertEquals(0.301, above.totalCapacity());
		assertEquals(SemanticSlot.storage(10), above.selections().getFirst().slot());
	}

	@Test
	void ignoresZeroCapacityAndSelectsEveryUsefulUnitWhenInsufficient() {
		ExactPoolSolver.Result result = solver.solve(100, List.of(
			candidate(9, 0, 64),
			candidate(10, 2.5, 3),
			candidate(11, 7.5, 2)
		));

		assertFalse(result.sufficient());
		assertEquals(22.5, result.totalCapacity());
		assertEquals(0, selectedUnits(result, SemanticSlot.storage(9)));
		assertEquals(3, selectedUnits(result, SemanticSlot.storage(10)));
		assertEquals(2, selectedUnits(result, SemanticSlot.storage(11)));
	}

	@Test
	void prefersAFiniteSufficientSolutionOverUnlimited() {
		ExactPoolSolver.Result result = solver.solve(10, List.of(
			TotemCandidate.unlimited(SemanticSlot.storage(9), 1),
			candidate(10, 5, 2)
		));

		assertTrue(result.sufficient());
		assertFalse(result.includesUnlimited());
		assertEquals(2, selectedUnits(result, SemanticSlot.storage(10)));
		assertEquals(0, selectedUnits(result, SemanticSlot.storage(9)));
	}

	@Test
	void usesOneUnlimitedUnitOnlyWhenFiniteCapacityCannotSuffice() {
		ExactPoolSolver.Result result = solver.solve(100, List.of(
			candidate(9, 5, 3),
			TotemCandidate.unlimited(SemanticSlot.storage(10), 64),
			TotemCandidate.unlimited(SemanticSlot.storage(11), 2)
		));

		assertTrue(result.sufficient());
		assertTrue(result.includesUnlimited());
		assertEquals(1, result.selections().size());
		assertTrue(result.selections().getFirst().unlimited());
		assertEquals(SemanticSlot.storage(11), result.selections().getFirst().slot());
	}

	@Test
	void resultDoesNotDependOnCandidateInputOrder() {
		List<TotemCandidate> forward = List.of(
			candidate(9, 4, 2),
			candidate(10, 6, 2),
			candidate(11, 10, 1)
		);
		List<TotemCandidate> reverse = List.of(forward.get(2), forward.get(1), forward.get(0));

		assertEquals(solver.solve(10, forward), solver.solve(10, reverse));
	}

	@Test
	void rejectsExternalCapacityPrecisionBeyondThreeDecimals() {
		assertThrows(IllegalArgumentException.class,
			() -> new TotemCandidate(SemanticSlot.storage(9), 1.0001, 1));
	}

	@Test
	void agreesWithBruteForceForSmallRandomPools() {
		Random random = new Random(0x70_07_E1_C);
		for (int example = 0; example < 500; example++) {
			int candidateCount = 1 + random.nextInt(6);
			List<TotemCandidate> candidates = new ArrayList<>();
			for (int index = 0; index < candidateCount; index++) {
				candidates.add(candidate(9 + index, random.nextInt(13) / 2.0, 1 + random.nextInt(4)));
			}
			double required = random.nextInt(81) / 2.0;

			assertEquivalent(bruteForce(required, candidates), solver.solve(required, candidates),
				"example=" + example + ", required=" + required + ", candidates=" + candidates);
		}
	}

	@Test
	void resolvesAFullInventoryStressCaseDeterministically() {
		List<TotemCandidate> candidates = new ArrayList<>();
		for (int index = 0; index < 27; index++) {
			candidates.add(candidate(9 + index, switch (index % 5) {
				case 0 -> 1.125;
				case 1 -> 2.375;
				case 2 -> 5.0;
				case 3 -> 8.625;
				default -> 13.0;
			}, 64));
		}

		ExactPoolSolver.Result first = solver.solve(8_000.001, candidates);
		ExactPoolSolver.Result second = solver.solve(8_000.001, candidates);

		assertTrue(first.sufficient());
		assertEquals(first, second);
		assertTrue(first.totalCapacity() >= 8_000.001);
	}

	private static TotemCandidate candidate(int inventoryIndex, double capacity, int units) {
		return new TotemCandidate(SemanticSlot.storage(inventoryIndex), capacity, units);
	}

	private static int selectedUnits(ExactPoolSolver.Result result, SemanticSlot slot) {
		return result.selections().stream()
			.filter(selection -> selection.slot().equals(slot))
			.mapToInt(Selection::units)
			.sum();
	}

	private static BruteForceResult bruteForce(double required, List<TotemCandidate> candidates) {
		List<TotemCandidate> ordered = candidates.stream()
			.filter(candidate -> candidate.capacity() > 0)
			.sorted(Comparator.comparing(TotemCandidate::slot))
			.toList();
		int[] selected = new int[ordered.size()];
		BruteForceResult[] best = new BruteForceResult[1];
		enumerate(ordered, required, 0, selected, best);
		if (best[0] != null) {
			return best[0];
		}
		int[] all = ordered.stream().mapToInt(TotemCandidate::units).toArray();
		return bruteForceResult(ordered, all, false);
	}

	private static void enumerate(
		List<TotemCandidate> candidates,
		double required,
		int index,
		int[] selected,
		BruteForceResult[] best
	) {
		if (index == candidates.size()) {
			BruteForceResult result = bruteForceResult(candidates, selected, true);
			if (result.totalCapacity + 1.0e-9 >= required && (best[0] == null || compare(result, best[0], candidates) < 0)) {
				best[0] = result;
			}
			return;
		}
		for (int units = 0; units <= candidates.get(index).units(); units++) {
			selected[index] = units;
			enumerate(candidates, required, index + 1, selected, best);
		}
	}

	private static BruteForceResult bruteForceResult(List<TotemCandidate> candidates, int[] selected, boolean sufficient) {
		double capacity = 0;
		int units = 0;
		Map<SemanticSlot, Integer> bySlot = new HashMap<>();
		for (int index = 0; index < candidates.size(); index++) {
			capacity += candidates.get(index).capacity() * selected[index];
			units += selected[index];
			if (selected[index] > 0) {
				bySlot.put(candidates.get(index).slot(), selected[index]);
			}
		}
		return new BruteForceResult(capacity, units, Map.copyOf(bySlot), sufficient);
	}

	private static int compare(BruteForceResult left, BruteForceResult right, List<TotemCandidate> candidates) {
		int capacity = Double.compare(left.totalCapacity, right.totalCapacity);
		if (capacity != 0) {
			return capacity;
		}
		int units = Integer.compare(left.units, right.units);
		if (units != 0) {
			return units;
		}
		List<TotemCandidate> preference = candidates.stream()
			.sorted(Comparator.comparingInt(TotemCandidate::units).thenComparing(TotemCandidate::slot))
			.toList();
		for (TotemCandidate candidate : preference) {
			int selected = Integer.compare(
				right.bySlot.getOrDefault(candidate.slot(), 0),
				left.bySlot.getOrDefault(candidate.slot(), 0)
			);
			if (selected != 0) {
				return selected;
			}
		}
		return 0;
	}

	private static void assertEquivalent(BruteForceResult expected, ExactPoolSolver.Result actual, String message) {
		assertEquals(expected.sufficient, actual.sufficient(), message);
		assertEquals(expected.totalCapacity, actual.totalCapacity(), 1.0e-9, message);
		Map<SemanticSlot, Integer> actualBySlot = new HashMap<>();
		for (Selection selection : actual.selections()) {
			actualBySlot.put(selection.slot(), selection.units());
		}
		assertEquals(expected.bySlot, actualBySlot, message);
	}

	private record BruteForceResult(
		double totalCapacity,
		int units,
		Map<SemanticSlot, Integer> bySlot,
		boolean sufficient
	) {
	}
}
