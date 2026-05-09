package fr.sorbonne_u.cps.pubsub.messages.filters;

/**
 * Comparable value filter accepting values strictly lower than the bound.
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public final class StrictlyLowerValueFilter extends ComparableValueFilter
{
	private static final long serialVersionUID = 1L;

	private final Comparable<?> bound;

	public StrictlyLowerValueFilter(Comparable<?> bound)
	{
		this.bound = requireBound(bound, "bound");
	}

	@Override
	protected boolean matches(Comparable<?> value)
	{
		return compareUnchecked(value, this.bound) < 0;
	}
}
