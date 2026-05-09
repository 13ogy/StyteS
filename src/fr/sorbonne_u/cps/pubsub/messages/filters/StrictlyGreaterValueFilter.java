package fr.sorbonne_u.cps.pubsub.messages.filters;

/**
 * Comparable value filter accepting values strictly greater than the bound.
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public final class StrictlyGreaterValueFilter extends ComparableValueFilter
{
	private static final long serialVersionUID = 1L;

	private final Comparable<?> bound;

	/**
	 * @param bound borne inférieure exclusive (non {@code null}).
	 */
	public StrictlyGreaterValueFilter(Comparable<?> bound)
	{
		this.bound = requireBound(bound, "bound");
	}

	/**
	 * @param value valeur candidate.
	 * @return {@code true} ssi {@code value > bound}.
	 */
	@Override
	protected boolean matches(Comparable<?> value)
	{
		return compareUnchecked(value, this.bound) > 0;
	}
}
