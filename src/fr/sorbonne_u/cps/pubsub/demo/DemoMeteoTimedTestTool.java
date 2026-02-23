package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.cvm.AbstractCVM;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.components.utils.tests.TestStep;
import fr.sorbonne_u.components.utils.tests.TestStepI;
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
import fr.sorbonne_u.utils.aclocks.ClocksServer;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Timed integration demo using BCM4Java test tool described in CDC Annexe B
 * (TestScenario + ClocksServer + AcceleratedClock).
 *
 * This demo replays the meteo scenario (CDC §3.4) but drives the actions
 * (subscriptions, publications) with timed test steps instead of Thread.sleep.
 */
public class DemoMeteoTimedTestTool extends AbstractCVM
{
	public static final String WIND_CHANNEL = "channel0";
	public static final String ALERT_CHANNEL = "channel1";

	// ---------------------------------------------------------------------
	// Timed test tool configuration
	// ---------------------------------------------------------------------

	public static final String CLOCK_URI = "meteo-test-clock";
	// start/end instants are arbitrary; only relative ordering matters.
	public static final String START_INSTANT = "2026-01-30T09:00:00.00Z";
	public static final String END_INSTANT = "2026-01-30T09:05:00.00Z";
	public static final double ACCELERATION_FACTOR = 60.0; // 1 min virtual = 1 sec real
	public static final long DELAY_TO_START_MS = 1500L;

	// IMPORTANT (CDC Annexe B): the participant URIs in the TestScenario must be
	// the *reflection inbound port URI* of the components.
	//
	// In BCM4Java, this is the component URI passed to the AbstractComponent
	// constructor (aka reflectionInboundPortURI). Therefore, we must create
	// components by passing that URI to their super constructor, not by reusing
	// application-level IDs.
	public static final String TURBINE_RIP_URI = "meteo-turbine";
	public static final String STATION_NEAR_RIP_URI = "meteo-station-near";
	public static final String STATION_FAR_RIP_URI = "meteo-station-far";
	public static final String OFFICE_RIP_URI = "meteo-office";

	public DemoMeteoTimedTestTool() throws Exception
	{
		super();
	}

	public static TestScenario testScenario() throws Exception
	{
		Instant startInstant = Instant.parse(START_INSTANT);
		Instant endInstant = Instant.parse(END_INSTANT);

		Instant tSubscribe = startInstant.plusSeconds(5);
		Instant tWindNear = startInstant.plusSeconds(15);
		Instant tWindFar = startInstant.plusSeconds(20);
		Instant tAlertOrange = startInstant.plusSeconds(30);
		Instant tAlertGreen = startInstant.plusSeconds(40);

		// Positions/regions used by the steps.
		Position2D turbinePos = new Position2D(0.0, 0.0);
		Position2D stationNearPos = new Position2D(1.0, 0.0);
		Position2D stationFarPos = new Position2D(100.0, 0.0);
		RegionI concerned = new CircularRegion(turbinePos, 10.0);

		MeteoAlert orange = new MeteoAlert(
			MeteoAlertI.AlterType.STORM,
			MeteoAlertI.Level.ORANGE,
			new RegionI[] { concerned },
			Instant.now(),
			Duration.ofMinutes(30));

		MeteoAlert green = new MeteoAlert(
			MeteoAlertI.AlterType.STORM,
			MeteoAlertI.Level.GREEN,
			new RegionI[] { concerned },
			Instant.now(),
			Duration.ofMinutes(1));

		return new TestScenario(
			CLOCK_URI,
			"[TimedDemo] BEGIN meteo timed scenario",
			"[TimedDemo] END meteo timed scenario",
			startInstant,
			endInstant,
			new TestStepI[] {
				new TestStep(
					CLOCK_URI,
					TURBINE_RIP_URI,
					tSubscribe,
					owner -> {
						try {
							((WindTurbine) owner).subscribeToWindAndAlerts(WIND_CHANNEL, ALERT_CHANNEL);
							owner.traceMessage("[TimedDemo] turbine subscribed\n");
						} catch (Exception e) {
							System.err.println("[TimedDemo] subscribe step failed: " + e);
						}
					}),

				new TestStep(
					CLOCK_URI,
					STATION_NEAR_RIP_URI,
					tWindNear,
					owner -> {
						try {
							((WeatherStation) owner).publishWind(WIND_CHANNEL, new WindData(stationNearPos, 5.0, 0.0));
							owner.traceMessage("[TimedDemo] station near published wind\n");
						} catch (Exception e) {
							System.err.println("[TimedDemo] windNear step failed: " + e);
						}
					}),

				new TestStep(
					CLOCK_URI,
					STATION_FAR_RIP_URI,
					tWindFar,
					owner -> {
						try {
							((WeatherStation) owner).publishWind(WIND_CHANNEL, new WindData(stationFarPos, 10.0, 0.0));
							owner.traceMessage("[TimedDemo] station far published wind\n");
						} catch (Exception e) {
							System.err.println("[TimedDemo] windFar step failed: " + e);
						}
					}),

				new TestStep(
					CLOCK_URI,
					OFFICE_RIP_URI,
					tAlertOrange,
					owner -> {
						try {
							((WeatherOffice) owner).publishAlert(ALERT_CHANNEL, orange);
							owner.traceMessage("[TimedDemo] office published ORANGE\n");
						} catch (Exception e) {
							System.err.println("[TimedDemo] orange step failed: " + e);
						}
					}),

				new TestStep(
					CLOCK_URI,
					OFFICE_RIP_URI,
					tAlertGreen,
					owner -> {
						try {
							((WeatherOffice) owner).publishAlert(ALERT_CHANNEL, green);
							owner.traceMessage("[TimedDemo] office published GREEN\n");
						} catch (Exception e) {
							System.err.println("[TimedDemo] green step failed: " + e);
						}
					})
			});
	}

	@Override
	public void deploy() throws Exception
	{
		// Broker
		AbstractComponent.createComponent(Broker.class.getCanonicalName(), new Object[] { 2, 0 });

		// Create the accelerated clock server required by the TestScenario tool.
		TestScenario ts = testScenario();
		long current = System.currentTimeMillis();
		long unixEpochStartTimeInNanos = TimeUnit.MILLISECONDS.toNanos(current + DELAY_TO_START_MS);
		Instant startInstant = Instant.parse(START_INSTANT);
		AbstractComponent.createComponent(
			ClocksServer.class.getCanonicalName(),
			new Object[] { CLOCK_URI, unixEpochStartTimeInNanos, startInstant, ACCELERATION_FACTOR });

		Position2D turbinePos = new Position2D(0.0, 0.0);
		Position2D stationNearPos = new Position2D(1.0, 0.0);
		Position2D stationFarPos = new Position2D(100.0, 0.0);

		// Participants: they must appear in the scenario through their reflection inbound port URI.
		System.out.println("[TimedDemo] participants RIP URIs: "
			+ TURBINE_RIP_URI + ", " + STATION_NEAR_RIP_URI + ", " + STATION_FAR_RIP_URI + ", " + OFFICE_RIP_URI);
		AbstractComponent.createComponent(
			WindTurbine.class.getCanonicalName(),
			new Object[] { TURBINE_RIP_URI, ts, "WT1", turbinePos, 20.0, 5_000L, MeteoAlertI.Level.ORANGE });

		AbstractComponent.createComponent(
			WeatherStation.class.getCanonicalName(),
			new Object[] { STATION_NEAR_RIP_URI, "WS1", stationNearPos });

		AbstractComponent.createComponent(
			WeatherStation.class.getCanonicalName(),
			new Object[] { STATION_FAR_RIP_URI, "WS2", stationFarPos });

		AbstractComponent.createComponent(
			WeatherOffice.class.getCanonicalName(),
			new Object[] { OFFICE_RIP_URI, "WO1" });

		System.out.println("[TimedDemo] scenario contains turbine? " + ts.entityAppearsIn(TURBINE_RIP_URI));
		System.out.println("[TimedDemo] scenario contains stationNear? " + ts.entityAppearsIn(STATION_NEAR_RIP_URI));
		System.out.println("[TimedDemo] scenario contains stationFar? " + ts.entityAppearsIn(STATION_FAR_RIP_URI));
		System.out.println("[TimedDemo] scenario contains office? " + ts.entityAppearsIn(OFFICE_RIP_URI));

		super.deploy();

		// No tracing/logging here to avoid opening multiple trace windows during the timed demo.
	}

	public static void main(String[] args)
	{
		try {
			DemoMeteoTimedTestTool cvm = new DemoMeteoTimedTestTool();
			cvm.startStandardLifeCycle(8000L);
			System.exit(0);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
