package fr.sorbonne_u.cps.pubsub.messages.filters;

import java.time.Instant;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.TimeFilterI;

/**
 * Time filter accepting timestamps lower than or equal to a given instant.
 *
 * <p>Created on : 2026-02-15</p>
 */
public class BeforeOrAtTimeFilter implements TimeFilterI
{
	private static final long serialVersionUID = 1L;

	protected final Instant upperBoundInclusive;

	public BeforeOrAtTimeFilter(Instant upperBoundInclusive)
	{
		if (upperBoundInclusive == null) {
			throw new IllegalArgumentException("upperBoundInclusive cannot be null.");
		}
		this.upperBoundInclusive = upperBoundInclusive;
	}

	@Override
	public boolean match(Instant timestamp)
	{
		if (timestamp == null) {
			return false;
		}
		return !timestamp.isAfter(this.upperBoundInclusive);
	}
}
