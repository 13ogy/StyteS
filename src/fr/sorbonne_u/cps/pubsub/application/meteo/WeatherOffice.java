package fr.sorbonne_u.cps.pubsub.application.meteo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.cps.pubsub.base.components.Client;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;
import fr.sorbonne_u.cps.pubsub.meteo.MeteoAlertI;
import fr.sorbonne_u.cps.pubsub.messages.Message;

/**
 * CDC §3.4 Weather office component.
 *
 * Publishes meteo alerts (MeteoAlertI) on an alert channel.
 */
public class WeatherOffice extends AbstractComponent
{
	private final Client psClient;
	private final String officeId;

	protected WeatherOffice(String officeId) throws Exception
	{
		super(1, 0);
		if (officeId == null || officeId.isEmpty()) {
			throw new IllegalArgumentException("officeId cannot be null/empty");
		}
		this.officeId = officeId;
		this.psClient = new Client(1, 0);
	}

	@Override
	public synchronized void start()
	{
		try {
			super.start();
			this.psClient.register(RegistrationClass.FREE);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void publishAlert(String alertChannel, MeteoAlertI alert) throws Exception
	{
		// Ensure the internal pub/sub client is registered (ports connected) before publishing.
		this.psClient.register(RegistrationClass.FREE);
		Message m = new Message((java.io.Serializable) alert);
		m.putProperty("type", "alert");
		m.putProperty("officeId", officeId);
		m.putProperty("level", alert.getLevel().toString());
		m.putProperty("alertType", alert.getAlertType().toString());

		this.logMessage("WeatherOffice[" + officeId + "] publish alert " + alert + " on " + alertChannel + "\n");
		psClient.publish(alertChannel, m);
	}
}
