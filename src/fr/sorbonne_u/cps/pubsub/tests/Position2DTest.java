package fr.sorbonne_u.cps.pubsub.tests;

import fr.sorbonne_u.cps.pubsub.meteo.RegionI;
import fr.sorbonne_u.cps.pubsub.meteo.impl.CircularRegion;
import fr.sorbonne_u.cps.pubsub.meteo.impl.Position2D;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link Position2D} covering the {@code distanceTo}
 * and {@code isInside} methods (CDC §3.4).
 *
 * <p>
 * What is being tested:
 * </p>
 * <ul>
 * <li>{@code distanceTo} computes the correct Euclidean distance.</li>
 * <li>{@code distanceTo(same)} returns 0.</li>
 * <li>{@code distanceTo(null)} returns {@link Double#POSITIVE_INFINITY} (defensive).</li>
 * <li>{@code distanceTo} with an incompatible {@code PositionI} subtype returns infinity.</li>
 * <li>{@code isInside} delegates to {@code RegionI.in(this)} — verified with a lambda stub.</li>
 * <li>{@code isInside(null)} returns false.</li>
 * <li>{@code isInside} with a {@link CircularRegion}: true at center, true at edge, false beyond.</li>
 * </ul>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class Position2DTest
{
	private static void info(String s)
	{
		System.out.println("[Position2DTest] " + s);
	}

	private static final double EPS = 1e-9;

	// -------------------------------------------------------------------------
	// distanceTo
	// -------------------------------------------------------------------------

	/** {@code distanceTo} sur la même instance retourne 0. */
	@Test
	public void testDistanceToSamePoint()
	{
		info("distanceTo the same point returns 0.");
		Position2D p = new Position2D(3.0, 4.0);
		assertEquals(0.0, p.distanceTo(p), EPS);
	}

	/** {@code distanceTo} entre deux Position2D de coordonnées égales retourne 0. */
	@Test
	public void testDistanceToIdenticalCoordinates()
	{
		info("distanceTo an equal but distinct Position2D returns 0.");
		Position2D a = new Position2D(3.0, 4.0);
		Position2D b = new Position2D(3.0, 4.0);
		assertEquals(0.0, a.distanceTo(b), EPS);
	}

	/** {@code distanceTo} respecte la distance euclidienne (cas 3-4-5 → 5). */
	@Test
	public void testDistanceToKnownEuclidean()
	{
		info("distanceTo computes correct Euclidean distance: 3-4-5 triangle → 5.");
		Position2D origin = new Position2D(0.0, 0.0);
		Position2D p = new Position2D(3.0, 4.0);
		assertEquals(5.0, origin.distanceTo(p), EPS);
		assertEquals(5.0, p.distanceTo(origin), EPS);
	}

	/** {@code distanceTo} cas général : sqrt((Δx)² + (Δy)²). */
	@Test
	public void testDistanceToGeneral()
	{
		info("distanceTo general case: sqrt((2-0)^2 + (2-0)^2) = sqrt(8).");
		Position2D a = new Position2D(0.0, 0.0);
		Position2D b = new Position2D(2.0, 2.0);
		assertEquals(Math.sqrt(8.0), a.distanceTo(b), EPS);
	}

	/** {@code distanceTo(null)} retourne {@link Double#POSITIVE_INFINITY} (défensif). */
	@Test
	public void testDistanceToNullReturnsInfinity()
	{
		info("distanceTo(null) returns POSITIVE_INFINITY (null is not a Position2D).");
		Position2D p = new Position2D(1.0, 1.0);
		assertEquals(Double.POSITIVE_INFINITY, p.distanceTo(null), EPS);
	}

	/** {@code distanceTo} sur un {@code PositionI} non Position2D retourne {@link Double#POSITIVE_INFINITY}. */
	@Test
	public void testDistanceToIncompatibleTypeReturnsInfinity()
	{
		info("distanceTo a non-Position2D PositionI returns POSITIVE_INFINITY.");
		Position2D p = new Position2D(0.0, 0.0);
		// Anonymous PositionI that is NOT a Position2D.
		fr.sorbonne_u.cps.pubsub.meteo.PositionI other = q -> false;
		assertEquals(Double.POSITIVE_INFINITY, p.distanceTo(other), EPS);
	}

	// -------------------------------------------------------------------------
	// isInside
	// -------------------------------------------------------------------------

	/** {@code isInside(null)} retourne {@code false}. */
	@Test
	public void testIsInsideNullRegionReturnsFalse()
	{
		info("isInside(null) returns false.");
		Position2D p = new Position2D(0.0, 0.0);
		assertFalse(p.isInside(null));
	}

	/** {@code isInside} délègue à {@code RegionI.in(this)} (vérifié avec lambdas stubs). */
	@Test
	public void testIsInsideDelegatesToRegion()
	{
		info("isInside delegates to RegionI.in(this): lambda stub returning true.");
		Position2D p = new Position2D(1.0, 2.0);
		RegionI alwaysTrue = q -> true;
		assertTrue(p.isInside(alwaysTrue));

		RegionI alwaysFalse = q -> false;
		assertFalse(p.isInside(alwaysFalse));
	}

	/** {@code isInside} sur {@link CircularRegion} : le centre est dedans. */
	@Test
	public void testIsInsideCircularRegionAtCenter()
	{
		info("isInside CircularRegion: point at center is inside.");
		Position2D center = new Position2D(0.0, 0.0);
		CircularRegion region = new CircularRegion(center, 5.0);
		assertTrue(center.isInside(region));
	}

	/** {@code isInside} sur {@link CircularRegion} : la borne (rayon exact) est dedans (inclusif). */
	@Test
	public void testIsInsideCircularRegionAtEdge()
	{
		info("isInside CircularRegion: point exactly at radius is inside (inclusive boundary).");
		Position2D center = new Position2D(0.0, 0.0);
		CircularRegion region = new CircularRegion(center, 5.0);
		Position2D edge = new Position2D(5.0, 0.0);
		assertTrue(edge.isInside(region));
	}

	/** {@code isInside} sur {@link CircularRegion} : au-delà du rayon, dehors. */
	@Test
	public void testIsInsideCircularRegionBeyondEdge()
	{
		info("isInside CircularRegion: point beyond radius is outside.");
		Position2D center = new Position2D(0.0, 0.0);
		CircularRegion region = new CircularRegion(center, 5.0);
		Position2D outside = new Position2D(6.0, 0.0);
		assertFalse(outside.isInside(region));
	}

	/** {@code isInside} sur {@link CircularRegion} non centrée à l'origine. */
	@Test
	public void testIsInsideCircularRegionOffCenter()
	{
		info("isInside CircularRegion: point inside a non-origin region.");
		Position2D center = new Position2D(10.0, 10.0);
		CircularRegion region = new CircularRegion(center, 3.0);
		Position2D inside = new Position2D(11.0, 10.0); // distance = 1 < 3
		Position2D outside = new Position2D(14.0, 10.0); // distance = 4 > 3
		assertTrue(inside.isInside(region));
		assertFalse(outside.isInside(region));
	}
}
