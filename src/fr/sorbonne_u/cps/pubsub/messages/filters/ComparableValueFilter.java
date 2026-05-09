package fr.sorbonne_u.cps.pubsub.messages.filters;

import java.io.Serializable;
import java.util.Objects;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.ValueFilterI;

/**
 * Abstract base class for value filters comparing the value against bounds.
 *
 * <p>
 * Per the soutenance review (1.6 + 1.7) we replaced the {@code Operator} enum
 * + {@code switch} construction with a class hierarchy: each comparison kind
 * is its own concrete subclass. Adding a new comparison only requires adding
 * a new subclass; no existing code (in particular, no {@code switch} clause)
 * needs to be updated. This is the Strategy pattern applied to comparable
 * value matching.
 * </p>
 *
 * <p>
 * The static factory methods on this class produce instances of the
 * appropriate concrete subclass and are kept for backward source compatibility
 * with all existing callers (demos, tests, application code).
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public abstract class ComparableValueFilter implements ValueFilterI
{
	private static final long serialVersionUID = 1L;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public final boolean match(Serializable value)
	{
		if (!(value instanceof Comparable)) {
			return false;
		}
		// Subclasses define how the value compares against their bound(s).
		return matches((Comparable) value);
	}

	/**
	 * Subclass-defined comparison.
	 *
	 * @param value the value to test (already known to be {@link Comparable}).
	 * @return {@code true} if {@code value} satisfies this filter's relation.
	 */
	protected abstract boolean matches(Comparable<?> value);

	// -------------------------------------------------------------------------
	// Factory methods (preserved for source compatibility)
	// -------------------------------------------------------------------------

	/**
	 * @param lowerBoundInclusive borne inférieure inclusive (non {@code null}).
	 * @return un filtre acceptant {@code value >= lowerBoundInclusive}.
	 */
	public static ComparableValueFilter greaterOrEqual(Comparable<?> lowerBoundInclusive)
	{
		return new GreaterOrEqualValueFilter(lowerBoundInclusive);
	}

	/**
	 * @param upperBoundInclusive borne supérieure inclusive (non {@code null}).
	 * @return un filtre acceptant {@code value <= upperBoundInclusive}.
	 */
	public static ComparableValueFilter lowerOrEqual(Comparable<?> upperBoundInclusive)
	{
		return new LowerOrEqualValueFilter(upperBoundInclusive);
	}

	/**
	 * @param lowerBoundInclusive borne inférieure inclusive (non {@code null}).
	 * @param upperBoundInclusive borne supérieure inclusive (non {@code null}).
	 * @return un filtre acceptant {@code lowerBoundInclusive <= value <= upperBoundInclusive}.
	 */
	public static ComparableValueFilter betweenInclusive(
		Comparable<?> lowerBoundInclusive,
		Comparable<?> upperBoundInclusive
		)
	{
		return new BetweenInclusiveValueFilter(lowerBoundInclusive, upperBoundInclusive);
	}

	/**
	 * @param lowerBoundExclusive borne inférieure exclusive (non {@code null}).
	 * @return un filtre acceptant {@code value > lowerBoundExclusive}.
	 */
	public static ComparableValueFilter strictlyGreater(Comparable<?> lowerBoundExclusive)
	{
		return new StrictlyGreaterValueFilter(lowerBoundExclusive);
	}

	/**
	 * @param upperBoundExclusive borne supérieure exclusive (non {@code null}).
	 * @return un filtre acceptant {@code value < upperBoundExclusive}.
	 */
	public static ComparableValueFilter strictlyLower(Comparable<?> upperBoundExclusive)
	{
		return new StrictlyLowerValueFilter(upperBoundExclusive);
	}

	// -------------------------------------------------------------------------
	// Shared helper
	// -------------------------------------------------------------------------

	@SuppressWarnings({ "rawtypes", "unchecked" })
	static int compareUnchecked(Comparable<?> a, Comparable<?> b)
	{
		return ((Comparable) a).compareTo(b);
	}

	static Comparable<?> requireBound(Comparable<?> bound, String name)
	{
		return Objects.requireNonNull(bound, name + " cannot be null.");
	}
}
