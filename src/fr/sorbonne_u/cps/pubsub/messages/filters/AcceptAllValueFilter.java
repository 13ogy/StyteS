package fr.sorbonne_u.cps.pubsub.messages.filters;

import java.io.Serializable;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.ValueFilterI;

/**
 * Implémentation de {@link ValueFilterI} (joker de valeur) acceptant toute
 * valeur, y compris {@code null}.
 *
 * <p>
 * Pratique pour composer un {@link fr.sorbonne_u.cps.pubsub.messages.filters.PropertyFilter}
 * qui ne teste que la présence d'une propriété nommée, sans contrainte sur sa
 * valeur (CDC §3.5 — filtrage par propriété).
 * </p>
 *
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class AcceptAllValueFilter implements ValueFilterI
{
	private static final long serialVersionUID = 1L;

	/**
	 * @param value valeur candidate (peut être {@code null}).
	 * @return toujours {@code true}.
	 */
	@Override
	public boolean match(Serializable value)
	{
		return true;
	}
}
