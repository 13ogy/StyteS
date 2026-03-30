package fr.sorbonne_u.cps.pubsub.application.meteo;

import fr.sorbonne_u.cps.pubsub.base.components.PluginClient;
import fr.sorbonne_u.components.annotations.OfferedInterfaces;
import fr.sorbonne_u.components.annotations.RequiredInterfaces;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;
import fr.sorbonne_u.cps.pubsub.meteo.MeteoAlertI;
import fr.sorbonne_u.cps.pubsub.messages.Message;

/**
 * Composant "Bureau météo" (CDC §3.4) implémenté comme un client pub/sub
 * basé sur greffons ({@link PluginClient}).
 */
@OfferedInterfaces(offered = { ReceivingCI.class })
@RequiredInterfaces(required = { RegistrationCI.class, PublishingCI.class, PrivilegedClientCI.class })
public class WeatherOffice extends PluginClient
{
	private final String officeId;

	protected WeatherOffice(String officeId) throws Exception
	{
		this(officeId, officeId);
	}

	protected WeatherOffice(String reflectionInboundPortURI, String officeId) throws Exception
	{
		super(reflectionInboundPortURI, 1, 0);
		if (officeId == null || officeId.isEmpty()) {
			throw new IllegalArgumentException("officeId cannot be null/empty");
		}
		this.officeId = officeId;
	}

	@Override
	public synchronized void start()
	{
		try {
			super.start();
			this.register(RegistrationClass.FREE);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void publishAlert(String alertChannel, MeteoAlertI alert) throws Exception
	{
		Message m = new Message((java.io.Serializable) alert);
		m.putProperty("type", "alert");
		m.putProperty("officeId", officeId);
		m.putProperty("level", alert.getLevel().toString());
		m.putProperty("alertType", alert.getAlertType().toString());

		String out = "WeatherOffice[" + officeId + "] publish alert " + alert + " on " + alertChannel;
		this.traceMessage(out + "\n");
		this.logMessage(out + "\n");
		this.publish(alertChannel, m);
	}
}
