package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.cvm.AbstractCVM;
import fr.sorbonne_u.components.cvm.AbstractDistributedCVM;
import fr.sorbonne_u.components.helpers.CVMDebugModes;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.components.utils.tests.TestStep;
import fr.sorbonne_u.components.utils.tests.TestStepI;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.exceptions.ChannelQuotaExceededException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI.PropertyFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;
import fr.sorbonne_u.cps.pubsub.messages.Message;
import fr.sorbonne_u.cps.pubsub.messages.MessageFilter;
import fr.sorbonne_u.cps.pubsub.messages.filters.*;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Final soutenance distributed integration test — 4 JVMs, 4 brokers,
 * gossip protocol fully exercised.
 *
 * <p><strong>Topology</strong></p>
 * <pre>
 *   B1 ↔ B2
 *   B1 ↔ B3
 *   B2 ↔ B3   (B1–B2–B3 form a fully-connected triangle)
 *   B3 → B4   (B4 only knows B3; tests chain propagation)
 * </pre>
 *
 * <p><strong>Deployment</strong></p>
 * <pre>
 *   JVM1 (broker1-jvm) : B1 + C1_SUB (subscriber)  + C2_PUB (publisher)
 *   JVM2 (broker2-jvm) : B2 + C3_PRIV (privileged)  + C4_SUB (subscriber)
 *   JVM3 (broker3-jvm) : B3 + C5_PRIV (privileged)  + C6_PUB (publisher)
 *                           + ClocksServer
 *   JVM4 (broker4-jvm) : B4 + C7_SUB (subscriber)   + C8_INTRUDER
 * </pre>
 *
 * <p><strong>Gossip messages exercised</strong></p>
 * <ul>
 *   <li>{@code RegisterGossipMessage}        — all 8 clients cross-JVM</li>
 *   <li>{@code ModifyServiceClassGossipMessage} — C3/C5 upgrade</li>
 *   <li>{@code CreateChannelGossipMessage}   — privileged channels</li>
 *   <li>{@code PublishGossipMessage}         — cross-JVM delivery</li>
 *   <li>{@code ModifyAuthorisedUsersGossipMessage} — C5 widens access</li>
 *   <li>{@code DestroyChannelGossipMessage}  — C3 destroys its channel</li>
 *   <li>{@code UnregisterGossipMessage}      — C6 unregisters</li>
 * </ul>
 *
 * <p><strong>Launch sequence (5 terminals)</strong></p>
 * <pre>
 *   # 1. Global registry
 *   java -cp "libs/*" fr.sorbonne_u.components.registry.GlobalRegistry localhost 55252
 *
 *   # 2. Cyclic barrier — 4 JVMs
 *   java -cp "libs/*" fr.sorbonne_u.components.cvm.utils.DCVMCyclicBarrier localhost 55253 4
 *
 *   # 3-6. One per JVM
 *   ./start-dcvm broker1-jvm
 *   ./start-dcvm broker2-jvm
 *   ./start-dcvm broker3-jvm
 *   ./start-dcvm broker4-jvm
 * </pre>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class DemoFinal extends AbstractDistributedCVM
{
    // =========================================================================
    // JVM URIs  (must match config.xml)
    // =========================================================================

    public static final String JVM1 = "broker1-jvm";
    public static final String JVM2 = "broker2-jvm";
    public static final String JVM3 = "broker3-jvm";
    public static final String JVM4 = "broker4-jvm";

    // =========================================================================
    // Broker URIs  (= reflectionInboundPortURIs)
    // =========================================================================

    public static final String B1 = "broker-1";
    public static final String B2 = "broker-2";
    public static final String B3 = "broker-3";
    public static final String B4 = "broker-4";

    // =========================================================================
    // Client URIs  (= reflectionInboundPortURIs)
    // =========================================================================

    /** Subscriber on JVM1 (registered at B1). */
    public static final String C1_SUB      = "client-1-sub";
    /** Publisher  on JVM1 (registered at B1). */
    public static final String C2_PUB      = "client-2-pub";
    /** Privileged on JVM2 (registered at B2, upgrades to STANDARD). */
    public static final String C3_PRIV     = "client-3-priv";
    /** Subscriber on JVM2 (registered at B2). */
    public static final String C4_SUB      = "client-4-sub";
    /** Privileged on JVM3 (registered at B3, upgrades to PREMIUM). */
    public static final String C5_PRIV     = "client-5-priv";
    /** Publisher  on JVM3 (registered at B3). */
    public static final String C6_PUB      = "client-6-pub";
    /** Subscriber on JVM4 (registered at B4 — end of chain). */
    public static final String C7_SUB      = "client-7-sub";
    /** Intruder   on JVM4 (registered at B4 — will be rejected). */
    public static final String C8_INTRUDER = "client-8-intruder";

    // =========================================================================
    // Channels
    // =========================================================================

    /** Free channel — available to all registered clients. */
    public static final String CH_FREE   = "channel0";
    /** Privileged channel created by C3 (STANDARD). */
    public static final String CH_STD    = "std-channel";
    /** Privileged channel created by C5 (PREMIUM). */
    public static final String CH_PREM   = "prem-channel";

    // =========================================================================
    // Clock
    // =========================================================================

    // clock
    public static final String  CLOCK_URI          = "gossip-test-clock";
    public static final String  START_INSTANT_STR  = "2026-02-01T10:00:00.00Z";
    public static final double  ACCELERATION_FACTOR = 1.0;
    public static final long    DELAY_TO_START_MS  = 40_000L;


    // =========================================================================
    // Constructor
    // =========================================================================
	/**
	 * Construit ce CVM ; la création réelle des composants se produit dans
	 * {@link #deploy()}.
	 *
	 * @throws Exception si l'initialisation parent échoue.
	 */

    public DemoFinal(String[] args) throws Exception { super(args); }

    // =========================================================================
    // Scenario
    // =========================================================================

    protected static MessageFilterI acceptAll()
    {
        return new MessageFilter(
                new MessageFilterI.PropertyFilterI[0],
                new MessageFilterI.PropertiesFilterI[0],
                new AcceptAllTimeFilter());
    }
    /**
     * Builds the timed test scenario.
     *
     * <p>
     * All registrations happen in each component's {@code execute()} method
     * before {@code executeTestScenario} is called. By the time the first
     * step fires, every client is already registered at its local broker, and
     * the initial {@code RegisterGossipMessage}s have had time to propagate.
     * </p>
     *
     * <p><strong>Timeline (ACCELERATION_FACTOR = 1.0)</strong></p>
     * <pre>
     *  t+ 5s  C3 upgrades to STANDARD
     *  t+ 5s  C5 upgrades to PREMIUM
     *  t+15s  C3 creates "std-channel"  (regex: C1,C4,C7)
     *  t+15s  C5 creates "prem-channel" (regex: C1,C4,C7)
     *                → gossip CreateChannel propagates to all brokers
     *  t+25s  C1,C4,C7 subscribe to "channel0" (free) with filters
     *  t+35s  C1 subscribes to "std-channel" (privileged)
     *  t+35s  C4 subscribes to "std-channel"
     *  t+35s  C7 subscribes to "std-channel" (via B4 — chain B3→B4)
     *  t+35s  C8 (intruder) tries to subscribe to "std-channel" → rejected
     *  t+45s  C2 publishes on "channel0"
     *               → delivered to C1(B1 local), C4, C7(B2 via gossip)
     *  t+55s  C3 publishes on "std-channel" (privileged publish)
     *               → delivered to C1, C4, C7
     *  t+60s  C5 modifies authorisedUsers of "prem-channel" (adds C4)
     *               → gossip ModifyAuthorisedUsers propagates
     *  t+70s  C4 subscribes to "prem-channel" (now authorised after gossip)
     *  t+75s  C5 publishes on "prem-channel"
     *  t+80s  C3 destroys "std-channel"
     *               → gossip DestroyChannel propagates to all brokers
     *  t+90s  C1 publishes on "std-channel"
     *               → fail
     *  t+95s  C6 unregisters
     *               → gossip UnregisterGossipMessage propagates
     * </pre>
     *
     * @return the fully configured {@link TestScenario}.
     * @throws Exception if scenario construction fails.
     */
    public static TestScenario buildScenario() throws Exception {
        Instant startInstant = Instant.parse(START_INSTANT_STR);
        Instant end   = startInstant.plusSeconds(120);

        // Time filter window — messages published during the test are accepted
        Instant acceptFrom  = Instant.now().plusSeconds(25);
        Instant acceptUntil = Instant.now().plusSeconds(120);

        Instant upgradeT = startInstant.plusSeconds(5);
        Instant createChannelT = startInstant.plusSeconds(15);
        Instant subscribeFreeT = startInstant.plusSeconds(25);
        Instant subscribeStdT = startInstant.plusSeconds(35);
        Instant publishFreeT = startInstant.plusSeconds(45);
        Instant publishStdT = startInstant.plusSeconds(55);
        Instant modifyAuthT = startInstant.plusSeconds(60);
        Instant subscribePrmT = startInstant.plusSeconds(70);
        Instant publishPrmT = startInstant.plusSeconds(75);
        Instant destroyStdT = startInstant.plusSeconds(80);
        Instant publishStdFailT = startInstant.plusSeconds(90);
        Instant unregisterT = startInstant.plusSeconds(95);






        // Regex for std-channel — C1, C4, C7 (and C3 as owner)
        String stdRegex  = "^(" + C1_SUB  + "-reg|"
                + C4_SUB  + "-reg|"
                + C7_SUB  + "-reg)$";
        // Regex for prem-channel — initially only C1 and C7
        String premRegex = "^(" + C1_SUB + "-reg|" + C7_SUB + "-reg)$";
        // Widened prem-channel regex — adds C4 after modifyAuthorisedUsers
        String premRegex2 = "^(" + C1_SUB + "-reg|"
                + C4_SUB + "-reg|"
                + C7_SUB + "-reg)$";

        return new TestScenario(
                CLOCK_URI, startInstant, end,
                new TestStepI[] {

                        // ── t+5s : C3 upgrades to STANDARD ──────────────────────────
                        new TestStep(CLOCK_URI, C3_PRIV, upgradeT, owner -> {
                            try {
                                ((PrivilegedClient) owner).modifyServiceClass(RegistrationClass.STANDARD);
                                owner.traceMessage("[C3] upgraded to STANDARD\n");
                            } catch (Exception e) {
                                owner.traceMessage("[C3] upgrade failed: " + e + "\n");
                            }
                        }),

                        // ── t+6s : C5 upgrades to PREMIUM ───────────────────────────
                        new TestStep(CLOCK_URI, C5_PRIV, upgradeT, owner -> {
                            try {
                                ((PrivilegedClient) owner).modifyServiceClass(RegistrationClass.PREMIUM);
                                owner.traceMessage("[C5] upgraded to PREMIUM\n");
                            } catch (Exception e) {
                                owner.traceMessage("[C5] upgrade failed: " + e + "\n");
                            }
                        }),

                        // ── t+10s : C3 creates "std-channel" ────────────────────────
                        // gossip CreateChannelGossipMessage → B1, B2, B3, B4
                        new TestStep(CLOCK_URI, C3_PRIV, createChannelT, owner -> {
                            try {
                                ((PrivilegedClient) owner).createChannel(CH_STD, stdRegex);
                                owner.traceMessage("[C3] created '" + CH_STD + "' (gossip→all brokers)\n");
                            } catch (Exception e) {
                                owner.traceMessage("[C3] createChannel failed: " + e + "\n");
                            }
                        }),

                        // ── t+12s : C5 creates "prem-channel" ───────────────────────
                        new TestStep(CLOCK_URI, C5_PRIV, createChannelT, owner -> {
                            try {
                                ((PrivilegedClient) owner).createChannel(CH_PREM, premRegex);
                                owner.traceMessage("[C5] created '" + CH_PREM + "' (gossip→all)\n");
                            } catch (Exception e) {
                                owner.traceMessage("[C5] createChannel failed: " + e + "\n");
                            }
                        }),

                        // ── t+18s : C1 subscribes to free channel with filters ───────
                        new TestStep(CLOCK_URI, C1_SUB, subscribeFreeT, owner -> {
                            try {
                                ((SubscriberClient) owner).subscribe(CH_FREE, acceptAll());
                                owner.traceMessage("[C1] subscribed to '" + CH_FREE
                                        + "' with type=data + time filter\n");
                            } catch (Exception e) {
                                owner.traceMessage("[C1] subscribe free failed: " + e + "\n");
                            }
                        }),

                        // ── t+18s : C4 subscribes to free channel ───────────────────
                        new TestStep(CLOCK_URI, C4_SUB, subscribeFreeT, owner -> {
                            try {

                                ((SubscriberClient) owner).subscribe(CH_FREE, acceptAll());
                                owner.traceMessage("[C4] subscribed to '" + CH_FREE + "'\n");
                            } catch (Exception e) {
                                owner.traceMessage("[C4] subscribe free failed: " + e + "\n");
                            }
                        }),

                        // ── t+18s : C7 subscribes to free channel (via B4, chain B3→B4)
                        new TestStep(CLOCK_URI, C7_SUB, subscribeFreeT, owner -> {
                            try {
                                ((SubscriberClient) owner).subscribe(CH_FREE, acceptAll());
                                owner.traceMessage("[C7] subscribed to '" + CH_FREE
                                        + "' via B4 (gossip chain B3→B4)\n");
                            } catch (Exception e) {
                                owner.traceMessage("[C7] subscribe free failed: " + e + "\n");
                            }
                        }),

                        // ── t+20s : C1 subscribes to std-channel (privileged) ────────
                        // channel known at B1 via gossip
                        new TestStep(CLOCK_URI, C1_SUB, subscribeStdT, owner -> {
                            try {
                                ((SubscriberClient) owner).subscribe(CH_STD, acceptAll());
                                owner.traceMessage("[C1] subscribed to '" + CH_STD
                                        + "' (known via gossip at B1)\n");
                            } catch (Exception e) {
                                owner.traceMessage("[C1] subscribe std failed: " + e + "\n");
                            }
                        }),

                        // ── t+21s : C4 subscribes to std-channel ────────────────────
                        new TestStep(CLOCK_URI, C4_SUB, subscribeStdT, owner -> {
                            try {
                                MessageFilterI f = MessageFilter.acceptAll();
                                ((SubscriberClient) owner).subscribe(CH_STD, f);
                                owner.traceMessage("[C4] subscribed to '" + CH_STD + "'\n");
                            } catch (Exception e) {
                                owner.traceMessage("[C4] subscribe std failed: " + e + "\n");
                            }
                        }),

                        // ── t+22s : C7 subscribes to std-channel (B4 — chain) ────────
                        new TestStep(CLOCK_URI, C7_SUB, subscribeStdT, owner -> {
                            try {
                                MessageFilterI f = MessageFilter.acceptAll();
                                ((SubscriberClient) owner).subscribe(CH_STD, f);
                                owner.traceMessage("[C7] subscribed to '" + CH_STD
                                        + "' (via B4 — chain gossip)\n");
                            } catch (Exception e) {
                                owner.traceMessage("[C7] subscribe std failed: " + e + "\n");
                            }
                        }),

                        // ── t+23s : C8 (intruder) tries to subscribe → must be rejected
                        new TestStep(CLOCK_URI, C8_INTRUDER, subscribeStdT, owner -> {
                            try {
                                ((SubscriberClient) owner).subscribe(CH_STD,
                                        MessageFilter.acceptAll());
                                owner.traceMessage("[C8] ERROR — should have been rejected!\n");
                            } catch (RuntimeException e) {
                                Throwable cause = e.getCause() != null
                                        ? e.getCause().getCause() : null;
                                if (cause instanceof UnauthorisedClientException) {
                                    owner.traceMessage("[C8] correctly rejected from '"
                                            + CH_STD + "' ✓\n");
                                } else {
                                    owner.traceMessage("[C8] unexpected exception: " + e + "\n");
                                }
                            } catch (Exception e) {
                                owner.traceMessage("[C8] unexpected: " + e + "\n");
                            }
                        }),

                        // ── t+28s : C2 publishes on free channel from JVM1 ───────────
                        // Expected delivery: C1(B1 local), C4(B2 via gossip),
                        //                   C7(B4 via chain B1→B3→B4)
                        new TestStep(CLOCK_URI, C2_PUB, publishFreeT, owner -> {
                            try {
                                MessageI m = new Message("data-from-c2");
                                m.putProperty("type",   "data");
                                m.putProperty("source", "JVM1");
                                ((PublisherClient) owner).publish(CH_FREE, m);
                                owner.traceMessage("[C2] published on '" + CH_FREE
                                        + "' (cross-JVM gossip test)\n");
                            } catch (Exception e) {
                                owner.traceMessage("[C2] publish failed: " + e + "\n");
                            }
                        }),


                        // ── t+36s : C3 publishes on std-channel (privileged) ─────────
                        // Expected: C1(B1 via gossip), C4(B2 local), C7(B4 via chain)
                        new TestStep(CLOCK_URI, C3_PRIV, publishStdT, owner -> {
                            try {
                                MessageI m = new Message("std-alert");
                                m.putProperty("type",  "alert");
                                m.putProperty("level", "ORANGE");
                                ((PrivilegedClient) owner).publish(CH_STD, m);
                                owner.traceMessage("[C3] published on '" + CH_STD
                                        + "' — cross-JVM privileged\n");
                            } catch (Exception e) {
                                owner.traceMessage("[C3] publish std failed: " + e + "\n");
                            }
                        }),

                        // ── t+42s : C5 widens prem-channel authorisation (adds C4) ───
                        // gossip ModifyAuthorisedUsersGossipMessage → all brokers
                        new TestStep(CLOCK_URI, C5_PRIV, modifyAuthT, owner -> {
                            try {
                                ((PrivilegedClient) owner).modifyAuthorisedUsers(
                                        CH_PREM, premRegex2);
                                owner.traceMessage("[C5] widened '" + CH_PREM
                                        + "' authorisation (gossip ModifyAuthorisedUsers)\n");
                            } catch (Exception e) {
                                owner.traceMessage("[C5] modifyAuthorisedUsers failed: " + e + "\n");
                            }
                        }),

                        // ── t+46s : C4 subscribes to prem-channel (now authorised) ───
                        new TestStep(CLOCK_URI, C4_SUB, subscribePrmT, owner -> {
                            try {
                                ((SubscriberClient) owner).subscribe(CH_PREM,
                                        MessageFilter.acceptAll());
                                owner.traceMessage("[C4] subscribed to '" + CH_PREM
                                        + "' after authorisation update\n");
                            } catch (Exception e) {
                                owner.traceMessage("[C4] subscribe prem failed: " + e + "\n");
                            }
                        }),

                        // ── t+50s : C5 publishes on prem-channel ─────────────────────
                        new TestStep(CLOCK_URI, C5_PRIV, publishPrmT, owner -> {
                            try {
                                MessageI m = new Message("prem-data");
                                m.putProperty("type",    "prem");
                                m.putProperty("quality", "high");
                                ((PrivilegedClient) owner).publish(CH_PREM, m);
                                owner.traceMessage("[C5] published on '" + CH_PREM + "'\n");
                            } catch (Exception e) {
                                owner.traceMessage("[C5] publish prem failed: " + e + "\n");
                            }
                        }),


                        // ── t+62s : C3 destroys std-channel ─────────────────────────
                        // gossip DestroyChannelGossipMessage → all brokers
                        new TestStep(CLOCK_URI, C3_PRIV, destroyStdT, owner -> {
                            try {
                                ((PrivilegedClient) owner).destroyChannel(CH_STD);
                                owner.traceMessage("[C3] destroyed '" + CH_STD
                                        + "' (gossip DestroyChannel→all)\n");
                            } catch (Exception e) {
                                owner.traceMessage("[C3] destroyChannel failed: " + e + "\n");
                            }
                        }),

                        new TestStep(CLOCK_URI, C3_PRIV, publishStdFailT, owner -> {
                            try {
                                MessageI m = new Message("std-alert");
                                m.putProperty("type",  "alert");
                                m.putProperty("level", "ORANGE");
                                ((PrivilegedClient) owner).publish(CH_STD, m);
                                owner.traceMessage("[C3] published on '" + CH_STD
                                        + "' — cross-JVM privileged\n");
                            } catch (RuntimeException e) {
                                Throwable cause = e.getCause() != null
                                        ? e.getCause().getCause() : null;
                                if (cause instanceof UnknownChannelException) {
                                    owner.traceMessage("[C3] correctly rejected from publishing on destroyed channel✓\n");
                                } else {
                                    owner.traceMessage("[C3] unexpected exception: " + e + "\n");
                                }
                            } catch (Exception e) {
                                owner.traceMessage("[C3] unexpected: " + e + "\n");
                            }

                        }),

                        // ── t+70s : C6 unregisters ───────────────────────────────────
                        // gossip UnregisterGossipMessage → all brokers
                        new TestStep(CLOCK_URI, C6_PUB, unregisterT, owner -> {
                            try {
                                ((PublisherClient) owner).unregister();
                                owner.traceMessage("[C6] unregistered (gossip Unregister→all)\n");
                            } catch (Exception e) {
                                owner.traceMessage("[C6] unregister failed: " + e + "\n");
                            }
                        }),
                });
    }

    // =========================================================================
    // initialise
    // =========================================================================

    @Override
    public void initialise() throws Exception {
        super.initialise();
    }

    // =========================================================================
    // instantiateAndPublish
    // =========================================================================

    @Override
    public void instantiateAndPublish() throws Exception {
        TestScenario.VERBOSE = true;
        TestScenario.DEBUG   = true;

        TestScenario ts = buildScenario();

        if (thisJVMURI.equals(JVM1)) {

            // B1 — neighbours: B2, B3
            List<String> broker1Neighbors = new ArrayList<>();
            broker1Neighbors.add(B2 + Broker.GOSSIP_INBOUND_PORT_URI_SUFFIX);
            broker1Neighbors.add(B3 + Broker.GOSSIP_INBOUND_PORT_URI_SUFFIX);

            createBroker(B1, broker1Neighbors);

            createSubscriber(C1_SUB, B1, ts, RegistrationClass.FREE);
            createPublisher(C2_PUB,  B1, ts, RegistrationClass.FREE);

        } else if (thisJVMURI.equals(JVM2)) {

            // B2 — neighbours: B1, B3
            List<String> broker2Neighbors = new ArrayList<>();
            broker2Neighbors.add(B1 + Broker.GOSSIP_INBOUND_PORT_URI_SUFFIX);
            broker2Neighbors.add(B3 + Broker.GOSSIP_INBOUND_PORT_URI_SUFFIX);
            createBroker(B2, broker2Neighbors);

            createPrivileged(C3_PRIV, B2, ts, RegistrationClass.FREE);
            createSubscriber(C4_SUB,  B2, ts, RegistrationClass.FREE);

        } else if (thisJVMURI.equals(JVM3)) {

            // B3 — neighbours: B1, B2, B4  (hub of the topology)
            List<String> broker3Neighbors = new ArrayList<>();
            broker3Neighbors.add(B1 + Broker.GOSSIP_INBOUND_PORT_URI_SUFFIX);
            broker3Neighbors.add(B2 + Broker.GOSSIP_INBOUND_PORT_URI_SUFFIX);
            broker3Neighbors.add(B4 + Broker.GOSSIP_INBOUND_PORT_URI_SUFFIX);

            createBroker(B3, broker3Neighbors);

            createPrivileged(C5_PRIV, B3, ts, RegistrationClass.FREE);
            createPublisher(C6_PUB,   B3, ts, RegistrationClass.FREE);

            // ClocksServer — unique, shared across all JVMs via RMI
            long unixStart = TimeUnit.MILLISECONDS.toNanos(
                    System.currentTimeMillis() + DELAY_TO_START_MS);
            AbstractComponent.createComponent(
                    ClocksServer.class.getCanonicalName(),
                    new Object[] {
                            CLOCK_URI,
                            unixStart,
                            Instant.parse(START_INSTANT_STR),
                            ACCELERATION_FACTOR
                    });

        } else if (thisJVMURI.equals(JVM4)) {

            // B4 — neighbour: B3 only  (leaf, tests chain propagation)
            List<String> broker4Neighbors = new ArrayList<>();
            broker4Neighbors.add(B3 + Broker.GOSSIP_INBOUND_PORT_URI_SUFFIX);
            createBroker(B4, broker4Neighbors);

            createSubscriber(C7_SUB,      B4, ts, RegistrationClass.FREE);
            createSubscriber(C8_INTRUDER, B4, ts, RegistrationClass.FREE);

        } else {
            System.out.println("Unknown JVM URI: " + thisJVMURI);
        }

        super.instantiateAndPublish();
    }

    // =========================================================================
    // interconnect — gossip connections are made in Broker.start()
    // =========================================================================

    @Override
    public void interconnect() throws Exception {
        super.interconnect();
    }

    // =========================================================================
    // Helper factory methods
    // =========================================================================

    private void createBroker(String uri, List<String> gossipNeighbors)
            throws Exception
    {
        AbstractComponent.createComponent(
                Broker.class.getCanonicalName(),
                new Object[] {
                        uri,          // reflectionInboundPortURI
                        2, 1,         // nbThreads, nbSchedulableThreads
                        3, 2, 5,      // nbFreeChannels, standardQuota, premiumQuota
                        2, 4, 8,      // nbReceptionThreads, nbPropagationThreads, nbDeliveryThreads
                        gossipNeighbors
                });
    }

    private void createSubscriber(String uri, String brokerURI, TestScenario ts,
                                  RegistrationClass rc) throws Exception {
        AbstractComponent.createComponent(
                SubscriberClient.class.getCanonicalName(),
                new Object[] { uri, brokerURI, ts, rc });
    }

    private void createPublisher(String uri, String brokerURI, TestScenario ts,
                                 RegistrationClass rc) throws Exception {
        AbstractComponent.createComponent(
                PublisherClient.class.getCanonicalName(),
                new Object[] { uri, brokerURI, ts, rc });
    }

    private void createPrivileged(String uri, String brokerURI, TestScenario ts,
                                  RegistrationClass rc) throws Exception {
        AbstractComponent.createComponent(
                PrivilegedClient.class.getCanonicalName(),
                new Object[] { uri, brokerURI, ts, rc });
    }

    // =========================================================================
    // Main
    // =========================================================================
	/**
	 * Point d'entrée standalone : démarre le cycle de vie centralisé du CVM
	 * pendant la durée codée en dur, puis termine la JVM.
	 *
	 * @param args ignorés.
	 */

    public static void main(String[] args)
    {
        try {
            DemoFinal dcvm = new DemoFinal(args);
            // 20s startup + 85s scenario + 15s safety margin
            dcvm.startStandardLifeCycle(160_000L);
            Thread.sleep(5_000L);
            System.exit(0);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}