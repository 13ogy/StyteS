package fr.sorbonne_u.cps.pubsub.application.meteo.filters;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.meteo.PositionI;
import fr.sorbonne_u.cps.pubsub.meteo.WindDataI;
import fr.sorbonne_u.cps.pubsub.meteo.impl.Position2D;

import java.io.Serializable;

/**
 * Filtre {@link MessageFilterI.ValueFilterI} spécifique à l'application qui
 * accepte une donnée {@link WindDataI} uniquement lorsque sa
 * {@link WindDataI#getPosition()} est à une distance inférieure ou égale à
 * {@code maxDistance} d'une position de référence.
 *
 * <p>
 * Principe de séparation des préoccupations : ce filtre fait partie de
 * l'<em>application éolienne</em>, pas du système publish/subscribe
 * générique. Il vit donc sous {@code fr.sorbonne_u.cps.pubsub.application
 * .meteo.filters}, aux côtés des autres abstractions du domaine météo
 * ({@code MeteoFilters}, {@code MeteoProperties},
 * {@code WindMessageFactory}, {@code MeteoAlertMessageFactory}).
 * Le paquetage générique {@code messages.filters} ne dépend ainsi
 * d'aucune classe du paquetage {@code meteo}.
 * </p>
 *
 * <p>
 * Le calcul de la distance est délégué à
 * {@link Position2D#distanceTo(PositionI)} pour respecter l'encapsulation
 * objet : aucun code appelant n'a à atteindre les coordonnées privées d'une
 * {@link Position2D} pour mesurer une distance.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class DistanceWindFilter implements MessageFilterI.ValueFilterI
{
	private static final long serialVersionUID = 1L;

	protected final PositionI referencePosition;
	protected final double maxDistance;

	/**
	 * Crée le filtre de distance.
	 *
	 * @param referencePosition position de référence (non {@code null}).
	 * @param maxDistance distance maximale acceptée, en unités de
	 * {@link Position2D#distanceTo(PositionI)}
	 * (doit être {@code >= 0}).
	 * @throws IllegalArgumentException si {@code referencePosition} est {@code null}
	 * ou si {@code maxDistance < 0}.
	 */
	public DistanceWindFilter(PositionI referencePosition, double maxDistance)
	{
		if (referencePosition == null) {
			throw new IllegalArgumentException("referencePosition cannot be null.");
		}
		if (maxDistance < 0.0) {
			throw new IllegalArgumentException("maxDistance must be >= 0.");
		}
		this.referencePosition = referencePosition;
		this.maxDistance = maxDistance;
	}

	/**
	 * @param value valeur candidate.
	 * @return {@code true} ssi {@code value} est un {@link WindDataI} dont la
	 * position est à distance {@code <= maxDistance} de la position
	 * de référence ; {@code false} sinon (y compris pour des
	 * implémentations de {@link PositionI} non gérables géométriquement).
	 */
	@Override
	public boolean match(Serializable value)
	{
		if (!(value instanceof WindDataI)) {
			return false;
		}
		WindDataI wind = (WindDataI) value;
		PositionI windPos = wind.getPosition();

		if (this.referencePosition instanceof Position2D) {
			return ((Position2D) this.referencePosition).distanceTo(windPos) <= this.maxDistance;
		}
		if (windPos instanceof Position2D) {
			return ((Position2D) windPos).distanceTo(this.referencePosition) <= this.maxDistance;
		}
		return false;
	}
}
