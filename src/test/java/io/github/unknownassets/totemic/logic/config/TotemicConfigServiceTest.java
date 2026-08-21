package io.github.unknownassets.totemic.logic.config;

import io.github.unknownassets.totemic.logic.Capacity;
import io.github.unknownassets.totemic.logic.PmdRoundingPolicy;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotemicConfigServiceTest {
	private static final CapacityProfile DEFAULT_CAPACITY = new CapacityProfile(
		Capacity.finite(30), Capacity.finite(30), Capacity.finite(20), Capacity.finite(10)
	);

	@Test
	void fallsBackToBuiltInDefaultsWithoutAnImportedSnapshot() {
		TotemicConfigService service = service();

		EffectiveTotemConfig peaceful = service.resolve(
			DifficultyTier.PEACEFUL, List.of(), UniqueTotemOverride.EMPTY
		);
		EffectiveTotemConfig hard = service.resolve(
			DifficultyTier.HARD, List.of(), UniqueTotemOverride.EMPTY
		);

		assertEquals(Capacity.finite(30), peaceful.capacity());
		assertEquals(Capacity.finite(10), hard.capacity());
		assertTrue(hard.consumeOnFailure());
		assertEquals(PmdRoundingPolicy.EXACT, hard.roundingPolicy());
		assertFalse(hard.excluded());
	}

	@Test
	void combinesAllMatchingSnapshotFieldsInSpecificityOrder() {
		TotemicConfigService service = service();
		TotemicConfigLayer snapshot = new TotemicConfigLayer(
			Optional.of(false),
			Optional.of(PmdRoundingPolicy.HALF_UP),
			Map.of(
				"pack:plants", TotemConfigPatch.fixedCapacity(5),
				"pack:large_fern", TotemConfigPatch.excluded(true)
			)
		);
		service.activate(new TotemicConfigSnapshot("pack", 1, snapshot));

		EffectiveTotemConfig effective = service.resolve(
			DifficultyTier.NORMAL,
			List.of("pack:plants", "pack:large_fern"),
			UniqueTotemOverride.EMPTY
		);

		assertEquals(Capacity.finite(5), effective.capacity());
		assertTrue(effective.excluded());
		assertFalse(effective.consumeOnFailure());
		assertEquals(PmdRoundingPolicy.HALF_UP, effective.roundingPolicy());
	}

	@Test
	void appliesOverrideThenUniqueAboveSnapshotAndDefaults() {
		TotemicConfigService service = service();
		service.activate(new TotemicConfigSnapshot("pack", 3, new TotemicConfigLayer(
			Optional.of(false),
			Optional.of(PmdRoundingPolicy.HALF_UP),
			Map.of("pack:plants", new TotemConfigPatch(
				Optional.of(CapacityProfile.fixed(5)), Optional.of(true)
			))
		)));
		service.replaceOverrides(new TotemicConfigLayer(
			Optional.of(true),
			Optional.of(PmdRoundingPolicy.EXACT),
			Map.of("pack:plants", new TotemConfigPatch(
				Optional.of(CapacityProfile.fixed(8)), Optional.of(false)
			))
		));

		EffectiveTotemConfig globalOverride = service.resolve(
			DifficultyTier.NORMAL, List.of("pack:plants"), UniqueTotemOverride.EMPTY
		);
		EffectiveTotemConfig uniqueOverride = service.resolve(
			DifficultyTier.NORMAL,
			List.of("pack:plants"),
			new UniqueTotemOverride(Optional.of(Capacity.finite(13)), Optional.of(true))
		);

		assertEquals(Capacity.finite(8), globalOverride.capacity());
		assertFalse(globalOverride.excluded());
		assertTrue(globalOverride.consumeOnFailure());
		assertEquals(PmdRoundingPolicy.EXACT, globalOverride.roundingPolicy());
		assertEquals(Capacity.finite(13), uniqueOverride.capacity());
		assertTrue(uniqueOverride.excluded());
	}

	@Test
	void higherPriorityFieldsDoNotEraseUnrelatedLowerPriorityFields() {
		TotemicConfigService service = service();
		service.activate(new TotemicConfigSnapshot("pack", 1, new TotemicConfigLayer(
			Optional.empty(), Optional.empty(), Map.of(
				"pack:plants", new TotemConfigPatch(
					Optional.of(CapacityProfile.fixed(4)), Optional.of(true)
				)
			)
		)));
		service.replaceOverrides(new TotemicConfigLayer(
			Optional.empty(), Optional.empty(), Map.of(
				"pack:plants", TotemConfigPatch.fixedCapacity(9)
			)
		));

		EffectiveTotemConfig effective = service.resolve(
			DifficultyTier.EASY, List.of("pack:plants"), UniqueTotemOverride.EMPTY
		);

		assertEquals(Capacity.finite(9), effective.capacity());
		assertTrue(effective.excluded());
	}

	@Test
	void rejectedAndMissingSourcesKeepTheLastValidSnapshot() {
		TotemicConfigService service = service();
		TotemicConfigSnapshot valid = new TotemicConfigSnapshot("pack", 7, new TotemicConfigLayer(
			Optional.empty(), Optional.empty(), Map.of("pack:a", TotemConfigPatch.fixedCapacity(2))
		));
		TotemicConfigService.SnapshotUpdate active = service.activate(valid);

		TotemicConfigService.SnapshotUpdate rejected = service.reject();
		EffectiveTotemConfig afterRejection = service.resolve(
			DifficultyTier.NORMAL, List.of("pack:a"), UniqueTotemOverride.EMPTY
		);
		TotemicConfigService.SnapshotUpdate missing = service.sourceMissing();
		EffectiveTotemConfig afterMissing = service.resolve(
			DifficultyTier.NORMAL, List.of("pack:a"), UniqueTotemOverride.EMPTY
		);

		assertEquals(TotemicConfigService.SnapshotStatus.ACTIVE, active.status());
		assertEquals(TotemicConfigService.SnapshotStatus.REJECTED, rejected.status());
		assertEquals(valid, rejected.activeSnapshot().orElseThrow());
		assertEquals(active.configurationRevision(), rejected.configurationRevision());
		assertEquals(Capacity.finite(2), afterRejection.capacity());
		assertEquals(TotemicConfigService.SnapshotStatus.SOURCE_MISSING, missing.status());
		assertEquals(valid, missing.activeSnapshot().orElseThrow());
		assertEquals(Capacity.finite(2), afterMissing.capacity());
	}

	@Test
	void explicitSnapshotDeletionReturnsToDefaultsWithoutDeletingOverrides() {
		TotemicConfigService service = service();
		service.activate(new TotemicConfigSnapshot("pack", 1, new TotemicConfigLayer(
			Optional.empty(), Optional.empty(), Map.of("pack:a", TotemConfigPatch.fixedCapacity(2))
		)));
		service.replaceOverrides(new TotemicConfigLayer(
			Optional.empty(), Optional.empty(), Map.of("pack:b", TotemConfigPatch.fixedCapacity(7))
		));

		TotemicConfigService.SnapshotUpdate cleared = service.clearSnapshot();

		assertEquals(TotemicConfigService.SnapshotStatus.DEFAULTS, cleared.status());
		assertTrue(cleared.activeSnapshot().isEmpty());
		assertEquals(Capacity.finite(20), service.resolve(
			DifficultyTier.NORMAL, List.of("pack:a"), UniqueTotemOverride.EMPTY
		).capacity());
		assertEquals(Capacity.finite(7), service.resolve(
			DifficultyTier.NORMAL, List.of("pack:b"), UniqueTotemOverride.EMPTY
		).capacity());
	}

	@Test
	void configurationLayersDefensivelyCopyDefinitionsAndRejectInvalidIds() {
		Map<String, TotemConfigPatch> definitions = new LinkedHashMap<>();
		definitions.put("pack:a", TotemConfigPatch.fixedCapacity(3));
		TotemicConfigLayer layer = new TotemicConfigLayer(
			Optional.empty(), Optional.empty(), definitions
		);
		definitions.clear();

		assertEquals(TotemConfigPatch.fixedCapacity(3), layer.definitions().get("pack:a"));
		assertThrows(UnsupportedOperationException.class, () -> layer.definitions().clear());
		assertThrows(IllegalArgumentException.class, () -> new TotemicConfigLayer(
			Optional.empty(), Optional.empty(), Map.of(" ", TotemConfigPatch.EMPTY)
		));
		assertThrows(IllegalArgumentException.class, () -> new TotemicConfigSnapshot(
			"pack", -1, TotemicConfigLayer.EMPTY
		));
	}

	@Test
	void configurationViewRemainsCoherentAcrossLaterSnapshotChanges() {
		TotemicConfigService service = service();
		service.activate(new TotemicConfigSnapshot("pack", 1, new TotemicConfigLayer(
			Optional.empty(), Optional.empty(), Map.of("pack:a", TotemConfigPatch.fixedCapacity(2))
		)));
		TotemicConfigService.ConfigurationView captured = service.view();

		service.activate(new TotemicConfigSnapshot("pack", 2, new TotemicConfigLayer(
			Optional.empty(), Optional.empty(), Map.of("pack:a", TotemConfigPatch.fixedCapacity(9))
		)));

		assertEquals(Capacity.finite(2), captured.resolve(
			DifficultyTier.NORMAL, List.of("pack:a"), UniqueTotemOverride.EMPTY
		).capacity());
		assertEquals(Capacity.finite(9), service.resolve(
			DifficultyTier.NORMAL, List.of("pack:a"), UniqueTotemOverride.EMPTY
		).capacity());
		assertTrue(captured.configurationRevision() < service.view().configurationRevision());
	}

	private static TotemicConfigService service() {
		return new TotemicConfigService(new TotemicConfigService.Defaults(
			DEFAULT_CAPACITY, true, PmdRoundingPolicy.EXACT
		));
	}
}
