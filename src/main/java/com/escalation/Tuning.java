package com.escalation;

/**
 * ALL balance constants and curves for this mod. One file, no conditionals, every node.
 *
 * <p>Empty on purpose — this is the scaffold. The house rule it exists to enforce is that
 * nothing else in the tree ever carries a balance number: rarity multipliers, affix costs,
 * loot amounts, the progression curves, the two hard guarantees' thresholds and every spawn
 * knob's default all land here ({@code docs/DESIGN.md} §2.1, §4.4, §5.1). The sibling repos
 * hold the same rule, and the property it buys is measurable: a one-character edit here must
 * move {@code Tuning.class} and nothing else in the built jar.
 *
 * <p>Two constraints on anything added below:
 *
 * <ul>
 * <li><b>Java 17 is the ceiling</b>, not the 25 this node compiles at. The port's 1.20.1
 *     node compiles this same file at source level 17. Records are fine; pattern-matching
 *     {@code switch}, sealed types and {@code Math.clamp} are not.</li>
 * <li><b>No conditionals of any kind</b> — no {@code //?} directives, no per-version
 *     branches. A version that needs a different number needs a different constant, not a
 *     fork of this file.</li>
 * </ul>
 */
public final class Tuning {
	private Tuning() {
	}
}
