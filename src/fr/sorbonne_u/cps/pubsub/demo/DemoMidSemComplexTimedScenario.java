package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.cvm.AbstractCVM;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.components.utils.tests.TestStep;
import fr.sorbonne_u.components.utils.tests.TestStepI;
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
 * It uses the CDC Annexe B tool (ClocksServer + TestScenario/TestStep) and covers:
 * FREE + privileged channels, authorisedUsers regex, quota errors, and all message
 * filter families (properties, comparable/multi-values, time windows).
 *
 * @author Bogdan Styn
 */
public class DemoMidSemComplexTimedScenario extends AbstractCVM
{
	// -------------------------------------------------------------------------
	// Clock configuration (matches the CDC text snippet style)
	// -------------------------------------------------------------------------

	public static final String TEST_CLOCK_URI = "midsem-test-clock";
	protected static final long START_DELAY = 60_000L;
	public static final double ACCELERATION_FACTOR = 60.0;
	protected static final long SCENARIO_OFFSET_SECONDS = 7_200L;

	// -------------------------------------------------------------------------
	// Channels
	// -------------------------------------------------------------------------

	public static final String CH_WIND_FREE = "wind-free";
	public static final String CH_ALERTS_FREE = "alerts-free";
	public static final String CH_ALERTS_STD_PRIVATE = "alerts-std-private";
	public static final String CH_WIND_PREM_PRIVATE = "wind-prem-private";

	// -------------------------------------------------------------------------
	// Participant URIs (must match reflection inbound port URIs)
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

	public static TestScenario buildScenario(Instant startInstant)
	{
		Instant start = startInstant.plusSeconds(SCENARIO_OFFSET_SECONDS);
		Instant end = start.plusSeconds(70);

		Instant tRunnerBootstrap = start.plusSeconds(1);

		Instant tRegisterAll = start.plusSeconds(5);
		Instant tUpgradeStd = start.plusSeconds(8);
		Instant tUpgradePrem = start.plusSeconds(10);
		Instant tCreateStdPriv = start.plusSeconds(12);
		Instant tQuotaStd = start.plusSeconds(13);
		Instant tCreatePremPriv = start.plusSeconds(14);
		Instant tSubNearWind = start.plusSeconds(16);
		Instant tSubNearAlertsPriv = start.plusSeconds(17);
		Instant tSubIntruderAlertsPriv = start.plusSeconds(18);
		Instant tPubNearWindOk = start.plusSeconds(20);
		Instant tPubFarWindReject = start.plusSeconds(22);
		Instant tPubBadMsgFiltered = start.plusSeconds(24);
		Instant tPubOrangePriv = start.plusSeconds(30);
		Instant tPubGreenPrivFiltered = start.plusSeconds(32);
		Instant tPubPremWindPriv = start.plusSeconds(35);

		// Scenario time window for time-based filters.
		Instant acceptFrom = start.plusSeconds(19);
		Instant acceptUntil = start.plusSeconds(31);

		// (beginningMessage, endingMessage, clockURI, startInstant, endInstant, steps)
		return new TestScenario(
			"[MidSemScenario] BEGIN",
			"[MidSemScenario] END",
			TEST_CLOCK_URI,
			start,
			end,
			new TestStepI[] {
				new TestStep(TEST_CLOCK_URI, SCENARIO_RUNNER_URI, tRunnerBootstrap, owner -> safe(owner, () -> {
					owner.logMessage("[MidSemScenario] runner bootstrap step\n");
				})),
				// 1) register everyone as FREE
				new TestStep(TEST_CLOCK_URI, TURBINE_NEAR_FREE_URI, tRegisterAll, owner -> safe(owner, () -> {
					((PluginClient) owner).register(RegistrationClass.FREE);
				})),
				new TestStep(TEST_CLOCK_URI, TURBINE_FAR_FREE_URI, tRegisterAll, owner -> safe(owner, () -> {
					((PluginClient) owner).register(RegistrationClass.FREE);
				})),
				new TestStep(TEST_CLOCK_URI, STATION_NEAR_FREE_URI, tRegisterAll, owner -> safe(owner, () -> {
					((PluginClient) owner).register(RegistrationClass.FREE);
				})),
				new TestStep(TEST_CLOCK_URI, STATION_FAR_FREE_URI, tRegisterAll, owner -> safe(owner, () -> {
					((PluginClient) owner).register(RegistrationClass.FREE);
				})),
				new TestStep(TEST_CLOCK_URI, OFFICE_STANDARD_URI, tRegisterAll, owner -> safe(owner, () -> {
					((PluginClient) owner).register(RegistrationClass.FREE);
				})),
				new TestStep(TEST_CLOCK_URI, OFFICE_PREMIUM_URI, tRegisterAll, owner -> safe(owner, () -> {
					((PluginClient) owner).register(RegistrationClass.FREE);
				})),
				new TestStep(TEST_CLOCK_URI, INTRUDER_FREE_URI, tRegisterAll, owner -> safe(owner, () -> {
					((PluginClient) owner).register(RegistrationClass.FREE);
				})),

				// 2) upgrade service class to use privileged operations
				new TestStep(TEST_CLOCK_URI, OFFICE_STANDARD_URI, tUpgradeStd, owner -> safe(owner, () -> {
					((PluginClient) owner).modifyServiceClass(RegistrationClass.STANDARD);
				})),
				new TestStep(TEST_CLOCK_URI, OFFICE_PREMIUM_URI, tUpgradePrem, owner -> safe(owner, () -> {
					((PluginClient) owner).modifyServiceClass(RegistrationClass.PREMIUM);
				})),

				// 3) create privileged channels
				new TestStep(TEST_CLOCK_URI, OFFICE_STANDARD_URI, tCreateStdPriv, owner -> safe(owner, () -> {
					String regex = "^(" + TURBINE_NEAR_FREE_URI + "|" + TURBINE_FAR_FREE_URI + ")$";
					((PluginClient) owner).createChannel(CH_ALERTS_STD_PRIVATE, regex);
				})),
				new TestStep(TEST_CLOCK_URI, OFFICE_STANDARD_URI, tQuotaStd, owner -> safe(owner, () -> {
					try {
						((PluginClient) owner).createChannel("std-extra-should-fail", ".*");
					} catch (ChannelQuotaExceededException expected) {
						owner.logMessage("[MidSemScenario] quota exceeded as expected\n");
					}
				})),
				new TestStep(TEST_CLOCK_URI, OFFICE_PREMIUM_URI, tCreatePremPriv, owner -> safe(owner, () -> {
					String regex = "^(" + TURBINE_NEAR_FREE_URI + "|" + STATION_NEAR_FREE_URI + ")$";
					((PluginClient) owner).createChannel(CH_WIND_PREM_PRIVATE, regex);
				})),

				// 4) turbine subscriptions using all filter families
				new TestStep(TEST_CLOCK_URI, TURBINE_NEAR_FREE_URI, tSubNearWind, owner -> safe(owner, () -> {
					MessageFilterI f = new MessageFilter(
						new PropertyFilterI[] {
							new PropertyFilter("type", new EqualsValueFilter("wind"))
						},
						new MessageFilterI.PropertiesFilterI[0],
						new BetweenTimeFilter(acceptFrom, acceptUntil));
					((PluginClient) owner).subscribe(CH_WIND_FREE, f);
				})),
				new TestStep(TEST_CLOCK_URI, TURBINE_NEAR_FREE_URI, tSubNearAlertsPriv, owner -> safe(owner, () -> {
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
					try {
						((PluginClient) owner).subscribe(CH_ALERTS_STD_PRIVATE, new MessageFilter(
							new PropertyFilterI[0], new MessageFilterI.PropertiesFilterI[0], new BeforeOrAtTimeFilter(end)));
					} catch (UnauthorisedClientException expected) {
						owner.logMessage("[MidSemScenario] unauthorised subscribe rejected as expected\n");
					}
				})),

				// 5) publications on free channels
				new TestStep(TEST_CLOCK_URI, STATION_NEAR_FREE_URI, tPubNearWindOk, owner -> safe(owner, () -> {
					MessageI m = new Message("wind-near");
					m.putProperty("type", "wind");
					m.putProperty("distance", 1);
					((PluginClient) owner).publish(CH_WIND_FREE, m);
				})),
				new TestStep(TEST_CLOCK_URI, STATION_FAR_FREE_URI, tPubFarWindReject, owner -> safe(owner, () -> {
					MessageI m = new Message("wind-far");
					m.putProperty("type", "wind");
					m.putProperty("distance", 10_000);
					((PluginClient) owner).publish(CH_WIND_FREE, m);
				})),
				new TestStep(TEST_CLOCK_URI, STATION_NEAR_FREE_URI, tPubBadMsgFiltered, owner -> safe(owner, () -> {
					MessageI m = new Message("bad-message");
					m.putProperty("type", "not-wind");
					((PluginClient) owner).publish(CH_WIND_FREE, m);
				})),

				// 6) privileged alert channel publications
				new TestStep(TEST_CLOCK_URI, OFFICE_STANDARD_URI, tPubOrangePriv, owner -> safe(owner, () -> {
					MessageI m = new Message("alert-orange");
					m.putProperty("type", "alert");
					m.putProperty("level", "ORANGE");
					m.putProperty("alertType", "STORM");
					((PluginClient) owner).publish(CH_ALERTS_STD_PRIVATE, m);
				})),
				new TestStep(TEST_CLOCK_URI, OFFICE_STANDARD_URI, tPubGreenPrivFiltered, owner -> safe(owner, () -> {
					MessageI m = new Message("alert-green");
					m.putProperty("type", "alert");
					m.putProperty("level", "GREEN");
					m.putProperty("alertType", "STORM");
					((PluginClient) owner).publish(CH_ALERTS_STD_PRIVATE, m);
				})),

				// 7) premium private wind channel
				new TestStep(TEST_CLOCK_URI, OFFICE_PREMIUM_URI, tPubPremWindPriv, owner -> safe(owner, () -> {
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
		AbstractComponent.createComponent(Broker.class.getCanonicalName(), new Object[] { 2, 0 });

		final long nowMs = System.currentTimeMillis();
		long unixEpochStartTimeInNanos =
			TimeUnit.MILLISECONDS.toNanos(nowMs + START_DELAY);

		// Start instant in the future relative to deployment.
		Instant startInstant = Instant.ofEpochMilli(nowMs)
			.plusSeconds((START_DELAY / 1000L) + 2L);
		AbstractComponent.createComponent(
			ClocksServer.class.getCanonicalName(),
			new Object[] { TEST_CLOCK_URI, unixEpochStartTimeInNanos, startInstant, ACCELERATION_FACTOR });

		TestScenario ts = buildScenario(startInstant);

		AbstractComponent.createComponent(PluginClient.class.getCanonicalName(), new Object[] { TURBINE_NEAR_FREE_URI, 1, 1 });
		AbstractComponent.createComponent(PluginClient.class.getCanonicalName(), new Object[] { TURBINE_FAR_FREE_URI, 1, 1 });
		AbstractComponent.createComponent(PluginClient.class.getCanonicalName(), new Object[] { STATION_NEAR_FREE_URI, 1, 1 });
		AbstractComponent.createComponent(PluginClient.class.getCanonicalName(), new Object[] { STATION_FAR_FREE_URI, 1, 1 });
		AbstractComponent.createComponent(PluginClient.class.getCanonicalName(), new Object[] { OFFICE_STANDARD_URI, 1, 1 });
		AbstractComponent.createComponent(PluginClient.class.getCanonicalName(), new Object[] { OFFICE_PREMIUM_URI, 1, 1 });
		AbstractComponent.createComponent(PluginClient.class.getCanonicalName(), new Object[] { INTRUDER_FREE_URI, 1, 1 });

		// A dedicated runner schedules all steps.
		AbstractComponent.createComponent(ScenarioRunner.class.getCanonicalName(), new Object[] { SCENARIO_RUNNER_URI, ts, 1, 1 });

		super.deploy();

		// Logging/tracing only.
		this.toggleTracing(TURBINE_NEAR_FREE_URI);
		this.toggleTracing(OFFICE_STANDARD_URI);
		this.toggleTracing(OFFICE_PREMIUM_URI);
		this.toggleTracing(INTRUDER_FREE_URI);
		this.toggleTracing(TURBINE_NEAR_FREE_URI);
		this.toggleTracing(OFFICE_STANDARD_URI);
		this.toggleTracing(OFFICE_PREMIUM_URI);
		this.toggleTracing(INTRUDER_FREE_URI);
	}

	/**
	 * Small helper for demo readability: constrain one property to be in a set.
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
		cvm.startStandardLifeCycle(180_000L);
			System.exit(0);
		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}
	}
}
