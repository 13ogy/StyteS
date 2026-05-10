package fr.sorbonne_u.cps.pubsub.meteo.impl;

import fr.sorbonne_u.cps.pubsub.meteo.PositionI;
import fr.sorbonne_u.cps.pubsub.meteo.RegionI;

import java.util.Objects;

/**
 * Circular region implementation for CDC §3.4.
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class CircularRegion implements RegionI {
	private static final long serialVersionUID = 1L;

	private final Position2D center;
	private final double radius;

	/**
	 * Crée une région circulaire.
	 *
	 * @param center centre de la région (non {@code null}).
	 * @param radius rayon (en unités de distance utilisées par {@link
	 *     Position2D#distanceTo(PositionI)}, doit être {@code >= 0}).
	 * @throws IllegalArgumentException si {@code center} est {@code null} ou si {@code radius < 0}.
	 */
	public CircularRegion(Position2D center, double radius) {
		if (center == null) {
			throw new IllegalArgumentException("center cannot be null");
		}
		if (radius < 0) {
			throw new IllegalArgumentException("radius must be >= 0");
		}
		this.center = center;
		this.radius = radius;
	}

	/**
	 * @return le centre du disque (jamais {@code null}).
	 */
	public Position2D getCenter() {
		return center;
	}

	/**
	 * @return le rayon du disque ({@code >= 0}).
	 */
	public double getRadius() {
		return radius;
	}

	/**
	 * @param p position candidate.
	 * @return {@code true} ssi {@code p} est à distance {@code <= radius} du centre.
	 */
	@Override
	public boolean in(PositionI p) {
		// Encapsulation: delegate the geometry to Position2D rather than
		// reaching into its private state.
		double d = this.center.distanceTo(p);
		return d <= this.radius;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof CircularRegion)) return false;
		CircularRegion that = (CircularRegion) o;
		return Double.compare(radius, that.radius) == 0 && center.equals(that.center);
	}

	@Override
	public int hashCode() {
		return Objects.hash(center, radius);
	}

	@Override
	public String toString() {
		return "CircularRegion{" + "center=" + center + ", radius=" + radius + '}';
	}
}
