package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.cvm.AbstractCVM;
import fr.sorbonne_u.cps.pubsub.application.meteo.WeatherOffice;
import fr.sorbonne_u.cps.pubsub.application.meteo.WeatherStation;
import fr.sorbonne_u.cps.pubsub.application.meteo.WindTurbine;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.meteo.MeteoAlertI;
import fr.sorbonne_u.cps.pubsub.meteo.RegionI;
import fr.sorbonne_u.cps.pubsub.meteo.impl.CircularRegion;
import fr.sorbonne_u.cps.pubsub.meteo.impl.MeteoAlert;
import fr.sorbonne_u.cps.pubsub.meteo.impl.Position2D;
import fr.sorbonne_u.cps.pubsub.meteo.impl.WindData;

import java.time.Duration;
import java.time.Instant;

/**
 * CDC §3.4 integration scenario (simple, without timed test tool).
 *
 * Creates:
 * - 1 Broker
 * - 1 WindTurbine
 * - 2 WeatherStations
 * - 1 WeatherOffice
 *
 * Scenario:
 * - turbine subscribes to wind (channel0) and alerts (channel1)
 * - station1 publishes a nearby wind -> accepted
 * - station2 publishes a far wind -> ignored
 * - office publishes ORANGE alert concerning turbine -> safety mode
 * - office publishes GREEN alert concerning turbine -> return normal
 
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class DemoMeteoAudit1 extends AbstractCVM
{
	public static final String BROKER_URI = "broker";
	public static final String WIND_CHANNEL = "channel0";
	public static final String ALERT_CHANNEL = "channel1";
	/**
	 * Construit ce CVM ; la création réelle des composants se produit dans
	 * {@link #deploy()}.
	 *
	 * @throws Exception si l'initialisation parent échoue.
	 */

	public DemoMeteoAudit1() throws Exception
	{
		super();
	}
	/**
	 * Crée et publie tous les composants du scénario, puis active le tracing
	 * sur les participants pertinents.
	 *
	 * @throws Exception si la création / publication d'un composant échoue.
	 */

	@Override
	public void deploy() throws Exception
	{
		AbstractComponent.createComponent(Broker.class.getCanonicalName(),
			new Object[] { BROKER_URI, 2, 1, 3, 2, 5, 2, 4, 8 });

		Position2D turbinePos = new Position2D(0.0, 0.0);
		Position2D stationNearPos = new Position2D(1.0, 0.0);
		Position2D stationFarPos = new Position2D(100.0, 0.0);

		this.turbineURI = AbstractComponent.createComponent(
			WindTurbine.class.getCanonicalName(),
			new Object[] { "WT1", "WT1", turbinePos, 20.0, 5_000L, MeteoAlertI.Level.ORANGE, BROKER_URI });

		this.station1URI = AbstractComponent.createComponent(
			WeatherStation.class.getCanonicalName(),
			new Object[] { "WS1", "WS1", stationNearPos, BROKER_URI });

		this.station2URI = AbstractComponent.createComponent(
			WeatherStation.class.getCanonicalName(),
			new Object[] { "WS2", "WS2", stationFarPos, BROKER_URI });

		this.officeURI = AbstractComponent.createComponent(
			WeatherOffice.class.getCanonicalName(),
			new Object[] { "WO1", "WO1", BROKER_URI });

		this.turbinePos = turbinePos;
		this.stationNearPos = stationNearPos;
		this.stationFarPos = stationFarPos;

		super.deploy();

		this.toggleTracing(this.turbineURI);
		this.toggleLogging(this.turbineURI);
		this.toggleTracing(this.station1URI);
		this.toggleLogging(this.station1URI);
		this.toggleTracing(this.station2URI);
		this.toggleLogging(this.station2URI);
		this.toggleTracing(this.officeURI);
		this.toggleLogging(this.officeURI);
	}

	private String turbineURI;
	private String station1URI;
	private String station2URI;
	private String officeURI;
	private Position2D turbinePos;
	private Position2D stationNearPos;
	private Position2D stationFarPos;

	@Override
	public void start() throws Exception
	{
		super.start();
	}
	/**
	 * Lance la phase d'exécution du CVM ; appelé par le cycle de vie BCM
	 * après {@link #deploy()}.
	 *
	 * @throws Exception si l'exécution échoue.
	 */

	@Override
	public void execute() throws Exception
	{
		super.execute();

		WindTurbine turbine = (WindTurbine) this.uri2component.get(this.turbineURI);
		WeatherStation station1 = (WeatherStation) this.uri2component.get(this.station1URI);
		WeatherStation station2 = (WeatherStation) this.uri2component.get(this.station2URI);
		WeatherOffice office = (WeatherOffice) this.uri2component.get(this.officeURI);

		// Subscriptions
		turbine.subscribeToWindAndAlerts(WIND_CHANNEL, ALERT_CHANNEL);
		Thread.sleep(200L);

		// Winds
		station1.publishWind(WIND_CHANNEL, new WindData(this.stationNearPos, 5.0, 0.0));
		station2.publishWind(WIND_CHANNEL, new WindData(this.stationFarPos, 10.0, 0.0));

		Thread.sleep(300L);

		// Alerts
		RegionI concerned = new CircularRegion(this.turbinePos, 10.0);
		MeteoAlert orange = new MeteoAlert(
			MeteoAlertI.AlertType.STORM,
			MeteoAlertI.Level.ORANGE,
			new RegionI[] { concerned },
			Instant.now(),
			Duration.ofMinutes(30));
		office.publishAlert(ALERT_CHANNEL, orange);

		Thread.sleep(300L);

		MeteoAlert green = new MeteoAlert(
			MeteoAlertI.AlertType.STORM,
			MeteoAlertI.Level.GREEN,
			new RegionI[] { concerned },
			Instant.now(),
			Duration.ofMinutes(1));
		office.publishAlert(ALERT_CHANNEL, green);

		Thread.sleep(500L);
	}
	/**
	 * Point d'entrée standalone : démarre le cycle de vie centralisé du CVM
	 * pendant la durée codée en dur, puis termine la JVM.
	 *
	 * @param args ignorés.
	 */

	public static void main(String[] args)
	{
		try {
			DemoMeteoAudit1 cvm = new DemoMeteoAudit1();
			cvm.startStandardLifeCycle(6000L);
			System.exit(0);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
