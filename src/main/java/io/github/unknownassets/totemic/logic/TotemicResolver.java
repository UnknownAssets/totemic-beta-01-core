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
		TotemCandidate activator = snapshot.mainHand().orElseGet(() -> snapshot.offHand().orElse(null));
		if (activator == null) {
			return notApplicable(rawPmd, effectivePmd, snapshot.roundingPolicy());
		}

		List<Selection> selections = new ArrayList<>();
		selections.add(oneUnit(activator));
		if (activator.unlimited()) {
			return result(Outcome.PROTECTED, rawPmd, effectivePmd, snapshot.roundingPolicy(), selections);
		}
		BigDecimal remaining = subtractFiniteCapacity(BigDecimal.valueOf(effectivePmd), activator);
		if (remaining.signum() == 0) {
			return result(Outcome.PROTECTED, rawPmd, effectivePmd, snapshot.roundingPolicy(), selections);
		}

		if (snapshot.mainHand().isPresent() && snapshot.offHand().isPresent()) {
			TotemCandidate secondHand = snapshot.offHand().get();
			if (secondHand.unlimited()) {
				selections.add(oneUnit(secondHand));
				return result(Outcome.PROTECTED, rawPmd, effectivePmd, snapshot.roundingPolicy(), selections);
			}
			if (secondHand.capacity() > 0.0) {
				selections.add(oneUnit(secondHand));
				remaining = subtractFiniteCapacity(remaining, secondHand);
				if (remaining.signum() == 0) {
					return result(Outcome.PROTECTED, rawPmd, effectivePmd, snapshot.roundingPolicy(), selections);
				}
			}
		}

		remaining = resolvePool(snapshot.storage(), remaining, selections);
		if (remaining.signum() == 0) {
			return result(Outcome.PROTECTED, rawPmd, effectivePmd, snapshot.roundingPolicy(), selections);
		}

		remaining = resolvePool(snapshot.hotbar(), remaining, selections);
		return result(remaining.signum() == 0 ? Outcome.PROTECTED : Outcome.INSUFFICIENT,
			rawPmd, effectivePmd, snapshot.roundingPolicy(), selections);
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

	private static BigDecimal subtractFiniteCapacity(BigDecimal remaining, TotemCandidate candidate) {
		return remaining.subtract(BigDecimal.valueOf(candidate.capacity())).max(BigDecimal.ZERO);
	}

	private static BigDecimal nonNegativeDifference(double minuend, double subtrahend) {
		return BigDecimal.valueOf(minuend).subtract(BigDecimal.valueOf(subtrahend)).max(BigDecimal.ZERO);
	}

	private static Selection oneUnit(TotemCandidate candidate) {
		return new Selection(candidate.slot(), 1, candidate.capacityValue());
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
}
