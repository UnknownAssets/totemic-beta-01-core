package io.github.unknownassets.totemic.logic;

import io.github.unknownassets.totemic.logic.TotemicResolution.Selection;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exact bounded solver for one semantic inventory pool.
 *
 * <p>Physical stack units remain indivisible, but are represented as bounded
 * multiplicities instead of being expanded into one object per item.</p>
 */
public final class ExactPoolSolver {
	private static final int CAPACITY_SCALE = 3;
	private static final BigDecimal CAPACITY_FACTOR = BigDecimal.TEN.pow(CAPACITY_SCALE);

	public Result solve(double requiredCapacity, List<TotemCandidate> candidates) {
		if (!Double.isFinite(requiredCapacity)) {
			throw new IllegalArgumentException("requiredCapacity");
		}
		if (requiredCapacity <= 0.0) {
			return new Result(List.of(), 0.0, true);
		}

		List<TotemCandidate> ordered = usefulCandidates(candidates);
		List<TotemCandidate> finite = ordered.stream().filter(candidate -> !candidate.unlimited()).toList();
		List<TotemCandidate> unlimited = ordered.stream().filter(TotemCandidate::unlimited).toList();
		if (finite.isEmpty()) {
			if (!unlimited.isEmpty()) {
				return unlimitedResult(requiredCapacity, unlimited);
			}
			return new Result(List.of(), 0.0, false);
		}

		BigInteger requiredMilliPoints = requiredMilliPoints(requiredCapacity);
		List<CapacityGroup> groups = capacityGroups(finite);
		BigInteger totalMilliPoints = groups.stream()
			.map(CapacityGroup::totalMilliPoints)
			.reduce(BigInteger.ZERO, BigInteger::add);
		if (totalMilliPoints.compareTo(requiredMilliPoints) < 0) {
			if (!unlimited.isEmpty()) {
				return unlimitedResult(requiredCapacity, unlimited);
			}
			return result(selectAll(finite), totalMilliPoints, false);
		}

		if (groups.size() == 1) {
			CapacityGroup group = groups.get(0);
			int units = ceilDivide(requiredMilliPoints, group.milliPoints).intValueExact();
			return result(toSelections(groups, new int[] { units }), group.milliPoints.multiply(BigInteger.valueOf(units)), true);
		}

		BigInteger commonDivisor = groups.stream()
			.map(CapacityGroup::milliPoints)
			.reduce(BigInteger.ZERO, BigInteger::gcd);
		BigInteger scaledRequired = ceilDivide(requiredMilliPoints, commonDivisor);
		List<CapacityGroup> scaledGroups = groups.stream()
			.map(group -> group.withMilliPoints(group.milliPoints.divide(commonDivisor)))
			.toList();

		Plan best = solveScaled(scaledRequired, scaledGroups);
		if (best == null) {
			throw new IllegalStateException("exact solver invariant");
		}

		BigInteger selectedMilliPoints = selectedCapacity(best.groupUnits, groups);
		return result(toSelections(groups, best.groupUnits), selectedMilliPoints, true);
	}

	private static Plan solveScaled(BigInteger required, List<CapacityGroup> groups) {
		Map<BigInteger, Plan> states = new HashMap<>();
		states.put(BigInteger.ZERO, new Plan(new int[groups.size()], 0));

		for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
			CapacityGroup group = groups.get(groupIndex);
			for (int chunkUnits : binaryChunks(group.totalUnits)) {
				BigInteger chunkCapacity = group.milliPoints.multiply(BigInteger.valueOf(chunkUnits));
				Map<BigInteger, Plan> next = new HashMap<>(states);
				for (Map.Entry<BigInteger, Plan> entry : states.entrySet()) {
					if (entry.getKey().compareTo(required) >= 0) {
						continue;
					}
					BigInteger sum = entry.getKey().add(chunkCapacity);
					Plan plan = entry.getValue().withAdditionalUnits(groupIndex, chunkUnits);
					next.merge(sum, plan, (left, right) -> betterRepresentation(left, right, groups));
				}
				states = retainOnlyMinimumSufficientSum(next, required);
			}
		}

		return states.entrySet().stream()
			.filter(entry -> entry.getKey().compareTo(required) >= 0)
			.min(Map.Entry.comparingByKey())
			.map(Map.Entry::getValue)
			.orElse(null);
	}

	private static Map<BigInteger, Plan> retainOnlyMinimumSufficientSum(Map<BigInteger, Plan> states, BigInteger required) {
		BigInteger minimumSufficient = null;
		for (BigInteger sum : states.keySet()) {
			if (sum.compareTo(required) >= 0 && (minimumSufficient == null || sum.compareTo(minimumSufficient) < 0)) {
				minimumSufficient = sum;
			}
		}
		if (minimumSufficient == null) {
			return states;
		}

		Map<BigInteger, Plan> retained = new HashMap<>();
		for (Map.Entry<BigInteger, Plan> entry : states.entrySet()) {
			if (entry.getKey().compareTo(required) < 0 || entry.getKey().equals(minimumSufficient)) {
				retained.put(entry.getKey(), entry.getValue());
			}
		}
		return retained;
	}

	private static Plan betterRepresentation(Plan left, Plan right, List<CapacityGroup> groups) {
		return compareRepresentation(left, right, groups) <= 0 ? left : right;
	}

	private static int compareRepresentation(Plan left, Plan right, List<CapacityGroup> groups) {
		int units = Integer.compare(left.totalUnits, right.totalUnits);
		if (units != 0) {
			return units;
		}

		for (PreferredStack stack : preferredStacks(groups)) {
			int leftSelected = selectedForStack(left.groupUnits[stack.groupIndex], stack);
			int rightSelected = selectedForStack(right.groupUnits[stack.groupIndex], stack);
			int selected = Integer.compare(rightSelected, leftSelected);
			if (selected != 0) {
				return selected;
			}
		}
		return 0;
	}

	private static int selectedForStack(int selectedInGroup, PreferredStack stack) {
		return Math.max(0, Math.min(stack.candidate.units(), selectedInGroup - stack.unitsBefore));
	}

	private static List<PreferredStack> preferredStacks(List<CapacityGroup> groups) {
		List<PreferredStack> preference = new ArrayList<>();
		for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
			int unitsBefore = 0;
			for (TotemCandidate candidate : groups.get(groupIndex).allocationOrder) {
				preference.add(new PreferredStack(groupIndex, unitsBefore, candidate));
				unitsBefore += candidate.units();
			}
		}
		preference.sort(Comparator.comparingInt((PreferredStack stack) -> stack.candidate.units())
			.thenComparing(stack -> stack.candidate.slot()));
		return preference;
	}

	private static List<Integer> binaryChunks(int units) {
		List<Integer> chunks = new ArrayList<>();
		int power = 1;
		int remaining = units;
		while (remaining > 0) {
			int chunk = Math.min(power, remaining);
			chunks.add(chunk);
			remaining -= chunk;
			if (power <= Integer.MAX_VALUE / 2) {
				power *= 2;
			}
		}
		return chunks;
	}

	private static List<TotemCandidate> usefulCandidates(List<TotemCandidate> candidates) {
		if (candidates == null) {
			throw new IllegalArgumentException("candidates");
		}
		Set<SemanticSlot> slots = new HashSet<>();
		for (TotemCandidate candidate : candidates) {
			if (candidate == null || !slots.add(candidate.slot())) {
				throw new IllegalArgumentException("candidate slots");
			}
		}
		return candidates.stream()
			.filter(candidate -> candidate.unlimited() || candidate.capacity() > 0.0)
			.sorted(Comparator.comparing(TotemCandidate::slot))
			.toList();
	}

	private static Result unlimitedResult(double requiredCapacity, List<TotemCandidate> candidates) {
		TotemCandidate selected = candidates.stream()
			.min(Comparator.comparingInt(TotemCandidate::units).thenComparing(TotemCandidate::slot))
			.orElseThrow();
		return new Result(
			List.of(new Selection(selected.slot(), 1, Capacity.unlimited())),
			requiredCapacity,
			true,
			true
		);
	}

	private static List<CapacityGroup> capacityGroups(List<TotemCandidate> candidates) {
		Map<BigInteger, List<TotemCandidate>> byCapacity = new LinkedHashMap<>();
		for (TotemCandidate candidate : candidates) {
			byCapacity.computeIfAbsent(toMilliPoints(candidate.capacity()), ignored -> new ArrayList<>()).add(candidate);
		}
		return byCapacity.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> new CapacityGroup(entry.getKey(), entry.getValue()))
			.toList();
	}

	private static List<Selection> toSelections(List<CapacityGroup> groups, int[] selectedByGroup) {
		Map<SemanticSlot, Selection> bySlot = new HashMap<>();
		for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
			CapacityGroup group = groups.get(groupIndex);
			int remaining = selectedByGroup[groupIndex];
			for (TotemCandidate candidate : group.allocationOrder) {
				int units = Math.min(candidate.units(), remaining);
				if (units > 0) {
					bySlot.put(candidate.slot(), new Selection(candidate.slot(), units, candidate.capacity()));
					remaining -= units;
				}
			}
			if (remaining != 0) {
				throw new IllegalStateException("group allocation invariant");
			}
		}
		return bySlot.values().stream().sorted(Comparator.comparing(Selection::slot)).toList();
	}

	private static List<Selection> selectAll(List<TotemCandidate> candidates) {
		return candidates.stream()
			.map(candidate -> new Selection(candidate.slot(), candidate.units(), candidate.capacity()))
			.toList();
	}

	private static BigInteger selectedCapacity(int[] selectedByGroup, List<CapacityGroup> groups) {
		BigInteger total = BigInteger.ZERO;
		for (int index = 0; index < groups.size(); index++) {
			total = total.add(groups.get(index).milliPoints.multiply(BigInteger.valueOf(selectedByGroup[index])));
		}
		return total;
	}

	private static Result result(List<Selection> selections, BigInteger totalMilliPoints, boolean sufficient) {
		return new Result(selections, new BigDecimal(totalMilliPoints, CAPACITY_SCALE).doubleValue(), sufficient);
	}

	private static BigInteger requiredMilliPoints(double requiredCapacity) {
		return BigDecimal.valueOf(requiredCapacity)
			.multiply(CAPACITY_FACTOR)
			.setScale(0, RoundingMode.CEILING)
			.toBigIntegerExact();
	}

	private static BigInteger toMilliPoints(double capacity) {
		return BigDecimal.valueOf(capacity).movePointRight(CAPACITY_SCALE).toBigIntegerExact();
	}

	private static BigInteger ceilDivide(BigInteger dividend, BigInteger divisor) {
		BigInteger[] quotientAndRemainder = dividend.divideAndRemainder(divisor);
		return quotientAndRemainder[1].signum() == 0
			? quotientAndRemainder[0]
			: quotientAndRemainder[0].add(BigInteger.ONE);
	}

	public record Result(List<Selection> selections, double totalCapacity, boolean sufficient, boolean includesUnlimited) {
		public Result(List<Selection> selections, double totalCapacity, boolean sufficient) {
			this(selections, totalCapacity, sufficient, false);
		}

		public Result {
			selections = List.copyOf(selections);
		}
	}

	private static final class Plan {
		private final int[] groupUnits;
		private final int totalUnits;

		private Plan(int[] groupUnits, int totalUnits) {
			this.groupUnits = groupUnits;
			this.totalUnits = totalUnits;
		}

		private Plan withAdditionalUnits(int groupIndex, int units) {
			int[] selected = groupUnits.clone();
			selected[groupIndex] += units;
			return new Plan(selected, Math.addExact(totalUnits, units));
		}
	}

	private static final class CapacityGroup {
		private final BigInteger milliPoints;
		private final List<TotemCandidate> allocationOrder;
		private final int totalUnits;

		private CapacityGroup(BigInteger milliPoints, List<TotemCandidate> candidates) {
			this.milliPoints = milliPoints;
			this.allocationOrder = candidates.stream()
				.sorted(Comparator.comparingInt(TotemCandidate::units).thenComparing(TotemCandidate::slot))
				.toList();
			this.totalUnits = this.allocationOrder.stream().mapToInt(TotemCandidate::units).sum();
		}

		private CapacityGroup withMilliPoints(BigInteger scaledMilliPoints) {
			return new CapacityGroup(scaledMilliPoints, allocationOrder);
		}

		private BigInteger milliPoints() {
			return milliPoints;
		}

		private BigInteger totalMilliPoints() {
			return milliPoints.multiply(BigInteger.valueOf(totalUnits));
		}
	}

	private record PreferredStack(int groupIndex, int unitsBefore, TotemCandidate candidate) {
	}
}
