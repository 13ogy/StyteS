package fr.sorbonne_u.cps.pubsub.application.meteo;

import java.io.Serializable;

import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.messages.Message;
import fr.sorbonne_u.cps.pubsub.meteo.MeteoAlertI;

/**
 * Factory building meteo alert {@link MessageI} instances populated with the
 * properties expected by {@link MeteoFilters}.
 *
 * <p>
 * Per the soutenance review (1.10), alert message construction must not be
 * scattered as inline literal-property assignments. Every alert message used
 * by the system goes through this factory.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public final class MeteoAlertMessageFactory
{
	private MeteoAlertMessageFactory() { /* factory only */ }

	/**
	 * Build an alert message.
	 *
	 * <p>
	 * Properties set:
	 * </p>
	 * <ul>
	 *   <li>{@link MeteoProperties#TYPE} = {@link MeteoProperties#TYPE_ALERT}</li>
	 *   <li>{@link MeteoProperties#PAYLOAD} = the {@link MeteoAlertI} payload</li>
	 *   <li>{@link MeteoProperties#OFFICE_ID} = {@code officeId}</li>
	 *   <li>{@link MeteoProperties#LEVEL} = {@code alert.getLevel().toString()}</li>
	 *   <li>{@link MeteoProperties#ALERT_TYPE} = {@code alert.getAlertType().toString()}</li>
	 * </ul>
	 *
	 * @param officeId identifier of the publishing {@link WeatherOffice}.
	 * @param alert    alert payload (must not be null).
	 * @return a fully populated {@link MessageI}.
	 */
	public static MessageI build(String officeId, MeteoAlertI alert)
	{
		if (officeId == null || officeId.isEmpty()) {
			throw new IllegalArgumentException("officeId must not be null/empty.");
		}
		if (alert == null) {
			throw new IllegalArgumentException("alert must not be null.");
		}

		Message m = new Message((Serializable) alert);
		m.putProperty(MeteoProperties.TYPE, MeteoProperties.TYPE_ALERT);
		m.putProperty(MeteoProperties.PAYLOAD, (Serializable) alert);
		m.putProperty(MeteoProperties.OFFICE_ID, officeId);
		m.putProperty(MeteoProperties.LEVEL, alert.getLevel().toString());
		m.putProperty(MeteoProperties.ALERT_TYPE, alert.getAlertType().toString());
		return m;
	}
}
