package fr.sorbonne_u.cps.pubsub.meteo.impl;

import fr.sorbonne_u.cps.pubsub.meteo.PositionI;
import fr.sorbonne_u.cps.pubsub.meteo.WindDataI;

import java.util.Objects;

/**
 * Simple wind data implementation for CDC §3.4.
 */
public class WindData implements WindDataI
{
	private static final long serialVersionUID = 1L;

	private final PositionI position;
	private final double x;
	private final double y;

	public WindData(PositionI position, double x, double y)
	{
		if (position == null) {
			throw new IllegalArgumentException("position cannot be null");
		}
		this.position = position;
		this.x = x;
		this.y = y;
	}

	@Override
	public PositionI getPosition()
	{
		return position;
	}

	@Override
	public double xComponent()
	{
		return x;
	}

	@Override
	public double yComponent()
	{
		return y;
	}

	@Override
	public double force()
	{
		return Math.sqrt(x * x + y * y);
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (!(o instanceof WindData)) return false;
		WindData windData = (WindData) o;
		return Double.compare(x, windData.x) == 0 &&
			Double.compare(y, windData.y) == 0 &&
			position.equals(windData.position);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(position, x, y);
	}

	@Override
	public String toString()
	{
		return "WindData{" +
			"position=" + position +
			", x=" + x +
			", y=" + y +
			", force=" + force() +
			'}';
	}
}
