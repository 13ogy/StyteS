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
import fr.sorbonne_u.cps.pubsub.base.components.PluginClient;
import fr.sorbonne_u.cps.pubsub.exceptions.ChannelQuotaExceededException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.PropertyFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;
import fr.sorbonne_u.cps.pubsub.messages.Message;
import fr.sorbonne_u.cps.pubsub.messages.MessageFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.AfterOrAtTimeFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.BeforeOrAtTimeFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.BetweenTimeFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.ComparableValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.EqualsValueFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.PropertyFilter;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Mid-semester timed integration scenario.
 *
 * It uses the ClocksServer + TestScenario/TestStep and covers:
 * FREE + privileged channels, authorisedUsers regex, quota errors, and all message
 * filter families - properties, comparable/multi-values and time windows
 *
 * @author Bogdan Styn
 */
public class DemoMidSemComplexTimedScenario extends AbstractCVM
{
	// -------------------------------------------------------------------------
	// Clock configuration
	// -------------------------------------------------------------------------

	public static final String TEST_CLOCK_URI = "midsem-test-clock";
	protected static final long START_DELAY = 3_000L;
	public static final double ACCELERATION_FACTOR = 1.0;
	protected static final long SCENARIO_OFFSET_SECONDS = 3L;

	// -------------------------------------------------------------------------
	// Channels
	// -------------------------------------------------------------------------

	public static final String CH_WIND_FREE = "wind-free";
	public static final String CH_ALERTS_FREE = "alerts-free";
	public static final String CH_ALERTS_STD_PRIVATE = "alerts-std-private";
	public static final String CH_WIND_PREM_PRIVATE = "wind-prem-private";

	// -------------------------------------------------------------------------
	// Participant URIs matching reflection inbound port URIs
	// -------------------------------------------------------------------------

	public static final String TURBINE_NEAR_FREE_URI = "turbine-near-free";
	public static final String TURBINE_FAR_FREE_URI = "turbine-far-free";
	public static final String STATION_NEAR_FREE_URI = "station-near-free";
	public static final String STATION_FAR_FREE_URI = "station-far-free";
	public static final String OFFICE_STANDARD_URI = "office-standard";
	public static final String OFFICE_PREMIUM_URI = "office-premium";
	public static final String INTRUDER_FREE_URI = "intruder-free";
	public static final String SCENARIO_RUNNER_URI = "scenario-runner";

	public DemoMidSemComplexTimedScenario() throws Exception
	{
		super();
	}

	// -------------------------------------------------------------------------
	// Scenario
	// -------------------------------------------------------------------------

	private static void logStep(fr.sorbonne_u.components.ComponentI owner, String label)
	{
		String msg = "[MidSemScenario] " + label + "\n";
		System.out.print(msg);
		((AbstractComponent) owner).logMessage(msg);
	}

	public static TestScenario buildScenario(Instant startInstant)
	{
		Instant start = startInstant.plusSeconds(SCENARIO_OFFSET_SECONDS);
		Instant end = start.plusSeconds(25);

		Instant tRunnerBootstrap = start.plusSeconds(1);
		Instant tRegisterAll = start.plusSeconds(3);
		Instant tUpgradeStd = start.plusSeconds(6);
		Instant tUpgradePrem = start.plusSeconds(6);
		Instant tCreateStdPriv = start.plusSeconds(8);
		Instant tQuotaStd = start.plusSeconds(9);
		Instant tCreatePremPriv = start.plusSeconds(10);
		Instant tSubNearWind = start.plusSeconds(12);
		Instant tSubNearAlertsPriv = start.plusSeconds(13);
		Instant tSubIntruderAlertsPriv = start.plusSeconds(14);
		Instant tPubNearWindOk = start.plusSeconds(17);
		Instant tPubFarWindReject = start.plusSeconds(18);
		Instant tPubBadMsgFiltered = start.plusSeconds(19);
		Instant tPubOrangePriv = start.plusSeconds(21);
		Instant tPubGreenPrivFiltered = start.plusSeconds(22);
		Instant tPubPremWindPriv = start.plusSeconds(23);

		// Scenario time window for time-based filters.
		Instant acceptFrom = start.plusSeconds(15);
		Instant acceptUntil = start.plusSeconds(24);

		// (beginningMessage, endingMessage, clockURI, startInstant, endInstant, steps)
		return new TestScenario(
			"[MidSemScenario] BEGIN",
			"[MidSemScenario] END",
			TEST_CLOCK_URI,
			start,
			end,
			new TestStepI[] {
				new TestStep(TEST_CLOCK_URI, SCENARIO_RUNNER_URI, tRunnerBootstrap, owner -> safe(owner, () -> {
					logStep(owner, "bootstrap");
					owner.logMessage("[MidSemScenario] runner bootstrap step\n");
				})),
				// 1) register everyone as FREE
				new TestStep(TEST_CLOCK_URI, TURBINE_NEAR_FREE_URI, tRegisterAll, owner -> safe(owner, () -> {
					logStep(owner, "register FREE turbine-near");
					((PluginClient) owner).register(RegistrationClass.FREE);
				})),
				new TestStep(TEST_CLOCK_URI, TURBINE_FAR_FREE_URI, tRegisterAll, owner -> safe(owner, () -> {
					logStep(owner, "register FREE turbine-far");
					((PluginClient) owner).register(RegistrationClass.FREE);
				})),
				new TestStep(TEST_CLOCK_URI, STATION_NEAR_FREE_URI, tRegisterAll, owner -> safe(owner, () -> {
					logStep(owner, "register FREE station-near");
					((PluginClient) owner).register(RegistrationClass.FREE);
				})),
				new TestStep(TEST_CLOCK_URI, STATION_FAR_FREE_URI, tRegisterAll, owner -> safe(owner, () -> {
					logStep(owner, "register FREE station-far");
					((PluginClient) owner).register(RegistrationClass.FREE);
				})),
				new TestStep(TEST_CLOCK_URI, OFFICE_STANDARD_URI, tRegisterAll, owner -> safe(owner, () -> {
					logStep(owner, "register FREE office-standard");
					((PluginClient) owner).register(RegistrationClass.FREE);
				})),
				new TestStep(TEST_CLOCK_URI, OFFICE_PREMIUM_URI, tRegisterAll, owner -> safe(owner, () -> {
					logStep(owner, "register FREE office-premium");
					((PluginClient) owner).register(RegistrationClass.FREE);
				})),
				new TestStep(TEST_CLOCK_URI, INTRUDER_FREE_URI, tRegisterAll, owner -> safe(owner, () -> {
					logStep(owner, "register FREE intruder");
					((PluginClient) owner).register(RegistrationClass.FREE);
				})),

				// 2) upgrade service class to use privileged operations
				new TestStep(TEST_CLOCK_URI, OFFICE_STANDARD_URI, tUpgradeStd, owner -> safe(owner, () -> {
					logStep(owner, "upgrade office-standard -> STANDARD");
					((PluginClient) owner).modifyServiceClass(RegistrationClass.STANDARD);
				})),
				new TestStep(TEST_CLOCK_URI, OFFICE_PREMIUM_URI, tUpgradePrem, owner -> safe(owner, () -> {
					logStep(owner, "upgrade office-premium -> PREMIUM");
					((PluginClient) owner).modifyServiceClass(RegistrationClass.PREMIUM);
				})),

				// 3) create privileged channels
				new TestStep(TEST_CLOCK_URI, OFFICE_STANDARD_URI, tCreateStdPriv, owner -> safe(owner, () -> {
					logStep(owner, "createChannel alerts-std-private (authorised turbines)");
					String regex = "^(" + TURBINE_NEAR_FREE_URI + "|" + TURBINE_FAR_FREE_URI + ")$";
					((PluginClient) owner).createChannel(CH_ALERTS_STD_PRIVATE, regex);
				})),
				new TestStep(TEST_CLOCK_URI, OFFICE_STANDARD_URI, tQuotaStd, owner -> safe(owner, () -> {
					logStep(owner, "createChannel std-extra-should-fail (quota)");
					try {
						((PluginClient) owner).createChannel("std-extra-should-fail", ".*");
					} catch (ChannelQuotaExceededException expected) {
						owner.logMessage("[MidSemScenario] quota exceeded as expected\n");
					}
				})),
				new TestStep(TEST_CLOCK_URI, OFFICE_PREMIUM_URI, tCreatePremPriv, owner -> safe(owner, () -> {
					logStep(owner, "createChannel wind-prem-private (authorised turbine-near + station-near)");
					String regex = "^(" + TURBINE_NEAR_FREE_URI + "|" + STATION_NEAR_FREE_URI + ")$";
					((PluginClient) owner).createChannel(CH_WIND_PREM_PRIVATE, regex);
				})),

				// 4) turbine subscriptions using all filter families
				new TestStep(TEST_CLOCK_URI, TURBINE_NEAR_FREE_URI, tSubNearWind, owner -> safe(owner, () -> {
					logStep(owner, "subscribe turbine-near to wind-free (type==wind + time window)");
					MessageFilterI f = new MessageFilter(
						new PropertyFilterI[] {
							new PropertyFilter("type", new EqualsValueFilter("wind"))
						},
						new MessageFilterI.PropertiesFilterI[0],
						new BetweenTimeFilter(acceptFrom, acceptUntil));
					((PluginClient) owner).subscribe(CH_WIND_FREE, f);
				})),
				new TestStep(TEST_CLOCK_URI, TURBINE_NEAR_FREE_URI, tSubNearAlertsPriv, owner -> safe(owner, () -> {
					logStep(owner, "subscribe turbine-near to alerts-std-private (alert filters)");
					MessageFilterI f = new MessageFilter(
						new PropertyFilterI[] {
							new PropertyFilter("type", new EqualsValueFilter("alert")),
							new PropertyFilter("level", ComparableValueFilter.greaterOrEqual("ORANGE")),
							new PropertyFilter("alertType", new MultiValuesFilterOnOneProperty(
								"STORM", "FLOOD"))
						},
						new MessageFilterI.PropertiesFilterI[0],
						new AfterOrAtTimeFilter(start));
					((PluginClient) owner).subscribe(CH_ALERTS_STD_PRIVATE, f);
				})),
				new TestStep(TEST_CLOCK_URI, INTRUDER_FREE_URI, tSubIntruderAlertsPriv, owner -> safe(owner, () -> {
					logStep(owner, "intruder subscribe to alerts-std-private (should be rejected)");
					try {
						((PluginClient) owner).subscribe(CH_ALERTS_STD_PRIVATE, new MessageFilter(
							new PropertyFilterI[0], new MessageFilterI.PropertiesFilterI[0], new BeforeOrAtTimeFilter(end)));
					} catch (UnauthorisedClientException expected) {
						owner.logMessage("[MidSemScenario] unauthorised subscribe rejected as expected\n");
					}
				})),

				// 5) publications on free channels
				new TestStep(TEST_CLOCK_URI, STATION_NEAR_FREE_URI, tPubNearWindOk, owner -> safe(owner, () -> {
					logStep(owner, "publish station-near wind-free (wind-near)");
					MessageI m = new Message("wind-near");
					m.putProperty("type", "wind");
					m.putProperty("distance", 1);
					((PluginClient) owner).publish(CH_WIND_FREE, m);
				})),
				new TestStep(TEST_CLOCK_URI, STATION_FAR_FREE_URI, tPubFarWindReject, owner -> safe(owner, () -> {
					logStep(owner, "publish station-far wind-free (wind-far)");
					MessageI m = new Message("wind-far");
					m.putProperty("type", "wind");
					m.putProperty("distance", 10_000);
					((PluginClient) owner).publish(CH_WIND_FREE, m);
				})),
				new TestStep(TEST_CLOCK_URI, STATION_NEAR_FREE_URI, tPubBadMsgFiltered, owner -> safe(owner, () -> {
					logStep(owner, "publish station-near wind-free (bad-message filtered)");
					MessageI m = new Message("bad-message");
					m.putProperty("type", "not-wind");
					((PluginClient) owner).publish(CH_WIND_FREE, m);
				})),

				// 6) privileged alert channel publications
				new TestStep(TEST_CLOCK_URI, OFFICE_STANDARD_URI, tPubOrangePriv, owner -> safe(owner, () -> {
					logStep(owner, "publish office-standard alerts-std-private (alert-orange)");
					MessageI m = new Message("alert-orange");
					m.putProperty("type", "alert");
					m.putProperty("level", "ORANGE");
					m.putProperty("alertType", "STORM");
					((PluginClient) owner).publish(CH_ALERTS_STD_PRIVATE, m);
				})),
				new TestStep(TEST_CLOCK_URI, OFFICE_STANDARD_URI, tPubGreenPrivFiltered, owner -> safe(owner, () -> {
					logStep(owner, "publish office-standard alerts-std-private (alert-green filtered)");
					MessageI m = new Message("alert-green");
					m.putProperty("type", "alert");
					m.putProperty("level", "GREEN");
					m.putProperty("alertType", "STORM");
					((PluginClient) owner).publish(CH_ALERTS_STD_PRIVATE, m);
				})),

				// 7) premium private wind channel
				new TestStep(TEST_CLOCK_URI, OFFICE_PREMIUM_URI, tPubPremWindPriv, owner -> safe(owner, () -> {
					logStep(owner, "publish office-premium wind-prem-private (wind-premium)");
					MessageI m = new Message("wind-premium");
					m.putProperty("type", "wind");
					m.putProperty("premium", true);
					((PluginClient) owner).publish(CH_WIND_PREM_PRIVATE, m);
				}))
			});
	}

	@FunctionalInterface
	private interface UnsafeRunnable { void run() throws Exception; }

	private static void safe(fr.sorbonne_u.components.ComponentI owner, UnsafeRunnable r)
	{
		try {
			r.run();
		} catch (Exception e) {
			((AbstractComponent) owner).logMessage(
				"[MidSemScenario] step failed: " + e.getClass().getSimpleName() + " - " + e.getMessage() + "\n");
		}
	}

	@Override
	public void deploy() throws Exception
	{
		TestScenario.VERBOSE = true;
		TestScenario.DEBUG = true;

		AbstractComponent.createComponent(Broker.class.getCanonicalName(), new Object[] { 2, 0, 3, 2, 5 });

		final long nowMs = System.currentTimeMillis();
		// Start instant in the future relative to deployment.
		Instant startInstant = Instant.ofEpochMilli(nowMs)
			.plusSeconds((START_DELAY / 1000L) + 2L);
		long unixEpochStartTimeInNanos =
			TimeUnit.MILLISECONDS.toNanos(startInstant.toEpochMilli());
		AbstractComponent.createComponent(
			ClocksServer.class.getCanonicalName(),
			new Object[] { TEST_CLOCK_URI, unixEpochStartTimeInNanos, startInstant, ACCELERATION_FACTOR });

		TestScenario ts = buildScenario(startInstant);

		AbstractComponent.createComponent(WindTurbine.class.getCanonicalName(), new Object[] { TURBINE_NEAR_FREE_URI, ts, 1, 1 });
		AbstractComponent.createComponent(WindTurbine.class.getCanonicalName(), new Object[] { TURBINE_FAR_FREE_URI, ts, 1, 1 });
		AbstractComponent.createComponent(WeatherStation.class.getCanonicalName(), new Object[] { STATION_NEAR_FREE_URI, ts, 1, 1 });
		AbstractComponent.createComponent(WeatherStation.class.getCanonicalName(), new Object[] { STATION_FAR_FREE_URI, ts, 1, 1 });
		AbstractComponent.createComponent(WeatherOffice.class.getCanonicalName(), new Object[] { OFFICE_STANDARD_URI, ts, 1, 1 });
		AbstractComponent.createComponent(WeatherOffice.class.getCanonicalName(), new Object[] { OFFICE_PREMIUM_URI, ts, 1, 1 });
		AbstractComponent.createComponent(ScenarioPluginClient.class.getCanonicalName(), new Object[] { INTRUDER_FREE_URI, ts, 1, 1 });
		AbstractComponent.createComponent(ScenarioRunner.class.getCanonicalName(), new Object[] { SCENARIO_RUNNER_URI, ts, 1, 1 });

		super.deploy();

		this.toggleTracing(TURBINE_NEAR_FREE_URI);
		this.toggleTracing(OFFICE_STANDARD_URI);
		this.toggleTracing(OFFICE_PREMIUM_URI);
		this.toggleTracing(INTRUDER_FREE_URI);
	}

	/**
	 * Small helper for constraining one property to be in a set.
	 */
	private static final class MultiValuesFilterOnOneProperty
		implements fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.ValueFilterI
	{
		private static final long serialVersionUID = 1L;
		private final String[] accepted;

		private MultiValuesFilterOnOneProperty(String... accepted)
		{
			this.accepted = accepted.clone();
		}

		@Override
		public boolean match(java.io.Serializable value)
		{
			if (value == null) return false;
			String s = value.toString();
			for (String a : accepted) {
				if (a.equals(s)) return true;
			}
			return false;
		}
	}

	public static void main(String[] args)
	{
		try {
			DemoMidSemComplexTimedScenario cvm = new DemoMidSemComplexTimedScenario();
		// START_DELAY + scenario execution + a margin.
		cvm.startStandardLifeCycle(35_000L);
			System.exit(0);
		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}
	}
}
