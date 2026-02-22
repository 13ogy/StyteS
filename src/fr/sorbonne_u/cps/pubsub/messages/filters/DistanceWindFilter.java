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
 * The current implementation computes distances when positions are {@link Position2D}.
 * Other {@link PositionI} implementations are rejected (distance treated as +infinity).
 * </p>
 */
public class DistanceWindFilter implements MessageFilterI.ValueFilterI
{
	private static final long serialVersionUID = 1L;

	protected final PositionI referencePosition;
	protected final double maxDistance;

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

	@Override
	public boolean match(Serializable value)
	{
		if (!(value instanceof WindDataI)) {
			return false;
		}
		WindDataI wind = (WindDataI) value;

		double d = distance(this.referencePosition, wind.getPosition());
		return d <= this.maxDistance;
	}

	protected static double distance(PositionI a, PositionI b)
	{
		if (a instanceof Position2D && b instanceof Position2D) {
			Position2D pa = (Position2D) a;
			Position2D pb = (Position2D) b;
			double dx = pa.getX() - pb.getX();
			double dy = pa.getY() - pb.getY();
			return Math.sqrt(dx * dx + dy * dy);
		}
		return Double.POSITIVE_INFINITY;
	}
}
