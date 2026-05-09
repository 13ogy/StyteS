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
import fr.sorbonne_u.cps.pubsub.meteo.PositionI;
import fr.sorbonne_u.cps.pubsub.meteo.WindDataI;
import fr.sorbonne_u.cps.pubsub.plugins.ClientPublicationPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin;

/**
 * Composant "Station météo" (CDC §3.4) implémenté comme un client du système
 * pub/sub basé sur greffons ({@link PluginClient}).
 *
 * <p>
 * Avantage : la station bénéficie directement des fonctionnalités côté client
 * (notamment CDC §3.5.3 si besoin) sans embarquer un second composant.
 * </p>
 */
@OfferedInterfaces(offered = { ReceivingCI.class })
@RequiredInterfaces(required = { RegistrationCI.class, PublishingCI.class, PrivilegedClientCI.class })
public class WeatherStation extends AbstractComponent
{
	// Weather Station register and publish messages
	// Needs registration and publish plugin
	private ClientRegistrationPlugin regPlugin;
	private ClientPublicationPlugin pubPlugin;

	private final String stationId;
	private final PositionI position;

	protected WeatherStation(String stationId, PositionI position) throws Exception
	{
		this(stationId, stationId, position, null);
	}

	/** Constructeur avec URI du port de réflexion (obligatoire pour BCM4Java). */
	protected WeatherStation(String reflectionInboundPortURI, String stationId, PositionI position) throws Exception
	{
		this(reflectionInboundPortURI, stationId, position, null);
	}

	/**
	 * Préféré (Phase C.3) : prend en plus l'URI de réflexion du courtier
	 * cible afin que le greffon d'enregistrement sache à qui se connecter.
	 */
	protected WeatherStation(String reflectionInboundPortURI, String stationId,
							 PositionI position, String brokerReflectionURI) throws Exception
	{
		super(reflectionInboundPortURI, 1, 0);
		if (stationId == null || stationId.isEmpty()) {
			throw new IllegalArgumentException("stationId cannot be null/empty");
		}
		if (position == null) {
			throw new IllegalArgumentException("position cannot be null");
		}
		this.stationId = stationId;
		this.position = position;

		regPlugin = new ClientRegistrationPlugin(brokerReflectionURI);
		regPlugin.setPluginURI(reflectionInboundPortURI + "-reg");

		pubPlugin = new ClientPublicationPlugin(regPlugin);
		pubPlugin.setPluginURI(reflectionInboundPortURI + "-pub");

	}

	@Override
	public synchronized void start() throws ComponentStartException {
		try {
			this.installPlugin(this.regPlugin);
			this.installPlugin(this.pubPlugin);
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

	public PositionI getPosition()
	{
		return position;
	}


	public void publishWind(String windChannel, WindDataI wind) throws Exception
	{
		MessageI m = WindMessageFactory.build(stationId, wind);

		String out = "WeatherStation[" + stationId + "] publish wind " + wind + " on " + windChannel;
		this.traceMessage(out + "\n");
		this.logMessage(out + "\n");
		this.pubPlugin.publish(windChannel, m);
	}
}
