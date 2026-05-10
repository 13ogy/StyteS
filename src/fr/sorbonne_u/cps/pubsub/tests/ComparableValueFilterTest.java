package fr.sorbonne_u.cps.pubsub.tests;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.ValueFilterI;
import fr.sorbonne_u.cps.pubsub.messages.filters.BetweenInclusiveValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.ComparableValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.GreaterOrEqualValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.LowerOrEqualValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.StrictlyGreaterValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.StrictlyLowerValueFilter;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for the {@link ComparableValueFilter} hierarchy (CDC §3.2).
 *
 * <p>
 * The single-operator {@code ComparableValueFilter} was refactored into a
 * class hierarchy:
 * </p>
 * <ul>
 * <li>{@link GreaterOrEqualValueFilter}</li>
 * <li>{@link LowerOrEqualValueFilter}</li>
 * <li>{@link BetweenInclusiveValueFilter}</li>
 * <li>{@link StrictlyGreaterValueFilter}</li>
 * <li>{@link StrictlyLowerValueFilter}</li>
 * </ul>
 *
 * <p>
 * What is being tested:
 * </p>
 * <ul>
 * <li>Boundary, above-boundary, and below-boundary cases with Integer values.</li>
 * <li>Factory methods return the correct concrete class.</li>
 * <li>BetweenInclusive with inverted bounds (lower &gt; upper) does NOT throw
 * (constructor is lenient) but matches nothing.</li>
 * <li>match returns false on null.</li>
 * <li>match returns false on non-Comparable input.</li>
 * </ul>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class ComparableValueFilterTest
{
	private static void info(String s)
	{
		System.out.println("[ComparableValueFilterTest] " + s);
	}

	// -------------------------------------------------------------------------
	// GreaterOrEqualValueFilter
	// -------------------------------------------------------------------------

	/** {@link GreaterOrEqualValueFilter} : à la borne, accepte (≥). */
	@Test
	public void testGreaterOrEqualAtBoundary()
	{
		info("GreaterOrEqualValueFilter: at boundary returns true.");
		ValueFilterI f = new GreaterOrEqualValueFilter(10);
		assertTrue(f.match(10));
	}

	/** {@link GreaterOrEqualValueFilter} : au-dessus de la borne, accepte. */
	@Test
	public void testGreaterOrEqualAboveBoundary()
	{
		info("GreaterOrEqualValueFilter: above boundary returns true.");
		ValueFilterI f = new GreaterOrEqualValueFilter(10);
		assertTrue(f.match(11));
		assertTrue(f.match(100));
	}

	/** {@link GreaterOrEqualValueFilter} : en-dessous de la borne, rejette. */
	@Test
	public void testGreaterOrEqualBelowBoundary()
	{
		info("GreaterOrEqualValueFilter: below boundary returns false.");
		ValueFilterI f = new GreaterOrEqualValueFilter(10);
		assertFalse(f.match(9));
		assertFalse(f.match(-5));
	}

	// -------------------------------------------------------------------------
	// LowerOrEqualValueFilter
	// -------------------------------------------------------------------------

	/** {@link LowerOrEqualValueFilter} : à la borne, accepte (≤). */
	@Test
	public void testLowerOrEqualAtBoundary()
	{
		info("LowerOrEqualValueFilter: at boundary returns true.");
		ValueFilterI f = new LowerOrEqualValueFilter(10);
		assertTrue(f.match(10));
	}

	/** {@link LowerOrEqualValueFilter} : en-dessous de la borne, accepte. */
	@Test
	public void testLowerOrEqualBelowBoundary()
	{
		info("LowerOrEqualValueFilter: below boundary returns true.");
		ValueFilterI f = new LowerOrEqualValueFilter(10);
		assertTrue(f.match(9));
		assertTrue(f.match(-100));
	}

	/** {@link LowerOrEqualValueFilter} : au-dessus de la borne, rejette. */
	@Test
	public void testLowerOrEqualAboveBoundary()
	{
		info("LowerOrEqualValueFilter: above boundary returns false.");
		ValueFilterI f = new LowerOrEqualValueFilter(10);
		assertFalse(f.match(11));
		assertFalse(f.match(50));
	}

	// -------------------------------------------------------------------------
	// BetweenInclusiveValueFilter
	// -------------------------------------------------------------------------

	/** {@link BetweenInclusiveValueFilter} : à la borne basse, accepte. */
	@Test
	public void testBetweenInclusiveLowerBoundary()
	{
		info("BetweenInclusiveValueFilter: at lower boundary returns true.");
		ValueFilterI f = new BetweenInclusiveValueFilter(10, 20);
		assertTrue(f.match(10));
	}

	/** {@link BetweenInclusiveValueFilter} : à la borne haute, accepte. */
	@Test
	public void testBetweenInclusiveUpperBoundary()
	{
		info("BetweenInclusiveValueFilter: at upper boundary returns true.");
		ValueFilterI f = new BetweenInclusiveValueFilter(10, 20);
		assertTrue(f.match(20));
	}

	/** {@link BetweenInclusiveValueFilter} : valeur strictement à l'intérieur, accepte. */
	@Test
	public void testBetweenInclusiveInRange()
	{
		info("BetweenInclusiveValueFilter: in range returns true.");
		ValueFilterI f = new BetweenInclusiveValueFilter(10, 20);
		assertTrue(f.match(15));
	}

	/** {@link BetweenInclusiveValueFilter} : hors intervalle, rejette. */
	@Test
	public void testBetweenInclusiveOutOfRange()
	{
		info("BetweenInclusiveValueFilter: out of range returns false.");
		ValueFilterI f = new BetweenInclusiveValueFilter(10, 20);
		assertFalse(f.match(9));
		assertFalse(f.match(21));
	}

	/** {@link BetweenInclusiveValueFilter} avec bornes inversées (lower &gt; upper) ne lève pas, mais ne matche aucune valeur. */
	@Test
	public void testBetweenInclusiveInvertedBoundsNoThrow()
	{
		info("BetweenInclusiveValueFilter: inverted bounds [10,5] do NOT throw; match returns false for all values.");
		// Constructor does NOT validate lower <= upper (by design, based on reading the source).
		BetweenInclusiveValueFilter f = new BetweenInclusiveValueFilter(10, 5);
		// No integer x can satisfy x >= 10 AND x <= 5 simultaneously.
		assertFalse(f.match(7));
		assertFalse(f.match(10));
		assertFalse(f.match(5));
	}

	// -------------------------------------------------------------------------
	// StrictlyGreaterValueFilter
	// -------------------------------------------------------------------------

	/** {@link StrictlyGreaterValueFilter} : à la borne, rejette (strict). */
	@Test
	public void testStrictlyGreaterAtBoundary()
	{
		info("StrictlyGreaterValueFilter: at boundary returns false.");
		ValueFilterI f = new StrictlyGreaterValueFilter(10);
		assertFalse(f.match(10));
	}

	/** {@link StrictlyGreaterValueFilter} : au-dessus, accepte. */
	@Test
	public void testStrictlyGreaterAboveBoundary()
	{
		info("StrictlyGreaterValueFilter: above boundary returns true.");
		ValueFilterI f = new StrictlyGreaterValueFilter(10);
		assertTrue(f.match(11));
	}

	/** {@link StrictlyGreaterValueFilter} : en-dessous, rejette. */
	@Test
	public void testStrictlyGreaterBelowBoundary()
	{
		info("StrictlyGreaterValueFilter: below boundary returns false.");
		ValueFilterI f = new StrictlyGreaterValueFilter(10);
		assertFalse(f.match(9));
	}

	// -------------------------------------------------------------------------
	// StrictlyLowerValueFilter
	// -------------------------------------------------------------------------

	/** {@link StrictlyLowerValueFilter} : à la borne, rejette (strict). */
	@Test
	public void testStrictlyLowerAtBoundary()
	{
		info("StrictlyLowerValueFilter: at boundary returns false.");
		ValueFilterI f = new StrictlyLowerValueFilter(10);
		assertFalse(f.match(10));
	}

	/** {@link StrictlyLowerValueFilter} : en-dessous, accepte. */
	@Test
	public void testStrictlyLowerBelowBoundary()
	{
		info("StrictlyLowerValueFilter: below boundary returns true.");
		ValueFilterI f = new StrictlyLowerValueFilter(10);
		assertTrue(f.match(9));
	}

	/** {@link StrictlyLowerValueFilter} : au-dessus, rejette. */
	@Test
	public void testStrictlyLowerAboveBoundary()
	{
		info("StrictlyLowerValueFilter: above boundary returns false.");
		ValueFilterI f = new StrictlyLowerValueFilter(10);
		assertFalse(f.match(11));
	}

	// -------------------------------------------------------------------------
	// Factory methods
	// -------------------------------------------------------------------------

	/** Fabrique {@link ComparableValueFilter#greaterOrEqual} retourne une instance de {@link GreaterOrEqualValueFilter}. */
	@Test
	public void testFactoryGreaterOrEqual()
	{
		info("ComparableValueFilter.greaterOrEqual factory returns GreaterOrEqualValueFilter.");
		ComparableValueFilter f = ComparableValueFilter.greaterOrEqual(5);
		assertNotNull(f);
		assertSame(GreaterOrEqualValueFilter.class, f.getClass());
	}

	/** Fabrique {@link ComparableValueFilter#lowerOrEqual} retourne une instance de {@link LowerOrEqualValueFilter}. */
	@Test
	public void testFactoryLowerOrEqual()
	{
		info("ComparableValueFilter.lowerOrEqual factory returns LowerOrEqualValueFilter.");
		ComparableValueFilter f = ComparableValueFilter.lowerOrEqual(5);
		assertNotNull(f);
		assertSame(LowerOrEqualValueFilter.class, f.getClass());
	}

	/** Fabrique {@link ComparableValueFilter#betweenInclusive} retourne une instance de {@link BetweenInclusiveValueFilter}. */
	@Test
	public void testFactoryBetweenInclusive()
	{
		info("ComparableValueFilter.betweenInclusive factory returns BetweenInclusiveValueFilter.");
		ComparableValueFilter f = ComparableValueFilter.betweenInclusive(1, 10);
		assertNotNull(f);
		assertSame(BetweenInclusiveValueFilter.class, f.getClass());
	}

	/** Fabrique {@link ComparableValueFilter#strictlyGreater} retourne une instance de {@link StrictlyGreaterValueFilter}. */
	@Test
	public void testFactoryStrictlyGreater()
	{
		info("ComparableValueFilter.strictlyGreater factory returns StrictlyGreaterValueFilter.");
		ComparableValueFilter f = ComparableValueFilter.strictlyGreater(5);
		assertNotNull(f);
		assertSame(StrictlyGreaterValueFilter.class, f.getClass());
	}

	/** Fabrique {@link ComparableValueFilter#strictlyLower} retourne une instance de {@link StrictlyLowerValueFilter}. */
	@Test
	public void testFactoryStrictlyLower()
	{
		info("ComparableValueFilter.strictlyLower factory returns StrictlyLowerValueFilter.");
		ComparableValueFilter f = ComparableValueFilter.strictlyLower(5);
		assertNotNull(f);
		assertSame(StrictlyLowerValueFilter.class, f.getClass());
	}

	// -------------------------------------------------------------------------
	// Null and non-Comparable inputs
	// -------------------------------------------------------------------------

	/** {@code match(null)} retourne {@code false} pour toutes les sous-classes (null n'est pas Comparable). */
	@Test
	public void testMatchReturnsFalseOnNull()
	{
		info("match(null) returns false for all subclasses (null is not Comparable).");
		assertFalse(ComparableValueFilter.greaterOrEqual(0).match(null));
		assertFalse(ComparableValueFilter.lowerOrEqual(0).match(null));
		assertFalse(ComparableValueFilter.betweenInclusive(0, 10).match(null));
		assertFalse(ComparableValueFilter.strictlyGreater(0).match(null));
		assertFalse(ComparableValueFilter.strictlyLower(10).match(null));
	}

	/** {@code match} sur un {@code Serializable} non-{@code Comparable} retourne {@code false} (garde {@code instanceof}). */
	@Test
	public void testMatchReturnsFalseOnNonComparable()
	{
		info("match on a non-Comparable Serializable returns false (instanceof guard).");
		// A plain Object[] is Serializable but not Comparable.
		java.io.Serializable nonComparable = new java.io.Serializable() {};
		assertFalse(ComparableValueFilter.greaterOrEqual(0).match(nonComparable));
	}

	// -------------------------------------------------------------------------
	// Null bound guard
	// -------------------------------------------------------------------------

	/** Les constructeurs rejettent une borne {@code null} avec {@link NullPointerException}. */
	@Test(expected = NullPointerException.class)
	public void testNullBoundThrowsNPE()
	{
		info("Constructors reject null bound with NullPointerException.");
		new GreaterOrEqualValueFilter(null);
	}
}
