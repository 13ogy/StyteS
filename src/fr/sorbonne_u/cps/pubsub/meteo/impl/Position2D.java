package fr.sorbonne_u.cps.pubsub.meteo.impl;

import fr.sorbonne_u.cps.pubsub.meteo.PositionI;
import fr.sorbonne_u.cps.pubsub.meteo.RegionI;

import java.util.Objects;

/**
 * A 2D position implementation (CDC §3.4).
 *
 * <p>
 * Per the soutenance review (1.8 + 1.9), distance and inclusion logic
 * involving 2D positions are now methods of this class rather than callers
 * pulling out raw {@code x}/{@code y} coordinates. This restores the
 * encapsulation of the position state.
 * </p>
 *
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class Position2D implements PositionI
{
	private static final long serialVersionUID = 1L;

	private final double x;
	private final double y;

	public Position2D(double x, double y)
	{
		this.x = x;
		this.y = y;
	}

	public double getX()
	{
		return x;
	}

	public double getY()
	{
		return y;
	}

	/**
	 * Euclidean distance between {@code this} and {@code other}.
	 *
	 * <p>
	 * Returns {@link Double#POSITIVE_INFINITY} for any incompatible
	 * {@link PositionI} subtype so callers remain comparison-safe.
	 * </p>
	 *
	 * @param other another position.
	 * @return the Euclidean distance, or {@link Double#POSITIVE_INFINITY} if
	 *         {@code other} is not a {@link Position2D}.
	 */
	public double distanceTo(PositionI other)
	{
		if (!(other instanceof Position2D)) {
			return Double.POSITIVE_INFINITY;
		}
		Position2D o = (Position2D) other;
		double dx = this.x - o.x;
		double dy = this.y - o.y;
		return Math.sqrt(dx * dx + dy * dy);
	}

	/**
	 * Test whether {@code this} is inside the given region.
	 *
	 * @param region a region.
	 * @return {@code true} if the region accepts this position.
	 */
	public boolean isInside(RegionI region)
	{
		if (region == null) {
			return false;
		}
		return region.in(this);
	}

	@Override
	public boolean equals(PositionI p)
	{
		if (!(p instanceof Position2D)) {
			return false;
		}
		Position2D other = (Position2D) p;
		return Double.compare(this.x, other.x) == 0 && Double.compare(this.y, other.y) == 0;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (!(o instanceof Position2D)) return false;
		Position2D position2D = (Position2D) o;
		return Double.compare(x, position2D.x) == 0 && Double.compare(y, position2D.y) == 0;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(x, y);
	}

	@Override
	public String toString()
	{
		return "Position2D{" + "x=" + x + ", y=" + y + '}';
	}
}
