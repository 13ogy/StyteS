package fr.sorbonne_u.cps.pubsub.meteo.impl;

import fr.sorbonne_u.cps.pubsub.meteo.PositionI;
import fr.sorbonne_u.cps.pubsub.meteo.RegionI;

import java.util.Objects;

/**
 * Simple circular region implementation for CDC §3.4.
 */
public class CircularRegion implements RegionI
{
	private static final long serialVersionUID = 1L;

	private final Position2D center;
	private final double radius;

	public CircularRegion(Position2D center, double radius)
	{
		if (center == null) {
			throw new IllegalArgumentException("center cannot be null");
		}
		if (radius < 0) {
			throw new IllegalArgumentException("radius must be >= 0");
		}
		this.center = center;
		this.radius = radius;
	}

	public Position2D getCenter()
	{
		return center;
	}

	public double getRadius()
	{
		return radius;
	}

	@Override
	public boolean in(PositionI p)
	{
		if (!(p instanceof Position2D)) {
			return false;
		}
		Position2D pos = (Position2D) p;
		double dx = pos.getX() - center.getX();
		double dy = pos.getY() - center.getY();
		return (dx * dx + dy * dy) <= radius * radius;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (!(o instanceof CircularRegion)) return false;
		CircularRegion that = (CircularRegion) o;
		return Double.compare(radius, that.radius) == 0 && center.equals(that.center);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(center, radius);
	}

	@Override
	public String toString()
	{
		return "CircularRegion{" + "center=" + center + ", radius=" + radius + '}';
	}
}
