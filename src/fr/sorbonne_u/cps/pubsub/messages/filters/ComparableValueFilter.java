package fr.sorbonne_u.cps.pubsub.messages.filters;

import java.io.Serializable;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.ValueFilterI;

/**
 * A value filter for values that implement {@link Comparable}.
 *
 * <p>
 * This filter supports comparisons: <=, >= and interval checks
 * (between, inclusive).
 * </p>
 *
 * <p>
 * Created on : 2026-02-15
 * </p>
 */
public class ComparableValueFilter implements ValueFilterI
{
	private static final long serialVersionUID = 1L;

	public enum Operator
	{
		/** value >= bound1 */
		GE,
		/** value <= bound1 */
		LE,
		/** bound1 <= value <= bound2 */
		BETWEEN_INCLUSIVE
	}

	protected final Operator operator;
	protected final Comparable<?> bound1;
	protected final Comparable<?> bound2;

	/**
	 * Create a comparable value filter.
	 *
	 * @param operator comparison operator.
	 * @param bound1   first bound (must not be null).
	 * @param bound2   second bound (only required for BETWEEN_INCLUSIVE).
	 */
	public ComparableValueFilter(Operator operator, Comparable<?> bound1, Comparable<?> bound2)
	{
		if (operator == null) {
			throw new IllegalArgumentException("operator cannot be null.");
		}
		if (bound1 == null) {
			throw new IllegalArgumentException("bound1 cannot be null.");
		}
		if (operator == Operator.BETWEEN_INCLUSIVE && bound2 == null) {
			throw new IllegalArgumentException("bound2 cannot be null for BETWEEN_INCLUSIVE.");
		}
		this.operator = operator;
		this.bound1 = bound1;
		this.bound2 = bound2;
	}

	public static ComparableValueFilter greaterOrEqual(Comparable<?> lowerBoundInclusive)
	{
		return new ComparableValueFilter(Operator.GE, lowerBoundInclusive, null);
	}

	public static ComparableValueFilter lowerOrEqual(Comparable<?> upperBoundInclusive)
	{
		return new ComparableValueFilter(Operator.LE, upperBoundInclusive, null);
	}

	public static ComparableValueFilter betweenInclusive(
		Comparable<?> lowerBoundInclusive,
		Comparable<?> upperBoundInclusive
		)
	{
		return new ComparableValueFilter(
			Operator.BETWEEN_INCLUSIVE,
			lowerBoundInclusive,
			upperBoundInclusive);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public boolean match(Serializable value)
	{
		if (value == null) {
			return false;
		}
		if (!(value instanceof Comparable)) {
			return false;
		}

		Comparable c = (Comparable) value;

		switch (this.operator) {
			case GE:
				return c.compareTo(this.bound1) >= 0;
			case LE:
				return c.compareTo(this.bound1) <= 0;
			case BETWEEN_INCLUSIVE:
				return c.compareTo(this.bound1) >= 0 && c.compareTo(this.bound2) <= 0;
			default:
				return false;
		}
	}
}
