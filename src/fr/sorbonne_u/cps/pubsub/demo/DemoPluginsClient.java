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

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Démonstration minimale de l'API plugin (CDC §3.5) : un publieur et un
 * souscripteur échangent un message via deux greffons composés sur des
 * composants client dédiés à leur rôle.
 *
 * <p>
 * <strong>Pourquoi cette refonte&nbsp;?</strong> La version précédente
 * pilotait les composants depuis {@code executeComponent(...)} en accédant
 * directement à la table interne {@code uri2component} de BCM4Java pour
 * caster en {@code PluginClient} et appeler des méthodes Java directement
 * sur l'instance. C'est exactement l'anti-patron pointé par le jury :
 * <em>« très mal organisé avec des accès directs aux structures de données
 * internes de BCM4Java pour ensuite faire des appels Java directement sur
 * les objets représentant les composants »</em>. Cette nouvelle version
 * utilise le mécanisme canonique BCM4Java de scénario temporisé
 * ({@link TestScenario} + {@link TestStep} + {@link ClocksServer}) déjà
 * employé par {@link DemoSeparatedPlugin}, {@link DemoMidSemComplexTimedScenario}
 * et {@link DemoMeteoTimedTestTool}.
 * </p>
 *
 * <h2>Composants instanciés</h2>
 * <ul>
 *   <li>un {@link Broker} centralisé ;</li>
 *   <li>un {@link ClocksServer} fournissant l'horloge accélérée commune ;</li>
 *   <li>un {@link PublisherClient} (composant cliennt jouant le rôle
 *       publieur, ne charge que les greffons {@code Registration} et
 *       {@code Publication}) ;</li>
 *   <li>un {@link SubscriberClient} (rôle souscripteur, ne charge que les
 *       greffons {@code Registration} et {@code Subscription}).</li>
 * </ul>
 *
 * <h2>Scénario</h2>
 * <ol>
 *   <li>{@code t = +1s} : le souscripteur s'abonne à {@code channel0} avec
 *       un filtre acceptant tout ;</li>
 *   <li>{@code t = +2s} : le publieur publie un message
 *       {@code "hello-from-plugin-client"} sur {@code channel0} ;</li>
 *   <li>la livraison déclenche {@link SubscriberClient#onReceive(String, MessageI)}
 *       qui trace la réception en console.</li>
 * </ol>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class DemoPluginsClient extends AbstractCVM
{
	public static final String  BROKER_URI            = "broker";
	public static final String  PUBLISHER_URI         = "plugin-client-publisher";
	public static final String  SUBSCRIBER_URI        = "plugin-client-subscriber";
	public static final String  CHANNEL               = "channel0";

	public static final String  CLOCK_URI             = "demo-plugins-clock";
	public static final String  START_INSTANT_STR     = "2026-04-22T09:00:00.00Z";
	public static final double  ACCELERATION_FACTOR   = 1.0;
	/** Délai (ms) entre le démarrage du CVM et l'instant de départ de
	 *  l'horloge accélérée ; doit couvrir la phase {@code start()} de tous
	 *  les composants avant que les étapes du scénario ne soient déclenchées. */
	protected static final long DELAY_TO_START_MS     = 8_000L;

	/**
	 * Construit ce CVM ; la création réelle des composants se produit dans
	 * {@link #deploy()}.
	 *
	 * @throws Exception si l'initialisation parent échoue.
	 */
	public DemoPluginsClient() throws Exception
	{
		super();
	}

	// =========================================================================
	// Scenario
	// =========================================================================

	/**
	 * Construit le scénario temporisé partagé : un pas de souscription, un
	 * pas de publication. Aucune étape n'invoque {@code register(...)} :
	 * l'enregistrement reste dans {@link PublisherClient#execute()} /
	 * {@link SubscriberClient#execute()}, conformément à la convention
	 * « register dans execute(), pas dans les étapes du scénario ».
	 *
	 * @return le scénario prêt à être passé aux composants participants.
	 */
	private TestScenario buildScenario()
	{
		Instant start = Instant.parse(START_INSTANT_STR);
		Instant tSub  = start.plusSeconds(1);
		Instant tPub  = start.plusSeconds(2);
		Instant end   = start.plusSeconds(3);

		MessageFilterI acceptAll = new MessageFilter(
				new MessageFilterI.PropertyFilterI[0],
				new MessageFilterI.PropertiesFilterI[0],
				new AcceptAllTimeFilter());

		return new TestScenario(
				"[BEGIN] DemoPluginsClient — minimal plugin exchange",
				"[END]   DemoPluginsClient — exchange complete",
				CLOCK_URI,
				start, end,
				new TestStepI[] {
					new TestStep(CLOCK_URI, SUBSCRIBER_URI, tSub, owner -> {
						try {
							((SubscriberClient) owner).subscribe(CHANNEL, acceptAll);
							((SubscriberClient) owner).traceMessage(
									"subscribed to " + CHANNEL + " ✓\n");
						} catch (Exception e) {
							((SubscriberClient) owner).logMessage(
									"subscribe failed: " + e + "\n");
						}
					}),
					new TestStep(CLOCK_URI, PUBLISHER_URI, tPub, owner -> {
						try {
							MessageI m = new Message("hello-from-plugin-client");
							((PublisherClient) owner).publish(CHANNEL, m);
							((PublisherClient) owner).traceMessage(
									"published on " + CHANNEL + " ✓\n");
						} catch (Exception e) {
							((PublisherClient) owner).logMessage(
									"publish failed: " + e + "\n");
						}
					}),
				});
	}

	// =========================================================================
	// Lifecycle
	// =========================================================================

	/**
	 * Crée le broker, le {@link ClocksServer} et les deux clients basés
	 * plugin. Active le tracing sur les participants pour rendre visible
	 * l'échange en console.
	 *
	 * @throws Exception si la création / publication d'un composant échoue.
	 */
	@Override
	public void deploy() throws Exception
	{
		TestScenario.VERBOSE = true;
		TestScenario.DEBUG   = true;

		TestScenario ts = buildScenario();

		AbstractComponent.createComponent(
				Broker.class.getCanonicalName(),
				new Object[] { BROKER_URI, 2, 1, 3, 2, 5, 2, 4, 8 });

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

		AbstractComponent.createComponent(
				PublisherClient.class.getCanonicalName(),
				new Object[] { PUBLISHER_URI, BROKER_URI, ts, RegistrationClass.FREE });

		AbstractComponent.createComponent(
				SubscriberClient.class.getCanonicalName(),
				new Object[] { SUBSCRIBER_URI, BROKER_URI, ts, RegistrationClass.FREE });

		super.deploy();

		this.toggleTracing(PUBLISHER_URI);
		this.toggleTracing(SUBSCRIBER_URI);
		this.toggleLogging(PUBLISHER_URI);
		this.toggleLogging(SUBSCRIBER_URI);
	}

	/**
	 * Point d'entrée standalone : démarre le cycle de vie centralisé du CVM
	 * pendant la durée codée en dur, puis termine la JVM.
	 *
	 * @param args ignorés.
	 */
	public static void main(String[] args)
	{
		try {
			DemoPluginsClient cvm = new DemoPluginsClient();
			// DELAY_TO_START_MS (8 s) + scenario virtual duration (3 s) / ACCEL
			// + safety margin.
			cvm.startStandardLifeCycle(20_000L);
			System.out.println("ending...");
			System.exit(0);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
