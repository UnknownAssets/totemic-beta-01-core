package io.github.unknownassets.totemic.logic.config;

import io.github.unknownassets.totemic.logic.PmdRoundingPolicy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record TotemicConfigLayer(
	Optional<Boolean> consumeOnFailure,
	Optional<PmdRoundingPolicy> roundingPolicy,
	Map<String, TotemConfigPatch> definitions
) {
	public static final TotemicConfigLayer EMPTY = new TotemicConfigLayer(
		Optional.empty(), Optional.empty(), Map.of()
	);

	public TotemicConfigLayer {
		consumeOnFailure = consumeOnFailure == null ? Optional.empty() : consumeOnFailure;
		roundingPolicy = roundingPolicy == null ? Optional.empty() : roundingPolicy;
		if (definitions == null) {
			definitions = Map.of();
		} else {
			Map<String, TotemConfigPatch> copy = new LinkedHashMap<>();
			definitions.forEach((definitionId, patch) -> {
				if (definitionId == null || definitionId.isBlank() || patch == null) {
					throw new IllegalArgumentException("definition");
				}
				copy.put(definitionId, patch);
			});
			definitions = Map.copyOf(copy);
		}
	}

	public TotemConfigPatch combineMatching(Iterable<String> definitionIds) {
		if (definitionIds == null) {
			throw new IllegalArgumentException("definitionIds");
		}
		TotemConfigPatch combined = TotemConfigPatch.EMPTY;
		for (String definitionId : definitionIds) {
			if (definitionId == null) {
				throw new IllegalArgumentException("definitionId");
			}
			TotemConfigPatch matching = definitions.get(definitionId);
			if (matching != null) {
				combined = combined.overlay(matching);
			}
		}
		return combined;
	}
}
