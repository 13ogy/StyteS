package fr.sorbonne_u.cps.pubsub.messages.filters;

/**
 * Comparable value filter accepting values {@code <=} the configured bound.
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public final class LowerOrEqualValueFilter extends ComparableValueFilter {
	private static final long serialVersionUID = 1L;

	private final Comparable<?> bound;

	/**
	 * @param bound borne supérieure inclusive (non {@code null}).
	 */
	public LowerOrEqualValueFilter(Comparable<?> bound) {
		this.bound = requireBound(bound, "bound");
	}

	/**
	 * @param value valeur candidate.
	 * @return {@code true} ssi {@code value <= bound}.
	 */
	@Override
	protected boolean matches(Comparable<?> value) {
		return compareUnchecked(value, this.bound) <= 0;
	}
}
