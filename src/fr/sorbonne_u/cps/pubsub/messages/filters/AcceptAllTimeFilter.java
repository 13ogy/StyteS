package fr.sorbonne_u.cps.pubsub.messages.filters;

import java.time.Instant;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.TimeFilterI;

/**
 * Implémentation de {@link TimeFilterI} (joker temporel) acceptant toute
 * estampille, y compris {@code null}.
 *
 * <p>
 * Utilisé par défaut dans {@link fr.sorbonne_u.cps.pubsub.messages.MessageFilter}
 * lorsqu'aucune contrainte temporelle n'est fournie au constructeur, afin
 * d'éviter à l'évaluation un test {@code timeFilter == null} (cf. CDC §3.5
 * sur les filtres de messages).
 * </p>
 *
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class AcceptAllTimeFilter implements TimeFilterI
{
	private static final long serialVersionUID = 1L;

	/**
	 * @param timestamp horodatage candidat (peut être {@code null}).
	 * @return toujours {@code true}.
	 */
	@Override
	public boolean match(Instant timestamp)
	{
		return true;
	}
}
