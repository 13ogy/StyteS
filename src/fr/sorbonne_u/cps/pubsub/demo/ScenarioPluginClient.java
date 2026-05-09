package fr.sorbonne_u.cps.pubsub.demo;

import fr.sorbonne_u.cps.pubsub.base.components.PluginClient;
import fr.sorbonne_u.components.annotations.OfferedInterfaces;
import fr.sorbonne_u.components.annotations.RequiredInterfaces;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

/**
 * Composant client de démo (TEST-ONLY) : un {@link PluginClient} qui exécute
 * les étapes du {@link TestScenario} qui lui sont assignées via son URI de
 * port de réflexion. Utilisé dans les démos temporisées (audit 2, mid-sem
 * complexe…) comme participant générique.
 *
 * <p>
 * Particularité BCM4Java : {@code executeTestScenario(...)} ne planifie que
 * les étapes du composant qui l'appelle ; chaque participant doit donc
 * appeler {@code executeTestScenario} dans son propre {@link #execute()}.
 * </p>
 *
 * <p>
 * Constructeur recommandé : 5 arguments
 * {@code (reflectionInboundPortURI, scenario, nbThreads, nbSchedulableThreads,
 * brokerReflectionURI)}. Le constructeur 4-arg est conservé pour rétro-compat
 * et marqué {@code @Deprecated} (voir Phase C.3).
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
@OfferedInterfaces(offered = { ReceivingCI.class })
@RequiredInterfaces(required = { RegistrationCI.class, PublishingCI.class, PrivilegedClientCI.class })
public class ScenarioPluginClient extends PluginClient
{
	/** Scénario temporisé partagé ; uniquement les étapes ciblant ce composant sont exécutées. */
	protected final TestScenario scenario;

	/**
	 * Constructeur historique sans URI de broker.
	 *
	 * @param reflectionInboundPortURI URI du port de réflexion (= URI participant).
	 * @param scenario                 scénario à exécuter.
	 * @param nbThreads                taille du pool standard de threads.
	 * @param nbSchedulableThreads     taille du pool schedulable.
	 * @throws Exception si la création du composant échoue.
	 * @deprecated utiliser le constructeur 5-arg avec l'URI du broker (Phase C.3).
	 */
	@Deprecated
	protected ScenarioPluginClient(
			String reflectionInboundPortURI,
			TestScenario scenario,
			int nbThreads,
			int nbSchedulableThreads) throws Exception
	{
		this(reflectionInboundPortURI, scenario, nbThreads, nbSchedulableThreads, null);
	}

	/**
	 * Construit un participant de scénario relié à un broker connu par son URI
	 * de réflexion.
	 *
	 * @param reflectionInboundPortURI URI du port de réflexion (= URI participant
	 *                                 dans le scénario).
	 * @param scenario                 scénario à exécuter.
	 * @param nbThreads                taille du pool standard.
	 * @param nbSchedulableThreads     taille du pool schedulable.
	 * @param brokerReflectionURI      URI du broker auquel se connecter (post C.3).
	 * @throws Exception si la création du composant ou du parent échoue.
	 */
	protected ScenarioPluginClient(
			String reflectionInboundPortURI,
			TestScenario scenario,
			int nbThreads,
			int nbSchedulableThreads,
			String brokerReflectionURI) throws Exception
	{
		super(reflectionInboundPortURI, nbThreads, nbSchedulableThreads, brokerReflectionURI);
		this.scenario = scenario;
	}

	/**
	 * Initialise l'horloge accélérée puis lance l'exécution du scénario si ce
	 * composant y apparaît comme participant.
	 *
	 * @throws Exception si l'initialisation de l'horloge ou l'exécution échoue.
	 */
	@Override
	public void execute() throws Exception
	{
		super.execute();
		if (this.scenario != null && this.scenario.entityAppearsIn(this.getReflectionInboundPortURI())) {
			this.initialiseClock(ClocksServer.STANDARD_INBOUNDPORT_URI, this.scenario.getClockURI());
			// executeTestScenario already waits until the accelerated clock start.
			this.executeTestScenario(this.scenario);
		}
	}

	// ---------------------------------------------------------------------
	// Reception hook override (make receptions always visible in console)
	// ---------------------------------------------------------------------

	/**
	 * Surcharge du hook {@code onReceive} de {@link PluginClient} qui ajoute
	 * une trace console préfixée {@code [MidSemScenario][RECEIVE]} pour rendre
	 * les livraisons visibles pendant la démo.
	 *
	 * @param channel canal sur lequel le message est délivré.
	 * @param message message livré ({@code null} = wakeup signalant un canal vide).
	 */
	@Override
	public void onReceive(String channel, fr.sorbonne_u.cps.pubsub.interfaces.MessageI message)
	{
		super.onReceive(channel, message);
		if (message != null) {
			System.out.print(
				"[MidSemScenario][RECEIVE] " + this.getReflectionInboundPortURI()
					+ " <- " + channel + " payload=" + message.getPayload() + "\n");
		}
	}
}
