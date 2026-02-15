package fr.sorbonne_u.cps.pubsub.messages.filters;

import java.io.Serializable;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.ValueFilterI;

/**
 * Filtre de valeur acceptant uniquement les valeurs égales à une valeur attendue
 * (comparaison via {@link Object#equals(Object)}).
 *
 * <p>
 * Created on : 2026-02-08
 * </p>
 */
public class EqualsValueFilter implements ValueFilterI
{
	private static final long serialVersionUID = 1L;

	/** Valeur attendue. */
	protected final Serializable expected;

	/**
	 * Crée le filtre.
	 *
	 * @param expected valeur attendue (peut être null).
	 */
	public EqualsValueFilter(Serializable expected)
	{
		this.expected = expected;
	}

	@Override
	public boolean match(Serializable value)
	{
		if (this.expected == null) {
			return value == null;
		}
		return this.expected.equals(value);
	}
}
