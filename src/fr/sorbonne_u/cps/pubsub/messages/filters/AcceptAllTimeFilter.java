package fr.sorbonne_u.cps.pubsub.messages.filters;
import java.time.Instant;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.TimeFilterI;

/**
 * Time filter accepting any timestamp (joker filter).
 *
 * <p>Created on : 2026-02-15</p>
 */
public class AcceptAllTimeFilter implements TimeFilterI
{
	private static final long serialVersionUID = 1L;

	@Override
	public boolean match(Instant timestamp)
	{
		return true;
	}
}
