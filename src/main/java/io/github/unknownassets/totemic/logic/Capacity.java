package io.github.unknownassets.totemic.logic;

import java.math.BigDecimal;

public sealed interface Capacity permits Capacity.Finite, Capacity.Unlimited {
	static Finite finite(double points) {
		return new Finite(points);
	}

	static Unlimited unlimited() {
		return Unlimited.INSTANCE;
	}

	default boolean isUnlimited() {
		return this instanceof Unlimited;
	}

	record Finite(double points) implements Capacity {
		public Finite {
			if (!Double.isFinite(points) || points < 0.0) {
				throw new IllegalArgumentException("capacity");
			}
			if (BigDecimal.valueOf(points).stripTrailingZeros().scale() > 3) {
				throw new IllegalArgumentException("capacity precision");
			}
			if (points == 0.0) {
				points = 0.0;
			}
		}
	}

	enum Unlimited implements Capacity {
		INSTANCE
	}
}
