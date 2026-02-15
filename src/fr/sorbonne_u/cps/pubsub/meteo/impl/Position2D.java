package fr.sorbonne_u.cps.pubsub.meteo.impl;

import fr.sorbonne_u.cps.pubsub.meteo.PositionI;

import java.util.Objects;

/**
 * Simple 2D position implementation for the meteo/eolienne application (CDC §3.4).
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
