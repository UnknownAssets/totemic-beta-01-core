package io.github.unknownassets.totemic.logic;

public record SemanticSlot(Kind kind, int index) implements Comparable<SemanticSlot> {
	public enum Kind {
		MAIN_HAND,
		OFF_HAND,
		STORAGE,
		HOTBAR
	}

	public SemanticSlot {
		if (kind == null) {
			throw new IllegalArgumentException("kind");
		}
		if ((kind == Kind.MAIN_HAND || kind == Kind.OFF_HAND) && index != -1) {
			throw new IllegalArgumentException("hand index");
		}
		if ((kind == Kind.STORAGE || kind == Kind.HOTBAR) && index < 0) {
			throw new IllegalArgumentException("inventory index");
		}
	}

	public static SemanticSlot mainHand() {
		return new SemanticSlot(Kind.MAIN_HAND, -1);
	}

	public static SemanticSlot offHand() {
		return new SemanticSlot(Kind.OFF_HAND, -1);
	}

	public static SemanticSlot storage(int inventoryIndex) {
		return new SemanticSlot(Kind.STORAGE, inventoryIndex);
	}

	public static SemanticSlot hotbar(int inventoryIndex) {
		return new SemanticSlot(Kind.HOTBAR, inventoryIndex);
	}

	@Override
	public int compareTo(SemanticSlot other) {
		int kindComparison = Integer.compare(this.kind.ordinal(), other.kind.ordinal());
		return kindComparison != 0 ? kindComparison : Integer.compare(this.index, other.index);
	}
}
