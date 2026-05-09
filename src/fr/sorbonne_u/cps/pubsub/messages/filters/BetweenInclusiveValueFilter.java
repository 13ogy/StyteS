package fr.sorbonne_u.cps.pubsub.messages.filters;

/**
 * Comparable value filter accepting values within {@code [lower, upper]} (inclusive).
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public final class BetweenInclusiveValueFilter extends ComparableValueFilter
{
	private static final long serialVersionUID = 1L;

	private final Comparable<?> lower;
	private final Comparable<?> upper;

	public BetweenInclusiveValueFilter(Comparable<?> lower, Comparable<?> upper)
	{
		this.lower = requireBound(lower, "lower");
		this.upper = requireBound(upper, "upper");
	}

	@Override
	protected boolean matches(Comparable<?> value)
	{
		return compareUnchecked(value, this.lower) >= 0
			&& compareUnchecked(value, this.upper) <= 0;
	}
}
