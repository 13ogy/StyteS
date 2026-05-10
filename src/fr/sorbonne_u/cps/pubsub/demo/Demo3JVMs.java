package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.cvm.AbstractDistributedCVM;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.components.utils.tests.TestStep;
import fr.sorbonne_u.components.utils.tests.TestStepI;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.base.components.PrivilegedClient;
import fr.sorbonne_u.cps.pubsub.base.components.SubscriberClient;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
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
 * CVM réparti sur <strong>trois JVM</strong> illustrant le protocole de bavardage (gossip, CDC
 * §3.7) entre brokers fédérés en chaîne {@code B1 ↔ B2 ↔ B3}.
 *
 * <p>Découpage des composants par JVM (les URI sont déclarées dans {@code
 * deployment/config/config3.xml}) :
 *
 * <ul>
 *   <li><strong>{@value #BROKER1_JVM_URI}</strong> — Broker B1 (voisin gossip : B2) + {@link
 *       SubscriberClient} C1 (FREE) + {@link ClocksServer}.
 *   <li><strong>{@value #BROKER2_JVM_URI}</strong> — Broker B2 (voisins gossip : B1, B3) + {@link
 *       SubscriberClient} C2 (FREE).
 *   <li><strong>{@value #BROKER3_JVM_URI}</strong> — Broker B3 (voisin gossip : B2) + {@link
 *       PrivilegedClient} C3 (FREE → STANDARD).
 * </ul>
 *
 * <p>Le scénario démontre que l'autorisation par {@code authorisedUsers} et la fédération gossip
 * coopèrent correctement : la modification d'autorisation publiée par C3 (broker B3) est propagée
 * jusqu'à B1/B2 via le bavardage avant que C2 (sur B2) puisse souscrire au canal privilégié.
 *
 * <p>Lancement (3 fenêtres terminal) :
 *
 * <pre>
 *   java -cp out:libs/* fr.sorbonne_u.cps.pubsub.demo.Demo3JVMs broker1-jvm deployment/config/config3.xml
 *   java -cp out:libs/* fr.sorbonne_u.cps.pubsub.demo.Demo3JVMs broker2-jvm deployment/config/config3.xml
 *   java -cp out:libs/* fr.sorbonne_u.cps.pubsub.demo.Demo3JVMs broker3-jvm deployment/config/config3.xml
 * </pre>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class Demo3JVMs extends AbstractDistributedCVM {

	// -------------------------------------------------------------------------
	// JVM URIs (must match config3.xml)
	// -------------------------------------------------------------------------

	public static final String BROKER1_JVM_URI = "broker1-jvm";
	public static final String BROKER2_JVM_URI = "broker2-jvm";
	public static final String BROKER3_JVM_URI = "broker3-jvm";

	// -------------------------------------------------------------------------
	// Component URIs
	// -------------------------------------------------------------------------

	public static final String BROKER1_URI = "broker-1";
	public static final String BROKER2_URI = "broker-2";
	public static final String BROKER3_URI = "broker-3";

	public static final String CLIENT1_URI = "client-1";
	public static final String CLIENT2_URI = "client-2";
	public static final String CLIENT3_URI = "client-3";

	/** Canal privilégié créé par C3 pendant le scénario. */
	public static final String CHANNEL = "gossip-channel";

	// -------------------------------------------------------------------------
	// Clock configuration
	// -------------------------------------------------------------------------

	public static final String CLOCK_URI = "gossip-test-clock";
	public static final String START_INSTANT_STR = "2026-02-01T10:00:00.00Z";
	public static final double ACCELERATION_FACTOR = 1.0;
	public static final long DELAY_TO_START_MS = 15_000L;

	/**
	 * Construit le CVM réparti.
	 *
	 * @param args {@code args[0]} = URI de cette JVM dans l'assemblage ; {@code args[1]} = chemin
	 *     du fichier XML de configuration.
	 * @throws Exception si la construction du superclasse échoue.
	 */
	public Demo3JVMs(String[] args) throws Exception {
		super(args);
	}

	@Override
	public void initialise() throws Exception {
		super.initialise();
	}

	/**
	 * Construit le scénario temporisé partagé par tous les composants client.
	 *
	 * <p>Étapes (instants relatifs à {@value #START_INSTANT_STR}, temps réel — accélération = 1.0)
	 * :
	 *
	 * <ol>
	 *   <li>C3 monte en classe STANDARD ;
	 *   <li>C3 crée le canal privilégié n'autorisant que C1 ;
	 *   <li>C1 souscrit au canal — succès ;
	 *   <li>C2 tente de souscrire — échec attendu ({@link UnauthorisedClientException}) ;
	 *   <li>C3 publie un message — seul C1 le reçoit ;
	 *   <li>C3 modifie {@code authorisedUsers} pour inclure C2 (propagé via gossip à B2) ;
	 *   <li>C2 souscrit — succès cette fois ;
	 *   <li>C3 publie — C1 et C2 reçoivent ;
	 *   <li>C2 se désinscrit ;
	 *   <li>C3 publie — seul C1 reçoit ;
	 *   <li>C3 détruit le canal.
	 * </ol>
	 *
	 * @return le {@link TestScenario} entièrement configuré.
	 * @throws Exception si la construction du scénario échoue.
	 */
	public static TestScenario testScenario() throws Exception {
		Instant startInstant = Instant.parse(START_INSTANT_STR);
		Instant endInstant = startInstant.plusSeconds(120);

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
				new TestStepI[] {
					new TestStep(
							CLOCK_URI,
							CLIENT3_URI,
							upgradeC3Instant,
							owner -> {
								try {
									((PrivilegedClient) owner)
											.modifyServiceClass(
													RegistrationCI.RegistrationClass.STANDARD);
									owner.traceMessage("[Test gossip] C3 upgraded to STANDARD\n");
								} catch (Exception e) {
									owner.logMessage(
											"[Test gossip] C3 upgrade STANDARD failed: "
													+ e
													+ "\n");
								}
							}),
					new TestStep(
							CLOCK_URI,
							CLIENT3_URI,
							createChannelC3Instant,
							owner -> {
								try {
									String regex = "^(" + CLIENT1_URI + "-reg)$";
									((PrivilegedClient) owner).createChannel(CHANNEL, regex);
									owner.traceMessage(
											"[Test gossip] C3 created '"
													+ CHANNEL
													+ "' (only C1)\n");
								} catch (Exception e) {
									owner.logMessage(
											"[Test gossip] C3 createChannel failed: " + e + "\n");
								}
							}),
					new TestStep(
							CLOCK_URI,
							CLIENT1_URI,
							subscribeC1Instant,
							owner -> {
								try {
									((SubscriberClient) owner).subscribe(CHANNEL, acceptAll());
									owner.traceMessage(
											"[Test gossip] C1 subscribed to '" + CHANNEL + "'\n");
								} catch (Exception e) {
									owner.logMessage(
											"[Test gossip] C1 subscribe failed: " + e + "\n");
								}
							}),
					new TestStep(
							CLOCK_URI,
							CLIENT2_URI,
							subscribeC2Instant,
							owner -> {
								try {
									((SubscriberClient) owner).subscribe(CHANNEL, acceptAll());
									owner.traceMessage(
											"[Test gossip] C2 unexpectedly subscribed to '"
													+ CHANNEL
													+ "'\n");
								} catch (Exception e) {
									Throwable cause =
											e.getCause() != null ? e.getCause().getCause() : null;
									if (cause instanceof UnauthorisedClientException) {
										owner.traceMessage(
												"[Test gossip] C2 correctly rejected from '"
														+ CHANNEL
														+ "' \u2713\n");
									} else {
										owner.traceMessage(
												"[Test gossip] C2 unexpected exception: "
														+ e
														+ "\n");
									}
								}
							}),
					new TestStep(
							CLOCK_URI,
							CLIENT3_URI,
							publishC3Instant,
							owner -> {
								try {
									MessageI m = new Message("gossip-msg1");
									m.putProperty("publisher", "C3");
									((PrivilegedClient) owner).publish(CHANNEL, m);
									owner.traceMessage(
											"[Test gossip] C3 published " + m.getPayload() + "\n");
								} catch (Exception e) {
									owner.traceMessage(
											"[Test gossip] C3 publish failed: " + e + "\n");
								}
							}),
					new TestStep(
							CLOCK_URI,
							CLIENT3_URI,
							authInstant,
							owner -> {
								try {
									String regex =
											"^(" + CLIENT1_URI + "-reg|" + CLIENT2_URI + "-reg)$";
									((PrivilegedClient) owner)
											.modifyAuthorisedUsers(CHANNEL, regex);
									owner.traceMessage(
											"[Test gossip] C3 modified authorised users (now"
												+ " C1+C2)\n");
								} catch (Exception e) {
									owner.traceMessage(
											"[Test gossip] C3 modifyAuthorisedUsers failed: "
													+ e
													+ "\n");
								}
							}),
					new TestStep(
							CLOCK_URI,
							CLIENT2_URI,
							subscribeC2Instant2,
							owner -> {
								try {
									((SubscriberClient) owner).subscribe(CHANNEL, acceptAll());
									owner.traceMessage(
											"[Test gossip] C2 subscribed to '" + CHANNEL + "'\n");
								} catch (Exception e) {
									owner.logMessage(
											"[Test gossip] C2 subscribe failed: " + e + "\n");
								}
							}),
					new TestStep(
							CLOCK_URI,
							CLIENT3_URI,
							publishC3Instant2,
							owner -> {
								try {
									MessageI m = new Message("gossip-msg2");
									m.putProperty("publisher", "C3");
									((PrivilegedClient) owner).publish(CHANNEL, m);
									owner.traceMessage(
											"[Test gossip] C3 published " + m.getPayload() + "\n");
								} catch (Exception e) {
									owner.traceMessage(
											"[Test gossip] C3 publish failed: " + e + "\n");
								}
							}),
					new TestStep(
							CLOCK_URI,
							CLIENT2_URI,
							unregisterC2Instant,
							owner -> {
								try {
									((SubscriberClient) owner).unregister();
									owner.traceMessage(
											"[Test gossip] C2 unregistered (gossip Unregister"
												+ " \u2192 all)\n");
								} catch (Exception e) {
									owner.traceMessage(
											"[Test gossip] C2 unregister failed: " + e + "\n");
								}
							}),
					new TestStep(
							CLOCK_URI,
							CLIENT3_URI,
							publishC1Instant,
							owner -> {
								try {
									MessageI m = new Message("gossip-msg3");
									m.putProperty("publisher", "C3");
									((PrivilegedClient) owner).publish(CHANNEL, m);
									owner.traceMessage(
											"[Test gossip] C3 published " + m.getPayload() + "\n");
								} catch (Exception e) {
									owner.traceMessage(
											"[Test gossip] C3 publish failed: " + e + "\n");
								}
							}),
					new TestStep(
							CLOCK_URI,
							CLIENT3_URI,
							destroyInstant,
							owner -> {
								try {
									((PrivilegedClient) owner).destroyChannel(CHANNEL);
									owner.traceMessage(
											"[Test gossip] C3 destroyed '" + CHANNEL + "'\n");
								} catch (Exception e) {
									owner.traceMessage(
											"[Test gossip] C3 destroyChannel failed: " + e + "\n");
								}
							}),
				});
	}

	/** Filtre acceptant tous les messages — utilisé par les souscriptions du scénario. */
	protected static MessageFilterI acceptAll() {
		return new MessageFilter(
				new MessageFilterI.PropertyFilterI[0],
				new MessageFilterI.PropertiesFilterI[0],
				new AcceptAllTimeFilter());
	}

	/**
	 * Instancie et publie les composants attribués à la JVM courante. Les voisins gossip d'un
	 * broker sont calculés à partir des URI des autres brokers via {@link
	 * Broker#GOSSIP_INBOUND_PORT_URI_SUFFIX}.
	 *
	 * @throws Exception si la création ou la publication d'un composant échoue.
	 */
	@Override
	public void instantiateAndPublish() throws Exception {
		TestScenario.VERBOSE = true;
		TestScenario.DEBUG = true;

		TestScenario ts = testScenario();

		if (thisJVMURI.equals(BROKER1_JVM_URI)) {
			List<String> b1Neighbors = new ArrayList<>();
			b1Neighbors.add(BROKER2_URI + Broker.GOSSIP_INBOUND_PORT_URI_SUFFIX);
			AbstractComponent.createComponent(
					Broker.class.getCanonicalName(),
					new Object[] {
						BROKER1_URI,
						2,
						1, // nbThreads, nbSchedulableThreads
						3,
						2,
						5, // nbFreeChannels, standardQuota, premiumQuota
						2,
						4,
						8, // nbReceptionThreads, nbPropagationThreads, nbDeliveryThreads
						b1Neighbors
					});

			AbstractComponent.createComponent(
					SubscriberClient.class.getCanonicalName(),
					new Object[] {
						CLIENT1_URI, BROKER1_URI, ts, RegistrationCI.RegistrationClass.FREE
					});

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
			List<String> b2Neighbors = new ArrayList<>();
			b2Neighbors.add(BROKER1_URI + Broker.GOSSIP_INBOUND_PORT_URI_SUFFIX);
			b2Neighbors.add(BROKER3_URI + Broker.GOSSIP_INBOUND_PORT_URI_SUFFIX);
			AbstractComponent.createComponent(
					Broker.class.getCanonicalName(),
					new Object[] {BROKER2_URI, 2, 1, 3, 2, 5, 2, 4, 8, b2Neighbors});

			AbstractComponent.createComponent(
					SubscriberClient.class.getCanonicalName(),
					new Object[] {
						CLIENT2_URI, BROKER2_URI, ts, RegistrationCI.RegistrationClass.FREE
					});

		} else if (thisJVMURI.equals(BROKER3_JVM_URI)) {
			List<String> b3Neighbors = new ArrayList<>();
			b3Neighbors.add(BROKER2_URI + Broker.GOSSIP_INBOUND_PORT_URI_SUFFIX);
			AbstractComponent.createComponent(
					Broker.class.getCanonicalName(),
					new Object[] {BROKER3_URI, 2, 1, 3, 2, 5, 2, 4, 8, b3Neighbors});

			AbstractComponent.createComponent(
					PrivilegedClient.class.getCanonicalName(),
					new Object[] {
						CLIENT3_URI, BROKER3_URI, ts, RegistrationCI.RegistrationClass.FREE
					});

		} else {
			System.err.println("Demo3JVMs: unknown JVM URI '" + thisJVMURI + "'");
		}

		super.instantiateAndPublish();
	}

	@Override
	public void interconnect() throws Exception {
		super.interconnect();
	}

	/**
	 * Point d'entrée. Démarre le cycle BCM standard avec un budget total de 120 s ({@value
	 * #DELAY_TO_START_MS} ms de démarrage + 100 s de scénario + marge).
	 *
	 * @param args URI de la JVM + chemin du fichier de configuration (transmis au constructeur).
	 */
	public static void main(String[] args) {
		try {
			Demo3JVMs dcvm = new Demo3JVMs(args);
			dcvm.startStandardLifeCycle(120_000L);
			Thread.sleep(5_000L);
			System.exit(0);
		} catch (Throwable e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
