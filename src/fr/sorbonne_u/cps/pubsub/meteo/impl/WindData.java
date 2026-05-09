package fr.sorbonne_u.cps.pubsub.meteo.impl;

import fr.sorbonne_u.cps.pubsub.meteo.PositionI;
import fr.sorbonne_u.cps.pubsub.meteo.WindDataI;

import java.util.Objects;

/**
 * Simple wind data implementation for CDC §3.4.
 
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class WindData implements WindDataI
{
	private static final long serialVersionUID = 1L;

	private final PositionI position;
	private final double x;
	private final double y;

	/**
	 * Crée une mesure de vent.
	 *
	 * @param position position d'observation (non {@code null}).
	 * @param x        composante horizontale du vecteur vent.
	 * @param y        composante verticale du vecteur vent.
	 * @throws IllegalArgumentException si {@code position} est {@code null}.
	 */
	public WindData(PositionI position, double x, double y)
	{
		if (position == null) {
			throw new IllegalArgumentException("position cannot be null");
		}
		this.position = position;
		this.x = x;
		this.y = y;
	}

	/** @return la position d'observation. */
	@Override
	public PositionI getPosition()
	{
		return position;
	}

	/** @return la composante horizontale du vecteur vent. */
	@Override
	public double xComponent()
	{
		return x;
	}

	/** @return la composante verticale du vecteur vent. */
	@Override
	public double yComponent()
	{
		return y;
	}

	/**
	 * @return la norme euclidienne du vecteur vent
	 *         ({@code sqrt(x*x + y*y)}, en m/s).
	 */
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
