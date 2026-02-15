package fr.sorbonne_u.cps.pubsub.messages.filters;

import java.time.Instant;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.TimeFilterI;

/**
 * Time filter accepting timestamps greater than or equal to a given instant.
 *
 * <p>Created on : 2026-02-15</p>
 */
public class AfterOrAtTimeFilter implements TimeFilterI
{
	private static final long serialVersionUID = 1L;

	protected final Instant lowerBoundInclusive;

	public AfterOrAtTimeFilter(Instant lowerBoundInclusive)
	{
		if (lowerBoundInclusive == null) {
			throw new IllegalArgumentException("lowerBoundInclusive cannot be null.");
		}
		this.lowerBoundInclusive = lowerBoundInclusive;
	}

	@Override
	public boolean match(Instant timestamp)
	{
		if (timestamp == null) {
			return false;
		}
		return !timestamp.isBefore(this.lowerBoundInclusive);
	}
}
