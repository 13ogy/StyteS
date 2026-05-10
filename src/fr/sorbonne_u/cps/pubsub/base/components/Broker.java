package fr.sorbonne_u.cps.pubsub.base.components;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.exceptions.ComponentShutdownException;
import fr.sorbonne_u.components.exceptions.ComponentStartException;
import fr.sorbonne_u.cps.pubsub.base.connectors.ReceivingConnector;
import fr.sorbonne_u.cps.pubsub.base.ports.BrokerPrivilegedInboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.BrokerPublishingInboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.BrokerReceptionOutboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.BrokerRegistrationInboundPort;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyExistingChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.exceptions.ChannelQuotaExceededException;
import fr.sorbonne_u.cps.pubsub.exceptions.NotSubscribedChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.gossip.connectors.GossipConnector;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.EmitterAwareGossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipImplementationI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipReceiverCI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipSenderCI;
import fr.sorbonne_u.cps.pubsub.gossip.messages.*;
import fr.sorbonne_u.cps.pubsub.gossip.ports.GossipReceiverInboundPort;
import fr.sorbonne_u.cps.pubsub.gossip.ports.GossipSenderOutboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;
import fr.sorbonne_u.cps.pubsub.messages.filters.AcceptAllMessageFilter;

import fr.sorbonne_u.components.annotations.OfferedInterfaces;
import fr.sorbonne_u.components.annotations.RequiredInterfaces;

import java.util.regex.Pattern;

/**
 * Composant {@code Broker} — cœur du système de publication/souscription
 * conforme au CDC (chapitres 3 et 4).
 *
 * <p>
 * Le broker centralise l'enregistrement des clients, la gestion des canaux
 * (libres et privilégiés), la souscription filtrée et la publication
 * asynchrone. En mode fédéré, il propage également chaque mutation
 * d'état (register, publish, createChannel, …) à ses voisins via le
 * protocole gossip dont il implémente {@link GossipImplementationI}.
 * </p>
 *
 * <h2>Interfaces composantes (CIs)</h2>
 * <ul>
 * <li>Offertes : {@link RegistrationCI}, {@link PublishingCI},
 * {@link PrivilegedClientCI}, {@link GossipReceiverCI}.</li>
 * <li>Requises : {@link ReceivingCI} (vers les clients abonnés),
 * {@link GossipSenderCI} (vers les voisins fédérés).</li>
 * </ul>
 *
 * <h2>Fonctionnalités</h2>
 * <ul>
 * <li><strong>Enregistrement</strong> : classes de service
 * {@code FREE}, {@code STANDARD}, {@code PREMIUM}.</li>
 * <li><strong>Canaux libres</strong> : pré-créés {@code channel0..channelN}.</li>
 * <li><strong>Souscriptions filtrées</strong> via
 * {@link MessageFilterI} ; un filtre {@code null} est normalisé en
 * {@link AcceptAllMessageFilter#INSTANCE}.</li>
 * <li><strong>Publication asynchrone</strong> (CDC §3.4 / §4.2) :
 * réception, propagation et livraison sont découplées par trois
 * executor services dédiés.</li>
 * <li><strong>Canaux privilégiés</strong> : créés par les clients
 * STANDARD/PREMIUM, gardés par une regex {@code authorisedUsers}
 * contrôlant l'accès en publication et en souscription.</li>
 * <li><strong>Quotas</strong> : limites de canaux privilégiés par classe
 * (paramètres {@code standardQuota}, {@code premiumQuota}).</li>
 * <li><strong>Gossip</strong> : déduplication atomique
 * ({@link #processedGossipURIs}), filtrage de l'émetteur immédiat
 * (skip-echo) et nettoyage périodique.</li>
 * </ul>
 *
 * <h2>Concurrence — verrous</h2>
 * <p>
 * Trois {@link ReentrantReadWriteLock} équitables protègent les états
 * partagés (cf. {@code docs/MUTEX.md}) :
 * </p>
 * <ul>
 * <li>{@link #registrationLock} — protège
 * {@code registeredClients}, {@code receptionPortsOUT} et
 * {@code createdPrivilegedChannelsCount} ;</li>
 * <li>{@link #channelsLock} — protège {@code channels} et
 * {@code privilegedChannels} ;</li>
 * <li>{@link #subscriptionsLock} — protège la table imbriquée
 * {@code subscriptions}.</li>
 * </ul>
 * <p>
 * <strong>Ordre canonique d'acquisition</strong> :
 * {@code registrationLock → channelsLock → subscriptionsLock}.
 * Toute acquisition dans un autre ordre constitue un risque d'inter-blocage
 * et est interdite (cf. {@code docs/MUTEX.md} §3).
 * </p>
 * <p>
 * <strong>Règle « pas d'appel distant sous verrou »</strong> :
 * aucun {@code doPortConnection}, {@code doPortDisconnection} ni
 * {@code receive(...)} ne doit s'exécuter alors qu'un de ces verrous est
 * détenu. Voir {@link #register} pour le motif rollback typique.
 * </p>
 * <p>
 * Quelques compteurs sont gérés sans verrou via des
 * {@link ConcurrentHashMap} et opérations atomiques :
 * {@link #createdPrivilegedChannelsCount} ({@code merge}),
 * {@link #inFlightPerChannel} ({@code compute}),
 * {@link #processedGossipURIs} ({@code putIfAbsent}). La carte
 * {@link #inFlightWaiters} fournit un objet par canal sur lequel
 * {@link #destroyChannel} peut bloquer en {@code wait/notify} pendant que
 * {@link #finishInFlight} le réveille.
 * </p>
 *
 * <h2>Pipeline asynchrone — quatre executor services</h2>
 * <p>
 * Voir {@code docs/PIPELINE.md}. Quatre exécuteurs nommés sont créés au
 * démarrage par {@link #init} :
 * </p>
 * <ul>
 * <li>{@link #ES_RECEPTION_URI} — désérialise les requêtes RMI hors
 * de la thread de dispatch ;</li>
 * <li>{@link #ES_PROPAGATION_URI} — construit le {@code snapshot} des
 * abonnés et planifie une livraison par cible ;</li>
 * <li>{@link #ES_DELIVERY_URI} — évalue le filtre puis appelle
 * {@code out.receive(channel, message)} pour un seul abonné ;</li>
 * <li>{@link #ES_GOSSIP_URI} — émet et reçoit les messages gossip
 * sans bloquer les autres pipelines.</li>
 * </ul>
 * Les indices retournés par {@link #createNewExecutorService} sont exposés
 * via {@link #getReceptionExecutorIndex()},
 * {@link #getPropagationExecutorIndex()},
 * {@link #getDeliveryExecutorIndex()} et
 * {@link #getGossipExecutorIndex()} pour que les ports inbound puissent
 * dispatcher avec {@code runTask(esIndex, lambda)}.
 *
 * <h2>URIs déterministes des ports inbound</h2>
 * <p>
 * les quatre ports inbound du broker sont publiés sous des URIs
 * dérivées du {@link #getReflectionInboundPortURI()} en y concaténant
 * un suffixe constant, ce qui supprime le risque
 * « last-broker-wins » des champs statiques globaux :
 * </p>
 * <ul>
 * <li>{@link #REGISTRATION_PORT_URI_SUFFIX} → {@code "<rip>-reg-in"} ;</li>
 * <li>{@link #PUBLISHING_PORT_URI_SUFFIX} → {@code "<rip>-pub-in"} ;</li>
 * <li>{@link #PRIVILEGED_PORT_URI_SUFFIX} → {@code "<rip>-priv-in"} ;</li>
 * <li>{@link #GOSSIP_INBOUND_PORT_URI_SUFFIX} → {@code "<rip>-gossip-in"}.</li>
 * </ul>
 * Les helpers {@link #registrationPortURIFor(String)},
 * {@link #publishingPortURIFor(String)},
 * {@link #privilegedPortURIFor(String)} et
 * {@link #gossipPortURIFor(String)} factorisent ce calcul côté client.
 *
 * <h2>Invariants principaux</h2>
 * <ul>
 * <li>Un client est dit enregistré ssi il apparaît à la fois dans
 * {@code registeredClients} et {@code receptionPortsOUT}.</li>
 * <li>{@code createdPrivilegedChannelsCount[uri]} reste toujours
 * dans {@code [0, quota(rc)]}.</li>
 * <li>Pour tout canal présent dans {@code channels}, il existe une
 * entrée dans {@code subscriptions} ; la réciproque tient après le
 * nettoyage post-destruction.</li>
 * <li>Un canal privilégié figure obligatoirement dans
 * {@code privilegedChannels} ET {@code channels}.</li>
 * <li>Aucun appel distant n'est effectué tant qu'un des trois RRWL
 * est détenu.</li>
 * </ul>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
@OfferedInterfaces(offered = {
	RegistrationCI.class, PublishingCI.class, PrivilegedClientCI.class, GossipReceiverCI.class
})
@RequiredInterfaces(required = {
	ReceivingCI.class, GossipSenderCI.class
})
public class Broker extends AbstractComponent implements GossipImplementationI
{
	// -------------------------------------------------------------------------
	// Executor services URIs (audit 2)
	// -------------------------------------------------------------------------

	public static final String ES_RECEPTION_URI = "broker-reception-es";
	public static final String ES_PROPAGATION_URI = "broker-propagation-es";
	public static final String ES_DELIVERY_URI = "broker-delivery-es";

	protected int esReceptionIndex;
	protected int esPropagationIndex;
	protected int esDeliveryIndex;

	public static final String ES_GOSSIP_URI = "broker-gossip-es";
	protected int esGossipIndex;

	// -------------------------------------------------------------------------
	// Concurrency control (audit 2)
	// -------------------------------------------------------------------------

	// Mode équitable : protège les écritures longues contre la famine.
	protected final ReentrantReadWriteLock registrationLock = new ReentrantReadWriteLock(true); // registeredClients, receptionPortsOUT, createdPrivilegedChannelsCount
	protected final ReentrantReadWriteLock channelsLock = new ReentrantReadWriteLock(true); // Channels, privilegedChannels
	protected final ReentrantReadWriteLock subscriptionsLock = new ReentrantReadWriteLock(true); // subscriptions
	// Note : processedGossipURIs est désormais un ConcurrentHashMap,
	// la déduplication se fait sans verrou via putIfAbsent. Aucun gossipLock
	// résiduel — on évite l'accumulation de verrous inutiles.

	// -------------------------------------------------------------------------
	// PublishingCI async (added in latest interface)
	// -------------------------------------------------------------------------

	/**
	 * Async publish with optional notification (CDC §3.4 + Audit 2).
	 *
	 * @throws IllegalArgumentException if any string parameter is null/empty,
	 * or {@code message} is null. ({@code notificationInbounhdPortURI}
	 * may be null = no notification.)
	 */
	public void asyncPublishAndNotify(
		String publisherReceptionPortURI,
		String channel,
		MessageI message,
		String notificationInbounhdPortURI
		) throws Exception
	{
		requireClient(publisherReceptionPortURI);
		requireChannel(channel);
		if (message == null) {
			throw new IllegalArgumentException("message cannot be null.");
		}
		assert publisherReceptionPortURI != null && !publisherReceptionPortURI.isEmpty()
				: "publisherReceptionPortURI normalised to non-empty after requireClient";
		assert channel != null && !channel.isEmpty()
				: "channel normalised to non-empty after requireChannel";
		// Audit 2: asynchronous submission.
		this.submitPublish(publisherReceptionPortURI, channel, message, notificationInbounhdPortURI);
	}

	/**
	 * Bulk async publish with optional notification.
	 *
	 * <p> : shares the single-snapshot bulk path with
	 * {@link #publish(String, String, ArrayList)}.</p>
	 *
	 * @throws IllegalArgumentException if any string parameter is null/empty,
	 * or {@code messages} is null/empty.
	 */
	public void asyncPublishAndNotify(
		String publisherReceptionPortURI,
		String channel,
		ArrayList<MessageI> messages,
		String notificationInbounhdPortURI
		) throws Exception
	{
		requireClient(publisherReceptionPortURI);
		requireChannel(channel);
		if (messages == null || messages.isEmpty()) {
			throw new IllegalArgumentException("messages cannot be null or empty.");
		}
		assert publisherReceptionPortURI != null && !publisherReceptionPortURI.isEmpty()
				: "publisherReceptionPortURI normalised to non-empty after requireClient";
		assert channel != null && !channel.isEmpty()
				: "channel normalised to non-empty after requireChannel";
		assert messages != null && !messages.isEmpty()
				: "messages normalised to non-empty after explicit check";
		this.bulkSubmit(publisherReceptionPortURI, channel, messages, notificationInbounhdPortURI);
	}


	// -------------------------------------------------------------------------
	// Ports
	// -------------------------------------------------------------------------

	private BrokerRegistrationInboundPort registrationPortIN;
	private BrokerPublishingInboundPort publishingPortIN;
	private BrokerPrivilegedInboundPort privilegedPortIN;
	private GossipReceiverInboundPort gossipPortIN;

	// Suffixes used to derive each broker inbound port URI from the
	// broker's reflection inbound port URI. The static field
	// REGISTRATION_PORT_URI was removed because it is per-JVM and was
	// silently clobbered by a second broker instantiated in the same JVM
	// (multi-broker scenarios in tests, ScenarioRunner wiring, etc.).
	public static final String REGISTRATION_PORT_URI_SUFFIX = "-reg-in";
	public static final String PUBLISHING_PORT_URI_SUFFIX = "-pub-in";
	public static final String PRIVILEGED_PORT_URI_SUFFIX = "-priv-in";

	// -------------------------------------------------------------------------
	// State
	// -------------------------------------------------------------------------

	/** Registered clients: receptionPortURI -> registration class. */
	final Map<String, RegistrationClass> registeredClients = new HashMap<>();
	/** Per-client outbound port to deliver messages. */
	private final Map<String, BrokerReceptionOutboundPort> receptionPortsOUT = new HashMap<>();
	/** All channels (FREE + privileged). */
	final Set<String> channels = new HashSet<>();

	/** Privileged channels metadata. */
	static class PrivilegedChannelInfo
	{
		final String ownerReceptionPortURI;
		Pattern authorisedUsersPattern;

		PrivilegedChannelInfo(String ownerReceptionPortURI, Pattern authorisedUsersPattern)
		{
			this.ownerReceptionPortURI = ownerReceptionPortURI;
			this.authorisedUsersPattern = authorisedUsersPattern;
		}
	}

	/** Privileged channels: channel -> info (owner + authorisedUsers regex). */
	final Map<String, PrivilegedChannelInfo> privilegedChannels = new HashMap<>();

	/** Per-client created privileged channels count. ConcurrentHashMap so
	 * callers can use atomic compute/merge without holding extra locks. */
	final java.util.concurrent.ConcurrentHashMap<String, Integer> createdPrivilegedChannelsCount =
			new java.util.concurrent.ConcurrentHashMap<>();

	/** Channel quotas */
	private int standardQuota;
	private int premiumQuota;
	private int nbFreeChannels;

	/** Subscriptions: channel -> (client receptionPortURI -> filter). */
	final Map<String, Map<String, MessageFilterI>> subscriptions = new HashMap<>();

	/** Number of messages currently in-flight per channel. ConcurrentHashMap
	 * so beginInFlight/finishInFlight use atomic compute() rather than
	 * synchronized blocks. */
	final java.util.concurrent.ConcurrentHashMap<String, Integer> inFlightPerChannel =
			new java.util.concurrent.ConcurrentHashMap<>();

	/** C.8-find-3: per-channel mutex used by destroyChannel to wait for
	 * in-flight delivery to drain without busy-polling. The corresponding
	 * finishInFlight call notifies any waiter when the count transitions
	 * from > 0 to 0. */
	private final java.util.concurrent.ConcurrentHashMap<String, Object> inFlightWaiters =
			new java.util.concurrent.ConcurrentHashMap<>();

	// -------------------------------------------------------------------------
	// Gossip data
	// -------------------------------------------------------------------------

	/**
	 * Outbound gossip ports keyed by the neighbour's reflection inbound
	 * port URI (i.e. the value carried in {@code GossipMessageI.getEmitterURI()}).
	 *
	 * <p>This map exists so that {@link #update(GossipMessageI[])} can identify
	 * which sender corresponds to the broker that just delivered a message and
	 * <strong>skip it</strong> when re-emitting (skip-echo). Renvoyer a
	 * gossip back to its immediate sender is wasteful: dedup will catch it,
	 * but only after a full round-trip RMI call. Skipping at the source
	 * removes the wasted work.</p>
	 */
	private final Map<String, GossipSenderOutboundPort> sendersByNeighbour = new HashMap<>();
	/** Neighboring brokers' URIs. */
	private List<String> gossipURIs;
	public static final String GOSSIP_INBOUND_PORT_URI_SUFFIX = "-gossip-in";

	/**
	 * Messages from other brokers that are already processed (ignored if
	 * received again, periodically GC'd by {@link #cleanupGossipMemory()}).
	 *
	 * <p>{@link ConcurrentHashMap} so that {@link #update(GossipMessageI[])}
	 * can dedup with a single atomic {@code putIfAbsent} call instead of
	 * lock + check + put. La précédente
	 * lock-based dedup had a window where two threads could both observe
	 * "not processed" and both proceed to forward the same message.</p>
	 */
	private final java.util.concurrent.ConcurrentMap<String, Instant> processedGossipURIs =
			new java.util.concurrent.ConcurrentHashMap<>();


	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------
	//
	// Refactor : all four overloads now delegate to a single
	// {@link #init(...)} helper. Two minimal pass-through constructors
	// remain because {@code super(...)} must be the first call:
	// - one with an explicit reflexion port URI (multi-JVM/distributed),
	// - one without (centralised CVM, generated reflexion port).
	// In both cases the broker's port URIs are derived deterministically
	// from {@link #getReflectionInboundPortURI()}, removing the
	// last-broker-wins hazard of the previous static field.

	/**
	 * Constructeur sans voisins gossip (broker isolé) ni URI de réflexion
	 * explicite — pratique pour les démos centralisées d'un seul broker.
	 *
	 * @param nbThreads nombre de threads ordinaires alloués au composant.
	 * @param nbSchedulableThreads nombre de threads ordonnançables.
	 * @param nbFreeChannels nombre de canaux libres pré-créés.
	 * @param standardQuota quota de canaux privilégiés pour STANDARD.
	 * @param premiumQuota quota de canaux privilégiés pour PREMIUM.
	 * @param nbReceptionThreads threads alloués à l'executor de réception.
	 * @param nbPropagationThreads threads alloués à l'executor de propagation.
	 * @param nbDeliveryThreads threads alloués à l'executor de livraison.
	 * @throws Exception erreur de publication des ports.
	 */
	protected Broker(int nbThreads, int nbSchedulableThreads,
					 int nbFreeChannels, int standardQuota, int premiumQuota,
					 int nbReceptionThreads, int nbPropagationThreads,
					 int nbDeliveryThreads) throws Exception {
		this(nbThreads, nbSchedulableThreads, nbFreeChannels,
				standardQuota, premiumQuota, nbReceptionThreads,
				nbPropagationThreads, nbDeliveryThreads,
				new ArrayList<>()); // pas de voisins
	}

	/**
	 * Constructeur avec voisins gossip mais sans URI de réflexion explicite.
	 *
	 * @param neighborsGossipURIs liste des URIs des ports gossip inbound des
	 * voisins fédérés (peut être vide).
	 */
	protected Broker(int nbThreads, int nbSchedulableThreads,
					 int nbFreeChannels, int standardQuota, int premiumQuota,
					 int nbReceptionThreads, int nbPropagationThreads,
					 int nbDeliveryThreads,
					 List<String> neighborsGossipURIs) throws Exception
	{
		super(nbThreads, nbSchedulableThreads);
		this.init(nbFreeChannels, standardQuota, premiumQuota,
				nbReceptionThreads, nbPropagationThreads, nbDeliveryThreads,
				neighborsGossipURIs);
	}

	/**
	 * Constructeur avec URI de réflexion explicite (cas distribué multi-JVM)
	 * et sans voisins gossip.
	 *
	 * @param reflexionPort URI du port inbound de réflexion à publier.
	 */
	protected Broker(String reflexionPort,
					 int nbThreads, int nbSchedulableThreads,
					 int nbFreeChannels, int standardQuota, int premiumQuota,
					 int nbReceptionThreads, int nbPropagationThreads,
					 int nbDeliveryThreads) throws Exception {
		this(reflexionPort,
				nbThreads, nbSchedulableThreads, nbFreeChannels,
				standardQuota, premiumQuota, nbReceptionThreads,
				nbPropagationThreads, nbDeliveryThreads,
				new ArrayList<>()); // pas de voisins
	}

	/**
	 * Constructeur complet (URI de réflexion explicite + voisins gossip).
	 * C'est la forme utilisée en mode fédéré distribué.
	 *
	 * @param reflexionPort URI du port inbound de réflexion.
	 * @param neighborsGossipURIs URIs gossip inbound des brokers voisins.
	 */
	protected Broker(String reflexionPort,
					 int nbThreads, int nbSchedulableThreads,
					 int nbFreeChannels, int standardQuota, int premiumQuota,
					 int nbReceptionThreads, int nbPropagationThreads,
					 int nbDeliveryThreads,
					 List<String> neighborsGossipURIs) throws Exception
	{
		super(reflexionPort, nbThreads, nbSchedulableThreads);
		this.init(nbFreeChannels, standardQuota, premiumQuota,
				nbReceptionThreads, nbPropagationThreads, nbDeliveryThreads,
				neighborsGossipURIs);
	}

	/**
	 * Common initialisation shared by every constructor:
	 * creates the executor services, the FREE channels, and publishes the
	 * four broker inbound ports under URIs deterministically derived from
	 * {@link #getReflectionInboundPortURI()}.
	 *
	 * <p>Must be called immediately after {@code super(...)} so that
	 * {@code getReflectionInboundPortURI()} returns the correct value
	 * (whether a URI was provided to the super constructor or generated
	 * by it).</p>
	 */
	private void init(int nbFreeChannels, int standardQuota, int premiumQuota,
					 int nbReceptionThreads, int nbPropagationThreads,
					 int nbDeliveryThreads,
					 List<String> neighborsGossipURIs) throws Exception
	{
		this.esReceptionIndex = this.createNewExecutorService(ES_RECEPTION_URI, nbReceptionThreads, false);
		this.esPropagationIndex = this.createNewExecutorService(ES_PROPAGATION_URI, nbPropagationThreads, false);
		this.esDeliveryIndex = this.createNewExecutorService(ES_DELIVERY_URI, nbDeliveryThreads, false);
		this.esGossipIndex = this.createNewExecutorService(ES_GOSSIP_URI, 4, false);

		this.nbFreeChannels = nbFreeChannels;
		for (int i = 0; i < nbFreeChannels; i++) {
			String c = "channel" + i;
			this.channels.add(c);
			this.subscriptions.put(c, new HashMap<>());
			this.inFlightPerChannel.put(c, 0);
		}
		this.standardQuota = standardQuota;
		this.premiumQuota = premiumQuota;

		final String rip = this.getReflectionInboundPortURI();

		this.registrationPortIN =
				new BrokerRegistrationInboundPort(registrationPortURIFor(rip), this);
		this.registrationPortIN.publishPort();

		this.publishingPortIN =
				new BrokerPublishingInboundPort(publishingPortURIFor(rip), this);
		this.publishingPortIN.publishPort();

		this.privilegedPortIN =
				new BrokerPrivilegedInboundPort(privilegedPortURIFor(rip), this);
		this.privilegedPortIN.publishPort();

		this.gossipPortIN =
				new GossipReceiverInboundPort(gossipPortURIFor(rip), ES_GOSSIP_URI, this);
		this.gossipPortIN.publishPort();

		this.gossipURIs = neighborsGossipURIs;
	}



	// -------------------------------------------------------------------------
	// Internal asynchronous pipeline (audit 2)
	// -------------------------------------------------------------------------

	/** Quota de canaux privilégiés autorisé pour un client {@code STANDARD}. */
	public int getStandardQuota(){
		return standardQuota;
	}
	/** Quota de canaux privilégiés autorisé pour un client {@code PREMIUM}. */
	public int getPremiumQuota(){
		return premiumQuota;
	}
	/** Nombre de canaux libres pré-créés à l'initialisation du broker. */
	public int getNbFreeChannels(){
		return nbFreeChannels;
	}

	// -------------------------------------------------------------------------
	// Executor service indices
	//
	// Exposed so inbound ports can dispatch incoming requests off the RMI
	// dispatch thread onto the broker's own dedicated executor pools. This
	// prevents RMI thread starvation under deep gossip propagation chains
	// (e.g. 4 federated JVMs).
	// -------------------------------------------------------------------------

	/** Index of the dedicated reception executor service ({@value #ES_RECEPTION_URI}). */
	public int getReceptionExecutorIndex() { return this.esReceptionIndex; }

	/** Index of the dedicated propagation executor service ({@value #ES_PROPAGATION_URI}). */
	public int getPropagationExecutorIndex() { return this.esPropagationIndex; }

	/** Index of the dedicated delivery executor service ({@value #ES_DELIVERY_URI}). */
	public int getDeliveryExecutorIndex() { return this.esDeliveryIndex; }

	/** Index of the dedicated gossip executor service ({@value #ES_GOSSIP_URI}). */
	public int getGossipExecutorIndex() { return this.esGossipIndex; }

	/**
	 * Cible de livraison pré-calculée par {@link #snapshotTargets} : associe
	 * l'URI d'un abonné, son port outbound de livraison et son filtre courant.
	 * Permet d'évaluer le filtre puis d'appeler {@code receive} sans toucher
	 * aux structures partagées.
	 */
	protected static class DeliveryTarget
	{
		final String subscriberURI;
		final BrokerReceptionOutboundPort out;
		final MessageFilterI filter;

		DeliveryTarget(String subscriberURI, BrokerReceptionOutboundPort out, MessageFilterI filter)
		{
			this.subscriberURI = subscriberURI;
			this.out = out;
			this.filter = filter;
		}
	}

	/**
	 * Soumet la première étape (réception) du pipeline asynchrone sur
	 * l'executor {@link #esReceptionIndex}. Fire-and-forget : aucun résultat
	 * n'est renvoyé à l'appelant ; les exceptions sont logguées.
	 *
	 * @param publisherReceptionPortURI URI du publieur (clef d'identification).
	 * @param channel canal cible.
	 * @param message message à publier.
	 * @param notificationInboundPortURI URI du port à notifier en fin de
	 * livraison (peut être {@code null}).
	 */
	protected void submitPublish(
		String publisherReceptionPortURI,
		String channel,
		MessageI message,
		String notificationInboundPortURI
		)
	{
		final Broker self = this;
		this.runTask(this.esReceptionIndex, o -> {
			try {
				((Broker) o).receptionStage(publisherReceptionPortURI, channel, message, notificationInboundPortURI);
			} catch (Exception e) {
				// L'étage de réception consigne l'exception et abandonne ce
				// message ; aucun mécanisme de notification d'arrêt anormal
				// n'est requis par le CDC.
				self.logMessage("[Broker] receptionStage exception: " + e + "\n");
			}
		});
	}

	/**
	 * Étape de réception du pipeline (cf. {@code docs/PIPELINE.md} §3) :
	 * incrémente d'abord le compteur {@code in-flight} pour que
	 * {@link #destroyChannel} puisse observer le message en cours, puis
	 * vérifie sous verrous {@code registrationLock.read} +
	 * {@code channelsLock.read} l'autorisation de publier, propage le
	 * message gossip aux voisins, et enfin soumet l'étape de propagation.
	 *
	 * <p><strong>Préconditions de verrou</strong> : aucune (la méthode acquiert
	 * et relâche elle-même les verrous nécessaires dans l'ordre canonique).</p>
	 *
	 * @throws UnknownClientException si le publieur n'est pas enregistré.
	 * @throws UnknownChannelException si le canal n'existe pas (ou plus).
	 * @throws UnauthorisedClientException si le publieur n'est pas autorisé.
	 */
	protected void receptionStage(
		String publisherReceptionPortURI,
		String channel,
		MessageI message,
		String notificationInboundPortURI
		) throws Exception
	{

		// Incrémenter avant validation : destroyChannel observe ainsi ce
		// message même si une vérification jette une exception en aval.
		this.beginInFlight(channel);

		this.registrationLock.readLock().lock();
		try {
			if (!this.registeredClients.containsKey(publisherReceptionPortURI)) {
				this.finishInFlight(channel);
				throw new UnknownClientException(publisherReceptionPortURI);
			}
			this.channelsLock.readLock().lock();
			try {
				if (!this.channels.contains(channel)) {
					this.finishInFlight(channel);
					throw new UnknownChannelException(channel);
				}
				if (!this.channelAuthorisedLocked(publisherReceptionPortURI, channel)) {
					this.finishInFlight(channel);
					throw new UnauthorisedClientException();
				}
			} finally {
				this.channelsLock.readLock().unlock();
			}

		} finally {
			this.registrationLock.readLock().unlock();
		}

		// send gossip
		PublishGossipMessage gossip = new PublishGossipMessage(
				java.util.UUID.randomUUID().toString(),
				Instant.now(),
				this.getReflectionInboundPortURI(),
				message,
				channel,
				publisherReceptionPortURI);
		this.gossipToNeighbours(new GossipMessageI[]{ gossip });

		// Submit propagation.
		this.runTask(this.esPropagationIndex, o -> {
			try {
				((Broker) o).propagationStage(channel, message);
			} catch (Exception e) {
				this.logMessage("[Broker] propagationStage exception: " + e + "\n");
				// ensure in-flight bookkeeping is decremented even in error.
				((Broker) o).finishInFlight(channel);
			}
		});
	}
	/**
	 * Incrémente atomiquement le compteur de messages en cours de livraison
	 * pour {@code channel}. Aucun verrou requis ({@link ConcurrentHashMap}).
	 */
	protected void beginInFlight(String channel)
	{
		// CHM.compute is atomic for a given key: no extra lock needed.
		this.inFlightPerChannel.compute(channel, (k, v) -> v == null ? 1 : v + 1);
	}

	/**
	 * Décrémente atomiquement le compteur {@code in-flight}. Si le compteur
	 * passe de {@code > 0} à {@code 0}, réveille toute thread bloquée dans
	 * {@link #destroyChannel} via le {@link #inFlightWaiters waiter} associé
	 *.
	 */
	protected void finishInFlight(String channel)
	{
		// C.8-find-3: capture whether this call drove the count from
		// > 0 to 0; if so, notify any thread blocked in destroyChannel.
		final boolean[] reachedZero = new boolean[]{ false };
		this.inFlightPerChannel.compute(channel, (k, v) -> {
			if (v == null) return 0;
			int next = v - 1;
			if (next <= 0) {
				reachedZero[0] = (v > 0);
				return 0;
			}
			return next;
		});
		if (reachedZero[0]) {
			Object waiter = this.inFlightWaiters.get(channel);
			if (waiter != null) {
				synchronized (waiter) {
					waiter.notifyAll();
				}
			}
		}
	}

	/**
	 * Étape de propagation (cf. {@code docs/PIPELINE.md} §3) : construit un
	 * snapshot des cibles puis enchaîne sur l'étape de livraison. Soumis sur
	 * l'executor {@link #esPropagationIndex} ; ne tient aucun verrou
	 * pendant l'appel récursif à {@link #deliverToTargets}.
	 */
	protected void propagationStage(String channel, MessageI message) throws Exception
	{
		List<DeliveryTarget> targets = this.snapshotTargets(channel);
		this.deliverToTargets(channel, message, targets);
	}

	/**
	 * Build a snapshot of the current subscriber set for {@code channel}.
	 * Throws {@link UnknownChannelException} if the channel was destroyed
	 * between the publisher check and propagation.
	 *
	 * <p> : factored out so bulk publish can reuse a single
	 * snapshot for many messages.</p>
	 */
	protected List<DeliveryTarget> snapshotTargets(String channel) throws Exception
	{
		List<DeliveryTarget> targets = new ArrayList<>();
		this.registrationLock.readLock().lock();
		try {
			this.subscriptionsLock.readLock().lock();
			try {
				Map<String, MessageFilterI> subs = this.subscriptions.get(channel);
				if (subs == null) {
					throw new UnknownChannelException(channel);
				}
				for (Map.Entry<String, MessageFilterI> e : subs.entrySet()) {
					String subscriberURI = e.getKey();
					MessageFilterI filter = e.getValue();
					BrokerReceptionOutboundPort out = this.receptionPortsOUT.get(subscriberURI);
					if (out != null) {
						targets.add(new DeliveryTarget(subscriberURI, out, filter));
					}
				}
			} finally {
				this.subscriptionsLock.readLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}
		return targets;
	}

	/**
	 * Submit one delivery task per pre-built target, decrementing the
	 * channel's in-flight counter when the last delivery completes.
	 */
	protected void deliverToTargets(
		String channel,
		MessageI message,
		List<DeliveryTarget> targets
		)
	{
		final int expected = targets.size();
		if (expected == 0) {
			this.finishInFlight(channel);
			return;
		}
		final java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(expected);
		for (DeliveryTarget t : targets) {
			this.runTask(this.esDeliveryIndex, o -> {
				try {
					MessageFilterI f = t.filter;
					if (f != null && f.match(message)) {
						t.out.receive(channel, message);
					}
				} catch (Exception e) {
					this.logMessage("[Broker] delivery exception to " + t.subscriberURI + ": " + e + "\n");
				} finally {
					if (remaining.decrementAndGet() == 0) {
						((Broker) o).finishInFlight(channel);
					}
				}
			});
		}
	}

	/**
	 * Verify that {@code publisherReceptionPortURI} is registered, that
	 * {@code channel} exists, and that the publisher is authorised to
	 * publish on it. Throws the appropriate business exception otherwise.
	 *
	 * <p> : extracted from {@link #receptionStage} so bulk publish
	 * can re-check per message and honour mid-batch revocations.</p>
	 */
	protected void validatePublisherAuthz(String publisherReceptionPortURI, String channel) throws Exception
	{
		this.registrationLock.readLock().lock();
		try {
			if (!this.registeredClients.containsKey(publisherReceptionPortURI)) {
				throw new UnknownClientException(publisherReceptionPortURI);
			}
			this.channelsLock.readLock().lock();
			try {
				if (!this.channels.contains(channel)) {
					throw new UnknownChannelException(channel);
				}
				if (!this.channelAuthorisedLocked(publisherReceptionPortURI, channel)) {
					throw new UnauthorisedClientException();
				}
			} finally {
				this.channelsLock.readLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}
	}

	// -------------------------------------------------------------------------
	// Component life cycle
	// -------------------------------------------------------------------------

	/**
	 * Démarrage du composant : crée et connecte un
	 * {@link GossipSenderOutboundPort} vers chaque voisin gossip configuré
	 * dans le constructeur. Les ports sont indexés dans
	 * {@link #sendersByNeighbour} par l'URI de réflexion du voisin afin de
	 * pouvoir filtrer l'émetteur immédiat dans {@link #update} (skip-echo).
	 */
	@Override
	public void start() throws ComponentStartException {
		super.start();
		// Connecter les ports sortants gossip vers chaque voisin.
		// On indexe les sender par URI de port d'écoute du voisin
		// (e.g. "broker-1-gossip-in") afin de pouvoir, dans update(),
		// retrouver à l'envers le voisin qui vient de nous transmettre
		// un message et l'exclure de la re-diffusion (skip-echo).
		try {
			for (String neighbourGossipInURI : this.gossipURIs) {
				GossipSenderOutboundPort out = new GossipSenderOutboundPort(this);
				out.publishPort();
				this.doPortConnection(
						out.getPortURI(),
						neighbourGossipInURI,
						GossipConnector.class.getCanonicalName());
				String neighbourReflectionURI =
						stripGossipSuffix(neighbourGossipInURI);
				this.sendersByNeighbour.put(neighbourReflectionURI, out);
			}
		} catch (Exception e) {
			throw new ComponentStartException(e);
		}
	}

	/**
	 * Strip the well-known {@link #GOSSIP_INBOUND_PORT_URI_SUFFIX} suffix
	 * from a gossip inbound port URI to recover the broker's reflection
	 * inbound port URI. Returns the input unchanged if the suffix is
	 * absent (defensive: some demos pass arbitrary URIs).
	 */
	private static String stripGossipSuffix(String gossipInboundURI) {
		if (gossipInboundURI != null
				&& gossipInboundURI.endsWith(GOSSIP_INBOUND_PORT_URI_SUFFIX)) {
			return gossipInboundURI.substring(
					0,
					gossipInboundURI.length() - GOSSIP_INBOUND_PORT_URI_SUFFIX.length());
		}
		return gossipInboundURI;
	}

	/**
	 * Boucle d'exécution du composant : planifie un nettoyage périodique de
	 * la mémoire gossip toutes les 2 minutes (si un thread ordonnançable
	 * est disponible). Sinon, le nettoyage devient opportuniste.
	 */
	@Override
	public void execute() throws Exception {
		super.execute();
		// Nettoyage périodique de la mémoire gossip (toutes les 2 minutes).
		// Nécessite au moins un thread ordonnançable. Si le constructeur
		// a été appelé avec nbSchedulableThreads = 0 (cas d'un broker
		// utilisé en réception pure dans un test), on ne peut pas planifier ;
		// on bascule alors sur un nettoyage opportuniste lors de l'arrivée
		// de nouveaux messages gossip (cf. update()).
		if (this.canScheduleTasks()) {
			this.scheduleTaskAtFixedRate(
					o -> ((Broker) o).cleanupGossipMemory(),
					2, 2, TimeUnit.MINUTES);
		}
	}

	/**
	 * Finalisation : déconnecte tous les ports outbound de livraison vers
	 * les clients ainsi que les ports gossip vers les voisins. Pas de
	 * destruction ici (laissée à {@link #shutdown()}).
	 */
	@Override
	public void finalise() throws Exception {

		for (BrokerReceptionOutboundPort out : this.receptionPortsOUT.values()) {
			if (out.connected()) {
				this.doPortDisconnection(out.getPortURI());
			}
		}
		for (GossipSenderOutboundPort out : this.sendersByNeighbour.values()) {
			if (out.connected()) {
				this.doPortDisconnection(out.getPortURI());
			}
		}

		super.finalise();
	}

	/**
	 * Arrêt du composant : dépublie et détruit tous les ports inbound et
	 * outbound. Synchronisé pour éviter une double-libération.
	 */
	@Override
	public synchronized void shutdown() throws ComponentShutdownException {
		try {
			for (BrokerReceptionOutboundPort out : this.receptionPortsOUT.values()) {
				if (!out.isDestroyed()) {
					out.unpublishPort();
					out.destroyPort();
				}
			}
			this.receptionPortsOUT.clear();

			for (GossipSenderOutboundPort out : this.sendersByNeighbour.values()) {
				if (!out.isDestroyed()) {
					out.unpublishPort();
					out.destroyPort();
				}
			}
			this.sendersByNeighbour.clear();

			// Unpublishing Inbound ports
			if (registrationPortIN != null) {
				registrationPortIN.unpublishPort();
				registrationPortIN.destroyPort();
			}
			if (this.publishingPortIN != null) {
				this.publishingPortIN.unpublishPort();
				this.publishingPortIN.destroyPort();
			}
			if (privilegedPortIN != null) {
				privilegedPortIN.unpublishPort();
				privilegedPortIN.destroyPort();
			}
			if (this.gossipPortIN != null) {
				this.gossipPortIN.unpublishPort();
				this.gossipPortIN.destroyPort();
			}
		} catch (Exception e) {
			throw new ComponentShutdownException(e);
		}
 super.shutdown();
}
	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Deterministic registration inbound port URI for the broker whose
	 * reflection inbound port URI is {@code brokerReflectionURI}
	 *. Replaces the per-JVM static {@code REGISTRATION_PORT_URI}.
	 */
	public static String registrationPortURIFor(String brokerReflectionURI)
	{
		Objects.requireNonNull(brokerReflectionURI, "brokerReflectionURI");
		return brokerReflectionURI + REGISTRATION_PORT_URI_SUFFIX;
	}

	/**
	 * Deterministic publishing inbound port URI for the given broker.
	 *
	 * <p>Public visibility is intentional and complementary to
	 * {@link #registrationPortURIFor(String)} / {@link #gossipPortURIFor(String)}.
	 * Le souci d'"encapsulation" pourrait être soulevé contre
	 * the broker exposing port <em>instances</em> via public field accessors
	 * ({@code Broker.publishingPortIN}/{@code Broker.privilegedPortIN}), not
	 * against deterministic URI derivation: knowing the URI of a port does
	 * not grant any permission. Authorisation is enforced server-side at
	 * each call ({@code requireRegistered(rip)} +
	 * {@code requireServiceClass(rip, expected)}). Exposing the URI here
	 * lets each role-specific plugin own its own outbound port
	 * and connect at {@code initialise()} time, ce qui est l'approche
	 * §5.2 review explicitly asked for.</p>
	 */
	public static String publishingPortURIFor(String brokerReflectionURI)
	{
		Objects.requireNonNull(brokerReflectionURI, "brokerReflectionURI");
		return brokerReflectionURI + PUBLISHING_PORT_URI_SUFFIX;
	}

	/**
	 * Deterministic privileged inbound port URI for the given broker.
	 *
	 * <p>Public visibility is intentional, see the rationale on
	 * {@link #publishingPortURIFor(String)}. Authorisation for privileged
	 * operations (e.g. {@link #createChannel(String, String, String)
	 * createChannel}) is enforced per-call by the broker via
	 * {@code requireServiceClass(rip, STANDARD|PREMIUM)}: a FREE client
	 * cannot escalate by knowing this URI.</p>
	 */
	public static String privilegedPortURIFor(String brokerReflectionURI)
	{
		Objects.requireNonNull(brokerReflectionURI, "brokerReflectionURI");
		return brokerReflectionURI + PRIVILEGED_PORT_URI_SUFFIX;
	}

	/** Deterministic gossip inbound port URI for the given broker. */
	public static String gossipPortURIFor(String brokerReflectionURI)
	{
		Objects.requireNonNull(brokerReflectionURI, "brokerReflectionURI");
		return brokerReflectionURI + GOSSIP_INBOUND_PORT_URI_SUFFIX;
	}

	// -------------------------------------------------------------------------
	// Registration (RegistrationCI)
	// -------------------------------------------------------------------------

	private boolean registeredLocked(String receptionPortURI){
		// Internal contract: caller must hold registrationLock (read or write).
		assert this.registrationLock.getReadHoldCount() > 0
				|| this.registrationLock.isWriteLockedByCurrentThread()
				: "registrationLock (read or write) must be held by current thread";
		return this.registeredClients.containsKey(receptionPortURI);
	}
	boolean channelExistLocked(String channel)
	{
		// Internal contract: caller must hold channelsLock (read or write).
		assert this.channelsLock.getReadHoldCount() > 0
				|| this.channelsLock.isWriteLockedByCurrentThread()
				: "channelsLock (read or write) must be held by current thread";
		return this.channels.contains(channel);
	}

	/**
	 * Vérifie sans bloquer le pipeline si le client identifié par
	 * {@code receptionPortURI} est actuellement enregistré.
	 *
	 * @param receptionPortURI URI du port de réception du client.
	 * @return {@code true} ssi le client figure dans {@code registeredClients}.
	 * @throws Exception en cas d'erreur d'acquisition de verrou.
	 */
	public boolean registered(String receptionPortURI) throws Exception
	{

		this.registrationLock.readLock().lock();
		try {
			return this.registeredLocked(receptionPortURI);
		} finally {
			this.registrationLock.readLock().unlock();
		}

	}

	/**
	 * CDC §3.4 contract: returns true iff the client identified by
	 * {@code receptionPortURI} is currently registered <strong>and</strong>
	 * registered under the requested service class {@code rc}.
	 *
	 * <p>If the client is not registered under any class, this method must
	 * raise {@link UnknownClientException} — silently
	 * returning {@code false} would hide the difference between
	 * "registered but in another class" and "not registered at all".</p>
	 *
	 * @param receptionPortURI URI of the client's reception port.
	 * @param rc service class to test.
	 * @return true iff the client is registered under {@code rc}.
	 * @throws UnknownClientException if the client is not registered.
	 * @throws Exception propagated from any I/O failure.
	 */
	public boolean registered(String receptionPortURI, RegistrationClass rc) throws Exception
	{
		assert receptionPortURI != null && !receptionPortURI.isEmpty() :
				"receptionPortURI cannot be null or empty";
		assert rc != null : "rc cannot be null";

		this.registrationLock.readLock().lock();
		try {
			RegistrationClass current = this.registeredClients.get(receptionPortURI);
			if (current == null) {
				throw new UnknownClientException(receptionPortURI);
			}
			return rc.equals(current);
		} finally {
			this.registrationLock.readLock().unlock();
		}

	}

	/**
	 * Enregistre un nouveau client avec sa classe de service initiale.
	 *
	 * <p>Algorithme :</p>
	 * <ol>
	 * <li>Réservation atomique de l'entrée sous {@code registrationLock.write}
	 * (rejette les doublons concurrents).</li>
	 * <li>Connexion distante <em>hors verrou</em> (règle « no remote call
	 * under lock »). Échec ⇒ rollback de l'étape 1.</li>
	 * <li>Mémorisation du port outbound de livraison sous
	 * {@code registrationLock.write}.</li>
	 * <li>Propagation gossip d'un {@link RegisterGossipMessage}.</li>
	 * </ol>
	 *
	 * @param receptionPortURI URI du port de réception du nouveau client.
	 * @param rc classe de service initiale.
	 * @return URI du port inbound broker à utiliser pour publier
	 * (publishing pour FREE, privileged sinon).
	 * @throws AlreadyRegisteredException si l'URI est déjà enregistrée.
	 * @throws IllegalArgumentException si un paramètre est null/vide.
	 */
	public String register(String receptionPortURI, RegistrationClass rc) throws Exception
	{
		if (receptionPortURI == null || receptionPortURI.isEmpty()) {
			throw new IllegalArgumentException("receptionPortURI cannot be null or empty.");
		}
		if (rc == null) {
			throw new IllegalArgumentException("rc cannot be null.");
		}

		// Réservation atomique de l'entrée afin que deux register() concurrents
		// sur la même URI ne réussissent pas tous les deux. Pas de
		// double-check préliminaire hors verrou : ce serait un TOCTOU.
		this.registrationLock.writeLock().lock();
		try {
			if (this.registeredLocked(receptionPortURI)) {
				throw new AlreadyRegisteredException();
			}
			this.registeredClients.put(receptionPortURI, rc);
			if (rc != RegistrationClass.FREE) {
				this.createdPrivilegedChannelsCount.putIfAbsent(receptionPortURI, 0);
			}
		} finally {
			this.registrationLock.writeLock().unlock();
		}

		// Connexion distante hors verrou : règle "no remote call under lock".
		BrokerReceptionOutboundPort out = new BrokerReceptionOutboundPort(this);
		try {
			out.publishPort();
			this.doPortConnection(
			out.getPortURI(),
			receptionPortURI,
			ReceivingConnector.class.getCanonicalName());
		} catch (Exception e) {
			// Rollback : on retire la réservation pour ne pas laisser un
			// client "enregistré" mais sans port de livraison.
			this.registrationLock.writeLock().lock();
			try {
				this.registeredClients.remove(receptionPortURI);
				this.createdPrivilegedChannelsCount.remove(receptionPortURI);
			} finally {
				this.registrationLock.writeLock().unlock();
			}
			try { out.unpublishPort(); } catch (Exception ignored) {}
			try { out.destroyPort(); } catch (Exception ignored) {}
			throw e;
		}

		this.registrationLock.writeLock().lock();
		try {
			this.receptionPortsOUT.put(receptionPortURI, out);
		} finally {
			this.registrationLock.writeLock().unlock();
		}

		// Post-mutation invariant: client is now visible in both maps.
		assert this.registered(receptionPortURI)
				: "client must be registered after successful register: " + receptionPortURI;
		assert this.receptionPortsOUT.containsKey(receptionPortURI)
				: "outbound port must be wired after successful register: " + receptionPortURI;


		RegisterGossipMessage gossip = new RegisterGossipMessage(
				java.util.UUID.randomUUID().toString(), // URI unique
				Instant.now(),
				this.getReflectionInboundPortURI(), // émetteur = ce courtier
				receptionPortURI, // client qui s'enregistre
				rc);

		this.gossipToNeighbours(new GossipMessageI[]{ gossip }); //initiation de la propagation

		// Retourner l'URI du port entrant adapté à la classe de service.
		// FREE : seulement publish ; STANDARD/PREMIUM : privileged
		// (qui hérite de PublishingCI, donc couvre publish également).
		if (rc == RegistrationClass.FREE) {
			return publishingPortIN.getPortURI();
		}else{
			return privilegedPortIN.getPortURI();
		}
	}

	/**
	 * Modifie la classe de service d'un client déjà enregistré (upgrade
	 * STANDARD/PREMIUM ou downgrade vers FREE). Lors d'un downgrade vers
	 * FREE, tous les canaux privilégiés possédés sont détruits et leur
	 * suppression propagée par gossip.
	 *
	 * <p> -find-5 : la transition d'état tient en une seule prise de
	 * {@code registrationLock.write} pour éliminer un TOCTOU avec un
	 * {@code unregister} concurrent. Aucun appel public n'est effectué sous
	 * le verrou.</p>
	 *
	 * @return URI du port inbound broker correspondant à la nouvelle classe.
	 * @throws UnknownClientException si le client n'est pas enregistré.
	 * @throws IllegalArgumentException si {@code rc} est {@code null}.
	 */
	public String modifyServiceClass(String receptionPortURI, RegistrationClass rc) throws Exception
	{
		if (rc == null) {
			throw new IllegalArgumentException("rc cannot be null.");
		}
		requireClient(receptionPortURI);

		// C.8-find-5: collapse the registration-state transition into a
		// SINGLE writeLock acquisition. The previous version called
		// registered() (which takes/releases readLock), then acquired
		// writeLock to flip the class, then released and re-acquired
		// later for the FREE-downgrade quota mutation — leaving a TOCTOU
		// window where a concurrent unregister + re-register could leak
		// state. Now: validate + flip class + (FREE: snapshot owned
		// channels, STANDARD/PREMIUM: install quota entry) atomically.
		// No public method is called while the writeLock is held — so
		// destroyChannelNow runs only afterwards.
		List<String> ownedChannels = new ArrayList<>();
		this.registrationLock.writeLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			this.registeredClients.put(receptionPortURI, rc);
			if (rc == RegistrationClass.FREE) {
				this.channelsLock.readLock().lock();
				try {
					for (Map.Entry<String, PrivilegedChannelInfo> e : this.privilegedChannels.entrySet()) {
						if (e.getValue().ownerReceptionPortURI.equals(receptionPortURI)) {
							ownedChannels.add(e.getKey());
						}
					}
				} finally {
					this.channelsLock.readLock().unlock();
				}
			} else {
				this.createdPrivilegedChannelsCount.putIfAbsent(receptionPortURI, 0);
			}
		} finally {
			this.registrationLock.writeLock().unlock();
		}


		// Allowing service class upgrade/downgrade
		if (rc == RegistrationClass.FREE) {
			List<GossipMessageI> gossipMessages = new ArrayList<>();
			// destroyChannelNow hors lock — elle prend ses propres locks
			for (String ch : ownedChannels) {
				this.destroyChannelNow(receptionPortURI, ch, false); //ne pas repropager
				gossipMessages.add(new DestroyChannelGossipMessage(
						java.util.UUID.randomUUID().toString(),
						Instant.now(),
						this.getReflectionInboundPortURI(),
						ch,
						receptionPortURI));
			}
			ModifyServiceClassGossipMessage modificationGossip = new ModifyServiceClassGossipMessage(
					java.util.UUID.randomUUID().toString(),
					Instant.now(),
					this.getReflectionInboundPortURI(),
					receptionPortURI,
					rc);
			gossipMessages.add(modificationGossip);

			// Final quota cleanup, with re-validation: a concurrent
			// unregister between the two writeLock sections must NOT
			// allow this remove() to clobber a fresh re-registration.
			this.registrationLock.writeLock().lock();
			try {
				if (this.registeredLocked(receptionPortURI)
						&& this.registeredClients.get(receptionPortURI) == RegistrationClass.FREE) {
					this.createdPrivilegedChannelsCount.remove(receptionPortURI);
				}
			} finally {
				this.registrationLock.writeLock().unlock();
			}
			this.gossipToNeighbours(gossipMessages.toArray(new GossipMessageI[0]));
			return publishingPortIN.getPortURI();

		} else {
			return privilegedPortIN.getPortURI();
		}
	}

	/**
	 * Désenregistre un client : détruit tous ses canaux privilégiés, retire
	 * ses souscriptions, son entrée registre et son port outbound de
	 * livraison, puis propage la suppression via gossip.
	 *
	 * @throws UnknownClientException si le client n'est pas enregistré.
	 */
	public void unregister(String receptionPortURI) throws Exception {
		// vérifier d'abord
		this.registrationLock.readLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}

		// détruire les canaux avant de retirer le client
		List<String> ownedChannels = new ArrayList<>();
		List<GossipMessageI> gossipMessages = new ArrayList<>();
		this.channelsLock.readLock().lock();
		try {
			for (Map.Entry<String, PrivilegedChannelInfo> e : privilegedChannels.entrySet()) {
				if (e.getValue().ownerReceptionPortURI.equals(receptionPortURI)) {
					ownedChannels.add(e.getKey());
				}
			}
		} finally {
			this.channelsLock.readLock().unlock();
		}
		for (String ch : ownedChannels) {
			this.destroyChannelNow(receptionPortURI, ch, false); // ne pas repropager
			gossipMessages.add(new DestroyChannelGossipMessage(
					java.util.UUID.randomUUID().toString(),
					Instant.now(),
					this.getReflectionInboundPortURI(),
					ch,
					receptionPortURI));
		}
		UnregisterGossipMessage gossip = new UnregisterGossipMessage(
				java.util.UUID.randomUUID().toString(),
				Instant.now(),
				this.getReflectionInboundPortURI(),
				receptionPortURI);

		gossipMessages.add(gossip);

		// retirer subscriptions
		this.subscriptionsLock.writeLock().lock();
		try {
			for (Map<String, MessageFilterI> subs : this.subscriptions.values()) {
				subs.remove(receptionPortURI);
			}
		} finally {
			this.subscriptionsLock.writeLock().unlock();
		}

		// maintenant retirer le client
		BrokerReceptionOutboundPort out;
		this.registrationLock.writeLock().lock();
		try {
			out = this.receptionPortsOUT.remove(receptionPortURI);
			this.registeredClients.remove(receptionPortURI);
			this.createdPrivilegedChannelsCount.remove(receptionPortURI);
		} finally {
			this.registrationLock.writeLock().unlock();
		}
		// Post-mutation invariants: client gone from every relevant map.
		assert !this.registeredClients.containsKey(receptionPortURI)
				: "client must be removed from registeredClients after unregister: " + receptionPortURI;
		assert !this.receptionPortsOUT.containsKey(receptionPortURI)
				: "client must be removed from receptionPortsOUT after unregister: " + receptionPortURI;
		assert !this.createdPrivilegedChannelsCount.containsKey(receptionPortURI)
				: "client must be removed from createdPrivilegedChannelsCount after unregister: " + receptionPortURI;
		// propager la suppression
		this.gossipToNeighbours(gossipMessages.toArray(new GossipMessageI[0]));

		// déconnecter hors verrou
		if (out != null) {
			try { if (out.connected()) this.doPortDisconnection(out.getPortURI()); }
			catch (Exception ignored) {}
			try { out.unpublishPort(); } catch (Exception ignored) {}
			try { out.destroyPort(); } catch (Exception ignored) {}
		}
	}

	// -------------------------------------------------------------------------
	// Channel/subscriptions (RegistrationCI)
	// -------------------------------------------------------------------------

	/**
	 * Pre-check used by every channel-guarded method: rejects {@code null} or
	 * empty {@code channel} arguments before any lock is taken.
	 *
	 * @throws IllegalArgumentException if {@code channel} is {@code null}
	 * or empty.
	 */
	private static void requireChannel(String channel) {
		if (channel == null || channel.isEmpty()) {
			throw new IllegalArgumentException("channel cannot be null or empty.");
		}
	}

	/**
	 * Pre-check used by every method that identifies a client by its
	 * reception port URI. Rejects {@code null}/empty before any
	 * lock is taken.
	 *
	 * @throws IllegalArgumentException if {@code receptionPortURI} is
	 * {@code null} or empty.
	 */
	private static void requireClient(String receptionPortURI) {
		if (receptionPortURI == null || receptionPortURI.isEmpty()) {
			throw new IllegalArgumentException(
					"receptionPortURI cannot be null or empty.");
		}
	}

	/**
	 * Normalise a {@code null} filter to the canonical accept-all
	 * singleton. The CDC §3.4 contract allows {@code null}
	 * to mean "accept every message"; storing the singleton instead of
	 * {@code null} avoids null checks on the delivery hot path.
	 */
	private static MessageFilterI orAcceptAll(MessageFilterI filter) {
		return filter == null ? AcceptAllMessageFilter.INSTANCE : filter;
	}

	/**
	 * Returns true iff the given channel currently exists.
	 *
	 * @throws IllegalArgumentException if {@code channel} is null/empty.
	 */
	public boolean channelExist(String channel) throws Exception
	{
		requireChannel(channel);
		this.channelsLock.readLock().lock();
		try {
			return this.channelExistLocked(channel);
		} finally {
			this.channelsLock.readLock().unlock();
		}	}


	private boolean channelAuthorisedLocked(String receptionPortURI, String channel) throws Exception{

		// Internal contract: both registrationLock AND channelsLock must
		// be held (read or write) by the current thread.
		assert this.registrationLock.getReadHoldCount() > 0
				|| this.registrationLock.isWriteLockedByCurrentThread()
				: "registrationLock must be held by current thread";
		assert this.channelsLock.getReadHoldCount() > 0
				|| this.channelsLock.isWriteLockedByCurrentThread()
				: "channelsLock must be held by current thread";
		if (!this.registeredLocked(receptionPortURI)) {
			throw new UnknownClientException(receptionPortURI);
		}
		if (!this.channelExistLocked(channel)) {
			throw new UnknownChannelException(channel);
		}
		// FREE channels: always authorised for registered clients.
		if (!this.privilegedChannels.containsKey(channel)) {
			return true;
		}
		// Privileged channel: check authorised users regex against the receptionPortURI.
		PrivilegedChannelInfo info = this.privilegedChannels.get(channel);
		if (info == null || info.authorisedUsersPattern == null) {
			// null/absent regex means "all authorised" (as allowed by CI contract).
			return true;
		}
		if (info.ownerReceptionPortURI.equals(receptionPortURI)) {
			return true;
		}
		return info.authorisedUsersPattern.matcher(receptionPortURI).matches();


	}

	/**
	 * @throws IllegalArgumentException if a parameter is null/empty.
	 * @throws UnknownClientException if {@code receptionPortURI} is unknown.
	 * @throws UnknownChannelException if {@code channel} does not exist.
	 */
	public boolean channelAuthorised(String receptionPortURI, String channel) throws Exception {
		requireClient(receptionPortURI);
		requireChannel(channel);

		this.registrationLock.readLock().lock();
		try {
			this.channelsLock.readLock().lock();
			try {
				return this.channelAuthorisedLocked(receptionPortURI, channel);
			} finally {
				this.channelsLock.readLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}
	}

	/**
	 * @throws IllegalArgumentException if a parameter is null/empty.
	 * @throws UnknownClientException if {@code receptionPortURI} is unknown.
	 * @throws UnknownChannelException if {@code channel} does not exist.
	 */
	public boolean subscribed(String receptionPortURI, String channel) throws Exception
	{
		requireClient(receptionPortURI);
		requireChannel(channel);
		this.registrationLock.readLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			this.channelsLock.readLock().lock();
			try {
				if (!this.channelExistLocked(channel)) {
					throw new UnknownChannelException(channel);
				}
				this.subscriptionsLock.readLock().lock();
				try {
					Map<String, MessageFilterI> subs = this.subscriptions.get(channel);
					return subs != null && subs.containsKey(receptionPortURI);
				} finally {
					this.subscriptionsLock.readLock().unlock();
				}
			} finally {
				this.channelsLock.readLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}
	}

	/**
	 * Subscribe {@code receptionPortURI} to {@code channel} with {@code filter}.
	 *
	 * <p>A {@code null} filter is interpreted as "accept every message" per
	 * CDC §3.4 and is stored internally as {@link AcceptAllMessageFilter#INSTANCE}
	 * so the delivery hot path never has to null-check.</p>
	 *
	 * @throws IllegalArgumentException if a string parameter is null/empty.
	 * @throws UnknownClientException if the client is not registered.
	 * @throws UnknownChannelException if the channel does not exist.
	 * @throws UnauthorisedClientException if the client lacks permission on
	 * a privileged channel.
	 */
	public void subscribe(String receptionPortURI, String channel, MessageFilterI filter) throws Exception
	{
		requireClient(receptionPortURI);
		requireChannel(channel);
		final MessageFilterI effective = orAcceptAll(filter);

		// Validation sous deux verrous lecture, puis souscription en écriture
		// (ordre canonique respecté).
		this.registrationLock.readLock().lock();
		try {
			this.channelsLock.readLock().lock();
			try {
				if (!this.channelAuthorisedLocked(receptionPortURI, channel)) {
					throw new UnauthorisedClientException();
				}
				this.subscriptionsLock.writeLock().lock();
				try {
					Map<String, MessageFilterI> subs = this.subscriptions.get(channel);
					if (subs == null) {
						// Course possible : le canal a été détruit entre la
						// vérification ci-dessus et la prise du verrou.
						throw new UnknownChannelException(channel);
					}
					subs.put(receptionPortURI, effective);
					// Post-mutation invariant: subscription is now visible.
					assert subs.containsKey(receptionPortURI)
							: "subscriber must be present after subscribe: "
							+ receptionPortURI + " on " + channel;
					assert subs.get(receptionPortURI) == effective
							: "stored filter must be the effective (non-null) filter";
				} finally {
					this.subscriptionsLock.writeLock().unlock();
				}
			} finally {
				this.channelsLock.readLock().unlock();
			}

		} finally {
			this.registrationLock.readLock().unlock();
		}
	}

	/**
	 * @throws IllegalArgumentException if a parameter is null/empty.
	 * @throws UnknownClientException if the client is not registered.
	 * @throws UnknownChannelException if the channel does not exist.
	 * @throws NotSubscribedChannelException if the client is not subscribed.
	 */
	public void unsubscribe(String receptionPortURI, String channel) throws Exception
	{
		requireClient(receptionPortURI);
		requireChannel(channel);
		this.registrationLock.readLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			this.channelsLock.readLock().lock();
			try {
				if (!this.channelExistLocked(channel)) {
					throw new UnknownChannelException(channel);
				}
				this.subscriptionsLock.writeLock().lock();
				try {
					Map<String, MessageFilterI> subs = this.subscriptions.get(channel);
					if (subs == null || !subs.containsKey(receptionPortURI)) {
						throw new NotSubscribedChannelException(
								"Client " + receptionPortURI + " not subscribed to " + channel);
					}
					subs.remove(receptionPortURI);
					// Post-mutation invariant: subscription is gone.
					assert !subs.containsKey(receptionPortURI)
							: "subscriber must be absent after unsubscribe: "
							+ receptionPortURI + " on " + channel;
				} finally {
					this.subscriptionsLock.writeLock().unlock();
				}
			} finally {
				this.channelsLock.readLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}
	}

	/**
	 * Modify the subscription filter of {@code receptionPortURI} on
	 * {@code channel}. A {@code null} filter is treated as "accept every
	 * message" and stored as {@link AcceptAllMessageFilter#INSTANCE}
	 *.
	 *
	 * @throws IllegalArgumentException if a string parameter is null/empty.
	 * @throws UnknownClientException if the client is not registered.
	 * @throws UnknownChannelException if the channel does not exist.
	 * @throws NotSubscribedChannelException if the client is not subscribed.
	 */
	public boolean modifyFilter(String receptionPortURI, String channel, MessageFilterI filter) throws Exception
	{
		requireClient(receptionPortURI);
		requireChannel(channel);
		final MessageFilterI effective = orAcceptAll(filter);

		this.registrationLock.readLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			this.channelsLock.readLock().lock();
			try {
				if (!this.channelExistLocked(channel)) {
					throw new UnknownChannelException(channel);
				}
				this.subscriptionsLock.writeLock().lock();
				try {
					Map<String, MessageFilterI> subs = this.subscriptions.get(channel);
					if (subs == null || !subs.containsKey(receptionPortURI)) {
						throw new NotSubscribedChannelException(
								"Client " + receptionPortURI + " not subscribed to " + channel);
					}
					subs.put(receptionPortURI, effective);
					// Post-mutation invariant: stored filter == new filter.
					assert subs.get(receptionPortURI) == effective
							: "stored filter must match the new effective filter for "
							+ receptionPortURI + " on " + channel;
					return true;
				} finally {
					this.subscriptionsLock.writeLock().unlock();
				}
			} finally {
				this.channelsLock.readLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}
	}

	// -------------------------------------------------------------------------
	// Publishing (PublishingCI)
	// -------------------------------------------------------------------------

	/**
	 * Asynchronous publish (CDC §3.4 / Audit 2). Runs the reception stage on
	 * the broker's dedicated reception executor; the call is fire-and-forget.
	 *
	 * <p>Pre-checks are surface only ({@code IllegalArgumentException} if any
	 * argument is null/empty). The business exceptions
	 * ({@link UnknownClientException}, {@link UnknownChannelException},
	 * {@link UnauthorisedClientException}) are raised inside the reception
	 * stage and surfaced via the broker logger (will route them
	 * through {@code AbnormalTerminationNotificationCI}).</p>
	 *
	 * @throws IllegalArgumentException if any argument is null/empty.
	 */
	public void publish(String publisherReceptionPortURI, String channel, MessageI message) throws Exception
	{
		requireClient(publisherReceptionPortURI);
		requireChannel(channel);
		if (message == null) {
			throw new IllegalArgumentException("message cannot be null.");
		}
		// Audit 2: publish must be asynchronous (fire-and-forget submission).
		this.submitPublish(publisherReceptionPortURI, channel, message, null);
	}

	/**
	 * Bulk publish: like {@link #publish(String, String, MessageI)} but for
	 * a non-empty list of messages.
	 *
	 * <p> : builds the subscriber snapshot ONCE before iterating
	 * the messages, so the cost of subscription scanning is paid one
	 * time per bulk call. Per-message we still re-validate the publisher
	 * (registered? channel exists? authorised?) so that mid-batch
	 * revocations take effect.</p>
	 *
	 * @throws IllegalArgumentException if any argument is null/empty.
	 */
	public void publish(String publisherReceptionPortURI, String channel, ArrayList<MessageI> messages) throws Exception
	{
		requireClient(publisherReceptionPortURI);
		requireChannel(channel);
		if (messages == null || messages.isEmpty()) {
			throw new IllegalArgumentException("messages cannot be null or empty.");
		}
		this.bulkSubmit(publisherReceptionPortURI, channel, messages, null);
	}

	/**
	 * Bulk reception path used by {@link #publish(String, String, ArrayList)}
	 * and the bulk overload of {@link #asyncPublishAndNotify}. Snapshots
	 * subscribers once, validates the publisher per message and dispatches
	 * each message through the propagation/delivery executor services.
	 */
	protected void bulkSubmit(
		String publisherReceptionPortURI,
		String channel,
		ArrayList<MessageI> messages,
		String notificationInboundPortURI
		)
	{
		final Broker self = this;
		final ArrayList<MessageI> snapshotMessages = new ArrayList<>(messages);
		this.runTask(this.esReceptionIndex, o -> {
			Broker broker = (Broker) o;
			List<DeliveryTarget> targets;
			try {
				broker.validatePublisherAuthz(publisherReceptionPortURI, channel);
				targets = broker.snapshotTargets(channel);
			} catch (Exception e) {
				self.logMessage("[Broker] bulk publish setup failed: " + e + "\n");
				return;
			}
			for (MessageI m : snapshotMessages) {
				if (m == null) continue;
				broker.beginInFlight(channel);
				try {
					broker.validatePublisherAuthz(publisherReceptionPortURI, channel);
				} catch (Exception e) {
					broker.finishInFlight(channel);
					self.logMessage(
							"[Broker] bulk publish skipped message after revocation: "
							+ e + "\n");
					continue;
				}
				PublishGossipMessage gossip = new PublishGossipMessage(
						java.util.UUID.randomUUID().toString(),
						Instant.now(),
						broker.getReflectionInboundPortURI(),
						m,
						channel,
						publisherReceptionPortURI);
				broker.gossipToNeighbours(new GossipMessageI[]{ gossip });
				final MessageI msg = m;
				broker.runTask(broker.esPropagationIndex, p -> {
					try {
						((Broker) p).deliverToTargets(channel, msg, targets);
					} catch (Exception e) {
						self.logMessage("[Broker] bulk delivery exception: " + e + "\n");
						((Broker) p).finishInFlight(channel);
					}
				});
			}
		});
	}

	// -------------------------------------------------------------------------
	// Privileged channels management (PrivilegedClientCI)
	// -------------------------------------------------------------------------

	/**
	 * @throws IllegalArgumentException if a parameter is null/empty.
	 * @throws UnknownClientException if the client is not registered.
	 * @throws UnknownChannelException if the channel does not exist.
	 */
	public boolean hasCreatedChannel(String receptionPortURI, String channel) throws Exception
	{
		requireClient(receptionPortURI);
		requireChannel(channel);
		this.registrationLock.readLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			this.channelsLock.readLock().lock();
			try {
				if (!this.channelExistLocked(channel)) {
					throw new UnknownChannelException(channel);
				}
				PrivilegedChannelInfo info = this.privilegedChannels.get(channel);
				return info != null && info.ownerReceptionPortURI.equals(receptionPortURI);
			} finally {
				this.channelsLock.readLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}

	}

	/**
	 * @throws IllegalArgumentException if {@code receptionPortURI} is null/empty.
	 * @throws UnknownClientException if the client is not registered.
	 */
	/**
	 * Indique si l'opération {@link #createChannel} doit être autorisée pour
	 * {@code receptionPortURI} compte tenu de sa classe et du nombre de
	 * canaux déjà créés. FREE → toujours {@code true} (interdit de créer).
	 *
	 * @throws UnknownClientException si le client n'est pas enregistré.
	 */
	public boolean channelQuotaReached(String receptionPortURI) throws Exception
	{
		requireClient(receptionPortURI);
		this.registrationLock.readLock().lock();
		try {
			return this.channelQuotaReachedLocked(receptionPortURI);
		} finally {
			this.registrationLock.readLock().unlock();
		}
	}

	// Pré-requis : registrationLock.read (ou .write) déjà détenu.
	private boolean channelQuotaReachedLocked(String receptionPortURI) throws Exception
	{
		assert this.registrationLock.getReadHoldCount() > 0
				|| this.registrationLock.isWriteLockedByCurrentThread()
				: "registrationLock must be held by current thread";
		if (!this.registeredLocked(receptionPortURI)) {
			throw new UnknownClientException(receptionPortURI);
		}

		RegistrationClass rc = this.registeredClients.get(receptionPortURI);
		int created = this.createdPrivilegedChannelsCount.getOrDefault(receptionPortURI, 0);

		switch (rc) {
			case FREE:
				return true; // FREE cannot create privileged channels
			case STANDARD:
				return created >= this.standardQuota;
			case PREMIUM:
				return created >= this.premiumQuota;
			default:
				throw new IllegalStateException("Unknown RegistrationClass: " + rc);
		}
	}

	/**
	 * Create a privileged channel owned by {@code receptionPortURI}, with
	 * an optional {@code autorisedUsers} regex (matched against subscribing
	 * clients' reception port URIs).
	 *
	 * @throws IllegalArgumentException if a string parameter is null/empty.
	 * @throws UnknownClientException if the client is not registered.
	 * @throws UnauthorisedClientException if the client is FREE
	 * (only STANDARD/PREMIUM may
	 * create privileged channels).
	 * @throws ChannelQuotaExceededException if the client already owns
	 * their quota of channels.
	 * @throws AlreadyExistingChannelException if {@code channel} already exists.
	 */
	public void createChannel(String receptionPortURI, String channel, String autorisedUsers) throws Exception
	{
		requireClient(receptionPortURI);
		requireChannel(channel);
		// Compilation regex hors verrou : section critique courte.
		Pattern p = null;
		if (autorisedUsers != null && !autorisedUsers.isEmpty()) {
			p = Pattern.compile(autorisedUsers);
		}

		this.registrationLock.writeLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			RegistrationClass rc = this.registeredClients.get(receptionPortURI);
			if (rc == RegistrationClass.FREE) {
				throw new UnauthorisedClientException();
			}
			if (this.channelQuotaReachedLocked(receptionPortURI)) {
				throw new ChannelQuotaExceededException(
						"Channel quota reached for " + receptionPortURI + " (" + rc + ")");
			}

			this.channelsLock.writeLock().lock();
			try {
				if (this.channelExistLocked(channel)) {
					throw new AlreadyExistingChannelException("Channel already exists: " + channel);
				}
				this.subscriptionsLock.writeLock().lock();
				try {
					this.channels.add(channel);
					this.privilegedChannels.put(channel, new PrivilegedChannelInfo(receptionPortURI, p));
					this.subscriptions.put(channel, new HashMap<>());
					this.inFlightPerChannel.put(channel, 0);
					this.createdPrivilegedChannelsCount.merge(receptionPortURI, 1, Integer::sum);
					// Post-mutation invariants.
					assert this.channels.contains(channel)
							: "channel must be present after createChannel: " + channel;
					assert this.privilegedChannels.containsKey(channel)
							: "channel must be in privilegedChannels after createChannel: " + channel;
					assert this.subscriptions.containsKey(channel)
							: "channel must have a subscriptions map after createChannel: " + channel;
					// Quota invariant: the new count must not exceed the
					// applicable quota for the owner's class.
					int maxQuota = (rc == RegistrationClass.PREMIUM)
							? this.premiumQuota : this.standardQuota;
					int newCount = this.createdPrivilegedChannelsCount
							.getOrDefault(receptionPortURI, 0);
					assert newCount > 0 && newCount <= maxQuota
							: "createdPrivilegedChannelsCount for " + receptionPortURI
							+ " must be in (0, " + maxQuota + "] after createChannel, got " + newCount;

				} finally {
					this.subscriptionsLock.writeLock().unlock();
				}
			} finally {
				this.channelsLock.writeLock().unlock();
			}
		} finally {
			this.registrationLock.writeLock().unlock();
		}

		// propager la creation du cannal
		RegistrationClass rc;
		this.registrationLock.readLock().lock();
		try {
			rc = this.registeredClients.get(receptionPortURI);
		} finally {
			this.registrationLock.readLock().unlock();
		}

		CreateChannelGossipMessage gossip = new CreateChannelGossipMessage(
				java.util.UUID.randomUUID().toString(),
				Instant.now(),
				this.getReflectionInboundPortURI(),
				channel,
				receptionPortURI,
				autorisedUsers,
				rc);
		this.gossipToNeighbours(new GossipMessageI[]{ gossip });
	}

	/**
	 * Replace the {@code authorisedUsers} regex of the privileged channel
	 * {@code channel} owned by {@code receptionPortURI}. The new regex is
	 * compiled before any lock is taken so an invalid pattern does not
	 * disturb broker state.
	 *
	 * @throws IllegalArgumentException if a parameter is null/empty.
	 * @throws UnknownClientException if the client is not registered.
	 * @throws UnknownChannelException if the channel does not exist.
	 * @throws UnauthorisedClientException if the client does not own
	 * this privileged channel.
	 */
	public void modifyAuthorisedUsers(String receptionPortURI, String channel, String autorisedUsers) throws Exception
	{
		requireClient(receptionPortURI);
		requireChannel(channel);
		if (autorisedUsers == null || autorisedUsers.isEmpty()) {
			throw new IllegalArgumentException("autorisedUsers cannot be null/empty for modifyAuthorisedUsers.");
		}

		Pattern newPattern = Pattern.compile(autorisedUsers);

		this.registrationLock.readLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			this.channelsLock.writeLock().lock();
			try {
				if (!this.channelExistLocked(channel)) {
					throw new UnknownChannelException(channel);
				}
				PrivilegedChannelInfo info = this.privilegedChannels.get(channel);
				if (info == null) {
					throw new UnauthorisedClientException();
				}
				if (!info.ownerReceptionPortURI.equals(receptionPortURI)) {
					throw new UnauthorisedClientException();
				}
				info.authorisedUsersPattern = newPattern;
				// Post-mutation invariant: pattern reference replaced.
				assert info.authorisedUsersPattern == newPattern
						: "authorisedUsersPattern must be the freshly compiled pattern";
			} finally {
				this.channelsLock.writeLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}

		RegistrationClass rc;
		this.registrationLock.readLock().lock();
		try {
			rc = this.registeredClients.get(receptionPortURI);
		} finally {
			this.registrationLock.readLock().unlock();
		}
		ModifyAuthorisedUsersGossipMessage gossip = new ModifyAuthorisedUsersGossipMessage
				(java.util.UUID.randomUUID().toString(),
				Instant.now(),
				this.getReflectionInboundPortURI(),
				channel,
				receptionPortURI,
				autorisedUsers,
				rc);
		this.gossipToNeighbours(new GossipMessageI[]{ gossip });

	}

	/**
	 * Asynchronous channel destruction (CDC §3.4): hides the channel
	 * immediately so no new publish accepts it, then schedules the
	 * subscriptions / quotas cleanup for after every in-flight delivery
	 * has drained.
	 *
	 * @throws IllegalArgumentException if a parameter is null/empty.
	 * @throws UnknownClientException if the client is not registered.
	 * @throws UnknownChannelException if the channel does not exist.
	 * @throws UnauthorisedClientException if the client does not own this channel.
	 */
	public void destroyChannel(String receptionPortURI, String channel) throws Exception
	{
		requireClient(receptionPortURI);
		requireChannel(channel);
		// Vérification d'autorisation sous verrous lecture, puis attente
		// active sur le compteur in-flight (hors verrous lourds).
		this.registrationLock.readLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			this.channelsLock.readLock().lock();
			try {
				if (!this.channelExistLocked(channel)) {
					throw new UnknownChannelException(channel);
				}
				PrivilegedChannelInfo info = this.privilegedChannels.get(channel);
				if (info == null || !info.ownerReceptionPortURI.equals(receptionPortURI)) {
					throw new UnauthorisedClientException();
				}
			} finally {
				this.channelsLock.readLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}

		// rednre le canal invisible aux nouveaux publish
		this.channelsLock.writeLock().lock();
		try {
			this.channels.remove(channel);
		} finally {
			this.channelsLock.writeLock().unlock();
		}
		// Post-mutation invariant: channel no longer accepts new publish/subscribe.
		assert !this.channels.contains(channel)
				: "channel must be hidden immediately after destroyChannel: " + channel;

		DestroyChannelGossipMessage gossip = new DestroyChannelGossipMessage(
				java.util.UUID.randomUUID().toString(),
				Instant.now(),
				this.getReflectionInboundPortURI(),
				channel,
				receptionPortURI);
		this.gossipToNeighbours(new GossipMessageI[]{ gossip });

		// le nettoyage se fait en arrière plan après que le client reçoit confimation direct de la destruction
		this.runTask(this.esDeliveryIndex, o -> {
			try {
				// C.8-find-3: wait/notify instead of Thread.sleep busy-poll.
				// finishInFlight notifies on the per-channel waiter when
				// the in-flight count transitions to 0; the bounded
				// wait(50) is a safety net against missed notifications.
				final Object waiter = ((Broker) o).inFlightWaiters
						.computeIfAbsent(channel, k -> new Object());
				while (((Broker) o).inFlightPerChannel
						.getOrDefault(channel, 0) > 0) {
					synchronized (waiter) {
						if (((Broker) o).inFlightPerChannel
								.getOrDefault(channel, 0) > 0) {
							waiter.wait(50);
						}
					}
				}
				((Broker) o).destroyChannelCleanup(receptionPortURI, channel);
			} catch (Exception e) {
				((Broker) o).logMessage(
						"[Broker] destroyChannel cleanup failed: " + e + "\n");
			} finally {
				((Broker) o).inFlightWaiters.remove(channel);
			}
		});
	}

	// Appelée par destroyChannel après l'attente in-flight
	// Détruit ce qui reste après que channels.remove() a déjà été fait (abonnements des utilisateurs)
	private void destroyChannelCleanup(String receptionPortURI, String channel)
			throws Exception
	{
		this.registrationLock.writeLock().lock();
		try {
			this.channelsLock.writeLock().lock();
			try {
				this.subscriptionsLock.writeLock().lock();
				try {
					this.subscriptions.remove(channel);
				} finally {
					this.subscriptionsLock.writeLock().unlock();
				}
				this.privilegedChannels.remove(channel);
				this.inFlightPerChannel.remove(channel);
				// C.8-find-1: clamp to zero (same as destroyChannelNow) so
				// stray gossip / re-entry cannot drive the quota negative
				// after the owner already downgraded to FREE (entry absent
				// = merge would otherwise re-create a -1 entry).
				this.createdPrivilegedChannelsCount.merge(
						receptionPortURI, -1, (a, b) -> {
							int next = a + b;
							return next < 0 ? 0 : next;
						});
				// Post-mutation invariants: cleanup wiped everything and
				// the quota counter cannot go negative.
				assert !this.subscriptions.containsKey(channel)
						: "subscriptions must be wiped after destroyChannelCleanup: " + channel;
				assert !this.privilegedChannels.containsKey(channel)
						: "privilegedChannels must be wiped after destroyChannelCleanup: " + channel;
				assert !this.inFlightPerChannel.containsKey(channel)
						: "inFlightPerChannel must be wiped after destroyChannelCleanup: " + channel;
				int currentCount = this.createdPrivilegedChannelsCount
						.getOrDefault(receptionPortURI, 0);
				assert currentCount >= 0
						: "createdPrivilegedChannelsCount must stay non-negative for "
						+ receptionPortURI + ", got " + currentCount;
			} finally {
				this.channelsLock.writeLock().unlock();
			}
		} finally {
			this.registrationLock.writeLock().unlock();
		}
	}

	/**
	 * Synchronous channel destruction (CDC §3.4): all state for the channel
	 * is wiped under the broker's locks, regardless of in-flight messages.
	 *
	 * @param propagate if true, broadcast a destroy gossip to neighbours.
	 * @throws IllegalArgumentException if a parameter is null/empty.
	 * @throws UnknownClientException if the client is not registered.
	 * @throws UnknownChannelException if the channel does not exist.
	 * @throws UnauthorisedClientException if the client does not own this channel.
	 */
	public void destroyChannelNow(String receptionPortURI, String channel, boolean propagate) throws Exception {
		requireClient(receptionPortURI);
		requireChannel(channel);
		this.registrationLock.writeLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			this.channelsLock.writeLock().lock();
			try {
				if (!this.channelExistLocked(channel)) {
					throw new UnknownChannelException(channel);
				}
				PrivilegedChannelInfo info = this.privilegedChannels.get(channel);
				if (info == null) {
					throw new UnauthorisedClientException();
				}
				if (!info.ownerReceptionPortURI.equals(receptionPortURI)) {
					throw new UnauthorisedClientException();
				}

				this.subscriptionsLock.writeLock().lock();
				try {
					this.subscriptions.remove(channel);
				} finally {
					this.subscriptionsLock.writeLock().unlock();
				}
				this.channels.remove(channel);
				this.privilegedChannels.remove(channel);
				this.inFlightPerChannel.remove(channel);
				this.createdPrivilegedChannelsCount.merge(receptionPortURI, -1, (a, b) -> {
					int next = a + b;
					return next < 0 ? 0 : next;
				});
				// Post-mutation invariants: channel state fully wiped and
				// the owner's quota counter remains non-negative.
				assert !this.channels.contains(channel)
						: "channel must be removed after destroyChannelNow: " + channel;
				assert !this.privilegedChannels.containsKey(channel)
						: "channel must be removed from privilegedChannels: " + channel;
				assert !this.inFlightPerChannel.containsKey(channel)
						: "channel must be removed from inFlightPerChannel: " + channel;
				int currentCount = this.createdPrivilegedChannelsCount
						.getOrDefault(receptionPortURI, 0);
				assert currentCount >= 0
						: "createdPrivilegedChannelsCount must stay non-negative for "
						+ receptionPortURI + ", got " + currentCount;
			} finally {
				this.channelsLock.writeLock().unlock();
			}
		} finally {
			this.registrationLock.writeLock().unlock();
		}
		if (propagate) {
			DestroyChannelGossipMessage gossip = new DestroyChannelGossipMessage(
					java.util.UUID.randomUUID().toString(),
					Instant.now(),
					this.getReflectionInboundPortURI(),
					channel,
					receptionPortURI);
			this.gossipToNeighbours(new GossipMessageI[]{gossip});
		}
	}
	/**
	 * Surcharge appelée par défaut depuis l'API publique :
	 * {@code destroyChannelNow(uri, channel)} équivaut à
	 * {@code destroyChannelNow(uri, channel, true)} (propagation gossip
	 * activée). La surcharge à 3 paramètres est exposée pour permettre aux
	 * gestionnaires gossip locaux d'éviter une re-propagation infinie.
	 */
	public void destroyChannelNow(String receptionPortURI, String channel)
			throws Exception
	{
		this.destroyChannelNow(receptionPortURI, channel, true);
	}



	// -------------------------------------------------------------------------
	// Gossip methodes
	// -------------------------------------------------------------------------
	/**
	 * Réception d'un lot de messages gossip d'un voisin
	 * (cf. {@code docs/GOSSIP.md}).
	 *
	 * <p>Pour chaque message :</p>
	 * <ol>
	 * <li>Déduplication atomique via {@link #markAsProcessed(GossipMessageI)}
	 * (CHM {@code putIfAbsent}, sans verrou — propagation lock-free).</li>
	 * <li>Application locale via le pattern <em>Visitor</em> :
	 * {@code ((AbstractGossipMessage) msg).accept(handler)} dispatche vers
	 * {@link BrokerGossipHandler#visit} typé (remplace les chaînes
	 * {@code instanceof} pour bénéficier du dispatch dynamique).</li>
	 * <li>Réémission filtrée vers tous les voisins <strong>sauf l'émetteur
	 * immédiat</strong> (skip-echo) — chaque envoi est
	 * dispatché sur l'ES gossip pour ne pas bloquer l'appelant RMI.</li>
	 * </ol>
	 *
	 * <p><strong>Pré : </strong>tous les messages gossip applicatifs étendent
	 * {@link AbstractGossipMessage} qui implémente
	 * {@link EmitterAwareGossipMessageI}. Un message tiers sans visitor est
	 * journalisé puis ignoré localement, mais reste relayé pour ne pas casser
	 * la fédération en cas d'extension future.</p>
	 */
	@Override
	public void update(GossipMessageI[] fromSender) {
		assert fromSender != null : "gossip array must not be null";
		final BrokerGossipHandler handler = new BrokerGossipHandler(this);
		for (GossipMessageI msg : fromSender) {
			// 1. Déduplication atomique (sans verrou).
			if (!markAsProcessed(msg)) {
				continue;
			}
			assert this.processedGossipURIs.containsKey(msg.gossipMessageURI())
					: "gossip URI must be memoised after markAsProcessed: "
					+ msg.gossipMessageURI();

			// 2. Skip-echo : mémoriser l'émetteur immédiat AVANT toute réécriture.
			// Tous nos messages applicatifs étendent AbstractGossipMessage
			// (qui implémente EmitterAwareGossipMessageI) ; le test reste
			// défensif au cas où un test fournirait un message tiers.
			final String immediateSenderReflectionURI =
					(msg instanceof EmitterAwareGossipMessageI)
							? ((EmitterAwareGossipMessageI) msg).getEmitterURI()
							: null;

			// 3. Application locale via Visitor (double-dispatch typé).
			if (msg instanceof AbstractGossipMessage) {
				try {
					((AbstractGossipMessage) msg).accept(handler);
				} catch (Exception e) {
					this.logMessage("[Broker] gossip handler failed for "
							+ msg.getClass().getSimpleName() + ": " + e + "\n");
				}
			} else {
				this.logMessage("[Broker] unhandled gossip type (no visitor): "
						+ msg.getClass().getName() + "\n");
			}

			// 4. Réémission aux voisins, en sautant l'émetteur immédiat.
			// On réécrit l'émetteur courant à la nôtre.
			final GossipMessageI forwarded =
					msg.copyWithNewEmitterURI(this.getReflectionInboundPortURI());
			for (Map.Entry<String, GossipSenderOutboundPort> e
					: this.sendersByNeighbour.entrySet()) {
				final String neighbourReflectionURI = e.getKey();
				final GossipSenderOutboundPort sender = e.getValue();
				// Skip-echo : ne pas renvoyer au broker qui vient de nous transmettre.
				if (immediateSenderReflectionURI != null
						&& immediateSenderReflectionURI.equals(neighbourReflectionURI)) {
					continue;
				}
				try {
					this.runTask(this.esGossipIndex, owner -> {
						try {
							sender.send(new GossipMessageI[] { forwarded });
						} catch (Exception ex) {
							((Broker) owner).logMessage(
									"[Broker] gossip forward failed: " + ex + "\n");
						}
					});
				} catch (Exception ex) {
					this.logMessage("[Broker] gossip forward submit failed: "
							+ ex + "\n");
				}
			}
		}
	}

	/**
	 * Implémentation de {@link GossipReceiverCI#receive} : délègue à
	 * {@link #update} (point d'entrée RMI vers la logique gossip).
	 */
	@Override
	public void receive(GossipMessageI[] gossipMessages) throws Exception {
		update(gossipMessages);
	}

	/**
	 * Diffuser un lot de messages gossip à <strong>tous</strong> les voisins
	 * (utilisé pour les messages générés localement, donc sans émetteur
	 * immédiat à exclure). Pour les retransmissions à partir de {@code update},
	 * voir le filtrage explicite dans {@link #update(GossipMessageI[])}
	 * (skip-echo).
	 */
	public void gossipToNeighbours(GossipMessageI[] messages) {
		for (GossipSenderOutboundPort sender : this.sendersByNeighbour.values()) {
			this.runTask(this.esGossipIndex, o -> {
				try {
					sender.send(messages);
				} catch (Exception e) {
					((Broker) o).logMessage(
							"[Broker] gossip send failed: " + e + "\n");
				}
			});
		}
	}
	/**
	 * Déduplication atomique d'un message gossip via {@link #processedGossipURIs}
	 * . Aucun verrou nécessaire :
	 * {@link java.util.concurrent.ConcurrentHashMap#putIfAbsent putIfAbsent}
	 * fournit la garantie atomique « première-occurrence ».
	 *
	 * @param msg le message gossip à déduper.
	 * @return {@code true} si c'est la première fois qu'on voit cette URI
	 * (donc à traiter), {@code false} s'il a déjà été traité.
	 */
	boolean markAsProcessed(GossipMessageI msg) {
		return this.processedGossipURIs.putIfAbsent(
				msg.gossipMessageURI(), msg.timestamp()) == null;
	}

	/**
	 * Nettoyage périodique de la mémoire gossip : supprime les URIs
	 * mémorisées il y a plus de 120 secondes. Sans verrou (cf. F.5).
	 */
	private void cleanupGossipMemory() {
		Instant threshold = Instant.now().minusSeconds(120);
		// ConcurrentHashMap : nettoyage sans verrou (cf. F.5).
		this.processedGossipURIs.entrySet()
				.removeIf(e -> e.getValue().isBefore(threshold));
	}

}
