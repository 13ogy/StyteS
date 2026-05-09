package fr.sorbonne_u.cps.pubsub.messages.filters;

import java.time.Instant;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.TimeFilterI;

/**
 * Implémentation de {@link TimeFilterI} acceptant les estampilles {@code t}
 * telles que {@code startInclusive <= t <= endInclusive}.
 *
 * <p>
 * Conjugue les sémantiques de {@link AfterOrAtTimeFilter} et
 * {@link BeforeOrAtTimeFilter} pour borner une fenêtre temporelle de
 * réception (CDC §3.5 — filtres temporels).
 * </p>
 *
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class BetweenTimeFilter implements TimeFilterI
{
	private static final long serialVersionUID = 1L;

	/** Borne inférieure inclusive. */
	protected final Instant startInclusive;
	/** Borne supérieure inclusive. */
	protected final Instant endInclusive;

	/**
	 * Crée le filtre temporel.
	 *
	 * @param startInclusive borne inférieure inclusive (non {@code null}).
	 * @param endInclusive   borne supérieure inclusive (non {@code null},
	 *                       et {@code >= startInclusive}).
	 * @throws IllegalArgumentException si une borne est {@code null} ou si
	 *                                  {@code endInclusive < startInclusive}.
	 */
	public BetweenTimeFilter(Instant startInclusive, Instant endInclusive)
	{
		if (startInclusive == null || endInclusive == null) {
			throw new IllegalArgumentException("startInclusive and endInclusive cannot be null.");
		}
		if (endInclusive.isBefore(startInclusive)) {
			throw new IllegalArgumentException("endInclusive must be >= startInclusive.");
		}
		this.startInclusive = startInclusive;
		this.endInclusive = endInclusive;
	}

	/**
	 * @param timestamp horodatage candidat.
	 * @return {@code true} ssi {@code timestamp != null} et
	 *         {@code startInclusive <= timestamp <= endInclusive}.
	 */
	@Override
	public boolean match(Instant timestamp)
	{
		if (timestamp == null) {
			return false;
		}
		return !timestamp.isBefore(this.startInclusive) && !timestamp.isAfter(this.endInclusive);
	}
}
