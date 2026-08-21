package io.github.unknownassets.totemic.logic;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public record DeathProtectionSnapshot(
	double previousHealth,
	double appliedDamage,
	Optional<TotemCandidate> mainHand,
	Optional<TotemCandidate> offHand,
	List<TotemCandidate> storage,
	List<TotemCandidate> hotbar,
	PmdRoundingPolicy roundingPolicy,
	boolean consumeOnFailure,
	Set<SemanticSlot> excludedHands
) {
	public DeathProtectionSnapshot(
		double previousHealth,
		double appliedDamage,
		Optional<TotemCandidate> mainHand,
		Optional<TotemCandidate> offHand,
		List<TotemCandidate> storage,
		List<TotemCandidate> hotbar
	) {
		this(previousHealth, appliedDamage, mainHand, offHand, storage, hotbar,
			PmdRoundingPolicy.EXACT, true, Set.of());
	}

	public DeathProtectionSnapshot(
		double previousHealth,
		double appliedDamage,
		Optional<TotemCandidate> mainHand,
		Optional<TotemCandidate> offHand,
		List<TotemCandidate> storage,
		List<TotemCandidate> hotbar,
		PmdRoundingPolicy roundingPolicy
	) {
		this(previousHealth, appliedDamage, mainHand, offHand, storage, hotbar,
			roundingPolicy, true, Set.of());
	}

	public DeathProtectionSnapshot {
		if (!Double.isFinite(previousHealth) || previousHealth < 0.0) {
			throw new IllegalArgumentException("previousHealth");
		}
		if (!Double.isFinite(appliedDamage) || appliedDamage < 0.0) {
			throw new IllegalArgumentException("appliedDamage");
		}
		mainHand = mainHand == null ? Optional.empty() : mainHand;
		offHand = offHand == null ? Optional.empty() : offHand;
		storage = storage == null ? List.of() : storage.stream().sorted((left, right) -> left.slot().compareTo(right.slot())).toList();
		hotbar = hotbar == null ? List.of() : hotbar.stream().sorted((left, right) -> left.slot().compareTo(right.slot())).toList();
		if (roundingPolicy == null) {
			throw new IllegalArgumentException("roundingPolicy");
		}
		excludedHands = excludedHands == null ? Set.of() : Set.copyOf(excludedHands);
		mainHand.ifPresent(candidate -> requireKind(candidate, SemanticSlot.Kind.MAIN_HAND));
		offHand.ifPresent(candidate -> requireKind(candidate, SemanticSlot.Kind.OFF_HAND));
		storage.forEach(candidate -> requireKind(candidate, SemanticSlot.Kind.STORAGE));
		hotbar.forEach(candidate -> requireKind(candidate, SemanticSlot.Kind.HOTBAR));
		for (SemanticSlot excluded : excludedHands) {
			if (excluded == null || (excluded.kind() != SemanticSlot.Kind.MAIN_HAND
				&& excluded.kind() != SemanticSlot.Kind.OFF_HAND)) {
				throw new IllegalArgumentException("excluded hand");
			}
			boolean present = excluded.kind() == SemanticSlot.Kind.MAIN_HAND
				? mainHand.isPresent()
				: offHand.isPresent();
			if (!present) {
				throw new IllegalArgumentException("missing excluded hand");
			}
		}
	}

	private static void requireKind(TotemCandidate candidate, SemanticSlot.Kind expected) {
		if (candidate == null || candidate.slot().kind() != expected) {
			throw new IllegalArgumentException("candidate pool");
		}
	}
}
