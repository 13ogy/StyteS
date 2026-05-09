package fr.sorbonne_u.cps.pubsub.application.meteo;

/**
 * Centralised property names used by the meteo application messages.
 *
 * <p>
 * The soutenance review (1.10) flagged that wind/alert messages were created
 * using inline string literals scattered across {@link WeatherStation},
 * {@link WeatherOffice}, {@link WindTurbine} and the demo subscriptions. This
 * class collects every property name in one place so a future rename only
 * requires editing this file.
 * </p>
 *
 * <p>
 * Every constant is also referenced by the dedicated message factories
 * ({@link WindMessageFactory}, {@link MeteoAlertMessageFactory}) and by the
 * dedicated filter helpers ({@link MeteoFilters}).
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public final class MeteoProperties
{
	private MeteoProperties() { /* constants only */ }

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
