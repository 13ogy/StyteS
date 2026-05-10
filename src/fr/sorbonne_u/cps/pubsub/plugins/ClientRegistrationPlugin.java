package fr.sorbonne_u.cps.pubsub.plugins;

import fr.sorbonne_u.components.AbstractPlugin;
import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.base.connectors.RegistrationConnector;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientInboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientRegistrationOutboundPort;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;

import java.util.Objects;

/**
 * Plugin client implémentant les opérations d'enregistrement (CDC §3.5).
 *
 * <p><strong>Description</strong></p>
 *
 * <p>
 * Ce greffon possède :
 * </p>
 * <ul>
 *   <li>le port inbound {@link ClientInboundPort} offrant
 *       {@code ReceivingCI} — son URI sert d'identité unique du client
 *       vis-à-vis du broker (clé d'enregistrement, motifs
 *       {@code authorisedUsers} des canaux privilégiés, …) ;</li>
 *   <li>le port outbound {@link ClientRegistrationOutboundPort} vers
 *       {@code RegistrationCI} (register / unregister / subscribe / …).</li>
 * </ul>
 *
 * <p>
 * Choix de conception : ce greffon ne possède plus les ports
 * {@code PublishingCI} et {@code PrivilegedClientCI}. Chaque rôle est
 * implanté par un greffon dédié ({@link ClientPublicationPlugin},
 * {@link ClientPrivilegedPlugin}) qui possède son propre port outbound.
 * Un composant n'instancie ainsi que les ports correspondant à son rôle,
 * conformément aux exemples {@code SimpleRingOverlayNetwork-CM9}
 * (un greffon = un port) et {@code ProducerConsumer-13032026} (greffons
 * producteur/consommateur disjoints).
 * </p>
 *
 * <p>Cycle de vie BCM (cf. {@link AbstractPlugin}) :</p>
 * <ul>
 *   <li>{@link #installOn} — déclare {@code ReceivingCI} (offerte) et
 *       {@code RegistrationCI} (requise) si non déclarées par le composant
 *       (idempotent) ;</li>
 *   <li>{@link #initialise} — publie les deux ports puis connecte le port
 *       de registration au broker (URI dérivée via
 *       {@link Broker#registrationPortURIFor(String)}) ;</li>
 *   <li>{@link #finalise} — déconnecte le port outbound s'il l'est
 *       encore ;</li>
 *   <li>{@link #uninstall} — déconnecte (défensif), dépublie et détruit
 *       les deux ports, retire les interfaces déclarées par {@code installOn}.</li>
 * </ul>
 *
 * <p>L'URI du plugin suit la convention
 * {@code <reflectionURI>-registration-plugin}.</p>
 *
 * <p>Created on : 2026-02-04</p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class		ClientRegistrationPlugin
extends		AbstractPlugin
implements		ClientRegistrationI
{
	private static final long serialVersionUID = 1L;

	/** Port inbound {@code ReceivingCI} ; son URI sert d'identité du client. */
	protected ClientInboundPort receptionPortIN;
	/** Port outbound vers {@code RegistrationCI} du broker. */
	protected ClientRegistrationOutboundPort registrationPortOUT;
	/** {@code true} ssi {@link #installOn} a effectivement déclaré
	 *  {@code ReceivingCI} sur le composant ; sinon, l'interface était
	 *  déjà déclarée (par exemple via {@code @OfferedInterfaces}) et le
	 *  greffon ne doit pas la retirer dans {@link #uninstall} sous peine
	 *  de violer la postcondition BCM. */
	private boolean addedOfferedInterface;
	/** Idem pour {@code RegistrationCI} (interface requise). */
	private boolean addedRequiredInterface;

	/** Plugin de souscription destinataire des messages reçus (peut être {@code null}). */
	private ClientSubscriptionPlugin subscriptionPlugin;

	/** Classe de service courante (FREE / STANDARD / PREMIUM). */
	protected RegistrationClass currentRC;
	/** {@code true} ssi le client est actuellement enregistré auprès du broker. */
	protected boolean registered;

	/** Reflection inbound port URI of the broker this client wants to talk to. */
	private final String brokerReflectionURI;

	/**
	 * @deprecated utilisez {@link #ClientRegistrationPlugin(String)} pour
	 *             qu'un client connaisse son broker dans un environnement
	 *             multi-broker (Phase C.3).
	 */
	@Deprecated
	public ClientRegistrationPlugin()
	{
		this(null);
	}

	/**
	 * Constructeur recommandé : le greffon connaît le broker auquel se
	 * connecter, identifié par son URI de réflexion. Les URIs des ports
	 * inbound du broker sont dérivées de manière déterministe via
	 * {@link Broker#registrationPortURIFor(String)} (et, pour les autres
	 * greffons, {@link Broker#publishingPortURIFor(String)} et
	 * {@link Broker#privilegedPortURIFor(String)}).
	 */
	public ClientRegistrationPlugin(String brokerReflectionURI)
	{
		super();
		this.brokerReflectionURI = brokerReflectionURI;
		this.registered = false;
	}

	/**
	 * Enregistre le greffon de souscription auquel les messages reçus sur
	 * le port {@code ReceivingCI} doivent être routés (cf. {@link #receive}).
	 *
	 * @param plugin	greffon de souscription destinataire ; peut être
	 *					{@code null} pour détacher la livraison.
	 */
	public void setSubscriptionPlugin(ClientSubscriptionPlugin plugin)
	{
		this.subscriptionPlugin = plugin;
	}

	/**
	 * Déclare {@code ReceivingCI} (offerte) et {@code RegistrationCI}
	 * (requise) sur le composant propriétaire (idempotent).
	 *
	 * <p>
	 * <strong>Idempotence :</strong> chaque {@code addOfferedInterface} /
	 * {@code addRequiredInterface} est gardé par un test
	 * {@code isOfferedInterface} / {@code isRequiredInterface}. Cela
	 * permet au composant propriétaire de déclarer ces interfaces
	 * statiquement via {@code @OfferedInterfaces} /
	 * {@code @RequiredInterfaces} sans déclencher la précondition de BCM.
	 * </p>
	 *
	 * @param owner		composant sur lequel installer le plugin.
	 * @throws Exception	si l'installation BCM échoue.
	 */
	@Override
	public void installOn(ComponentI owner) throws Exception
	{
		super.installOn(owner);
		if (!owner.isOfferedInterface(ReceivingCI.class)) {
			this.addOfferedInterface(ReceivingCI.class);
			this.addedOfferedInterface = true;
		}
		if (!owner.isRequiredInterface(RegistrationCI.class)) {
			this.addRequiredInterface(RegistrationCI.class);
			this.addedRequiredInterface = true;
		}
	}

	/**
	 * Publie les deux ports possédés par ce greffon et connecte le port
	 * outbound {@code RegistrationCI} au broker (URI dérivée via
	 * {@link Broker#registrationPortURIFor(String)}).
	 *
	 * @throws Exception	si l'URI de réflexion du broker n'a pas été
	 *						fourni au constructeur, ou si la publication ou
	 *						la connexion d'un port échoue.
	 */
	@Override
	public void initialise() throws Exception
	{
		super.initialise();
		if (this.brokerReflectionURI == null
				|| this.brokerReflectionURI.isEmpty()) {
			throw new IllegalStateException(
				"ClientRegistrationPlugin requires a broker reflection inbound "
				+ "port URI; use ClientRegistrationPlugin(String) instead of "
				+ "the deprecated no-arg variant (Phase C.3).");
		}
		this.receptionPortIN = new ClientInboundPort(
			this.getOwner(), this.getPluginURI());
		this.receptionPortIN.publishPort();

		this.registrationPortOUT = new ClientRegistrationOutboundPort(
			this.getOwner());
		this.registrationPortOUT.publishPort();

		this.getOwner().doPortConnection(
			this.registrationPortOUT.getPortURI(),
			Broker.registrationPortURIFor(this.brokerReflectionURI),
			RegistrationConnector.class.getCanonicalName());
	}

	/**
	 * Déconnecte le port outbound s'il l'est encore.
	 *
	 * @throws Exception	si la déconnexion BCM échoue.
	 */
	@Override
	public void finalise() throws Exception
	{
		if (this.registrationPortOUT != null
				&& this.registrationPortOUT.connected()) {
			this.getOwner().doPortDisconnection(
				this.registrationPortOUT.getPortURI());
		}
		super.finalise();
	}

	/**
	 * Démantèle le greffon : déconnecte (défensif), dépublie et détruit les
	 * deux ports, puis retire les interfaces déclarées par {@link #installOn}.
	 *
	 * @throws Exception	si une opération BCM (déconnexion, dépublication,
	 *						destruction de port) échoue.
	 */
	@Override
	public void uninstall() throws Exception
	{
		if (this.registrationPortOUT != null
				&& this.registrationPortOUT.connected()) {
			this.getOwner().doPortDisconnection(
				this.registrationPortOUT.getPortURI());
		}
		if (this.receptionPortIN != null
				&& !this.receptionPortIN.isDestroyed()) {
			if (this.receptionPortIN.isPublished()) {
				this.receptionPortIN.unpublishPort();
			}
			this.receptionPortIN.destroyPort();
		}
		if (this.registrationPortOUT != null
				&& !this.registrationPortOUT.isDestroyed()) {
			if (this.registrationPortOUT.isPublished()) {
				this.registrationPortOUT.unpublishPort();
			}
			this.registrationPortOUT.destroyPort();
		}
		if (this.addedOfferedInterface
				&& this.getOwner().isOfferedInterface(ReceivingCI.class)) {
			this.removeOfferedInterface(ReceivingCI.class);
			this.addedOfferedInterface = false;
		}
		if (this.addedRequiredInterface
				&& this.getOwner().isRequiredInterface(RegistrationCI.class)) {
			this.removeRequiredInterface(RegistrationCI.class);
			this.addedRequiredInterface = false;
		}
		super.uninstall();
	}

	// ---------------------------------------------------------------------
	// Accessors
	// ---------------------------------------------------------------------

	/**
	 * @return l'URI publiée du port {@code ReceivingCI} ; cette URI est
	 *         l'identité unique de ce client telle que vue par le broker.
	 * @throws Exception si le port n'est pas encore publié.
	 */
	public String getReceptionPortURI() throws Exception
	{
		return this.receptionPortIN.getPortURI();
	}

	/**
	 * @return le port outbound {@code RegistrationCI} possédé par ce
	 *         greffon, partagé avec les greffons de souscription/publication
	 *         (qui s'en servent pour les méthodes {@code channelExist} et
	 *         {@code channelAuthorised} qui font partie de {@code RegistrationCI}).
	 */
	public ClientRegistrationOutboundPort getRegistrationPortOUT()
	{
		return this.registrationPortOUT;
	}

	// ---------------------------------------------------------------------
	// ClientRegistrationI
	// ---------------------------------------------------------------------

	/**
	 * {@inheritDoc}
	 *
	 * @return {@code true} ssi {@link #register} a été appelé avec succès
	 *         et {@link #unregister} ne l'a pas (encore) annulé.
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
	public boolean registered(RegistrationClass rc)
	throws UnknownClientException
	{
		if (!this.registered) {
			throw new UnknownClientException("not registered");
		}
		return rc != null && rc == this.currentRC;
	}

	/**
	 * Enregistre ce client auprès du broker avec la classe de service
	 * {@code rc}.
	 *
	 * <p>
	 * Choix de conception : la connexion des ports outbound de
	 * publication / privilégié est désormais réalisée par les greffons
	 * dédiés ({@link ClientPublicationPlugin},
	 * {@link ClientPrivilegedPlugin}) à leur {@code initialise()}, et non
	 * plus par {@code register(...)}. La valeur de retour de
	 * {@code RegistrationCI.register(rip, rc)} (URI du port inbound côté
	 * broker) est donc ignorée : chaque greffon dérive lui-même son URI
	 * de port côté broker via les helpers statiques
	 * {@link Broker#publishingPortURIFor(String)} /
	 * {@link Broker#privilegedPortURIFor(String)}. Les permissions sont
	 * vérifiées par le broker à chaque appel
	 * ({@code requireServiceClass(rip, …)}).
	 * </p>
	 *
	 * @param rc	classe de service demandée ; non {@code null}.
	 * @throws AlreadyRegisteredException	si le client est déjà enregistré.
	 */
	@Override
	public void register(RegistrationClass rc)
	throws AlreadyRegisteredException
	{
		Objects.requireNonNull(rc, "rc");
		if (this.registered) throw new AlreadyRegisteredException();
		try {
			this.registrationPortOUT.register(
				this.receptionPortIN.getPortURI(), rc);
			this.currentRC = rc;
			this.registered = true;
		} catch (AlreadyRegisteredException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Modifie la classe de service du client côté broker.
	 *
	 * <p>
	 * Choix de conception : aucune reconfiguration des ports outbound
	 * client n'est nécessaire. Les ports {@code PublishingCI} et
	 * {@code PrivilegedClientCI} sont déjà connectés à leurs ports inbound
	 * dédiés du broker (via leurs greffons respectifs). Le broker autorise
	 * ou rejette chaque appel en fonction de la classe de service courante
	 * du client ({@code requireServiceClass(rip, expected)}), donc un
	 * client passant de FREE à STANDARD se voit immédiatement autorisé à
	 * créer des canaux privilégiés sans rebrancher quoi que ce soit.
	 * </p>
	 *
	 * @param rc	nouvelle classe de service ; non {@code null} et
	 *				différente de la classe courante.
	 * @throws UnknownClientException		si le client n'est pas enregistré.
	 * @throws AlreadyRegisteredException	si {@code rc} est égale à la
	 *										classe courante.
	 */
	@Override
	public void modifyServiceClass(RegistrationClass rc)
	throws UnknownClientException, AlreadyRegisteredException
	{
		Objects.requireNonNull(rc, "rc");
		if (!this.registered) {
			throw new UnknownClientException("not registered");
		}
		if (rc == this.currentRC) {
			throw new AlreadyRegisteredException();
		}
		try {
			this.registrationPortOUT.modifyServiceClass(
				this.receptionPortIN.getPortURI(), rc);
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
		if (!this.registered) {
			throw new UnknownClientException("not registered");
		}
		try {
			this.registrationPortOUT.unregister(
				this.receptionPortIN.getPortURI());
			this.registered = false;
		} catch (UnknownClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Point d'entrée invoqué par le port {@link ClientInboundPort} de ce
	 * greffon lors de la livraison d'un message par le broker. Re-route
	 * vers le {@link ClientSubscriptionPlugin} associé (si présent) qui
	 * applique la livraison passive (callback handler) et notifie les
	 * consommateurs en attente via {@code waitForNextMessage} /
	 * {@code getNextMessage}.
	 *
	 * @param channel	canal source du message.
	 * @param message	message livré par le broker.
	 */
	public void receive(String channel, MessageI message)
	{
		ClientSubscriptionPlugin sp = this.subscriptionPlugin;
		if (sp != null) {
			sp.receive(channel, message);
		}
	}

	/**
	 * Variante batch invoquée par {@link ClientInboundPort#receive(String, MessageI[])}.
	 *
	 * @param channel	canal source des messages.
	 * @param messages	messages livrés par le broker.
	 */
	public void receive(String channel, MessageI[] messages)
	{
		ClientSubscriptionPlugin sp = this.subscriptionPlugin;
		if (sp != null) {
			sp.receive(channel, messages);
		}
	}
}
