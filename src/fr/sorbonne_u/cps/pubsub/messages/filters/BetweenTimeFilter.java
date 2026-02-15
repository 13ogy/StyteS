package fr.sorbonne_u.cps.pubsub.messages.filters;

import java.time.Instant;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.TimeFilterI;

/**
 * Time filter accepting timestamps within an interval [{@code startInclusive},
 * {@code endInclusive}].
 *
 * <p>Created on : 2026-02-15</p>
 */
public class BetweenTimeFilter implements TimeFilterI
{
	private static final long serialVersionUID = 1L;

	protected final Instant startInclusive;
	protected final Instant endInclusive;

	public BetweenTimeFilter(Instant startInclusive, Instant endInclusive)
	{
		if (startInclusive == null || endInclusive == null) {
			throw new IllegalArgumentException("startInclusive and endInclusive cannot be null.");
		}
		if (endInclusive.isBefore(startInclusive)) {
			throw new IllegalArgumentException("endInclusive must be >= startInclusive.");
		}
		this.startInclusive = startInclusive;
		this.endInclusive = endInclusive;
	}

	@Override
	public boolean match(Instant timestamp)
	{
		if (timestamp == null) {
			return false;
		}
		return !timestamp.isBefore(this.startInclusive) && !timestamp.isAfter(this.endInclusive);
	}
}
