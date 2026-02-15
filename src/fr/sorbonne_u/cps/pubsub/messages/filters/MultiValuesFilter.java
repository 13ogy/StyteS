package fr.sorbonne_u.cps.pubsub.messages.filters;

import java.io.Serializable;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.MultiValuesFilterI;

/**
 * Base implementation of {@link MultiValuesFilterI}.
 *
 * <p>
 * The filter targets a fixed set of property names (order matters) and delegates
 * the cross-constraint logic to subclasses through {@link #matchValues(Serializable...)}.
 * </p>
 *
 * <p>Created on : 2026-02-15</p>
 */
public abstract class MultiValuesFilter implements MultiValuesFilterI
{
	private static final long serialVersionUID = 1L;

	protected final String[] names;

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

	@Override
	public String[] getNames()
	{
		return this.names.clone();
	}

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
