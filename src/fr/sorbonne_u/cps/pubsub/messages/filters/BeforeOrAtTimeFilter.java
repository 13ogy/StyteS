package fr.sorbonne_u.cps.pubsub.messages.filters;

import java.time.Instant;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.TimeFilterI;

/**
 * Implémentation de {@link TimeFilterI} acceptant les estampilles {@code t}
 * telles que {@code t <= upperBoundInclusive}.
 *
 * <p>
 * Permet à un abonné de ne recevoir que les messages publiés jusqu'à un
 * instant donné (CDC §3.5 — filtres temporels).
 * </p>
 *
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class BeforeOrAtTimeFilter implements TimeFilterI
{
	private static final long serialVersionUID = 1L;

	/** Borne supérieure inclusive. */
	protected final Instant upperBoundInclusive;

	/**
	 * Crée le filtre temporel.
	 *
	 * @param upperBoundInclusive borne supérieure inclusive (non {@code null}).
	 * @throws IllegalArgumentException si {@code upperBoundInclusive} est {@code null}.
	 */
	public BeforeOrAtTimeFilter(Instant upperBoundInclusive)
	{
		if (upperBoundInclusive == null) {
			throw new IllegalArgumentException("upperBoundInclusive cannot be null.");
		}
		this.upperBoundInclusive = upperBoundInclusive;
	}

	/**
	 * @param timestamp horodatage candidat.
	 * @return {@code true} ssi {@code timestamp != null} et
	 *         {@code timestamp <= upperBoundInclusive}.
	 */
	@Override
	public boolean match(Instant timestamp)
	{
		if (timestamp == null) {
			return false;
		}
		return !timestamp.isAfter(this.upperBoundInclusive);
	}
}
