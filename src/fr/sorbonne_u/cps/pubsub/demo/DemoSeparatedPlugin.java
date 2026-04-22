package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.cvm.AbstractCVM;
import fr.sorbonne_u.components.exceptions.ComponentShutdownException;
import fr.sorbonne_u.components.exceptions.ComponentStartException;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.components.utils.tests.TestStep;
import fr.sorbonne_u.components.utils.tests.TestStepI;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
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
import fr.sorbonne_u.cps.pubsub.plugins.ClientPrivilegedPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientPublicationPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientSubscriptionPlugin;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

import java.io.Serializable;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Mid-semester timed integration scenario — tests the pub/sub SYSTEM only,
 * independently of the weather application.
 *
 * Structure follows the BCM4Java rules:
 * - Components are generic pub/sub clients (not WindTurbine/WeatherStation)
 * - Registration happens in each component's execute(), NOT in scenario steps
 * - Scenario steps only contain domain actions (subscribe, publish, createChannel)
 * - Each component receives the scenario via its constructor
 * - executeTestScenario is called inside execute()
 *
 * @author Bogdan Styn
 */
public class DemoSeparatedPlugin extends AbstractCVM
{
    // -------------------------------------------------------------------------
    // Clock configuration
    // -------------------------------------------------------------------------

    public static final String  TEST_CLOCK_URI       = "midsem-test-clock";
    public static final String  START_INSTANT_STR    = "2026-04-22T09:00:00.00Z";
    public static final double  ACCELERATION_FACTOR  = 1.0; // 1 virtual minute = 1 real second
    protected static final long DELAY_TO_START       = 3L;

    // -------------------------------------------------------------------------
    // Channels
    // -------------------------------------------------------------------------

    public static final String CH_WIND_FREE        = "channel0"; // free channel
    public static final String CH_ALERTS_FREE      = "channel1"; // free channel
    public static final String CH_ALERTS_STD_PRIV  = "alerts-std-private";
    public static final String CH_WIND_PREM_PRIV   = "wind-prem-private";

    // -------------------------------------------------------------------------
    // Component URIs — imposed via constructor, used in scenario steps
    // -------------------------------------------------------------------------

    public static final String TURBINE_NEAR_URI  = "turbine-near";
    public static final String TURBINE_FAR_URI   = "turbine-far";
    public static final String STATION_NEAR_URI  = "station-near";
    public static final String STATION_FAR_URI   = "station-far";
    public static final String OFFICE_STD_URI    = "office-standard";
    public static final String OFFICE_PREM_URI   = "office-premium";
    public static final String INTRUDER_URI      = "intruder";
    public static final String SCENARIO_RUNNER_URI = "scenario-runner";

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public DemoSeparatedPlugin() throws Exception { super(); }



    // =========================================================================
    // Scenario definition — only domain actions, NO registration
    // =========================================================================

    /**
     * Builds the test scenario. Registration is done in each component's
     * execute() before executeTestScenario is called, so by the time
     * the first scenario step fires, all components are already registered.
     */
    public static TestScenario buildScenario() throws Exception {
        Instant startInstant = Instant.parse(START_INSTANT_STR);
        Instant endInstant   = startInstant.plusSeconds(25);

        //Instant tRunnerBootstrap = startInstant.plusSeconds(1);
        Instant tUpgradeStd      = startInstant.plusSeconds(6);
        Instant tUpgradePrem     = startInstant.plusSeconds(6);
        Instant tCreateStdPriv   = startInstant.plusSeconds(8);
        Instant tQuotaExceeded   = startInstant.plusSeconds(9);
        Instant tCreatePremPriv  = startInstant.plusSeconds(10);
        Instant tSubNearWind     = startInstant.plusSeconds(12);
        Instant tSubNearPriv     = startInstant.plusSeconds(13);
        Instant tSubIntruder     = startInstant.plusSeconds(14);
        Instant tPubWindOk       = startInstant.plusSeconds(17);
        Instant tPubWindFiltered = startInstant.plusSeconds(18);
        Instant tPubBadMsg       = startInstant.plusSeconds(19);
        Instant tPubOrangeAlert  = startInstant.plusSeconds(21);
        Instant tPubGreenAlert   = startInstant.plusSeconds(22);
        Instant tPubPremWind     = startInstant.plusSeconds(23);

        // Time window used by BetweenTimeFilter on wind-free channel
        Instant acceptFrom  = Instant.now().plusSeconds(15);
        Instant acceptUntil = Instant.now().plusSeconds(50);

        return new TestScenario(
                "[MidSemScenario] BEGIN",
                "[MidSemScenario] END",
                TEST_CLOCK_URI,
                startInstant,
                endInstant,
                new TestStepI[] {

                        // ------------------------------------------------------------------
                        // 1. Upgrade service class (from FREE registered in execute())
                        // ------------------------------------------------------------------
                        new TestStep(TEST_CLOCK_URI, OFFICE_STD_URI, tUpgradeStd, owner -> {
                            try {
                                ((PrivilegedClient) owner).modifyServiceClass(RegistrationClass.STANDARD);
                                ((PrivilegedClient) owner).traceMessage("upgraded to STANDARD\n");
                            } catch (Exception e) {
                                ((PrivilegedClient) owner).logMessage("upgrade STANDARD failed: " + e + "\n");
                            }
                        }),
                        new TestStep(TEST_CLOCK_URI, OFFICE_PREM_URI, tUpgradePrem, owner -> {
                            try {
                                ((PrivilegedClient) owner).modifyServiceClass(RegistrationClass.PREMIUM);
                                ((PrivilegedClient) owner).traceMessage("upgraded to PREMIUM\n");
                            } catch (Exception e) {
                                ((PrivilegedClient) owner).logMessage("upgrade PREMIUM failed: " + e + "\n");
                            }
                        }),

                        // ------------------------------------------------------------------
                        // 2. Create privileged channels
                        // ------------------------------------------------------------------
                        new TestStep(TEST_CLOCK_URI, OFFICE_STD_URI, tCreateStdPriv, owner -> {
                            try {
                                String regex = "^(" + TURBINE_NEAR_URI + "-reg|" + STATION_NEAR_URI + "-reg)$";
                                ((PrivilegedClient) owner).createChannel(CH_ALERTS_STD_PRIV, regex);
                                ((PrivilegedClient) owner).traceMessage("created " + CH_ALERTS_STD_PRIV + "\n");
                            } catch (Exception e) {
                                ((PrivilegedClient) owner).logMessage("createChannel failed: " + e + "\n");
                            }
                        }),

                        // Quota test — STANDARD only allows 2 channels; trying a 3rd must fail
                        new TestStep(TEST_CLOCK_URI, OFFICE_STD_URI, tQuotaExceeded, owner -> {
                            try {
                                ((PrivilegedClient) owner).createChannel("extra-should-fail", ".*");
                                ((PrivilegedClient) owner).logMessage("ERROR: quota not enforced!\n");
                            } catch (ChannelQuotaExceededException expected) {
                                assert true : "quota exceeded as expected";
                                ((PrivilegedClient) owner).traceMessage("quota exceeded as expected ✓\n");
                            } catch (Exception e) {
                                ((PrivilegedClient) owner).logMessage("unexpected exception: " + e + "\n");
                            }
                        }),

                        new TestStep(TEST_CLOCK_URI, OFFICE_PREM_URI, tCreatePremPriv, owner -> {
                            try {
                                String regex = "^(" + TURBINE_NEAR_URI + "-reg|" + STATION_NEAR_URI + "-reg)$";
                                ((PrivilegedClient) owner).createChannel(CH_WIND_PREM_PRIV, regex);
                                ((PrivilegedClient) owner).traceMessage("created " + CH_WIND_PREM_PRIV + "\n");
                            } catch (Exception e) {
                                ((PrivilegedClient) owner).logMessage("createChannel premium failed: " + e + "\n");
                            }
                        }),

                        // ------------------------------------------------------------------
                        // 3. Subscriptions with various filter families
                        // ------------------------------------------------------------------

                        // Turbine-near subscribes to wind-free with time window filter
                        new TestStep(TEST_CLOCK_URI, TURBINE_NEAR_URI, tSubNearWind, owner -> {
                            try {
                                MessageFilterI f = new MessageFilter(
                                        new PropertyFilterI[] {
                                                new PropertyFilter("type", new EqualsValueFilter("wind"))
                                        },
                                        new MessageFilterI.PropertiesFilterI[0],
                                        new BetweenTimeFilter(acceptFrom, acceptUntil));
                                ((SubscriberClient) owner).subscribe(CH_WIND_FREE, f);
                                ((SubscriberClient) owner).traceMessage("subscribed to wind-free ✓\n");
                            } catch (Exception e) {
                                ((SubscriberClient) owner).logMessage("subscribe wind failed: " + e + "\n");
                            }
                        }),

                        // Turbine-near subscribes to privileged alerts channel
                        new TestStep(TEST_CLOCK_URI, TURBINE_NEAR_URI, tSubNearPriv, owner -> {
                            try {
                                MessageFilterI f = new MessageFilter(
                                        new PropertyFilterI[] {
                                                new PropertyFilter("type",      new EqualsValueFilter("alert")),
                                                new PropertyFilter("level",     ComparableValueFilter.greaterOrEqual("ORANGE")),
                                                new PropertyFilter("alertType", new MultiValuesFilter("STORM", "FLOOD"))
                                        },
                                        new MessageFilterI.PropertiesFilterI[0],
                                        new AfterOrAtTimeFilter(Instant.parse(START_INSTANT_STR)));
                                ((SubscriberClient) owner).subscribe(CH_ALERTS_STD_PRIV, f);
                                ((SubscriberClient) owner).traceMessage("subscribed to alerts-priv ✓\n");
                            } catch (Exception e) {
                                ((SubscriberClient) owner).logMessage("subscribe alerts failed: " + e + "\n");
                            }
                        }),

                        // Intruder tries to subscribe to privileged channel — must be rejected
                        new TestStep(TEST_CLOCK_URI, INTRUDER_URI, tSubIntruder, owner -> {
                            try {
                                MessageFilterI f = new MessageFilter(
                                        new PropertyFilterI[0],
                                        new MessageFilterI.PropertiesFilterI[0],
                                        new BeforeOrAtTimeFilter(endInstant));
                                ((SubscriberClient) owner).subscribe(CH_ALERTS_STD_PRIV, f);
                                ((SubscriberClient) owner).logMessage("ERROR: intruder should have been rejected!\n");
                            } catch (RuntimeException e) {
                                Throwable cause = e.getCause() != null ? e.getCause().getCause() : null;
                                if (cause instanceof UnauthorisedClientException) {
                                    assert true : "unauthorised subscribe rejected as expected";
                                    ((SubscriberClient) owner).traceMessage("intruder rejected as expected ✓\n");
                                } else {
                                    ((SubscriberClient) owner).logMessage("unexpected: " + e + "\n");
                                }
                            } catch (Exception e) {
                                ((SubscriberClient) owner).logMessage("unexpected: " + e + "\n");
                            }
                        }),

                        // ------------------------------------------------------------------
                        // 4. Publications on free channels
                        // ------------------------------------------------------------------

                        // station-near publishes wind — turbine-near SHOULD receive it
                        // (type==wind, within time window)
                        new TestStep(TEST_CLOCK_URI, STATION_NEAR_URI, tPubWindOk, owner -> {
                            try {
                                MessageI m = new Message("wind-near");
                                m.putProperty("type",     "wind");
                                m.putProperty("distance", 1);
                                ((PublisherClient) owner).publish(CH_WIND_FREE, m);
                                ((PublisherClient) owner).traceMessage("published wind-near ✓\n");
                            } catch (Exception e) {
                                ((PublisherClient) owner).logMessage("publish wind-near failed: " + e + "\n");
                            }
                        }),

                        // station-far publishes wind — turbine-near should NOT receive
                        // (outside time window — before acceptFrom)
                        new TestStep(TEST_CLOCK_URI, STATION_FAR_URI, tPubWindFiltered, owner -> {
                            try {
                                MessageI m = new Message("wind-far");
                                m.putProperty("type",     "wind");
                                m.putProperty("distance", 10_000);
                                ((PublisherClient) owner).publish(CH_WIND_FREE, m);
                                ((PublisherClient) owner).traceMessage("published wind-far (should be filtered by distance)\n");
                            } catch (Exception e) {
                                ((PublisherClient) owner).logMessage("publish wind-far failed: " + e + "\n");
                            }
                        }),

                        // station-near publishes wrong type — filtered by type filter
                        new TestStep(TEST_CLOCK_URI, STATION_NEAR_URI, tPubBadMsg, owner -> {
                            try {
                                MessageI m = new Message("bad-type-msg");
                                m.putProperty("type", "not-wind");
                                ((PublisherClient) owner).publish(CH_WIND_FREE, m);
                                ((PublisherClient) owner).traceMessage("published bad-type (should be filtered)\n");
                            } catch (Exception e) {
                                ((PublisherClient) owner).logMessage("publish bad-type failed: " + e + "\n");
                            }
                        }),

                        // ------------------------------------------------------------------
                        // 5. Publications on privileged channels
                        // ------------------------------------------------------------------

                        // office-std publishes ORANGE alert — turbine-near SHOULD receive it
                        new TestStep(TEST_CLOCK_URI, OFFICE_STD_URI, tPubOrangeAlert, owner -> {
                            try {
                                MessageI m = new Message("alert-orange");
                                m.putProperty("type",      "alert");
                                m.putProperty("level",     "ORANGE");
                                m.putProperty("alertType", "STORM");
                                ((PrivilegedClient) owner).publish(CH_ALERTS_STD_PRIV, m);
                                ((PrivilegedClient) owner).traceMessage("published ORANGE alert ✓\n");
                            } catch (Exception e) {
                                ((PrivilegedClient) owner).logMessage("publish orange failed: " + e + "\n");
                            }
                        }),

                        // office-std publishes GREEN alert — filtered (level < ORANGE)
                        new TestStep(TEST_CLOCK_URI, OFFICE_STD_URI, tPubGreenAlert, owner -> {
                            try {
                                MessageI m = new Message("alert-green");
                                m.putProperty("type",      "alert");
                                m.putProperty("level",     "GREEN");
                                m.putProperty("alertType", "STORM");
                                ((PrivilegedClient) owner).publish(CH_ALERTS_STD_PRIV, m);
                                ((PrivilegedClient) owner).traceMessage("published GREEN alert (should be filtered)\n");
                            } catch (Exception e) {
                                ((PrivilegedClient) owner).logMessage("publish green failed: " + e + "\n");
                            }
                        }),

                        // office-prem publishes on premium private channel
                        new TestStep(TEST_CLOCK_URI, OFFICE_PREM_URI, tPubPremWind, owner -> {
                            try {
                                MessageI m = new Message("wind-premium");
                                m.putProperty("type",    "wind");
                                m.putProperty("premium", true);
                                ((PrivilegedClient) owner).publish(CH_WIND_PREM_PRIV, m);
                                ((PrivilegedClient) owner).traceMessage("published premium wind ✓\n");
                            } catch (Exception e) {
                                ((PrivilegedClient) owner).logMessage("publish premium failed: " + e + "\n");
                            }
                        }),
                }
        );
    }

    // =========================================================================
    // CVM deploy — creates components and passes them the scenario
    // =========================================================================

    @Override
    public void deploy() throws Exception {
        TestScenario.VERBOSE = true;
        TestScenario.DEBUG   = true;

        // build scenario
        TestScenario ts = buildScenario();

        // Create Broker
        AbstractComponent.createComponent(
                Broker.class.getCanonicalName(),
                new Object[] { 2, 0, 3, 2, 5 });

        // Create ClocksServer
        long current = System.currentTimeMillis();
        long unixEpochStartTimeInNanos =
                TimeUnit.MILLISECONDS.toNanos(current + DELAY_TO_START);
        Instant startInstant = Instant.parse(START_INSTANT_STR);
        AbstractComponent.createComponent(
                ClocksServer.class.getCanonicalName(),
                new Object[] {
                        TEST_CLOCK_URI,
                        unixEpochStartTimeInNanos,
                        startInstant,
                        ACCELERATION_FACTOR
                });

        //  Create participants — each receives its URI + the scenario
        //   URI is imposed via constructor → used as reflectionInboundPortURI

        // Turbines — SubscriberClient (register FREE, subscribe only)
        AbstractComponent.createComponent(
                SubscriberClient.class.getCanonicalName(),
                new Object[] { TURBINE_NEAR_URI, ts, RegistrationClass.FREE });

        AbstractComponent.createComponent(
                SubscriberClient.class.getCanonicalName(),
                new Object[] { TURBINE_FAR_URI, ts, RegistrationClass.FREE });

        // Intruder — SubscriberClient (register FREE, will be rejected)
        AbstractComponent.createComponent(
                SubscriberClient.class.getCanonicalName(),
                new Object[] { INTRUDER_URI, ts, RegistrationClass.FREE });

        // Stations — PublisherClient (register FREE, publish only)
        AbstractComponent.createComponent(
                PublisherClient.class.getCanonicalName(),
                new Object[] { STATION_NEAR_URI, ts, RegistrationClass.FREE });

        AbstractComponent.createComponent(
                PublisherClient.class.getCanonicalName(),
                new Object[] { STATION_FAR_URI, ts, RegistrationClass.FREE });

        // Offices — PrivilegedClient (register FREE, then upgrade + createChannel)
        AbstractComponent.createComponent(
                PrivilegedClient.class.getCanonicalName(),
                new Object[] { OFFICE_STD_URI, ts, RegistrationClass.FREE });

        AbstractComponent.createComponent(
                PrivilegedClient.class.getCanonicalName(),
                new Object[] { OFFICE_PREM_URI, ts, RegistrationClass.FREE });

        super.deploy();

        /* //Enable tracing for interesting participants
        this.toggleTracing(TURBINE_NEAR_URI);
        this.toggleTracing(OFFICE_STD_URI);
        this.toggleTracing(OFFICE_PREM_URI);
        this.toggleTracing(INTRUDER_URI);*/
    }

    // =========================================================================
    // Helper — filter accepting a property value from a fixed set
    // =========================================================================

    /**
     * Filter that accepts a value if it belongs to a predefined set of strings.
     * Extracted as a proper named class (not anonymous) for clarity.
     */
    public static final class MultiValuesFilter
            implements MessageFilterI.ValueFilterI
    {
        private static final long serialVersionUID = 1L;
        private final String[] accepted;

        public MultiValuesFilter(String... accepted) {
            this.accepted = accepted.clone();
        }

        @Override
        public boolean match(Serializable value) {
            if (value == null) return false;
            String s = value.toString();
            for (String a : accepted) {
                if (a.equals(s)) return true;
            }
            return false;
        }
    }

    // =========================================================================
    // Main
    // =========================================================================

    public static void main(String[] args) {
        try {
            DemoSeparatedPlugin cvm = new DemoSeparatedPlugin();
            // DELAY_TO_START + scenario virtual duration / ACCELERATION_FACTOR + margin
            cvm.startStandardLifeCycle(100_000L);
            System.exit(0);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}