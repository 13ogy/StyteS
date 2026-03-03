package fr.sorbonne_u.cps.pubsub.messages.filters;

import java.time.Instant;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.TimeFilterI;

/**
 * Time filter that accepts any timestamp.
 *
 *
 * @author Bogdan Styn
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
