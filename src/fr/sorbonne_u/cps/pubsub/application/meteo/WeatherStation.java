package fr.sorbonne_u.cps.pubsub.application.meteo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.cps.pubsub.base.components.Client;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;
import fr.sorbonne_u.cps.pubsub.meteo.PositionI;
import fr.sorbonne_u.cps.pubsub.meteo.WindDataI;
import fr.sorbonne_u.cps.pubsub.messages.Message;

/**
 * CDC §3.4 Weather station component.
 *
 * Publishes wind observations (WindDataI) on a wind channel.
 */
public class WeatherStation extends AbstractComponent
{
	private final Client psClient;
	private final String stationId;
	private final PositionI position;

	protected WeatherStation(String stationId, PositionI position) throws Exception
	{
		// Use the component URI as reflection inbound port URI (BCM4Java requirement).
		this(stationId, stationId, position);
	}

	/** Constructor variant allowing to set the reflection inbound port URI. */
	protected WeatherStation(String reflectionInboundPortURI, String stationId, PositionI position) throws Exception
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

		// Use the generic pub/sub client implementation.
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

	public PositionI getPosition()
	{
		return position;
	}

	public void publishWind(String windChannel, WindDataI wind) throws Exception
	{

		this.psClient.register(RegistrationClass.FREE);
		Message m = new Message((java.io.Serializable) wind);
		m.putProperty("type", "wind");
		// Expose the payload as a property as well so that value-based filters can be applied
		// using PropertyFilter on "payload" (default filtering at subscription level).
		m.putProperty("payload", (java.io.Serializable) wind);
		m.putProperty("stationId", stationId);
		m.putProperty("force", Double.toString(wind.force()));
		m.putProperty("x", Double.toString(wind.xComponent()));
		m.putProperty("y", Double.toString(wind.yComponent()));

		String out = "WeatherStation[" + stationId + "] publish wind " + wind + " on " + windChannel;
		System.out.println(out);
		this.logMessage(out + "\n");
		psClient.publish(windChannel, m);
	}
}
