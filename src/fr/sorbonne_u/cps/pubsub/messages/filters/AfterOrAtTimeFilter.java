package fr.sorbonne_u.cps.pubsub.messages.filters;

import java.time.Instant;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.TimeFilterI;

/**
 * Implémentation de {@link TimeFilterI} acceptant les estampilles {@code t}
 * telles que {@code t >= lowerBoundInclusive}.
 *
 * <p>
 * Permet à un abonné de ne recevoir que les messages publiés à partir d'un
 * instant donné (CDC §3.5 — filtres temporels).
 * </p>
 *
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class AfterOrAtTimeFilter implements TimeFilterI
{
	private static final long serialVersionUID = 1L;

	/** Borne inférieure inclusive. */
	protected final Instant lowerBoundInclusive;

	/**
	 * Crée le filtre temporel.
	 *
	 * @param lowerBoundInclusive borne inférieure inclusive (non {@code null}).
	 * @throws IllegalArgumentException si {@code lowerBoundInclusive} est {@code null}.
	 */
	public AfterOrAtTimeFilter(Instant lowerBoundInclusive)
	{
		if (lowerBoundInclusive == null) {
			throw new IllegalArgumentException("lowerBoundInclusive cannot be null.");
		}
		this.lowerBoundInclusive = lowerBoundInclusive;
	}

	/**
	 * @param timestamp horodatage candidat.
	 * @return {@code true} ssi {@code timestamp != null} et
	 *         {@code timestamp >= lowerBoundInclusive}.
	 */
	@Override
	public boolean match(Instant timestamp)
	{
		if (timestamp == null) {
			return false;
		}
		return !timestamp.isBefore(this.lowerBoundInclusive);
	}
}
