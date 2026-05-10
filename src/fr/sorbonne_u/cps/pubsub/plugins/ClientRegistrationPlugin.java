package fr.sorbonne_u.cps.pubsub.plugins;

import fr.sorbonne_u.components.AbstractPlugin;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.base.connectors.ClientBrokerPrivilegedConnector;
import fr.sorbonne_u.cps.pubsub.base.connectors.ClientBrokerPublishingConnector;
import fr.sorbonne_u.cps.pubsub.base.connectors.ClientBrokerRegistrationConnector;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientInboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientPrivilegedOutboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientPublishingOutboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientRegistrationOutboundPort;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.*;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;

/**
 * Plugin client implémentant les opérations d'enregistrement (CDC §3.5).
 *
 * <p><strong>Description</strong></p>
 *
 * <p>
 * Ce plugin est le pivot de l'architecture cliente : il possède
 * <em>tous</em> les ports nécessaires au dialogue avec le broker, et les
 * autres plugins ({@link ClientPublicationPlugin},
 * {@link ClientSubscriptionPlugin}, {@link ClientPrivilegedPlugin}) y
 * accèdent au lieu de publier leurs propres ports. Cela évite de
 * multiplier les ports inbound côté client et garantit une identité unique
 * (l'URI du port {@code ReceivingCI}) pour ce client vis-à-vis du broker.
 * </p>
 *
 * <p>Ports possédés :</p>
 * <ul>
 *   <li>{@link ClientInboundPort} — port inbound offrant
 *       {@code ReceivingCI}, utilisé par le broker pour livrer les
 *       messages ; son URI sert d'identité du client ;</li>
 *   <li>{@link ClientRegistrationOutboundPort} — port outbound vers
 *       {@code RegistrationCI} (register / unregister / subscribe / …) ;</li>
 *   <li>{@link ClientPublishingOutboundPort} — port outbound vers
 *       {@code PublishingCI}, connecté au moment de {@link #register} ;</li>
 *   <li>{@link ClientPrivilegedOutboundPort} — port outbound vers
 *       {@code PrivilegedClientCI}, connecté en plus pour les classes
 *       STANDARD et PREMIUM (DUAL-CONNECT, voir {@link #register}).</li>
 * </ul>
 *
 * <p>Cycle de vie BCM (cf. {@link AbstractPlugin}) :</p>
 * <ul>
 *   <li>{@link #installOn} — déclare les interfaces offertes/requises sur
 *       le composant propriétaire si elles ne sont pas déjà présentes
 *       (idempotent : une déclaration via {@code @OfferedInterfaces} ou
 *       {@code @RequiredInterfaces} sur le composant est respectée) ;</li>
 *   <li>{@link #initialise} — publie les quatre ports et connecte le port
 *       de registration au broker (URI dérivée de
 *       {@link Broker#registrationPortURIFor(String)}) ;</li>
 *   <li>{@link #finalise} — déconnecte les ports outbound encore
 *       connectés ;</li>
 *   <li>{@link #uninstall} — re-déconnecte défensivement, dépublie et
 *       détruit tous les ports puis retire les interfaces déclarées par
 *       {@code installOn}. {@code uninstall()} peut être appelé directement
 *       depuis {@code shutdown()} sans passer par {@code finalise()}, d'où
 *       les gardes {@code connected()} / {@code isPublished()}.</li>
 * </ul>
 *
 * <p><strong>Invariants &amp; contrats</strong></p>
 * <ul>
 *   <li>{@link #installOn} et {@link #uninstall} sont idempotents : ils ne
 *       lèvent pas si une interface est déjà (resp. n'est plus) présente ;</li>
 *   <li>après {@link #register} avec {@link RegistrationClass#STANDARD} ou
 *       {@link RegistrationClass#PREMIUM}, les ports
 *       {@code publishingPortOUT} <em>et</em> {@code privilegedPortOUT}
 *       sont tous deux connectés (DUAL-CONNECT) ;</li>
 *   <li>l'URI du plugin suit la convention
 *       {@code <reflectionURI>-registration-plugin} fixée par
 *       {@code PluginClient}.</li>
 * </ul>
 *
 * <p>Created on : 2026-02-04</p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class ClientRegistrationPlugin extends AbstractPlugin implements ClientRegistrationI
{
	private static final long serialVersionUID = 1L;

	/** Port inbound {@code ReceivingCI} ; son URI sert d'identité du client. */
	protected ClientInboundPort receptionPortIN;
	/** Port outbound vers {@code RegistrationCI} du broker. */
	protected ClientRegistrationOutboundPort registrationPortOUT;
	/** Port outbound vers {@code PublishingCI} (connecté lors du {@link #register}). */
	protected ClientPublishingOutboundPort publishingPortOUT;
	/** Port outbound vers {@code PrivilegedClientCI} (connecté pour STANDARD/PREMIUM). */
	protected ClientPrivilegedOutboundPort privilegedPortOUT;

	/** Plugin de souscription destinataire des messages reçus (peut être {@code null}). */
	private ClientSubscriptionPlugin subscriptionPlugin;

	/** Classe de service courante (FREE / STANDARD / PREMIUM). */
	protected RegistrationClass currentRC;
	/** {@code true} ssi le client est actuellement enregistré auprès du broker. */
	protected boolean registered;

	/** Reflection inbound port URI of the broker this client wants to talk
	 *  to. {@code null} only when the deprecated no-arg constructor was
	 *  used; in that case {@link #initialise()} will fail with a clear
	 *  message. (Phase C.3) */
	private final String brokerReflectionURI;

	/**
	 * @deprecated use {@link #ClientRegistrationPlugin(String)} so that
	 *             this client knows which broker to connect to in a
	 *             multi-broker environment (Phase C.3).
	 */
	@Deprecated
	public ClientRegistrationPlugin()
	{
		this(null);
	}

	/**
	 * Preferred constructor (Phase C.3): the plugin knows which broker
	 * it must contact, identified by its reflection inbound port URI.
	 * The plugin uses {@link Broker#registrationPortURIFor(String)} to
	 * derive the broker's registration port URI deterministically.
	 */
	public ClientRegistrationPlugin(String brokerReflectionURI)
	{
		super();
		this.brokerReflectionURI = brokerReflectionURI;
		this.registered = false;
	}

	/**
	 * Enregistre le plugin de souscription auquel les messages reçus sur le
	 * port {@code ReceivingCI} doivent être routés (cf. {@link #receive}).
	 *
	 * @param plugin	plugin de souscription destinataire ; peut être
	 *					{@code null} pour détacher la livraison.
	 */
	public void setSubscriptionPlugin(ClientSubscriptionPlugin plugin){
		this.subscriptionPlugin = plugin;
	}

	/**
	 * Déclare sur le composant propriétaire les interfaces offertes
	 * ({@code ReceivingCI}) et requises ({@code RegistrationCI},
	 * {@code PublishingCI}, {@code PrivilegedClientCI}) nécessaires au
	 * dialogue avec le broker.
	 *
	 * <p>
	 * <strong>Idempotence :</strong> chaque {@code addOfferedInterface} /
	 * {@code addRequiredInterface} est gardé par un test
	 * {@code isOfferedInterface} / {@code isRequiredInterface}. Cela permet
	 * au composant propriétaire de déclarer ces interfaces statiquement via
	 * {@code @OfferedInterfaces} / {@code @RequiredInterfaces} sans
	 * déclencher la précondition de BCM (re-déclaration interdite).
	 * </p>
	 *
	 * @param owner		composant sur lequel installer le plugin.
	 * @throws Exception	si l'installation BCM échoue.
	 */
	@Override
	public void installOn(fr.sorbonne_u.components.ComponentI owner) throws Exception
	{
		super.installOn(owner);
		// Add interfaces only if the owner does not already declare them
		// (e.g. via @OfferedInterfaces / @RequiredInterfaces annotations).
		// Re-adding triggers a precondition assertion under -ea.
		if (!owner.isOfferedInterface(ReceivingCI.class)) {
			this.addOfferedInterface(ReceivingCI.class);
		}
		if (!owner.isRequiredInterface(RegistrationCI.class)) {
			this.addRequiredInterface(RegistrationCI.class);
		}
		if (!owner.isRequiredInterface(PublishingCI.class)) {
			this.addRequiredInterface(PublishingCI.class);
		}
		if (!owner.isRequiredInterface(PrivilegedClientCI.class)) {
			this.addRequiredInterface(PrivilegedClientCI.class);
		}
	}
	/**
	 * Publie les quatre ports du plugin et connecte le port outbound
	 * {@code RegistrationCI} au broker. L'URI du port {@code RegistrationCI}
	 * du broker est dérivée de l'URI de réflexion du broker (Phase C.3) via
	 * {@link Broker#registrationPortURIFor(String)}.
	 *
	 * @throws Exception	si l'URI de réflexion du broker n'a pas été
	 *						fourni au constructeur, ou si la publication ou
	 *						la connexion d'un port échoue.
	 */
	@Override
	public void initialise() throws Exception {
		super.initialise();
		if (this.brokerReflectionURI == null || this.brokerReflectionURI.isEmpty()) {
			throw new IllegalStateException(
				"ClientRegistrationPlugin requires a broker reflection inbound "
				+ "port URI; use ClientRegistrationPlugin(String) instead of "
				+ "the deprecated no-arg variant (Phase C.3).");
		}
		// Publish ports
		this.receptionPortIN = new ClientInboundPort(this.getOwner(), this.getPluginURI());
		this.receptionPortIN.publishPort();

		this.registrationPortOUT = new ClientRegistrationOutboundPort(this.getOwner());
		this.registrationPortOUT.publishPort();

		this.publishingPortOUT = new ClientPublishingOutboundPort(this.getOwner());
		this.publishingPortOUT.publishPort();

		this.privilegedPortOUT = new ClientPrivilegedOutboundPort(this.getOwner());
		this.privilegedPortOUT.publishPort();

		// Connect ports
		this.getOwner().doPortConnection(
				this.registrationPortOUT.getPortURI(),
				Broker.registrationPortURIFor(this.brokerReflectionURI),
				ClientBrokerRegistrationConnector.class.getCanonicalName());
	}
	/**
	 * Déconnecte les ports outbound encore connectés. Les ports sont
	 * détruits par {@link #uninstall} ; {@code finalise()} se contente
	 * d'assurer un état déconnecté propre.
	 *
	 * @throws Exception	si une déconnexion BCM échoue.
	 */
	@Override
	public void finalise() throws Exception {
		if (this.registrationPortOUT != null && this.registrationPortOUT.connected()) {
			this.getOwner().doPortDisconnection(this.registrationPortOUT.getPortURI());
		}
		if (this.publishingPortOUT != null && this.publishingPortOUT.connected()) {
			this.getOwner().doPortDisconnection(this.publishingPortOUT.getPortURI());
		}
		if (this.privilegedPortOUT != null && this.privilegedPortOUT.connected()) {
			this.getOwner().doPortDisconnection(this.privilegedPortOUT.getPortURI());
		}
		super.finalise();
	}
	/**
	 * Démantèle complètement le plugin : déconnecte (défensivement)
	 * chacun des ports outbound encore connectés, dépublie puis détruit
	 * les quatre ports, et retire les interfaces déclarées par
	 * {@link #installOn}.
	 *
	 * <p>
	 * <strong>Sûreté en cas de {@code shutdown()} :</strong>
	 * {@code uninstall()} peut être invoqué directement par
	 * {@code AbstractComponent.shutdown()} sans passer par
	 * {@link #finalise()}, ou être appelé après que des helpers de
	 * shutdown défensifs ont déjà dépublié certains ports. C'est pourquoi
	 * chaque action est gardée par {@code connected()},
	 * {@code isPublished()} ou {@code isDestroyed()} (les préconditions
	 * BCM échoueraient sinon sous {@code -ea}).
	 * </p>
	 *
	 * @throws Exception	si une opération BCM (déconnexion, dépublication,
	 *						destruction de port) échoue.
	 */
	@Override
	public void uninstall() throws Exception
	{
		// Defensive disconnect-before-destroy: shutdown() may invoke uninstall()
		// before finalise() runs (or finalise() may have been bypassed entirely),
		// in which case destroyPort() would trip a !connected() precondition.
		if (this.registrationPortOUT != null && this.registrationPortOUT.connected()) {
			this.getOwner().doPortDisconnection(this.registrationPortOUT.getPortURI());
		}
		if (this.publishingPortOUT != null && this.publishingPortOUT.connected()) {
			this.getOwner().doPortDisconnection(this.publishingPortOUT.getPortURI());
		}
		if (this.privilegedPortOUT != null && this.privilegedPortOUT.connected()) {
			this.getOwner().doPortDisconnection(this.privilegedPortOUT.getPortURI());
		}

		// Unpublish ports (guarded with isPublished() — defensive shutdown
		// helpers may have already unpublished some ports).
		if (this.receptionPortIN != null && !this.receptionPortIN.isDestroyed()) {
			if (this.receptionPortIN.isPublished()) this.receptionPortIN.unpublishPort();
			this.receptionPortIN.destroyPort();
		}
		if (this.registrationPortOUT != null && !this.registrationPortOUT.isDestroyed()) {
			if (this.registrationPortOUT.isPublished()) this.registrationPortOUT.unpublishPort();
			this.registrationPortOUT.destroyPort();
		}
		if (this.publishingPortOUT != null && !this.publishingPortOUT.isDestroyed()) {
			if (this.publishingPortOUT.isPublished()) this.publishingPortOUT.unpublishPort();
			this.publishingPortOUT.destroyPort();
		}
		if (this.privilegedPortOUT != null && !this.privilegedPortOUT.isDestroyed()) {
			if (this.privilegedPortOUT.isPublished()) this.privilegedPortOUT.unpublishPort();
			this.privilegedPortOUT.destroyPort();
		}
		// Remove interfaces only if currently declared (mirrors the guarded
		// installOn(): when the owner declares them via annotations, the
		// plugin must not try to remove them).
		if (this.getOwner().isOfferedInterface(ReceivingCI.class)) {
			this.removeOfferedInterface(ReceivingCI.class);
		}
		if (this.getOwner().isRequiredInterface(RegistrationCI.class)) {
			this.removeRequiredInterface(RegistrationCI.class);
		}
		if (this.getOwner().isRequiredInterface(PrivilegedClientCI.class)) {
			this.removeRequiredInterface(PrivilegedClientCI.class);
		}
		if (this.getOwner().isRequiredInterface(PublishingCI.class)) {
			this.removeRequiredInterface(PublishingCI.class);
		}

		super.uninstall();
	}

	// ---------------------------------------------------------------------
	// Accessors
	// ---------------------------------------------------------------------

	/**
	 * @return l'URI publiée du port {@code ReceivingCI} ; cette URI est
	 *         l'identité unique de ce client telle que vue par le broker
	 *         (utilisée comme clé d'enregistrement, dans les motifs
	 *         {@code authorisedUsers} des canaux privilégiés, etc.).
	 * @throws Exception si le port n'est pas encore publié.
	 */
	public String getReceptionPortURI() throws Exception
	{
		return this.receptionPortIN.getPortURI();
	}

	/**
	 * @return le port outbound {@code RegistrationCI} possédé par ce plugin,
	 *         partagé avec les autres plugins clients.
	 */
	public ClientRegistrationOutboundPort getRegistrationPortOUT()
	{
		return this.registrationPortOUT;
	}

	/**
	 * @return le port outbound {@code PublishingCI} possédé par ce plugin,
	 *         connecté au broker dès l'appel à {@link #register}.
	 */
	public ClientPublishingOutboundPort getPublishingPortOUT()
	{
		return this.publishingPortOUT;
	}

	/**
	 * @return le port outbound {@code PrivilegedClientCI} possédé par ce
	 *         plugin ; n'est connecté que pour les classes
	 *         {@link RegistrationClass#STANDARD} et
	 *         {@link RegistrationClass#PREMIUM}.
	 */
	public ClientPrivilegedOutboundPort getPrivilegedPortOUT()
	{
		return this.privilegedPortOUT;
	}

	// ---------------------------------------------------------------------
	// ClientRegistrationI
	// ---------------------------------------------------------------------

	/**
	 * {@inheritDoc}
	 *
	 * @return {@code true} ssi {@link #register} a été appelé avec succès
	 *         et que {@link #unregister} ne l'a pas (encore) annulé.
	 */
	@Override
	public boolean registered()
	{
		return this.registered;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @param rc	classe de service à comparer.
	 * @return		{@code true} ssi le client est enregistré et que sa
	 *				classe courante est égale à {@code rc}.
	 * @throws UnknownClientException	si le client n'est pas enregistré.
	 */
	@Override
	public boolean registered(RegistrationClass rc) throws UnknownClientException
	{
		if (!this.registered) {
			throw new UnknownClientException("not registered");
		}
		return rc != null && rc == this.currentRC;
	}

	/**
	 * Enregistre ce client auprès du broker avec la classe de service
	 * {@code rc} et établit le câblage outbound correspondant.
	 *
	 * <p><strong>DUAL-CONNECT (CDC §3.5.1) :</strong></p>
	 * <ul>
	 *   <li>{@link RegistrationClass#FREE} : seul {@code publishingPortOUT}
	 *       est connecté au port {@code PublishingCI} retourné par le
	 *       broker ;</li>
	 *   <li>{@link RegistrationClass#STANDARD} et
	 *       {@link RegistrationClass#PREMIUM} : le broker retourne l'URI
	 *       d'un port {@code PrivilegedClientCI} (qui hérite de
	 *       {@code PublishingCI}) ; <em>les deux</em> ports
	 *       {@code privilegedPortOUT} et {@code publishingPortOUT} sont
	 *       alors connectés à cette même URI, afin que les appels passant
	 *       par le plugin de publication continuent de fonctionner pour
	 *       ces classes de service.</li>
	 * </ul>
	 *
	 * @param rc	classe de service demandée ; non {@code null}.
	 * @throws AlreadyRegisteredException	si le client est déjà enregistré.
	 */
	@Override
	public void register(RegistrationClass rc) throws AlreadyRegisteredException
	{
		if (this.registered) throw new AlreadyRegisteredException();

		try {
			String brokerPortURI = this.registrationPortOUT.register(
					this.receptionPortIN.getPortURI(), rc);

			if (rc == RegistrationClass.FREE) {
				// FREE clients only get publishing
				this.getOwner().doPortConnection(
						this.publishingPortOUT.getPortURI(),
						brokerPortURI,
						ClientBrokerPublishingConnector.class.getCanonicalName());
			} else {
				// STANDARD/PREMIUM get privileged (which also covers publishing
				// because PrivilegedClientCI extends PublishingCI). Connect
				// BOTH outbound ports to the same broker privileged inbound
				// port so the publication plugin (which uses the publishing
				// outbound port) keeps working for these classes.
				this.getOwner().doPortConnection(
						this.privilegedPortOUT.getPortURI(),
						brokerPortURI,
						ClientBrokerPrivilegedConnector.class.getCanonicalName());
				this.getOwner().doPortConnection(
						this.publishingPortOUT.getPortURI(),
						brokerPortURI,
						ClientBrokerPublishingConnector.class.getCanonicalName());
			}
			this.currentRC = rc;
			this.registered=true;
		}catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	/**
	 * Modifie la classe de service du client en re-câblant les ports
	 * outbound (déconnecte ceux liés à l'ancienne classe puis se reconnecte
	 * au port retourné par le broker pour la nouvelle classe).
	 *
	 * @param rc	nouvelle classe de service demandée ; non {@code null}
	 *				et différente de la classe courante.
	 * @throws UnknownClientException		si le client n'est pas enregistré.
	 * @throws AlreadyRegisteredException	si {@code rc} est égale à la
	 *										classe courante.
	 */
	@Override
	public void modifyServiceClass(RegistrationClass rc) throws UnknownClientException, AlreadyRegisteredException
	{
		if (!this.registered) {
			throw new UnknownClientException("not registered");
		}
		if (rc == this.currentRC) {
			throw new AlreadyRegisteredException();
		}
		try {
			String brokerPortURI = this.registrationPortOUT.modifyServiceClass(this.receptionPortIN.getPortURI(), rc);

			if (this.currentRC == RegistrationClass.FREE) {
				this.getOwner().doPortDisconnection(this.publishingPortOUT.getPortURI());
			} else {
				this.getOwner().doPortDisconnection(this.privilegedPortOUT.getPortURI());
				if (this.publishingPortOUT.connected()) {
					this.getOwner().doPortDisconnection(this.publishingPortOUT.getPortURI());
				}
			}

			if (rc == RegistrationClass.FREE) {
				this.getOwner().doPortConnection(
						this.publishingPortOUT.getPortURI(),
						brokerPortURI,
						ClientBrokerPublishingConnector.class.getCanonicalName());
			} else {
				this.getOwner().doPortConnection(
						this.privilegedPortOUT.getPortURI(),
						brokerPortURI,
						ClientBrokerPrivilegedConnector.class.getCanonicalName());
				this.getOwner().doPortConnection(
						this.publishingPortOUT.getPortURI(),
						brokerPortURI,
						ClientBrokerPublishingConnector.class.getCanonicalName());
			}

			this.currentRC = rc;
		} catch (UnknownClientException | AlreadyRegisteredException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Désenregistre le client auprès du broker.
	 *
	 * @throws UnknownClientException	si le client n'est pas enregistré.
	 */
	@Override
	public void unregister() throws UnknownClientException
	{
		try {
			if (!this.registered) {
				throw new UnknownClientException("not registered");
			}
			this.registrationPortOUT.unregister(this.receptionPortIN.getPortURI());
			this.registered = false;
		} catch (UnknownClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}


	/**
	 * Point d'entrée invoqué par le port {@link ClientInboundPort} de ce
	 * plugin lors de la livraison d'un message par le broker. Re-route
	 * vers le {@link ClientSubscriptionPlugin} associé (si présent) qui
	 * applique la livraison passive (callback handler) et notifie les
	 * consommateurs en attente via {@code waitForNextMessage} /
	 * {@code getNextMessage}.
	 *
	 * @param channel	canal de provenance du message.
	 * @param message	message reçu.
	 */
	public void receive(String channel, MessageI message) {
		if (this.subscriptionPlugin != null) {
			this.subscriptionPlugin.receive(channel, message);
		}
	}
}
