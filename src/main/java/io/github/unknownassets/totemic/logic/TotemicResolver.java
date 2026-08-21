package io.github.unknownassets.totemic.logic;

import io.github.unknownassets.totemic.logic.TotemicResolution.Outcome;
import io.github.unknownassets.totemic.logic.TotemicResolution.Selection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class TotemicResolver {
	private final ExactPoolSolver poolSolver = new ExactPoolSolver();

	public TotemicResolution resolve(DeathProtectionSnapshot snapshot) {
		double rawPmd = nonNegativeDifference(snapshot.appliedDamage(), snapshot.previousHealth()).doubleValue();
		double effectivePmd = snapshot.roundingPolicy().apply(rawPmd);
		if (snapshot.mainHand().isPresent()
			&& snapshot.excludedHands().contains(SemanticSlot.mainHand())) {
			return result(Outcome.VANILLA_DELEGATED, rawPmd, effectivePmd,
				snapshot.roundingPolicy(), List.of());
		}
		TotemCandidate activator = snapshot.mainHand().orElseGet(() -> snapshot.offHand().orElse(null));
		if (activator == null) {
			return notApplicable(rawPmd, effectivePmd, snapshot.roundingPolicy());
		}
		if (snapshot.excludedHands().contains(activator.slot())) {
			return result(Outcome.VANILLA_DELEGATED, rawPmd, effectivePmd,
				snapshot.roundingPolicy(), List.of());
		}

		List<Selection> selections = new ArrayList<>();
		CandidateUse activatorUse = useCandidateStack(activator, BigDecimal.valueOf(effectivePmd), true);
		selections.add(selection(activator, activatorUse.units));
		BigDecimal remaining = activatorUse.remaining;
		if (remaining.signum() == 0) {
			return result(Outcome.PROTECTED, rawPmd, effectivePmd, snapshot.roundingPolicy(), selections);
		}

		if (snapshot.mainHand().isPresent() && snapshot.offHand().isPresent()) {
			TotemCandidate secondHand = snapshot.offHand().get();
			if (snapshot.excludedHands().contains(secondHand.slot())) {
				return result(Outcome.VANILLA_DELEGATED, rawPmd, effectivePmd,
					snapshot.roundingPolicy(), selections);
			}
			CandidateUse secondHandUse = useCandidateStack(secondHand, remaining, false);
			if (secondHandUse.units > 0) {
				selections.add(selection(secondHand, secondHandUse.units));
				remaining = secondHandUse.remaining;
			}
			if (remaining.signum() == 0) {
				return result(Outcome.PROTECTED, rawPmd, effectivePmd, snapshot.roundingPolicy(), selections);
			}
		}

		remaining = resolvePool(snapshot.storage(), remaining, selections);
		if (remaining.signum() == 0) {
			return result(Outcome.PROTECTED, rawPmd, effectivePmd, snapshot.roundingPolicy(), selections);
		}

		remaining = resolvePool(snapshot.hotbar(), remaining, selections);
		if (remaining.signum() == 0) {
			return result(Outcome.PROTECTED, rawPmd, effectivePmd,
				snapshot.roundingPolicy(), selections);
		}
		return result(Outcome.INSUFFICIENT, rawPmd, effectivePmd,
			snapshot.roundingPolicy(), snapshot.consumeOnFailure() ? selections : List.of());
	}

	private BigDecimal resolvePool(List<TotemCandidate> pool, BigDecimal remaining, List<Selection> selections) {
		if (pool.stream().anyMatch(TotemCandidate::unlimited)) {
			ExactPoolSolver.Result poolResult = poolSolver.solve(remaining.doubleValue(), pool);
			selections.addAll(poolResult.selections());
			return BigDecimal.ZERO;
		}
		BigDecimal available = pool.stream()
			.filter(candidate -> candidate.capacity() > 0.0)
			.map(candidate -> BigDecimal.valueOf(candidate.capacity()).multiply(BigDecimal.valueOf(candidate.units())))
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		if (available.signum() == 0) {
			return remaining;
		}

		if (available.compareTo(remaining) < 0) {
			for (TotemCandidate candidate : pool) {
				if (candidate.capacity() > 0.0) {
					selections.add(new Selection(candidate.slot(), candidate.units(), candidate.capacity()));
				}
			}
			return remaining.subtract(available);
		}

		ExactPoolSolver.Result poolResult = poolSolver.solve(remaining.doubleValue(), pool);
		selections.addAll(poolResult.selections());
		return remaining.subtract(BigDecimal.valueOf(poolResult.totalCapacity())).max(BigDecimal.ZERO);
	}

	private static BigDecimal nonNegativeDifference(double minuend, double subtrahend) {
		return BigDecimal.valueOf(minuend).subtract(BigDecimal.valueOf(subtrahend)).max(BigDecimal.ZERO);
	}

	private static CandidateUse useCandidateStack(TotemCandidate candidate, BigDecimal remaining, boolean activator) {
		int selectedUnits = activator ? 1 : 0;
		if (candidate.unlimited()) {
			return new CandidateUse(1, BigDecimal.ZERO);
		}

		BigDecimal unitCapacity = BigDecimal.valueOf(candidate.capacity());
		if (activator) {
			remaining = remaining.subtract(unitCapacity).max(BigDecimal.ZERO);
		}
		if (remaining.signum() == 0 || unitCapacity.signum() == 0) {
			return new CandidateUse(selectedUnits, remaining);
		}

		int availableUnits = candidate.units() - selectedUnits;
		if (availableUnits <= 0) {
			return new CandidateUse(selectedUnits, remaining);
		}
		BigDecimal requiredUnits = remaining.divide(unitCapacity, 0, java.math.RoundingMode.CEILING);
		int additionalUnits = requiredUnits.compareTo(BigDecimal.valueOf(availableUnits)) >= 0
			? availableUnits
			: requiredUnits.intValueExact();
		selectedUnits += additionalUnits;
		remaining = remaining.subtract(unitCapacity.multiply(BigDecimal.valueOf(additionalUnits))).max(BigDecimal.ZERO);
		return new CandidateUse(selectedUnits, remaining);
	}

	private static Selection selection(TotemCandidate candidate, int units) {
		return new Selection(candidate.slot(), units, candidate.capacityValue());
	}

	private static TotemicResolution notApplicable(double rawPmd, double effectivePmd, PmdRoundingPolicy policy) {
		return new TotemicResolution(Outcome.NOT_APPLICABLE, rawPmd, effectivePmd,
			policy != PmdRoundingPolicy.EXACT, 0.0, false, List.of());
	}

	private static TotemicResolution result(
		Outcome outcome,
		double rawPmd,
		double effectivePmd,
		PmdRoundingPolicy policy,
		List<Selection> selections
	) {
		double committed = selections.stream().mapToDouble(Selection::totalCapacity).sum();
		boolean includesUnlimited = selections.stream().anyMatch(Selection::unlimited);
		return new TotemicResolution(outcome, rawPmd, effectivePmd,
			policy != PmdRoundingPolicy.EXACT, committed, includesUnlimited, selections);
	}

	private record CandidateUse(int units, BigDecimal remaining) {
	}
}
