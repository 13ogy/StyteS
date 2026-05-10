package fr.sorbonne_u.cps.pubsub.base.components;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.exceptions.ComponentStartException;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.cps.pubsub.plugins.ClientPublicationPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

import java.util.ArrayList;

/**
 * Composant client de démonstration jouant le rôle de <strong>publieur pur</strong> : enregistré au
 * broker (par défaut FREE) puis publie sur des canaux. Compose deux plugins :
 *
 * <ul>
 *   <li>{@link ClientRegistrationPlugin} — registration + port {@code ReceivingCI} ;
 *   <li>{@link ClientPublicationPlugin} — port sortant {@link
 *       fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI}.
 * </ul>
 *
 * <p><strong>Convention constructeur</strong> : {@code (uri, brokerReflectionURI, scenario,
 * registrationClass)}.
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class PublisherClient extends AbstractComponent {

	// -------------------------------------------------------------------------
	// PublisherClient — registration + publication only
	// -------------------------------------------------------------------------

	private final ClientRegistrationPlugin regPlugin;
	private final ClientPublicationPlugin pubPlugin;
	private final TestScenario testScenario;
	private final RegistrationCI.RegistrationClass initialRC;
	private final String brokerReflectionURI;

	/**
	 * Crée un publieur.
	 *
	 * @param uri URI de port de réflexion / identifiant.
	 * @param brokerReflectionURI URI de réflexion du broker cible.
	 * @param ts scénario temporisé optionnel ({@code null} = aucun).
	 * @param rc classe initiale d'enregistrement.
	 * @throws Exception si la construction échoue.
	 */
	protected PublisherClient(
			String uri,
			String brokerReflectionURI,
			TestScenario ts,
			RegistrationCI.RegistrationClass rc)
			throws Exception {
		super(uri, 1, 1);
		this.testScenario = ts;
		this.initialRC = rc;
		this.brokerReflectionURI = brokerReflectionURI;

		this.regPlugin = new ClientRegistrationPlugin(brokerReflectionURI);
		this.regPlugin.setPluginURI(uri + "-reg");

		this.pubPlugin = new ClientPublicationPlugin(regPlugin, brokerReflectionURI);
		this.pubPlugin.setPluginURI(uri + "-pub");

		this.toggleTracing();
		this.getTracer().setTitle(uri);
	}

	/**
	 * Installe les plugins {@code reg} + {@code pub} avant {@link #execute()}.
	 *
	 * @throws ComponentStartException si un plugin ne peut être installé.
	 */
	@Override
	public synchronized void start() throws ComponentStartException {
		try {
			this.installPlugin(this.regPlugin);
			this.installPlugin(this.pubPlugin);
		} catch (Exception e) {
			throw new ComponentStartException(e);
		}
		super.start();
	}

	/**
	 * Enregistre le client à la classe initiale, initialise l'horloge accélérée et exécute le
	 * scénario si ce composant y apparaît.
	 *
	 * @throws Exception en cas d'échec d'enregistrement, d'horloge ou de scénario.
	 */
	@Override
	public void execute() throws Exception {
		super.execute();
		this.regPlugin.register(this.initialRC);
		this.traceMessage("registered ✓\n");
		if (this.testScenario != null
				&& this.testScenario.entityAppearsIn(this.getReflectionInboundPortURI())) {
			this.initialiseClock(
					ClocksServer.STANDARD_INBOUNDPORT_URI, this.testScenario.getClockURI());
			this.traceMessage("clock initialized ✓\n");
			this.executeTestScenario(this.testScenario);
		}
	}

	/**
	 * Publie un message unique sur le canal donné.
	 *
	 * @param channel canal cible.
	 * @param message message à publier.
	 * @throws Exception si la publication échoue côté broker.
	 */
	public void publish(String channel, MessageI message) throws Exception {
		this.pubPlugin.publish(channel, message);
	}

	/**
	 * Publie un lot de messages sur le canal donné.
	 *
	 * @param channel canal cible.
	 * @param messages liste de messages à publier en lot.
	 * @throws Exception si la publication échoue côté broker.
	 */
	public void publish(String channel, ArrayList<MessageI> messages) throws Exception {
		this.pubPlugin.publish(channel, messages);
	}

	/**
	 * Désenregistre le client auprès du broker.
	 *
	 * @throws UnknownClientException si le client n'est plus connu côté broker.
	 */
	public void unregister() throws UnknownClientException {
		this.regPlugin.unregister();
	}

	/**
	 * Modifie la classe d'enregistrement (FREE ↔ STANDARD ↔ PREMIUM).
	 *
	 * @param rc nouvelle classe d'enregistrement.
	 * @throws Exception si l'opération échoue côté broker.
	 */
	public void modifyServiceClass(RegistrationCI.RegistrationClass rc) throws Exception {
		this.regPlugin.modifyServiceClass(rc);
	}
}
