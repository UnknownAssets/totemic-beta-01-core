package io.github.unknownassets.totemic.logic;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum PmdRoundingPolicy {
	EXACT {
		@Override
		public double apply(double rawPmd) {
			return requirePmd(rawPmd);
		}
	},
	HALF_UP {
		@Override
		public double apply(double rawPmd) {
			requirePmd(rawPmd);
			return BigDecimal.valueOf(rawPmd).setScale(0, RoundingMode.HALF_UP).doubleValue();
		}
	};

	public abstract double apply(double rawPmd);

	private static double requirePmd(double rawPmd) {
		if (!Double.isFinite(rawPmd) || rawPmd < 0.0) {
			throw new IllegalArgumentException("rawPmd");
		}
		return rawPmd == 0.0 ? 0.0 : rawPmd;
	}
}
