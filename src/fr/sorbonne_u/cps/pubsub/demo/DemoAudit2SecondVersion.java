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
 * - Parallélisme via trois pools de threads distincts
 * - Concurrence maîtrisée dans le broker
 * - Modes de réception avancés : getNextMessage (Future) et waitForNextMessage(Duration)
 * - Deux clients publient simultanément pour montrer le parallélisme
 *
 * Structure BCM4Java correcte :
 * - Registration dans execute() de chaque composant, PAS dans les steps
 * - Steps contiennent uniquement des actions métier
 * - Composants génériques (SubscriberClient, PublisherClient) — pas PluginClient
 */
public class DemoAudit2SecondVersion extends AbstractCVM
{
    // -------------------------------------------------------------------------
    // Clock configuration
    // -------------------------------------------------------------------------

    public static final String  CLOCK_URI           = "audit2-clock";
    public static final String  START_INSTANT_STR   = "2026-02-01T09:00:00.00Z";
    public static final String  END_INSTANT_STR     = "2026-02-01T09:02:00.00Z";
    public static final double  ACCELERATION_FACTOR = 1.0; // temps réel sur Windows
    public static final long    DELAY_TO_START_MS   = 5_000L;

    // -------------------------------------------------------------------------
    // Component URIs
    // -------------------------------------------------------------------------

    public static final String CLIENT_A_URI = "audit2-client-A"; // subscriber + publisher
    public static final String CLIENT_B_URI = "audit2-client-B"; // subscriber + publisher

    // -------------------------------------------------------------------------
    // Channels
    // -------------------------------------------------------------------------

    public static final String CHANNEL = "channel0"; // free channel

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
    // Scenario — only domain actions, NO registration
    // =========================================================================

    public static TestScenario testScenario() throws Exception
    {
        Instant start = Instant.parse(START_INSTANT_STR);
        Instant end   = start.plusSeconds(25);

        Instant tSubscribeA    = start.plusSeconds(3);
        Instant tSubscribeB    = start.plusSeconds(3);
        Instant tFutureB       = start.plusSeconds(7);  // B requests Future BEFORE publish
        Instant tPublishA      = start.plusSeconds(10);  // A and B publish simultaneously
        Instant tPublishB      = start.plusSeconds(10);  // same instant → parallel publish
        Instant tTimedWaitA    = start.plusSeconds(15);  // A waits with timeout

        return new TestScenario(
                "[Audit2] BEGIN",
                "[Audit2] END",
                CLOCK_URI,
                start,
                end,
                new TestStepI[] {

                        // ------------------------------------------------------------------
                        // Subscriptions — clients already registered in execute()
                        // ------------------------------------------------------------------

                        new TestStep(CLOCK_URI, CLIENT_A_URI, tSubscribeA, owner -> {
                            try {
                                ((FullClient) owner).subscribe(CHANNEL, acceptAll());
                                System.out.println("[Audit2] A subscribed to " + CHANNEL);
                                owner.traceMessage("[Audit2] A subscribed\n");
                            } catch (Exception e) {
                                owner.traceMessage("[Audit2] A subscribe failed: " + e + "\n");
                            }
                        }),

                        new TestStep(CLOCK_URI, CLIENT_B_URI, tSubscribeB, owner -> {
                            try {
                                ((FullClient) owner).subscribe(CHANNEL, acceptAll());
                                System.out.println("[Audit2] B subscribed to " + CHANNEL);
                                owner.traceMessage("[Audit2] B subscribed\n");
                            } catch (Exception e) {
                                owner.traceMessage("[Audit2] B subscribe failed: " + e + "\n");
                            }
                        }),

                        // ------------------------------------------------------------------
                        //  B requests a Future BEFORE the publish — demonstrates getNextMessage
                        //    The Future will be completed when a message arrives
                        // ------------------------------------------------------------------

                        new TestStep(CLOCK_URI, CLIENT_B_URI, tFutureB, owner -> {
                            try {
                                System.out.println("[Audit2] B calling getNextMessage() — Future created");
                                owner.traceMessage("[Audit2] B: getNextMessage() called\n");
                                Future<MessageI> f = ((FullClient) owner).getNextMessage(CHANNEL);
                                // non-blocking — continue immediately
                                owner.traceMessage("[Audit2] B: Future returned immediately (not yet completed)\n");
                                // Now wait for it with a timeout
                                MessageI msg = f.get(15, TimeUnit.SECONDS);
                                System.out.println("[Audit2] B Future completed: " + (msg != null ? msg.getPayload() : "null"));
                                owner.traceMessage("[Audit2] B: Future completed => " + (msg != null ? msg.getPayload() : "null") + "\n");
                            } catch (Exception e) {
                                owner.traceMessage("[Audit2] B: Future failed/timeout: " + e + "\n");
                            }
                        }),

                        // ------------------------------------------------------------------
                        // A and B publish simultaneously — demonstrates parallel publish
                        //    Both go through the broker pipeline at the same time
                        //    showing the 3 thread pools working in parallel
                        // ------------------------------------------------------------------

                        new TestStep(CLOCK_URI, CLIENT_A_URI, tPublishA, owner -> {
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

                        new TestStep(CLOCK_URI, CLIENT_B_URI, tPublishB, owner -> {
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

                        // ------------------------------------------------------------------
                        //  A uses waitForNextMessage with a timeout — demonstrates §3.5.3
                        //    Should receive B-msg (since A-msg went to B's Future)
                        // ------------------------------------------------------------------

                        new TestStep(CLOCK_URI, CLIENT_A_URI, tTimedWaitA, owner -> {
                            try {
                                System.out.println("[Audit2] A calling waitForNextMessage(2s)");
                                MessageI msg = ((FullClient) owner).waitForNextMessage(CHANNEL, Duration.ofSeconds(10));
                                if (msg != null) {
                                    System.out.println("[Audit2] A received: " + msg.getPayload());
                                    owner.traceMessage("[Audit2] A: waitForNextMessage => " + msg.getPayload() + "\n");
                                } else {
                                    System.out.println("[Audit2] A: timeout — no message received");
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

        //Build scenario first
        TestScenario ts = testScenario();

        //Create Broker with configurable thread pools
        AbstractComponent.createComponent(
                Broker.class.getCanonicalName(),
                new Object[] {
                        2, 0,   // nbThreads, nbSchedulableThreads
                        3, 2, 5, // nbFreeChannels, standardQuota, premiumQuota
                        2, 4, 8  // nbReceptionThreads, nbPropagationThreads, nbDeliveryThreads
                });

        //Create ClocksServer
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

        // Create FullClient components — they register in execute()
        //    FullClient = registration + subscription + publication
        AbstractComponent.createComponent(
                FullClient.class.getCanonicalName(),
                new Object[] { CLIENT_A_URI, ts, RegistrationClass.FREE });

        AbstractComponent.createComponent(
                FullClient.class.getCanonicalName(),
                new Object[] { CLIENT_B_URI, ts, RegistrationClass.FREE });

        super.deploy();

    }

    // =========================================================================
    // Main
    // =========================================================================

    public static void main(String[] args)
    {
        try {
            DemoAudit2SecondVersion cvm = new DemoAudit2SecondVersion();
            // DELAY(5s) + scenario(~50s virtual / 1.0 factor) + margin(10s)
            cvm.startStandardLifeCycle(130_000L);
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}