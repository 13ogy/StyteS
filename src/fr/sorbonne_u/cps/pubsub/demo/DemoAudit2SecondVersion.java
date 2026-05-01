package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.cvm.AbstractCVM;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.components.utils.tests.TestStep;
import fr.sorbonne_u.components.utils.tests.TestStepI;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;
import fr.sorbonne_u.cps.pubsub.messages.Message;
import fr.sorbonne_u.cps.pubsub.messages.MessageFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.AcceptAllTimeFilter;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Scénario d'intégration temporisé pour l'audit 2 (CDC §4.4).
 *
 * Démontre :
 * - Appels asynchrones pour la publication (pipeline broker)
 * - Parallélisme via trois pools de threads distincts :
 *   5 clients publient au même instant → 5 messages traversent le pipeline simultanément
 *   → esReceptionIndex reçoit 5 tâches en parallèle
 *   → esPropagationIndex propage 5 messages vers 5 abonnés en parallèle
 *   → esDeliveryIndex livre 25 combinaisons (5 msgs × 5 abonnés) en parallèle
 * - Concurrence maîtrisée dans le broker (sections critiques)
 * - Modes de réception avancés : getNextMessage (Future) et waitForNextMessage(Duration)
 */
public class DemoAudit2SecondVersion extends AbstractCVM
{
    // -------------------------------------------------------------------------
    // Clock configuration
    // -------------------------------------------------------------------------

    public static final String  CLOCK_URI           = "audit2-clock";
    public static final String  START_INSTANT_STR   = "2026-02-01T09:00:00.00Z";
    public static final double  ACCELERATION_FACTOR = 1.0; // temps réel sur Windows
    public static final long    DELAY_TO_START_MS   = 5_000L;

    // -------------------------------------------------------------------------
    // Component URIs
    // -------------------------------------------------------------------------

    public static final String CLIENT_A_URI = "audit2-client-A";
    public static final String CLIENT_B_URI = "audit2-client-B";
    public static final String CLIENT_C_URI = "audit2-client-C";
    public static final String CLIENT_D_URI = "audit2-client-D";
    public static final String CLIENT_E_URI = "audit2-client-E";

    // -------------------------------------------------------------------------
    // Channels
    // -------------------------------------------------------------------------

    public static final String CHANNEL = "channel0";

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public DemoAudit2SecondVersion() throws Exception { super(); }

    // -------------------------------------------------------------------------
    // Filter helper
    // -------------------------------------------------------------------------

    protected static MessageFilterI acceptAll()
    {
        return new MessageFilter(
                new MessageFilterI.PropertyFilterI[0],
                new MessageFilterI.PropertiesFilterI[0],
                new AcceptAllTimeFilter());
    }

    // =========================================================================
    // Scenario
    // =========================================================================

    public static TestScenario testScenario() throws Exception
    {
        Instant start = Instant.parse(START_INSTANT_STR);

        // Steps spaced at least 5s apart for Windows precision
        Instant tSubscribe  = start.plusSeconds(5);  // all 5 subscribe
        Instant tFutureB    = start.plusSeconds(12); // B gets a Future before publish
        Instant tPublish    = start.plusSeconds(20); // all 5 publish simultaneously
        Instant tTimedWaitA = start.plusSeconds(30); // A waits with timeout
        Instant end         = start.plusSeconds(45); // enough margin after last step

        return new TestScenario(
                "[Audit2] BEGIN",
                "[Audit2] END",
                CLOCK_URI,
                start,
                end,
                new TestStepI[] {

                        // ------------------------------------------------------------------
                        // 1. All 5 clients subscribe — already registered in execute()
                        // ------------------------------------------------------------------

                        new TestStep(CLOCK_URI, CLIENT_A_URI, tSubscribe, owner -> {
                            try {
                                ((FullClient) owner).subscribe(CHANNEL, acceptAll());
                                System.out.println("[Audit2] A subscribed");
                                owner.traceMessage("[Audit2] A subscribed\n");
                            } catch (Exception e) {
                                owner.traceMessage("[Audit2] A subscribe failed: " + e + "\n");
                            }
                        }),

                        new TestStep(CLOCK_URI, CLIENT_B_URI, tSubscribe, owner -> {
                            try {
                                ((FullClient) owner).subscribe(CHANNEL, acceptAll());
                                System.out.println("[Audit2] B subscribed");
                                owner.traceMessage("[Audit2] B subscribed\n");
                            } catch (Exception e) {
                                owner.traceMessage("[Audit2] B subscribe failed: " + e + "\n");
                            }
                        }),

                        new TestStep(CLOCK_URI, CLIENT_C_URI, tSubscribe, owner -> {
                            try {
                                ((FullClient) owner).subscribe(CHANNEL, acceptAll());
                                System.out.println("[Audit2] C subscribed");
                                owner.traceMessage("[Audit2] C subscribed\n");
                            } catch (Exception e) {
                                owner.traceMessage("[Audit2] C subscribe failed: " + e + "\n");
                            }
                        }),

                        new TestStep(CLOCK_URI, CLIENT_D_URI, tSubscribe, owner -> {
                            try {
                                ((FullClient) owner).subscribe(CHANNEL, acceptAll());
                                System.out.println("[Audit2] D subscribed");
                                owner.traceMessage("[Audit2] D subscribed\n");
                            } catch (Exception e) {
                                owner.traceMessage("[Audit2] D subscribe failed: " + e + "\n");
                            }
                        }),

                        new TestStep(CLOCK_URI, CLIENT_E_URI, tSubscribe, owner -> {
                            try {
                                ((FullClient) owner).subscribe(CHANNEL, acceptAll());
                                System.out.println("[Audit2] E subscribed");
                                owner.traceMessage("[Audit2] E subscribed\n");
                            } catch (Exception e) {
                                owner.traceMessage("[Audit2] E subscribe failed: " + e + "\n");
                            }
                        }),

                        // ------------------------------------------------------------------
                        // 2. B gets a Future BEFORE publish — demonstrates getNextMessage
                        //    Non-blocking: B continues immediately, Future completes later
                        // ------------------------------------------------------------------

                        new TestStep(CLOCK_URI, CLIENT_B_URI, tFutureB, owner -> {
                            try {
                                System.out.println("[Audit2] B calling getNextMessage() — non-blocking");
                                owner.traceMessage("[Audit2] B: getNextMessage() called\n");
                                Future<MessageI> f = ((FullClient) owner).getNextMessage(CHANNEL);
                                owner.traceMessage("[Audit2] B: Future returned immediately\n");
                                // wait with timeout for it to be completed by a publish
                                MessageI msg = f.get(15, TimeUnit.SECONDS);
                                System.out.println("[Audit2] B Future completed: "
                                        + (msg != null ? msg.getPayload() : "null"));
                                owner.traceMessage("[Audit2] B: Future => "
                                        + (msg != null ? msg.getPayload() : "null") + "\n");
                            } catch (Exception e) {
                                owner.traceMessage("[Audit2] B: Future failed/timeout: " + e + "\n");
                            }
                        }),

                        // ------------------------------------------------------------------
                        // 3. All 5 clients publish at the SAME instant
                        //    → 5 messages enter the broker pipeline simultaneously
                        //    → demonstrates parallelism across the 3 thread pools
                        // ------------------------------------------------------------------

                        new TestStep(CLOCK_URI, CLIENT_A_URI, tPublish, owner -> {
                            try {
                                MessageI m = new Message("A-msg");
                                m.putProperty("publisher", "A");
                                ((FullClient) owner).publish(CHANNEL, m);
                                System.out.println("[Audit2] A published async — returned immediately");
                                owner.traceMessage("[Audit2] A: publish submitted asynchronously\n");
                            } catch (Exception e) {
                                owner.traceMessage("[Audit2] A: publish failed: " + e + "\n");
                            }
                        }),

                        new TestStep(CLOCK_URI, CLIENT_B_URI, tPublish, owner -> {
                            try {
                                MessageI m = new Message("B-msg");
                                m.putProperty("publisher", "B");
                                ((FullClient) owner).publish(CHANNEL, m);
                                System.out.println("[Audit2] B published async — returned immediately");
                                owner.traceMessage("[Audit2] B: publish submitted asynchronously\n");
                            } catch (Exception e) {
                                owner.traceMessage("[Audit2] B: publish failed: " + e + "\n");
                            }
                        }),

                        new TestStep(CLOCK_URI, CLIENT_C_URI, tPublish, owner -> {
                            try {
                                MessageI m = new Message("C-msg");
                                m.putProperty("publisher", "C");
                                ((FullClient) owner).publish(CHANNEL, m);
                                System.out.println("[Audit2] C published async — returned immediately");
                                owner.traceMessage("[Audit2] C: publish submitted asynchronously\n");
                            } catch (Exception e) {
                                owner.traceMessage("[Audit2] C: publish failed: " + e + "\n");
                            }
                        }),

                        new TestStep(CLOCK_URI, CLIENT_D_URI, tPublish, owner -> {
                            try {
                                MessageI m = new Message("D-msg");
                                m.putProperty("publisher", "D");
                                ((FullClient) owner).publish(CHANNEL, m);
                                System.out.println("[Audit2] D published async — returned immediately");
                                owner.traceMessage("[Audit2] D: publish submitted asynchronously\n");
                            } catch (Exception e) {
                                owner.traceMessage("[Audit2] D: publish failed: " + e + "\n");
                            }
                        }),

                        new TestStep(CLOCK_URI, CLIENT_E_URI, tPublish, owner -> {
                            try {
                                MessageI m = new Message("E-msg");
                                m.putProperty("publisher", "E");
                                ((FullClient) owner).publish(CHANNEL, m);
                                System.out.println("[Audit2] E published async — returned immediately");
                                owner.traceMessage("[Audit2] E: publish submitted asynchronously\n");
                            } catch (Exception e) {
                                owner.traceMessage("[Audit2] E: publish failed: " + e + "\n");
                            }
                        }),

                        // ------------------------------------------------------------------
                        // 4. A waits for next message with timeout — demonstrates §3.5.3
                        // ------------------------------------------------------------------

                        new TestStep(CLOCK_URI, CLIENT_A_URI, tTimedWaitA, owner -> {
                            try {
                                System.out.println("[Audit2] A calling waitForNextMessage(10s)");
                                MessageI msg = ((FullClient) owner)
                                        .waitForNextMessage(CHANNEL, Duration.ofSeconds(10));
                                if (msg != null) {
                                    System.out.println("[Audit2] A received: " + msg.getPayload());
                                    owner.traceMessage("[Audit2] A: waitForNextMessage => "
                                            + msg.getPayload() + "\n");
                                } else {
                                    System.out.println("[Audit2] A: timeout");
                                    owner.traceMessage("[Audit2] A: waitForNextMessage => timeout\n");
                                }
                            } catch (Exception e) {
                                owner.traceMessage("[Audit2] A: waitForNextMessage failed: " + e + "\n");
                            }
                        }),
                }
        );
    }

    // =========================================================================
    // CVM deploy
    // =========================================================================

    @Override
    public void deploy() throws Exception
    {
        TestScenario.VERBOSE = true;
        TestScenario.DEBUG   = true;

        // Build scenario first
        TestScenario ts = testScenario();

        // Create Broker with configurable thread pools
        AbstractComponent.createComponent(
                Broker.class.getCanonicalName(),
                new Object[] {
                        2, 0,    // nbThreads, nbSchedulableThreads
                        3, 2, 5, // nbFreeChannels, standardQuota, premiumQuota
                        2, 4, 8  // nbReceptionThreads, nbPropagationThreads, nbDeliveryThreads
                });

        //  Create ClocksServer
        long current = System.currentTimeMillis();
        long unixEpochStartTimeInNanos =
                TimeUnit.MILLISECONDS.toNanos(current + DELAY_TO_START_MS);
        Instant startInstant = Instant.parse(START_INSTANT_STR);
        AbstractComponent.createComponent(
                ClocksServer.class.getCanonicalName(),
                new Object[] {
                        CLOCK_URI,
                        unixEpochStartTimeInNanos,
                        startInstant,
                        ACCELERATION_FACTOR
                });

        // Create all 5 FullClient components — register in execute()
        AbstractComponent.createComponent(
                FullClient.class.getCanonicalName(),
                new Object[] { CLIENT_A_URI, ts, RegistrationClass.FREE });

        AbstractComponent.createComponent(
                FullClient.class.getCanonicalName(),
                new Object[] { CLIENT_B_URI, ts, RegistrationClass.FREE });

        AbstractComponent.createComponent(
                FullClient.class.getCanonicalName(),
                new Object[] { CLIENT_C_URI, ts, RegistrationClass.FREE });

        AbstractComponent.createComponent(
                FullClient.class.getCanonicalName(),
                new Object[] { CLIENT_D_URI, ts, RegistrationClass.FREE });

        AbstractComponent.createComponent(
                FullClient.class.getCanonicalName(),
                new Object[] { CLIENT_E_URI, ts, RegistrationClass.FREE });

        super.deploy();


        this.toggleTracing(CLIENT_A_URI);
        this.toggleLogging(CLIENT_A_URI);
        this.toggleTracing(CLIENT_B_URI);
        this.toggleLogging(CLIENT_B_URI);
        this.toggleTracing(CLIENT_C_URI);
        this.toggleLogging(CLIENT_C_URI);
        this.toggleTracing(CLIENT_D_URI);
        this.toggleLogging(CLIENT_D_URI);
        this.toggleTracing(CLIENT_E_URI);
        this.toggleLogging(CLIENT_E_URI);
    }

    // =========================================================================
    // Main
    // =========================================================================

    public static void main(String[] args)
    {
        try {
            DemoAudit2SecondVersion cvm = new DemoAudit2SecondVersion();
            // DELAY(5s) + scenario(45s) + margin(10s) = 60s
            cvm.startStandardLifeCycle(60_000L);
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}