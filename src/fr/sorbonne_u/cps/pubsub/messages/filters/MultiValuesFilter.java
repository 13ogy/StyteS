package fr.sorbonne_u.cps.pubsub.messages.filters;

import java.io.Serializable;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.MultiValuesFilterI;

/**
 * Base implementation of {@link MultiValuesFilterI}.
 *
 * <p>
 * The filter targets a fixed set of property names and delegates
 * the cross-constraint logic to subclasses through {@link #matchValues(Serializable...)}.
 * </p>
 *
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public abstract class MultiValuesFilter implements MultiValuesFilterI
{
	private static final long serialVersionUID = 1L;

	protected final String[] names;

	/**
	 * Crée le filtre.
	 *
	 * @param names noms des propriétés ciblées (non {@code null}, longueur {@code >= 2},
	 * chaque nom non {@code null} ni vide). Une copie défensive est faite.
	 * @throws IllegalArgumentException si la précondition n'est pas respectée.
	 */
	public MultiValuesFilter(String... names)
	{
		if (names == null || names.length < 2) {
			throw new IllegalArgumentException("names must be non-null and of length >= 2.");
		}
		for (String n : names) {
			if (n == null || n.isEmpty()) {
				throw new IllegalArgumentException("property names must be non-null and non-empty.");
			}
		}
		this.names = names.clone();
	}

	/**
	 * @return une copie défensive des noms de propriétés ciblés (jamais {@code null}).
	 */
	@Override
	public String[] getNames()
	{
		return this.names.clone();
	}

	/**
	 * @param values valeurs des propriétés (mêmes positions que {@link #getNames()}).
	 * @return {@code false} si {@code values} est {@code null} ou n'a pas la même
	 * arité que {@link #getNames()} ; sinon délègue à
	 * {@link #matchValues(Serializable...)}.
	 */
	@Override
	public final boolean match(Serializable... values)
	{
		if (values == null || values.length != this.names.length) {
			return false;
		}
		return matchValues(values);
	}

	/**
	 * Apply the cross constraint on the given values.
	 *
	 * @param values values (same length as {@link #getNames()}).
	 * @return true if accepted, false otherwise.
	 */
	protected abstract boolean matchValues(Serializable... values);
}
