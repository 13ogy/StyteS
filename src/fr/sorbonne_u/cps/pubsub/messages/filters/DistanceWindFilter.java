package fr.sorbonne_u.cps.pubsub.messages.filters;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.meteo.PositionI;
import fr.sorbonne_u.cps.pubsub.meteo.WindDataI;
import fr.sorbonne_u.cps.pubsub.meteo.impl.Position2D;

import java.io.Serializable;

/**
 * Value filter for wind payloads that enforces a maximum distance to a reference position.
 *
 * <p>
 * This filter is meant to be used as a default filter on wind subscriptions so that
 * distance-based rejection is performed by the pub/sub system filtering stage rather
 * than inside the subscriber business logic.
 * </p>
 *
 * <p>
 * The filter accepts a value if:
 * </p>
 * <ul>
 *   <li>the value is a {@link WindDataI};</li>
 *   <li>its {@link WindDataI#getPosition()} is within {@code maxDistance} of {@code referencePosition}.</li>
 * </ul>
 *
 * <p>
 * Distance computation is delegated to {@link Position2D#distanceTo(PositionI)}
 * (cf. soutenance review 1.8): no caller may reach into the private coordinates
 * of a {@link Position2D}.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class DistanceWindFilter implements MessageFilterI.ValueFilterI
{
	private static final long serialVersionUID = 1L;

	protected final PositionI referencePosition;
	protected final double maxDistance;

	/**
	 * Crée le filtre de distance.
	 *
	 * @param referencePosition position de référence (non {@code null}).
	 * @param maxDistance       distance maximale acceptée, en unités de
	 *                          {@link fr.sorbonne_u.cps.pubsub.meteo.impl.Position2D#distanceTo}
	 *                          (doit être {@code >= 0}).
	 * @throws IllegalArgumentException si {@code referencePosition} est {@code null}
	 *                                  ou si {@code maxDistance < 0}.
	 */
	public DistanceWindFilter(PositionI referencePosition, double maxDistance)
	{
		if (referencePosition == null) {
			throw new IllegalArgumentException("referencePosition cannot be null.");
		}
		if (maxDistance < 0.0) {
			throw new IllegalArgumentException("maxDistance must be >= 0.");
		}
		this.referencePosition = referencePosition;
		this.maxDistance = maxDistance;
	}

	/**
	 * @param value valeur candidate.
	 * @return {@code true} ssi {@code value} est un {@link WindDataI} dont la
	 *         position est à distance {@code <= maxDistance} de la position
	 *         de référence ; {@code false} sinon (y compris pour des
	 *         implémentations de {@link PositionI} non gérables géométriquement).
	 */
	@Override
	public boolean match(Serializable value)
	{
		if (!(value instanceof WindDataI)) {
			return false;
		}
		WindDataI wind = (WindDataI) value;
		PositionI windPos = wind.getPosition();

		if (this.referencePosition instanceof Position2D) {
			return ((Position2D) this.referencePosition).distanceTo(windPos) <= this.maxDistance;
		}
		if (windPos instanceof Position2D) {
			return ((Position2D) windPos).distanceTo(this.referencePosition) <= this.maxDistance;
		}
		// Unknown position implementation: fail safely.
		return false;
	}
}
