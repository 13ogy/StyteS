package fr.sorbonne_u.cps.pubsub.application.meteo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.exceptions.ComponentShutdownException;
import fr.sorbonne_u.components.exceptions.ComponentStartException;
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
import fr.sorbonne_u.cps.pubsub.plugins.ClientPrivilegedPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin;

/**
 * Composant "Bureau météo" (CDC §3.4) implémenté comme un client pub/sub
 * basé sur greffons ({@link PluginClient}).
 */
@OfferedInterfaces(offered = { ReceivingCI.class })
@RequiredInterfaces(required = { RegistrationCI.class, PublishingCI.class, PrivilegedClientCI.class })
public class WeatherOffice extends AbstractComponent
{

	// Weather Office register and publish messages as privileged clients
	// Needs registration and privileged plugin
	private final ClientRegistrationPlugin regPlugin;
	private final ClientPrivilegedPlugin privPlugin;

	private final String officeId;

	protected WeatherOffice(String reflectionInboundPortURI, String officeId) throws Exception {
		super(reflectionInboundPortURI, 1, 0);
		this.officeId = officeId;

		regPlugin = new ClientRegistrationPlugin();
		regPlugin.setPluginURI(reflectionInboundPortURI + "-reg");

		privPlugin = new ClientPrivilegedPlugin(regPlugin);
		privPlugin.setPluginURI(reflectionInboundPortURI + "-priv");
	}

	@Override
	public synchronized void start() throws ComponentStartException {
		try {
			this.installPlugin(this.regPlugin);
			this.installPlugin(this.privPlugin);
		} catch (Exception e) {
			throw new ComponentStartException(e);
		}
		super.start();
	}
	@Override
	public void execute()
	{
		try {
			super.execute();
			this.regPlugin.register(RegistrationClass.FREE);
			this.regPlugin.modifyServiceClass(RegistrationClass.STANDARD);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	@Override
	public void finalise() throws Exception {
		regPlugin.finalise();
		privPlugin.finalise();
		super.finalise();
	}

	@Override
	public synchronized void shutdown() throws ComponentShutdownException {
		try {
			this.privPlugin.uninstall();
			this.regPlugin.uninstall();
		} catch (Exception e) {
			throw new ComponentShutdownException(e);
		}
		super.shutdown();
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
		this.privPlugin.publish(alertChannel, m);
	}
}
