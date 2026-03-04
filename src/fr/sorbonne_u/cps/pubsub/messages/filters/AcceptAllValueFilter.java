package fr.sorbonne_u.cps.pubsub.messages.filters;

import java.io.Serializable;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.ValueFilterI;

/**
 * Value filter accepting any value.
 *
 *
 * @author Bogdan Styn
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
