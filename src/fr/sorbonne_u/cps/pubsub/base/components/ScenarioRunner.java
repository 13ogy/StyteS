package fr.sorbonne_u.cps.pubsub.base.components;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.exceptions.ComponentShutdownException;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

/**
 * Composant utilitaire (démo) dont la seule responsabilité est d'exécuter un {@link TestScenario}
 * BCM4Java une fois le composant démarré. Utilisé comme « participant exécuteur » dans les démos
 * qui ne veulent pas attribuer le scénario à un client métier (par exemple {@link
 * fr.sorbonne_u.cps.pubsub.demo.DemoMidSemComplexTimedScenario}).
 *
 * <p>Pattern du composant : initialise l'horloge accélérée ({@link
 * ClocksServer#STANDARD_INBOUNDPORT_URI}) puis appelle {@code executeTestScenario(...)}. Le
 * scénario doit déjà avoir été construit avant le déploiement.
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class ScenarioRunner extends AbstractComponent {
	/** Scénario temporisé que ce composant exécutera dans son {@link #execute()}. */
	protected final TestScenario scenario;

	/**
	 * Crée un runner de scénario.
	 *
	 * @param reflectionInboundPortURI URI du port de réflexion BCM (= URI participant dans le
	 *     {@link TestScenario}).
	 * @param scenario scénario à exécuter ({@code null} = aucun).
	 * @param nbThreads taille du pool standard de threads.
	 * @param nbSchedulableThreads taille du pool schedulable (doit être {@code > 0} pour {@code
	 *     executeTestScenario}).
	 * @throws Exception si la création du composant échoue.
	 */
	protected ScenarioRunner(
			String reflectionInboundPortURI,
			TestScenario scenario,
			int nbThreads,
			int nbSchedulableThreads)
			throws Exception {
		super(reflectionInboundPortURI, nbThreads, nbSchedulableThreads);
		this.scenario = scenario;
	}

	/**
	 * Initialise l'horloge accélérée puis exécute le scénario assigné, s'il existe. Appelé par le
	 * cycle de vie BCM après {@code start()}.
	 *
	 * @throws Exception si l'initialisation de l'horloge ou l'exécution échoue.
	 */
	@Override
	public void execute() throws Exception {
		super.execute();
		if (this.scenario != null) {
			this.initialiseClock(
					ClocksServer.STANDARD_INBOUNDPORT_URI, this.scenario.getClockURI());
			this.executeTestScenario(this.scenario);
		}
	}

	/**
	 * Hook BCM standard d'arrêt ; aucune ressource spécifique à libérer ici.
	 *
	 * @throws ComponentShutdownException si le hook parent échoue.
	 */
	@Override
	public void shutdown() throws ComponentShutdownException {
		super.shutdown();
	}
}
