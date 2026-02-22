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
 */
public class DemoMeteoAudit1 extends AbstractCVM
{
	public static final String WIND_CHANNEL = "channel0";
	public static final String ALERT_CHANNEL = "channel1";

	public DemoMeteoAudit1() throws Exception
	{
		super();
	}

	@Override
	public void deploy() throws Exception
	{
		AbstractComponent.createComponent(Broker.class.getCanonicalName(), new Object[] { 2, 0 });

		Position2D turbinePos = new Position2D(0.0, 0.0);
		Position2D stationNearPos = new Position2D(1.0, 0.0);
		Position2D stationFarPos = new Position2D(100.0, 0.0);

		String turbineURI = AbstractComponent.createComponent(
			WindTurbine.class.getCanonicalName(),
			new Object[] { "WT1", turbinePos, 20.0, 5_000L, MeteoAlertI.Level.ORANGE });

		String station1URI = AbstractComponent.createComponent(
			WeatherStation.class.getCanonicalName(),
			new Object[] { "WS1", stationNearPos });

		String station2URI = AbstractComponent.createComponent(
			WeatherStation.class.getCanonicalName(),
			new Object[] { "WS2", stationFarPos });

		String officeURI = AbstractComponent.createComponent(
			WeatherOffice.class.getCanonicalName(),
			new Object[] { "WO1" });

		super.deploy();

		WindTurbine turbine = (WindTurbine) this.uri2component.get(turbineURI);
		WeatherStation station1 = (WeatherStation) this.uri2component.get(station1URI);
		WeatherStation station2 = (WeatherStation) this.uri2component.get(station2URI);
		WeatherOffice office = (WeatherOffice) this.uri2component.get(officeURI);

		this.toggleTracing(turbineURI);
		this.toggleLogging(turbineURI);
		this.toggleTracing(station1URI);
		this.toggleLogging(station1URI);
		this.toggleTracing(station2URI);
		this.toggleLogging(station2URI);
		this.toggleTracing(officeURI);
		this.toggleLogging(officeURI);

		// Subscriptions
		turbine.subscribeToWindAndAlerts(WIND_CHANNEL, ALERT_CHANNEL);
		Thread.sleep(200L);

		// Winds
		station1.publishWind(WIND_CHANNEL, new WindData(stationNearPos, 5.0, 0.0));
		station2.publishWind(WIND_CHANNEL, new WindData(stationFarPos, 10.0, 0.0));

		Thread.sleep(300L);

		// Alerts
		RegionI concerned = new CircularRegion(turbinePos, 10.0);
		MeteoAlert orange = new MeteoAlert(
			MeteoAlertI.AlterType.STORM,
			MeteoAlertI.Level.ORANGE,
			new RegionI[] { concerned },
			Instant.now(),
			Duration.ofMinutes(30));
		office.publishAlert(ALERT_CHANNEL, orange);

		Thread.sleep(300L);

		MeteoAlert green = new MeteoAlert(
			MeteoAlertI.AlterType.STORM,
			MeteoAlertI.Level.GREEN,
			new RegionI[] { concerned },
			Instant.now(),
			Duration.ofMinutes(1));
		office.publishAlert(ALERT_CHANNEL, green);

		Thread.sleep(500L);
	}

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
