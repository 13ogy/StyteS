package fr.sorbonne_u.cps.pubsub.messages.filters;

import java.io.Serializable;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.ValueFilterI;

/**
 * Filtre de valeur acceptant toute valeur.
 *
 * <p>
 * Created on : 2026-02-08
 * </p>
 */
public class AcceptAllValueFilter implements ValueFilterI
{
	private static final long serialVersionUID = 1L;

	@Override
	public boolean match(Serializable value)
	{
		return true;
	}
}
