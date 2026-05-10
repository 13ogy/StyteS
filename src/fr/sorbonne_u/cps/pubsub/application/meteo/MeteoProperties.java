package fr.sorbonne_u.cps.pubsub.application.meteo;

/**
 * Noms de propriétés centralisés pour les messages de l'application météo.
 *
 * <p>Cette classe collecte en un seul endroit tous les noms de propriétés utilisés par les messages
 * vent et alerte, conformément au principe DRY : un futur renommage ne nécessite qu'une seule
 * édition. Les composants applicatifs ({@link WeatherStation}, {@link WeatherOffice}, {@link
 * WindTurbine}) et les démos ne manipulent jamais ces littéraux directement.
 *
 * <p>Chaque constante est également référencée par les fabriques de messages dédiées ({@link
 * WindMessageFactory}, {@link MeteoAlertMessageFactory}) et par les constructeurs de filtres dédiés
 * ({@link MeteoFilters}).
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public final class MeteoProperties {
	private MeteoProperties() {
		/* constants only */
	}

	// -------------------------------------------------------------------------
	// Common
	// -------------------------------------------------------------------------

	/** Discriminator of the message kind: see {@link #TYPE_WIND} / {@link #TYPE_ALERT}. */
	public static final String TYPE = "type";

	/** The original payload, exposed as a property so value filters can match on it. */
	public static final String PAYLOAD = "payload";

	// -------------------------------------------------------------------------
	// Wind messages
	// -------------------------------------------------------------------------

	/** Value of {@link #TYPE} for wind messages. */
	public static final String TYPE_WIND = "wind";

	/** Identifier of the publishing {@link WeatherStation}. */
	public static final String STATION_ID = "stationId";

	/** Wind force (m/s), serialised as {@link Double} via {@link Double#toString(double)}. */
	public static final String FORCE = "force";

	/** X component of the wind vector. */
	public static final String X = "x";

	/** Y component of the wind vector. */
	public static final String Y = "y";

	// -------------------------------------------------------------------------
	// Alert messages
	// -------------------------------------------------------------------------

	/** Value of {@link #TYPE} for alert messages. */
	public static final String TYPE_ALERT = "alert";

	/** Identifier of the publishing {@link WeatherOffice}. */
	public static final String OFFICE_ID = "officeId";

	/** Severity level (e.g. {@code GREEN}, {@code YELLOW}, {@code ORANGE}, {@code RED}). */
	public static final String LEVEL = "level";

	/** Hazard type (rain, wind, ...). */
	public static final String ALERT_TYPE = "alertType";

	// -------------------------------------------------------------------------
	// Channel naming convention
	// -------------------------------------------------------------------------

	/** Default FREE channel for wind observations. */
	public static final String DEFAULT_WIND_CHANNEL = "channel0";

	/** Default FREE channel for meteo alerts. */
	public static final String DEFAULT_ALERT_CHANNEL = "channel1";
}
