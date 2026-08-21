package io.github.unknownassets.totemic.logic.config;

public record TotemicConfigSnapshot(
	String sourceId,
	long sourceRevision,
	TotemicConfigLayer configuration
) {
	public TotemicConfigSnapshot {
		if (sourceId == null || sourceId.isBlank()) {
			throw new IllegalArgumentException("sourceId");
		}
		if (sourceRevision < 0) {
			throw new IllegalArgumentException("sourceRevision");
		}
		if (configuration == null) {
			throw new IllegalArgumentException("configuration");
		}
	}
}
