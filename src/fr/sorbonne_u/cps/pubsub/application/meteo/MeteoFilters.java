package fr.sorbonne_u.cps.pubsub.application.meteo;

import fr.sorbonne_u.cps.pubsub.application.meteo.filters.DistanceWindFilter;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.messages.MessageFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.AcceptAllTimeFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.ComparableValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.EqualsValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.PropertyFilter;
import fr.sorbonne_u.cps.pubsub.meteo.MeteoAlertI;
import fr.sorbonne_u.cps.pubsub.meteo.PositionI;

/**
 * Constructeurs des {@link MessageFilterI} utilisés par les abonnés météo.
 *
 * <p>Centraliser ici la composition des filtres applicatifs respecte le principe DRY : les noms de
 * propriétés ne sont jamais dupliqués comme littéraux dans les composants ou les démos, et tout
 * filtre métier passe par cette fabrique.
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public final class MeteoFilters {
	private MeteoFilters() {
		/* factory only */
	}

	/**
	 * Filter accepting wind messages whose {@link MeteoProperties#PAYLOAD} is within {@code
	 * maxDistance} of {@code reference}.
	 */
	public static MessageFilterI windWithinDistance(PositionI reference, double maxDistance) {
		return new MessageFilter(
				new MessageFilterI.PropertyFilterI[] {
					new PropertyFilter(
							MeteoProperties.TYPE, new EqualsValueFilter(MeteoProperties.TYPE_WIND)),
					new PropertyFilter(
							MeteoProperties.PAYLOAD, new DistanceWindFilter(reference, maxDistance))
				},
				new MessageFilterI.PropertiesFilterI[0],
				new AcceptAllTimeFilter());
	}

	/** Filter accepting any wind message regardless of position. */
	public static MessageFilterI anyWind() {
		return new MessageFilter(
				new MessageFilterI.PropertyFilterI[] {
					new PropertyFilter(
							MeteoProperties.TYPE, new EqualsValueFilter(MeteoProperties.TYPE_WIND))
				},
				new MessageFilterI.PropertiesFilterI[0],
				new AcceptAllTimeFilter());
	}

	/** Filter accepting any meteo alert message. */
	public static MessageFilterI anyAlert() {
		return new MessageFilter(
				new MessageFilterI.PropertyFilterI[] {
					new PropertyFilter(
							MeteoProperties.TYPE, new EqualsValueFilter(MeteoProperties.TYPE_ALERT))
				},
				new MessageFilterI.PropertiesFilterI[0],
				new AcceptAllTimeFilter());
	}

	/**
	 * Filter accepting alerts whose {@link MeteoProperties#LEVEL} is at least the provided minimum
	 * (string-comparison on the level name; works because {@link MeteoAlertI.Level} names are
	 * alphabetically meaningful for the existing demo, but callers should prefer {@link
	 * #atLeastAlertLevel} when comparing on the enum directly).
	 */
	public static MessageFilterI alertWithLevelAtLeastByName(String minimumLevelName) {
		return new MessageFilter(
				new MessageFilterI.PropertyFilterI[] {
					new PropertyFilter(
							MeteoProperties.TYPE,
							new EqualsValueFilter(MeteoProperties.TYPE_ALERT)),
					new PropertyFilter(
							MeteoProperties.LEVEL,
							ComparableValueFilter.greaterOrEqual(minimumLevelName))
				},
				new MessageFilterI.PropertiesFilterI[0],
				new AcceptAllTimeFilter());
	}

	/** Convenience overload taking the {@link MeteoAlertI.Level} enum. */
	public static MessageFilterI atLeastAlertLevel(MeteoAlertI.Level minimum) {
		return alertWithLevelAtLeastByName(minimum.toString());
	}
}
