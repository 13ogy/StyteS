package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.components.cvm.AbstractCVM;
import fr.sorbonne_u.components.cvm.AbstractDistributedCVM;
import fr.sorbonne_u.components.helpers.CVMDebugModes;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.components.utils.tests.TestStep;
import fr.sorbonne_u.components.utils.tests.TestStepI;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.cps.pubsub.messages.Message;
import fr.sorbonne_u.cps.pubsub.messages.MessageFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.AcceptAllTimeFilter;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Distributed CVM demonstrating the gossip protocol between two brokers
 * across two separate JVMs.
 *
 * <p><strong>Description</strong></p>
 * <p>
 * This deployment validates the gossip protocol (CDC §3.7) by running two
 * broker components on separate JVMs, each hosting one client component:
 * </p>
 * <ul>
 *   <li><strong>JVM {@value #BROKER1_JVM_URI}</strong> — Broker B1 +
 *       {@link SubscriberClient} C1 (FREE class) + {@link ClocksServer}</li>
 *   <li><strong>JVM {@value #BROKER2_JVM_URI}</strong> — Broker B2 +
 *       {@link PrivilegedClient} C2 (starts FREE, upgrades to STANDARD)</li>
 * </ul>
 *
 * <p><strong>Test scenario</strong></p>
 * <ol>
 *   <li>C1 and C2 register at their respective local brokers; registration
 *       is propagated via {@code RegisterGossipMessage}.</li>
 *   <li>C2 upgrades to STANDARD via {@code modifyServiceClass}.</li>
 *   <li>C2 creates a privileged channel {@value #CHANNEL} restricted to C1;
 *       creation is propagated via {@code CreateChannelGossipMessage} so B1
 *       replicates the channel locally.</li>
 *   <li>C1 subscribes to {@value #CHANNEL} on B1.</li>
 *   <li>C2 publishes a message on {@value #CHANNEL} via B2; B2 validates
 *       and propagates a {@code PublishGossipMessage} to B1, which delivers
 *       the message to C1.</li>
 * </ol>
 * </pre>
 *
 * <p><strong>Invariant</strong></p>
 * <pre>
 * invariant {@code true}
 * </pre>
 *
 * @author Bogdan Styn, Setbel Melissa
 */

public class DemoTimedDistributed extends AbstractDistributedCVM {

    /** URI of the JVMs as declared in {@code config.xml}. */
    public static final String BROKER1_JVM_URI = "broker1-jvm";
    public static final String BROKER2_JVM_URI = "broker2-jvm";

    /** URI of the brokers */
    public static final String BROKER1_URI = "broker-1";
    public static final String BROKER2_URI = "broker-2";
    /** URI of the clients */
    public static final String CLIENT1_URI = "client-1";
    public static final String CLIENT2_URI = "client-2";


    /** Name of the privileged channel created by C2 during the test. */
    public static final String CHANNEL = "gossip-channel";

    // clock
    public static final String  CLOCK_URI          = "gossip-test-clock";
    public static final String  START_INSTANT_STR  = "2026-02-01T10:00:00.00Z";
    public static final double  ACCELERATION_FACTOR = 1.0;
    public static final long    DELAY_TO_START_MS  = 15_000L;

    /**
     * Instantiate the distributed CVM object.
     *
     * <p><strong>Contract</strong></p>
     * <pre>
     * pre  {@code args != null && args.length >= 2}
     * post {@code true}
     * </pre>
     *
     * @param args command-line arguments: {@code args[0]} is the URI of this
     *             JVM in the assembly; {@code args[1]} is the path to the XML
     *             configuration file.
     * @throws Exception if the superclass constructor fails.
     */
    public DemoTimedDistributed(String[] args) throws Exception {
        super(args);
    }


    @Override
    public void initialise() throws Exception
    {
        /*AbstractCVM.DEBUG_MODE.add(CVMDebugModes.LIFE_CYCLE);
        AbstractCVM.DEBUG_MODE.add(CVMDebugModes.PORTS);
        AbstractCVM.DEBUG_MODE.add(CVMDebugModes.CONNECTING);*/
        super.initialise();
    }
    /**
     * Build the timed test scenario shared by all client components.
     *
     * <p>
     * Steps (all instants relative to {@value #START_INSTANT_STR},
     * real-time with {@code ACCELERATION_FACTOR = 1.0}):
     * </p>
     * <ol>
     *   <li><strong>t+5 s</strong>  — C2 upgrades to STANDARD.</li>
     *   <li><strong>t+15 s</strong> — C2 creates privileged channel
     *       {@value #CHANNEL}; gossip propagates to B1.</li>
     *   <li><strong>t+25 s</strong> — C1 subscribes to {@value #CHANNEL}
     *       on B1 (channel is now known locally via gossip).</li>
     *   <li><strong>t+35 s</strong> — C2 publishes a message; B2 sends a
     *       {@code PublishGossipMessage} to B1 which delivers it to C1.</li>
     * </ol>
     *
     * @return the fully configured {@link TestScenario}.
     * @throws Exception if scenario construction fails.
     */
    public static TestScenario testScenario() throws Exception
    {
        Instant startInstant = Instant.parse(START_INSTANT_STR);
        Instant endInstant   = startInstant.plusSeconds(100);

        // upgrade C2 -> create channel C2 -> subscribe C1 → publish C2

        Instant upgradeC2Instant = startInstant.plusSeconds(5);
        Instant createChannelC2Instaant = startInstant.plusSeconds(15);
        Instant subscribeC1Instant = startInstant.plusSeconds(25);
        Instant publishC2Instant   = startInstant.plusSeconds(35); // après gossip RegisterGossip propagé


        return new TestScenario(
                CLOCK_URI,
                startInstant,
                endInstant,
                new TestStepI[]{
                        //C2 upgrade
                        new TestStep(CLOCK_URI, CLIENT2_URI, upgradeC2Instant, owner -> {
                            try {
                                ((PrivilegedClient) owner).modifyServiceClass(RegistrationCI.RegistrationClass.STANDARD);
                                ((PrivilegedClient) owner).traceMessage("upgraded to STANDARD\n");
                            } catch (Exception e) {
                                ((PrivilegedClient) owner).logMessage("upgrade STANDARD failed: " + e + "\n");
                            }
                        }),

                        //C2 create channel
                        new TestStep(CLOCK_URI, CLIENT2_URI, createChannelC2Instaant, owner -> {
                            try {
                                String regex = "^(" + CLIENT1_URI + "-reg)$";
                                ((PrivilegedClient) owner).createChannel(CHANNEL, regex);
                                ((PrivilegedClient) owner).traceMessage("created " + CHANNEL + "\n");
                            } catch (Exception e) {
                                ((PrivilegedClient) owner).logMessage("createChannel failed: " + e + "\n");
                            }
                        }),
                        // C1 subscribe to channel
                        new TestStep(CLOCK_URI, CLIENT1_URI, subscribeC1Instant,
                                owner -> {
                                    try {
                                        ((SubscriberClient) owner).subscribe(CHANNEL, acceptAll());
                                        ((SubscriberClient) owner).traceMessage("subscribed to channel "+ CHANNEL +"\n");
                                    } catch (Exception e) {
                                        System.err.println("C1 subscribe failed: " + e);
                                    }
                                }),

                        //C2 publish
                        new TestStep(CLOCK_URI, CLIENT2_URI, publishC2Instant, owner -> {
                            try {
                                MessageI m = new Message("gossip-msg");
                                m.putProperty("publisher", "C2");
                                ((PrivilegedClient) owner).publish(CHANNEL, m);
                                System.out.println("[Test gossip] C2 published  — returned immediately");
                                ((PrivilegedClient) owner).traceMessage("[Test gossip] C2: published message "+m.getPayload()+"\n");
                            } catch (Exception e) {
                                owner.traceMessage("[Test gossip] C2: publish failed: " + e + "\n");
                            }
                        }),
                }
        );
    }

    /**
     * Build a message filter that accepts every message.
     *
     * @return a {@link MessageFilter} with no property or time constraints.
     */
    protected static MessageFilterI acceptAll()
    {
        return new MessageFilter(
                new MessageFilterI.PropertyFilterI[0],
                new MessageFilterI.PropertiesFilterI[0],
                new AcceptAllTimeFilter());
    }

    /**
     * Instantiate and publish components on the current JVM.
     *
     * <p>
     * On {@value #BROKER1_JVM_URI}: creates Broker B1 (gossip neighbour =
     * B2), SubscriberClient C1, and the shared ClocksServer.<br>
     * On {@value #BROKER2_JVM_URI}: creates Broker B2 (gossip neighbour =
     * B1) and PrivilegedClient C2.
     * </p>
     *
     * @throws Exception if component creation or port publication fails.
     */
    @Override
    public void instantiateAndPublish() throws Exception{

        TestScenario.VERBOSE = true;
        TestScenario.DEBUG   = true;

        // Build scenario first
        TestScenario ts = testScenario();

        if (thisJVMURI.equals(BROKER1_JVM_URI)) {

            List<String> broker1Neighbors = new ArrayList<>();
            broker1Neighbors.add(BROKER2_URI + Broker.GOSSIP_INBOUND_PORT_URI_SUFFIX);
            // JVM 1 : Broker B1 connecté à B2 via gossip
            // B1 connaît l'URI du port gossip entrant de B2
            AbstractComponent.createComponent(
                    Broker.class.getCanonicalName(),
                    new Object[] {
                            BROKER1_URI,              // reflectionInboundPortURI
                            2, 1,                     // nbThreads, nbSchedulableThreads
                            3, 2, 5,                  // nbFreeChannels, standardQuota, premiumQuota
                            2, 4, 8,                  // nbReceptionThreads, nbPropagationThreads, nbDeliveryThreads
                            broker1Neighbors // URI gossip de B2
                    });

            //  C1 s'enregistre chez B1
            AbstractComponent.createComponent(
                    SubscriberClient.class.getCanonicalName(),
                    new Object[] { CLIENT1_URI, BROKER1_URI, ts, RegistrationCI.RegistrationClass.FREE });

            // ClocksServer sur JVM 1
            long current = System.currentTimeMillis();
            long unixEpochStartTimeInNanos =
                    TimeUnit.MILLISECONDS.toNanos(current + DELAY_TO_START_MS);
            AbstractComponent.createComponent(
                    ClocksServer.class.getCanonicalName(),
                    new Object[] {
                            CLOCK_URI,
                            unixEpochStartTimeInNanos,
                            Instant.parse(START_INSTANT_STR),
                            ACCELERATION_FACTOR
                    });

        } else if (thisJVMURI.equals(BROKER2_JVM_URI)) {


            List<String> broker2Neighbors = new ArrayList<>();
            broker2Neighbors.add(BROKER1_URI + Broker.GOSSIP_INBOUND_PORT_URI_SUFFIX);
            // JVM 2 : Broker B2 connecté à B1 via gossip
            AbstractComponent.createComponent(
                    Broker.class.getCanonicalName(),
                    new Object[] {
                            BROKER2_URI,
                            2, 1,
                            3, 2, 5,
                            2, 4, 8,
                            broker2Neighbors // URI gossip de B1
                    });

            //Client C2 s'enregistre chez B2
            AbstractComponent.createComponent(
                    PrivilegedClient.class.getCanonicalName(),
                    new Object[] { CLIENT2_URI, BROKER2_URI, ts, RegistrationCI.RegistrationClass.FREE });

        } else {
            System.out.println("Unknown JVM URI: " + thisJVMURI);
        }

        super.instantiateAndPublish();
    }



    @Override
    public void	interconnect() throws Exception{
        super.interconnect();
    }


    // =========================================================================
    // Main
    // =========================================================================
    /**
     * Entry point.
     *
     * <p>
     * Starts the standard BCM4Java life cycle with a total budget of
     * 120 seconds ({@value #DELAY_TO_START_MS} ms startup delay +
     * 100 s scenario + safety margin).
     * </p>
     *
     * @param args JVM URI and config file path (forwarded to the constructor).
     */
    public static void main(String[] args)
    {

        try {
            DemoTimedDistributed dcvm = new DemoTimedDistributed(args);
            // DELAY(5s) + exécution(30s) + marge(10s)
            dcvm.startStandardLifeCycle(120_000L);
            Thread.sleep(5_000L);
            System.exit(0);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);

        }
    }
}
