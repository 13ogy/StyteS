package fr.sorbonne_u.cps.pubsub.demo;


import fr.sorbonne_u.cps.pubsub.base.components.PrivilegedClient;
import fr.sorbonne_u.cps.pubsub.base.components.SubscriberClient;
import fr.sorbonne_u.components.cvm.AbstractCVM;
import fr.sorbonne_u.components.cvm.AbstractDistributedCVM;
import fr.sorbonne_u.components.helpers.CVMDebugModes;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.components.utils.tests.TestStep;
import fr.sorbonne_u.components.utils.tests.TestStepI;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.cps.pubsub.messages.Message;
import fr.sorbonne_u.cps.pubsub.messages.MessageFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.AcceptAllTimeFilter;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

import java.nio.channels.Channel;
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
 *
 *
 * <p><strong>Invariant</strong></p>
 * <pre>
 * invariant {@code true}
 * </pre>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */

public class Demo3JVMs extends AbstractDistributedCVM {

    /** URI of the JVMs as declared in {@code config.xml}. */
    public static final String BROKER1_JVM_URI = "broker1-jvm";
    public static final String BROKER2_JVM_URI = "broker2-jvm";
    public static final String BROKER3_JVM_URI = "broker3-jvm";


    /** URI of the brokers */
    public static final String BROKER1_URI = "broker-1";
    public static final String BROKER2_URI = "broker-2";
    public static final String BROKER3_URI = "broker-3";

    /** URI of the clients */
    public static final String CLIENT1_URI = "client-1";
    public static final String CLIENT2_URI = "client-2";
    public static final String CLIENT3_URI = "client-3";



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
    public Demo3JVMs(String[] args) throws Exception {
        super(args);
    }


    @Override
    /**
     * Hook d'initialisation BCM appelé avant {@link #instantiateAndPublish()} :
     * point d'entrée standard pour activer les modes de debug.
     *
     * @throws Exception si l'initialisation parent échoue.
     */
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
     *
     * C3 creates channel std that only allows C1
     * C1 subscribes to channel while C2 fails
     * C3 modifies authorized used to include C2
     * C2 subscribes
     * C3 publishes a messages that both clients get
     * C2 unregisters
     * C3 publishes a message that only C1 gets
     * C3 deletes channel
     *
     * TO-DO : make sure the channel is deleted
     *
     * @return the fully configured {@link TestScenario}.
     * @throws Exception if scenario construction fails.
     */
    public static TestScenario testScenario() throws Exception
    {
        Instant startInstant = Instant.parse(START_INSTANT_STR);
        Instant endInstant   = startInstant.plusSeconds(120);


        Instant upgradeC3Instant = startInstant.plusSeconds(5);
        Instant createChannelC3Instant = startInstant.plusSeconds(15);
        Instant subscribeC1Instant = startInstant.plusSeconds(25);
        Instant subscribeC2Instant = startInstant.plusSeconds(30);
        Instant publishC3Instant = startInstant.plusSeconds(40);
        Instant authInstant = startInstant.plusSeconds(50);
        Instant subscribeC2Instant2 = startInstant.plusSeconds(60);
        Instant publishC3Instant2 = startInstant.plusSeconds(70);
        Instant unregisterC2Instant = startInstant.plusSeconds(80);
        Instant publishC1Instant = startInstant.plusSeconds(90);
        Instant destroyInstant = startInstant.plusSeconds(100);



        return new TestScenario(
                CLOCK_URI,
                startInstant,
                endInstant,
                new TestStepI[]{
                        //C3 upgrade
                        new TestStep(CLOCK_URI, CLIENT3_URI, upgradeC3Instant, owner -> {
                            try {
                                ((PrivilegedClient) owner).modifyServiceClass(RegistrationCI.RegistrationClass.STANDARD);
                                ((PrivilegedClient) owner).traceMessage("upgraded to STANDARD\n");
                            } catch (Exception e) {
                                ((PrivilegedClient) owner).logMessage("upgrade STANDARD failed: " + e + "\n");
                            }
                        }),

                        //C3 create channel
                        new TestStep(CLOCK_URI, CLIENT3_URI, createChannelC3Instant, owner -> {
                            try {
                                System.out.println("creating channel\n");
                                String regex = "^(" + CLIENT1_URI + "-reg)$";
                                ((PrivilegedClient) owner).createChannel(CHANNEL, regex);
                                System.out.println("created channel\n");
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
                        // C2 tries to subscribe to channel (fails)
                        new TestStep(CLOCK_URI, CLIENT2_URI, subscribeC2Instant,
                                owner -> {
                                    try {
                                        ((SubscriberClient) owner).subscribe(CHANNEL, acceptAll());
                                        ((SubscriberClient) owner).traceMessage("subscribed to channel "+ CHANNEL +"\n");
                                    } catch (Exception e) {
                                        Throwable cause = e.getCause() != null
                                                ? e.getCause().getCause() : null;
                                        if (cause instanceof UnauthorisedClientException) {
                                            owner.traceMessage("[C2] correctly rejected from '"
                                                    + CHANNEL + "' ✓\n");
                                        } else {
                                            owner.traceMessage("[C2] unexpected exception: " + e + "\n");
                                        }
                                    }
                                }),

                        //C3 publish
                        new TestStep(CLOCK_URI, CLIENT3_URI, publishC3Instant, owner -> {
                            try {
                                MessageI m = new Message("gossip-msg1");
                                m.putProperty("publisher", "C3");
                                ((PrivilegedClient) owner).publish(CHANNEL, m);
                                System.out.println("[Test gossip] C3 published  — returned immediately");
                                ((PrivilegedClient) owner).traceMessage("[Test gossip] C3: published message "+m.getPayload()+"\n");
                            } catch (Exception e) {
                                owner.traceMessage("[Test gossip] C3: publish failed: " + e + "\n");
                            }
                        }),

                        // C3 makes std-channel accessible to C2
                        new TestStep(CLOCK_URI, CLIENT3_URI, authInstant,
                                owner -> {
                                try {
                                    String regex = "^(" + CLIENT1_URI + "-reg|"+ CLIENT2_URI + "-reg)$";
                                    ((PrivilegedClient) owner).modifyAuthorisedUsers(CHANNEL, regex);
                                    ((PrivilegedClient) owner).traceMessage("[Test gossip] C3: modifies authorised users\n");
                                } catch (Exception e) {
                                    owner.traceMessage("[Test gossip] C3: publish failed: " + e + "\n");
                                }
                            }),
                        // C2 subscribe to channel
                        new TestStep(CLOCK_URI, CLIENT2_URI, subscribeC2Instant2,
                                owner -> {
                                    try {
                                        ((SubscriberClient) owner).subscribe(CHANNEL, acceptAll());
                                        ((SubscriberClient) owner).traceMessage("subscribed to channel "+ CHANNEL +"\n");
                                    } catch (Exception e) {
                                        System.err.println("C2 subscribe failed: " + e);
                                    }
                                }),

                        //C3 publish again
                        new TestStep(CLOCK_URI, CLIENT3_URI, publishC3Instant2, owner -> {
                            try {
                                MessageI m = new Message("gossip-msg2");
                                m.putProperty("publisher", "C3");
                                ((PrivilegedClient) owner).publish(CHANNEL, m);
                                System.out.println("[Test gossip] C3 published  — returned immediately");
                                ((PrivilegedClient) owner).traceMessage("[Test gossip] C3: published message "+m.getPayload()+"\n");
                            } catch (Exception e) {
                                owner.traceMessage("[Test gossip] C3: publish failed: " + e + "\n");
                            }
                        }),

                        //C2 unregisters
                        new TestStep(CLOCK_URI, CLIENT2_URI, unregisterC2Instant,
                                owner ->{
                            try{
                                ((SubscriberClient) owner).unregister();
                                owner.traceMessage("[Test gossip] C2 unregistered (gossip Unregister→all)\n");
                            } catch (Exception e) {
                                owner.traceMessage("[Test gossip] C2 unregister failed: " + e + "\n");
                            }

                        }),

                        // C3 publish (should not reach C2)
                        new TestStep(CLOCK_URI, CLIENT3_URI, publishC1Instant, owner -> {
                            try {
                                MessageI m = new Message("gossip-msg3");
                                m.putProperty("publisher", "C3");
                                ((PrivilegedClient) owner).publish(CHANNEL, m);
                                System.out.println("[Test gossip] C3 published");
                                ((PrivilegedClient) owner).traceMessage("[Test gossip] C3: published message "+m.getPayload()+"\n");
                            } catch (Exception e) {
                                owner.traceMessage("[Test gossip] C3: publish failed: " + e + "\n");
                            }
                        }),

                        // C3 destroys channel
                        new TestStep(CLOCK_URI, CLIENT3_URI, destroyInstant, owner -> {
                            try {
                                ((PrivilegedClient) owner).destroyChannel(CHANNEL);
                                owner.traceMessage("[Test gossip] C3 destroyed '" + CHANNEL
                                        +"\n");
                            } catch (Exception e) {
                                owner.traceMessage("[Test gossip] C3 destroyChannel failed: " + e + "\n");
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
            broker2Neighbors.add(BROKER3_URI + Broker.GOSSIP_INBOUND_PORT_URI_SUFFIX);
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
                    SubscriberClient.class.getCanonicalName(),
                    new Object[] { CLIENT2_URI, BROKER2_URI, ts, RegistrationCI.RegistrationClass.FREE });

        } else if (thisJVMURI.equals(BROKER3_JVM_URI)) {


        List<String> broker3Neighbors = new ArrayList<>();
        broker3Neighbors.add(BROKER2_URI + Broker.GOSSIP_INBOUND_PORT_URI_SUFFIX);
        // JVM 3 : Broker B3 connecté à B2 via gossip
        AbstractComponent.createComponent(
                Broker.class.getCanonicalName(),
                new Object[] {
                        BROKER3_URI,
                        2, 1,
                        3, 2, 5,
                        2, 4, 8,
                        broker3Neighbors // URI gossip de B1
                });

        //Client C2 s'enregistre chez B2
        AbstractComponent.createComponent(
                PrivilegedClient.class.getCanonicalName(),
                new Object[] { CLIENT3_URI, BROKER3_URI, ts, RegistrationCI.RegistrationClass.FREE });

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
            Demo3JVMs dcvm = new Demo3JVMs(args);
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
