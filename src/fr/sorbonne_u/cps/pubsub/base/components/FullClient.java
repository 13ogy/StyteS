package fr.sorbonne_u.cps.pubsub.base.components;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.exceptions.ComponentStartException;
import fr.sorbonne_u.components.utils.tests.TestScenario;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.cps.pubsub.plugins.ClientPublicationPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientSubscriptionPlugin;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

import java.time.Duration;
import java.util.concurrent.Future;

/**
 * Composant client de démonstration <strong>complet</strong> : combine registration + souscription
 * + publication. Compose trois plugins :
 *
 * <ul>
 *   <li>{@link ClientRegistrationPlugin} ;
 *   <li>{@link ClientSubscriptionPlugin} (avec {@link #onReceive(String, MessageI)} comme {@code
 *       MessageDeliveryHandler}) ;
 *   <li>{@link ClientPublicationPlugin}.
 * </ul>
 *
 * <p>Expose en plus l'API de réception avancée du CDC §3.5.3 : {@link #getNextMessage(String)}
 * retournant un {@link Future} et {@link #waitForNextMessage(String, Duration)} bloquant avec
 * timeout.
 *
 * <p><strong>Convention constructeur</strong> : {@code (uri, brokerReflectionURI, scenario,
 * registrationClass)}.
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class FullClient extends AbstractComponent {

	private final ClientRegistrationPlugin regPlugin;
	private final ClientSubscriptionPlugin subPlugin;
	private final ClientPublicationPlugin pubPlugin;

	private final TestScenario testScenario;
	private final RegistrationCI.RegistrationClass initialRC;
	private final String uri;
	private final String brokerReflectionURI;

	/**
	 * Crée un client complet (sub + pub + priv-less).
	 *
	 * @param uri URI de port de réflexion / identifiant.
	 * @param brokerReflectionURI URI de réflexion du broker cible.
	 * @param ts scénario temporisé optionnel.
	 * @param rc classe initiale d'enregistrement.
	 * @throws Exception si la construction échoue.
	 */
	protected FullClient(
			String uri,
			String brokerReflectionURI,
			TestScenario ts,
			RegistrationCI.RegistrationClass rc)
			throws Exception {
		super(uri, 1, 1);
		this.testScenario = ts;
		this.initialRC = rc;
		this.uri = uri;
		this.brokerReflectionURI = brokerReflectionURI;
		this.regPlugin = new ClientRegistrationPlugin(brokerReflectionURI);
		this.regPlugin.setPluginURI(uri + "-reg");

		this.subPlugin = new ClientSubscriptionPlugin(regPlugin, this::onReceive);
		this.subPlugin.setPluginURI(uri + "-sub");

		this.regPlugin.setSubscriptionPlugin(this.subPlugin);

		this.pubPlugin = new ClientPublicationPlugin(regPlugin, brokerReflectionURI);
		this.pubPlugin.setPluginURI(uri + "-pub");

		this.toggleTracing();
		this.getTracer().setTitle(uri);
	}

	/**
	 * Installe les trois plugins (reg, pub, sub) avant {@link #execute()}.
	 *
	 * @throws ComponentStartException si un plugin ne peut être installé.
	 */
	@Override
	public synchronized void start() throws ComponentStartException {
		try {
			this.installPlugin(this.regPlugin);
			this.installPlugin(this.pubPlugin);
			this.installPlugin(this.subPlugin);
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

		if (this.testScenario != null
				&& this.testScenario.entityAppearsIn(this.getReflectionInboundPortURI())) {
			this.initialiseClock(
					ClocksServer.STANDARD_INBOUNDPORT_URI, this.testScenario.getClockURI());
			this.executeTestScenario(this.testScenario);
		}
	}

	/**
	 * Hook de réception délégué au {@link ClientSubscriptionPlugin} ; trace et logue le message
	 * reçu.
	 *
	 * @param channel canal source.
	 * @param message message reçu (peut être {@code null}).
	 */
	public void onReceive(String channel, MessageI message) {
		String msg =
				"["
						+ uri
						+ "] RECEIVED on "
						+ channel
						+ ": payload="
						+ (message != null ? message.getPayload() : "null")
						+ "\n";
		System.out.println(msg);
		this.traceMessage(msg);
		this.logMessage(msg);
	}

	/**
	 * Publie un message sur le canal donné.
	 *
	 * @param channel canal cible.
	 * @param message message à publier.
	 * @throws Exception si la publication échoue côté broker.
	 */
	public void publish(String channel, MessageI message) throws Exception {
		this.pubPlugin.publish(channel, message);
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

	/**
	 * Souscrit au canal donné avec le filtre fourni.
	 *
	 * @param channel canal cible.
	 * @param filter filtre de message à appliquer côté broker.
	 * @throws Exception si la souscription échoue côté broker.
	 */
	public void subscribe(String channel, MessageFilterI filter) throws Exception {
		this.subPlugin.subscribe(channel, filter);
	}

	/**
	 * Désinscrit le client du canal donné.
	 *
	 * @param channel canal cible.
	 * @throws Exception si la désinscription échoue côté broker.
	 */
	public void unsubscribe(String channel) throws Exception {
		this.subPlugin.unsubscribe(channel);
	}

	/**
	 * API de réception avancée non bloquante (CDC §3.5.3) : retourne un {@link Future} qui sera
	 * complété à l'arrivée du prochain message sur {@code channel}.
	 *
	 * @param channel canal observé.
	 * @return un Future complété par le prochain {@link MessageI} reçu.
	 */
	public Future<MessageI> getNextMessage(String channel) {
		return this.subPlugin.getNextMessage(channel);
	}

	/**
	 * Bloque jusqu'à la réception d'un message sur {@code channel} (sans timeout).
	 *
	 * @param channel canal observé.
	 * @return le message reçu.
	 */
	public MessageI waitForNextMessage(String channel) {
		return this.subPlugin.waitForNextMessage(channel);
	}

	/**
	 * Bloque jusqu'à la réception d'un message sur {@code channel} ou expiration du délai (CDC
	 * §3.5.3).
	 *
	 * @param channel canal observé.
	 * @param d durée maximale d'attente.
	 * @return le message reçu, ou {@code null} en cas de timeout.
	 */
	public MessageI waitForNextMessage(String channel, Duration d) {
		return this.subPlugin.waitForNextMessage(channel, d);
	}
}
