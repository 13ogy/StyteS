package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.cvm.AbstractCVM;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.components.utils.tests.TestStep;
import fr.sorbonne_u.components.utils.tests.TestStepI;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.base.components.PluginClient;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.messages.MessageFilter;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;
import fr.sorbonne_u.cps.pubsub.messages.Message;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Scénario d'intégration temporisé pour l'audit 2 (CDC §4.4) en utilisant
 * l'outil de tests BCM4Java (Annexe B : {@link TestScenario} + {@link ClocksServer}).
 *
 * <p>
 * Objectifs démontrés :
 * </p>
 * <ul>
 *   <li>Appels asynchrones pour la publication (pipeline asynchrone dans le broker).</li>
 *   <li>Parallélisme explicite via trois pools de threads (réception/propagation/livraison).</li>
 *   <li>Concurrence maîtrisée (sections critiques dans le broker).</li>
 *   <li>Modes de réception avancés côté client : {@code getNextMessage} et
 *       {@code waitForNextMessage(Duration)}.</li>
 * </ul>
 */
public class DemoAudit2TimedScenario extends AbstractCVM
{
	public static final String CLOCK_URI = "audit2-clock";
	public static final String START_INSTANT = "2026-02-01T09:00:00.00Z";
	public static final String END_INSTANT = "2026-02-01T09:00:30.00Z";
	public static final double ACCELERATION_FACTOR = 10.0;
	public static final long DELAY_TO_START_MS = 1000L;

	public static final String CLIENT_A_RIP_URI = "audit2-client-A";
	public static final String CLIENT_B_RIP_URI = "audit2-client-B";
	public static final String CHANNEL = "channel0";

	public DemoAudit2TimedScenario() throws Exception
	{
		super();
	}

	/** Filtre "accepte tout" pour simplifier le scénario. */
	protected static MessageFilterI acceptAllMessageFilter()
	{
		return new MessageFilter(
			new MessageFilterI.PropertyFilterI[] {},
			new MessageFilterI.PropertiesFilterI[] {},
			timestamp -> true);
	}

	public static TestScenario testScenario() throws Exception
	{
		Instant start = Instant.parse(START_INSTANT);
		Instant end = Instant.parse(END_INSTANT);

		Instant tRegister = start.plusSeconds(1);
		Instant tSubscribe = start.plusSeconds(2);
		Instant tFuture = start.plusSeconds(3);
		Instant tPublishA = start.plusSeconds(4);
		Instant tPublishB = start.plusSeconds(4);
		Instant tTimedWait = start.plusSeconds(5);

		return new TestScenario(
			CLOCK_URI,
			"[Audit2Timed] DÉBUT du scénario temporisé (audit 2)",
			"[Audit2Timed] FIN du scénario temporisé (audit 2)",
			start,
			end,
			new TestStepI[] {
				new TestStep(CLOCK_URI, CLIENT_A_RIP_URI, tRegister, owner -> {
					try {
						System.out.println("[Audit2Timed][STEP@" + tRegister + "] A : enregistrement PREMIUM");
						((PluginClient) owner).register(RegistrationClass.PREMIUM);
						owner.traceMessage("[Audit2Timed] A enregistré (PREMIUM)\n");
					} catch (Exception e) {
						owner.traceMessage("[Audit2Timed] A : échec enregistrement : " + e + "\n");
					}
				}),
				new TestStep(CLOCK_URI, CLIENT_B_RIP_URI, tRegister, owner -> {
					try {
						System.out.println("[Audit2Timed][STEP@" + tRegister + "] B : enregistrement PREMIUM");
						((PluginClient) owner).register(RegistrationClass.PREMIUM);
						owner.traceMessage("[Audit2Timed] B enregistré (PREMIUM)\n");
					} catch (Exception e) {
						owner.traceMessage("[Audit2Timed] B : échec enregistrement : " + e + "\n");
					}
				}),
				new TestStep(CLOCK_URI, CLIENT_A_RIP_URI, tSubscribe, owner -> {
					try {
						System.out.println("[Audit2Timed][STEP@" + tSubscribe + "] A : subscribe sur " + CHANNEL);
						((PluginClient) owner).subscribe(CHANNEL, acceptAllMessageFilter());
						owner.traceMessage("[Audit2Timed] A : abonné à " + CHANNEL + "\n");
					} catch (Exception e) {
						owner.traceMessage("[Audit2Timed] A : échec subscribe : " + e + "\n");
					}
				}),
				new TestStep(CLOCK_URI, CLIENT_B_RIP_URI, tSubscribe, owner -> {
					try {
						System.out.println("[Audit2Timed][STEP@" + tSubscribe + "] B : subscribe sur " + CHANNEL);
						((PluginClient) owner).subscribe(CHANNEL, acceptAllMessageFilter());
						owner.traceMessage("[Audit2Timed] B : abonné à " + CHANNEL + "\n");
					} catch (Exception e) {
						owner.traceMessage("[Audit2Timed] B : échec subscribe : " + e + "\n");
					}
				}),
				new TestStep(CLOCK_URI, CLIENT_B_RIP_URI, tFuture, owner -> {
					try {
						System.out.println("[Audit2Timed][STEP@" + tFuture + "] B : getNextMessage(" + CHANNEL + ") (Future)");
						PluginClient c = (PluginClient) owner;
						Future<?> f = c.subscriptionPlugin.getNextMessage(CHANNEL);
						owner.traceMessage("[Audit2Timed] B : Future getNextMessage() créé\n");
						Object msg = f.get(2, TimeUnit.SECONDS);
						owner.traceMessage("[Audit2Timed] B : Future terminé => " + msg + "\n");
					} catch (Exception e) {
						owner.traceMessage("[Audit2Timed] B : Future en échec/timeout : " + e + "\n");
					}
				}),
				new TestStep(CLOCK_URI, CLIENT_A_RIP_URI, tPublishA, owner -> {
					try {
						System.out.println("[Audit2Timed][STEP@" + tPublishA + "] A : publish async sur " + CHANNEL);
						((PluginClient) owner).publish(CHANNEL, new Message("A-msg"));
						owner.traceMessage("[Audit2Timed] A : publish envoyé (asynchrone)\n");
					} catch (Exception e) {
						owner.traceMessage("[Audit2Timed] A : échec publish : " + e + "\n");
					}
				}),
				new TestStep(CLOCK_URI, CLIENT_B_RIP_URI, tPublishB, owner -> {
					try {
						System.out.println("[Audit2Timed][STEP@" + tPublishB + "] B : publish async sur " + CHANNEL);
						((PluginClient) owner).publish(CHANNEL, new Message("B-msg"));
						owner.traceMessage("[Audit2Timed] B : publish envoyé (asynchrone)\n");
					} catch (Exception e) {
						owner.traceMessage("[Audit2Timed] B : échec publish : " + e + "\n");
					}
				}),
				new TestStep(CLOCK_URI, CLIENT_A_RIP_URI, tTimedWait, owner -> {
					try {
						System.out.println("[Audit2Timed][STEP@" + tTimedWait + "] A : waitForNextMessage(" + CHANNEL + ", 2s)");
						PluginClient c = (PluginClient) owner;
						Object msg = c.subscriptionPlugin.waitForNextMessage(CHANNEL, Duration.ofSeconds(2));
						owner.traceMessage("[Audit2Timed] A : waitForNextMessage => " + msg + "\n");
					} catch (Exception e) {
						owner.traceMessage("[Audit2Timed] A : échec timed wait : " + e + "\n");
					}
				})
			});
	}

	@Override
	public void deploy() throws Exception
	{
		TestScenario.VERBOSE = true;
		TestScenario.DEBUG = true;

		System.out.println("[Audit2Timed] Déploiement du CVM : création du broker + clients + horloge accélérée + runner...\n");

		AbstractComponent.createComponent(Broker.class.getCanonicalName(), new Object[] { 4, 0 });

		TestScenario ts = testScenario();
		long current = System.currentTimeMillis();
		long unixEpochStartTimeInNanos = TimeUnit.MILLISECONDS.toNanos(current + DELAY_TO_START_MS);
		Instant startInstant = Instant.parse(START_INSTANT);
		AbstractComponent.createComponent(
			ClocksServer.class.getCanonicalName(),
			new Object[] { CLOCK_URI, unixEpochStartTimeInNanos, startInstant, ACCELERATION_FACTOR });

		AbstractComponent.createComponent(
			PluginClient.class.getCanonicalName(),
			new Object[] { CLIENT_A_RIP_URI, 2, 0 });
		AbstractComponent.createComponent(
			PluginClient.class.getCanonicalName(),
			new Object[] { CLIENT_B_RIP_URI, 2, 0 });

		// Participant "runner" qui exécute les étapes du scénario qui lui sont
		// affectées (et uniquement celles-ci) : il doit donc être un PluginClient
		// exécutant executeTestScenario comme les autres participants.
		AbstractComponent.createComponent(
			ScenarioPluginClient.class.getCanonicalName(),
			new Object[] { "audit2-scenario-runner", ts, 1, 0 });

		super.deploy();

		// -----------------------------------------------------------------
		// Activation du tracing + logging pour tous les composants.
		// -----------------------------------------------------------------
		// NOTE: le broker n'a pas d'URI de port de réflexion explicite passé dans
		// createComponent(...) ; on n'active donc ici tracing/logging que pour les
		// participants pour lesquels on connaît l'URI.

		this.toggleTracing(CLIENT_A_RIP_URI);
		this.toggleLogging(CLIENT_A_RIP_URI);

		this.toggleTracing(CLIENT_B_RIP_URI);
		this.toggleLogging(CLIENT_B_RIP_URI);

		this.toggleTracing("audit2-scenario-runner");
		this.toggleLogging("audit2-scenario-runner");

		// Le CVM n'est pas un composant BCM (pas de traceMessage/logMessage).
		// Pour avoir du contenu, on produit des traces via les participants
		// (voir les TestStep qui appellent owner.traceMessage(...)).
	}

	public static void main(String[] args)
	{
		try {
			DemoAudit2TimedScenario cvm = new DemoAudit2TimedScenario();
			cvm.startStandardLifeCycle(8000L);
			System.exit(0);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
