package fr.sorbonne_u.cps.pubsub.application.meteo;

import java.io.Serializable;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.messages.Message;
import fr.sorbonne_u.cps.pubsub.meteo.WindDataI;

/**
 * Factory building wind {@link MessageI} instances populated with the
 * properties expected by {@link MeteoFilters}.
 *
 * <p>
 * Per the soutenance review (1.10), wind message construction must not be
 * scattered as inline literal-property assignments. Every wind message used by
 * the system goes through this factory.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public final class WindMessageFactory
{
	private WindMessageFactory() { /* factory only */ }

	/**
	 * Build a wind message.
	 *
	 * <p>
	 * Properties set:
	 * </p>
	 * <ul>
	 *   <li>{@link MeteoProperties#TYPE} = {@link MeteoProperties#TYPE_WIND}</li>
	 *   <li>{@link MeteoProperties#PAYLOAD} = the {@link WindDataI} payload (so value filters can reach it)</li>
	 *   <li>{@link MeteoProperties#STATION_ID} = {@code stationId}</li>
	 *   <li>{@link MeteoProperties#FORCE} = string form of {@link WindDataI#force()}</li>
	 *   <li>{@link MeteoProperties#X} = string form of {@link WindDataI#xComponent()}</li>
	 *   <li>{@link MeteoProperties#Y} = string form of {@link WindDataI#yComponent()}</li>
	 * </ul>
	 *
	 * @param stationId identifier of the publishing {@link WeatherStation}.
	 * @param wind      wind observation (must not be null).
	 * @return a fully populated {@link MessageI}.
	 */
	public static MessageI build(String stationId, WindDataI wind)
	{
		if (stationId == null || stationId.isEmpty()) {
			throw new IllegalArgumentException("stationId must not be null/empty.");
		}
		if (wind == null) {
			throw new IllegalArgumentException("wind must not be null.");
		}

		Message m = new Message((Serializable) wind);
		m.putProperty(MeteoProperties.TYPE, MeteoProperties.TYPE_WIND);
		m.putProperty(MeteoProperties.PAYLOAD, (Serializable) wind);
		m.putProperty(MeteoProperties.STATION_ID, stationId);
		m.putProperty(MeteoProperties.FORCE, Double.toString(wind.force()));
		m.putProperty(MeteoProperties.X, Double.toString(wind.xComponent()));
		m.putProperty(MeteoProperties.Y, Double.toString(wind.yComponent()));
		return m;
	}
}
