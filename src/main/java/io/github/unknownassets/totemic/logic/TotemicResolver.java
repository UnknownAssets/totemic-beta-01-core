package io.github.unknownassets.totemic.logic;

import io.github.unknownassets.totemic.logic.TotemicResolution.Outcome;
import io.github.unknownassets.totemic.logic.TotemicResolution.Selection;

import java.util.ArrayList;
import java.util.List;

public final class TotemicResolver {
	private final ExactPoolSolver poolSolver = new ExactPoolSolver();

	public TotemicResolution resolve(DeathProtectionSnapshot snapshot) {
		double rawPmd = Math.max(0.0, snapshot.appliedDamage() - snapshot.previousHealth());
		TotemCandidate activator = snapshot.mainHand().orElseGet(() -> snapshot.offHand().orElse(null));
		if (activator == null) {
			return TotemicResolution.notApplicable(rawPmd);
		}

		List<Selection> selections = new ArrayList<>();
		selections.add(oneUnit(activator));
		double remaining = Math.max(0.0, rawPmd - activator.capacity());
		if (remaining <= 0.0) {
			return result(Outcome.PROTECTED, rawPmd, selections);
		}

		if (snapshot.mainHand().isPresent() && snapshot.offHand().isPresent()) {
			TotemCandidate secondHand = snapshot.offHand().get();
			if (secondHand.capacity() > 0.0) {
				selections.add(oneUnit(secondHand));
				remaining = Math.max(0.0, remaining - secondHand.capacity());
				if (remaining <= 0.0) {
					return result(Outcome.PROTECTED, rawPmd, selections);
				}
			}
		}

		remaining = resolvePool(snapshot.storage(), remaining, selections);
		if (remaining <= 0.0) {
			return result(Outcome.PROTECTED, rawPmd, selections);
		}

		remaining = resolvePool(snapshot.hotbar(), remaining, selections);
		return result(remaining <= 0.0 ? Outcome.PROTECTED : Outcome.INSUFFICIENT, rawPmd, selections);
	}

	private double resolvePool(List<TotemCandidate> pool, double remaining, List<Selection> selections) {
		double available = pool.stream().filter(candidate -> candidate.capacity() > 0.0)
			.mapToDouble(candidate -> candidate.capacity() * candidate.units()).sum();
		if (available <= 0.0) {
			return remaining;
		}

		if (available < remaining) {
			for (TotemCandidate candidate : pool) {
				if (candidate.capacity() > 0.0) {
					selections.add(new Selection(candidate.slot(), candidate.units(), candidate.capacity()));
				}
			}
			return remaining - available;
		}

		ExactPoolSolver.Result poolResult = poolSolver.solve(remaining, pool);
		selections.addAll(poolResult.selections());
		return Math.max(0.0, remaining - poolResult.totalCapacity());
	}

	private static Selection oneUnit(TotemCandidate candidate) {
		return new Selection(candidate.slot(), 1, candidate.capacity());
	}

	private static TotemicResolution result(Outcome outcome, double rawPmd, List<Selection> selections) {
		double committed = selections.stream().mapToDouble(Selection::totalCapacity).sum();
		return new TotemicResolution(outcome, rawPmd, committed, selections);
	}
}
