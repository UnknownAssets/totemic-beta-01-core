package io.github.unknownassets.totemic.logic;

import java.util.List;
import java.util.Optional;

public record DeathProtectionSnapshot(
	double previousHealth,
	double appliedDamage,
	Optional<TotemCandidate> mainHand,
	Optional<TotemCandidate> offHand,
	List<TotemCandidate> storage,
	List<TotemCandidate> hotbar
) {
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
	}
}
