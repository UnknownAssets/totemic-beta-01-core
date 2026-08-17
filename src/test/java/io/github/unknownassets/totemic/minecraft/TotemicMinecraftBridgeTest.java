package io.github.unknownassets.totemic.minecraft;

import io.github.unknownassets.totemic.logic.SemanticSlot;
import io.github.unknownassets.totemic.logic.TotemicResolution;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TotemicMinecraftBridgeTest {
	@Test
	void formatsTheRequestedAnnouncement() {
		TotemicResolution resolution = new TotemicResolution(
			TotemicResolution.Outcome.PROTECTED,
			12.0,
			15.0,
			List.of(
				new TotemicResolution.Selection(SemanticSlot.mainHand(), 1, 5.0),
				new TotemicResolution.Selection(SemanticSlot.storage(9), 2, 5.0)
			)
		);

		assertEquals("(3 Totems) [15/12]", TotemicMinecraftBridge.announcementText(resolution));
	}

	@Test
	void keepsTheAnnouncementCompactForOneTotemAndFractionalDamage() {
		TotemicResolution resolution = new TotemicResolution(
			TotemicResolution.Outcome.PROTECTED,
			12.5,
			20.0,
			List.of(new TotemicResolution.Selection(SemanticSlot.offHand(), 1, 20.0))
		);

		assertEquals("(1 Totem) [20/12.5]", TotemicMinecraftBridge.announcementText(resolution));
	}
}
