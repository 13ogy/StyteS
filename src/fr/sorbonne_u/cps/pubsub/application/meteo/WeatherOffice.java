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
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.meteo.MeteoAlertI;
import fr.sorbonne_u.cps.pubsub.plugins.ClientPrivilegedPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin;

/**
 * Composant "Bureau météo" (CDC §3.4) implémenté comme un client pub/sub
 * basé sur greffons ({@link PluginClient}).
 *
 * <p>
 * Le bureau s'enregistre comme client {@code FREE} puis se promeut en
 * {@code STANDARD} pour bénéficier des canaux privilégiés (CDC §3.3 — quotas
 * par classe de service).
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
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
		this(reflectionInboundPortURI, officeId, null);
	}

	/** Phase C.3: identifie le courtier cible via son URI de réflexion. */
	protected WeatherOffice(String reflectionInboundPortURI, String officeId,
							String brokerReflectionURI) throws Exception {
		super(reflectionInboundPortURI, 1, 0);
		this.officeId = officeId;

		regPlugin = new ClientRegistrationPlugin(brokerReflectionURI);
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
		super.finalise();
	}

	@Override
	public synchronized void shutdown() throws fr.sorbonne_u.components.exceptions.ComponentShutdownException {
		fr.sorbonne_u.cps.pubsub.demo.PortCleanupUtil.disconnectStillConnectedOutboundPorts(this);
		super.shutdown();
	}

	/**
	 * Publie une alerte météo sur le canal indiqué.
	 *
	 * <p>
	 * Le message est construit via {@link MeteoAlertMessageFactory#build(String, MeteoAlertI)}
	 * (CDC §3.5 — convention de propriétés des messages d'alerte).
	 * </p>
	 *
	 * @param alertChannel canal de publication (typiquement
	 *                     {@link MeteoProperties#DEFAULT_ALERT_CHANNEL} ou un
	 *                     canal privilégié appartenant au bureau).
	 * @param alert        alerte à publier (non {@code null}).
	 * @throws Exception si la publication via le greffon échoue.
	 */
	public void publishAlert(String alertChannel, MeteoAlertI alert) throws Exception
	{
		MessageI m = MeteoAlertMessageFactory.build(officeId, alert);

		String out = "WeatherOffice[" + officeId + "] publish alert " + alert + " on " + alertChannel;
		this.traceMessage(out + "\n");
		this.logMessage(out + "\n");
		this.privPlugin.publish(alertChannel, m);
	}
}
