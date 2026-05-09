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
import fr.sorbonne_u.cps.pubsub.base.ports.PrivilegedClientInboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.PublishingInboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.ReceivingOutboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.RegistrationInboundPort;
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
 * Broker component implementing a publication/subscription system.
 *
 * <p>
 * Functionalities:
 * </p>
 * <ul>
 *   <li><strong>Registration</strong>: clients register with a service class
 *       (FREE, STANDARD, PREMIUM).</li>
 *   <li><strong>FREE channels</strong>: pre-created channels {@code channel0..channelN} available to all registered clients.</li>
 *   <li><strong>Subscriptions</strong>: per-channel subscriptions associated with {@link fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI}
 *       to enable filtered delivery.</li>
 *   <li><strong>Publishing</strong>: publication on a channel delivers messages to subscribed clients whose filters match.</li>
 *   <li><strong>Privileged channels (step 2)</strong>: STANDARD/PREMIUM clients can create/destroy channels and define
 *       an {@code authorisedUsers} regular expression. Access control is enforced on both subscribe and publish.</li>
 *   <li><strong>Quotas</strong>: STANDARD and PREMIUM privileged channel creation is limited by quotas.</li>
 * </ul>
 *
 * @author Bogdan Styn, Setbel Melissa
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
	// Note (Phase F.5) : processedGossipURIs est désormais un ConcurrentHashMap,
	// la déduplication se fait sans verrou via putIfAbsent. Aucun gossipLock
	// résiduel — on évite l'accumulation de verrous inutiles.

	// -------------------------------------------------------------------------
	// PublishingCI async (added in latest interface)
	// -------------------------------------------------------------------------

	/**
	 * Async publish with optional notification (CDC §3.4 + Audit 2).
	 *
	 * @throws IllegalArgumentException if any string parameter is null/empty,
	 *         or {@code message} is null. ({@code notificationInbounhdPortURI}
	 *         may be null = no notification.)
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
	 * <p>Phase C.5: shares the single-snapshot bulk path with
	 * {@link #publish(String, String, ArrayList)}.</p>
	 *
	 * @throws IllegalArgumentException if any string parameter is null/empty,
	 *         or {@code messages} is null/empty.
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

	private RegistrationInboundPort registrationPortIN;
	private PublishingInboundPort publishingPortIN;
	private PrivilegedClientInboundPort privilegedPortIN;
	private GossipReceiverInboundPort gossipPortIN;

	// Suffixes used to derive each broker inbound port URI from the
	// broker's reflection inbound port URI (Phase C.3). The static field
	// REGISTRATION_PORT_URI was removed because it is per-JVM and was
	// silently clobbered by a second broker instantiated in the same JVM
	// (multi-broker scenarios in tests, ScenarioRunner wiring, etc.).
	public static final String REGISTRATION_PORT_URI_SUFFIX = "-reg-in";
	public static final String PUBLISHING_PORT_URI_SUFFIX   = "-pub-in";
	public static final String PRIVILEGED_PORT_URI_SUFFIX   = "-priv-in";

	// -------------------------------------------------------------------------
	// State
	// -------------------------------------------------------------------------

	/** Registered clients: receptionPortURI -> registration class. */
	private final Map<String, RegistrationClass> registeredClients = new HashMap<>();
	/** Per-client outbound port to deliver messages. */
	private final Map<String, ReceivingOutboundPort> receptionPortsOUT = new HashMap<>();
	/** All channels (FREE + privileged). */
	private final Set<String> channels = new HashSet<>();

	/** Privileged channels metadata. */
	private static class PrivilegedChannelInfo
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
	private final Map<String, PrivilegedChannelInfo> privilegedChannels = new HashMap<>();

	/** Per-client created privileged channels count. ConcurrentHashMap so
	 *  callers can use atomic compute/merge without holding extra locks. */
	private final java.util.concurrent.ConcurrentHashMap<String, Integer> createdPrivilegedChannelsCount =
			new java.util.concurrent.ConcurrentHashMap<>();

	/** Channel quotas */
	private int standardQuota;
	private int premiumQuota;
	private int nbFreeChannels;

	/** Subscriptions: channel -> (client receptionPortURI -> filter). */
	private final Map<String, Map<String, MessageFilterI>> subscriptions = new HashMap<>();

	/** Number of messages currently in-flight per channel. ConcurrentHashMap
	 *  so beginInFlight/finishInFlight use atomic compute() rather than
	 *  synchronized blocks. */
	private final java.util.concurrent.ConcurrentHashMap<String, Integer> inFlightPerChannel =
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
	 * <strong>skip it</strong> when re-emitting (soutenance §6.2). Sending a
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
	 * lock + check + put (soutenance §6.6 / Phase F.5). The previous
	 * lock-based dedup had a window where two threads could both observe
	 * "not processed" and both proceed to forward the same message.</p>
	 */
	private final java.util.concurrent.ConcurrentMap<String, Instant> processedGossipURIs =
			new java.util.concurrent.ConcurrentHashMap<>();


	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------
	//
	// Phase C.3 refactor: all four overloads now delegate to a single
	// {@link #init(...)} helper. Two minimal pass-through constructors
	// remain because {@code super(...)} must be the first call:
	//   - one with an explicit reflexion port URI (multi-JVM/distributed),
	//   - one without (centralised CVM, generated reflexion port).
	// In both cases the broker's port URIs are derived deterministically
	// from {@link #getReflectionInboundPortURI()}, removing the
	// last-broker-wins hazard of the previous static field.

	protected Broker(int nbThreads, int nbSchedulableThreads,
					 int nbFreeChannels, int standardQuota, int premiumQuota,
					 int nbReceptionThreads, int nbPropagationThreads,
					 int nbDeliveryThreads) throws Exception {
		this(nbThreads, nbSchedulableThreads, nbFreeChannels,
				standardQuota, premiumQuota, nbReceptionThreads,
				nbPropagationThreads, nbDeliveryThreads,
				new ArrayList<>()); // pas de voisins
	}

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

	// broker avec port de reflexion pour la répartition
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
	 * Common initialisation shared by every constructor (Phase C.3):
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
		this.esReceptionIndex   = this.createNewExecutorService(ES_RECEPTION_URI,   nbReceptionThreads,   false);
		this.esPropagationIndex = this.createNewExecutorService(ES_PROPAGATION_URI, nbPropagationThreads, false);
		this.esDeliveryIndex    = this.createNewExecutorService(ES_DELIVERY_URI,    nbDeliveryThreads,    false);
		this.esGossipIndex      = this.createNewExecutorService(ES_GOSSIP_URI,      4,                    false);

		this.nbFreeChannels = nbFreeChannels;
		for (int i = 0; i < nbFreeChannels; i++) {
			String c = "channel" + i;
			this.channels.add(c);
			this.subscriptions.put(c, new HashMap<>());
			this.inFlightPerChannel.put(c, 0);
		}
		this.standardQuota = standardQuota;
		this.premiumQuota  = premiumQuota;

		final String rip = this.getReflectionInboundPortURI();

		this.registrationPortIN =
				new RegistrationInboundPort(registrationPortURIFor(rip), this);
		this.registrationPortIN.publishPort();

		this.publishingPortIN =
				new PublishingInboundPort(publishingPortURIFor(rip), this);
		this.publishingPortIN.publishPort();

		this.privilegedPortIN =
				new PrivilegedClientInboundPort(privilegedPortURIFor(rip), this);
		this.privilegedPortIN.publishPort();

		this.gossipPortIN =
				new GossipReceiverInboundPort(gossipPortURIFor(rip), this);
		this.gossipPortIN.publishPort();

		this.gossipURIs = neighborsGossipURIs;
	}



	// -------------------------------------------------------------------------
	// Internal asynchronous pipeline (audit 2)
	// -------------------------------------------------------------------------

	public int getStandardQuota(){
		return standardQuota;
	}
	public int getPremiumQuota(){
		return premiumQuota;
	}
	public int getNbFreeChannels(){
		return nbFreeChannels;
	}

	// -------------------------------------------------------------------------
	// Executor service indices (Phase D.3)
	//
	// Exposed so inbound ports can dispatch incoming requests off the RMI
	// dispatch thread onto the broker's own dedicated executor pools. This
	// prevents RMI thread starvation under deep gossip propagation chains
	// (e.g. 4 federated JVMs).
	// -------------------------------------------------------------------------

	/** Index of the dedicated reception executor service ({@value #ES_RECEPTION_URI}). */
	public int getReceptionExecutorIndex()   { return this.esReceptionIndex; }

	/** Index of the dedicated propagation executor service ({@value #ES_PROPAGATION_URI}). */
	public int getPropagationExecutorIndex() { return this.esPropagationIndex; }

	/** Index of the dedicated delivery executor service ({@value #ES_DELIVERY_URI}). */
	public int getDeliveryExecutorIndex()    { return this.esDeliveryIndex; }

	/** Index of the dedicated gossip executor service ({@value #ES_GOSSIP_URI}). */
	public int getGossipExecutorIndex()      { return this.esGossipIndex; }

	protected static class DeliveryTarget
	{
		final String subscriberURI;
		final ReceivingOutboundPort out;
		final MessageFilterI filter;

		DeliveryTarget(String subscriberURI, ReceivingOutboundPort out, MessageFilterI filter)
		{
			this.subscriberURI = subscriberURI;
			this.out = out;
			this.filter = filter;
		}
	}

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
				// TODO: abnormal termination notification
				self.logMessage("[Broker] receptionStage exception: " + e + "\n");
			}
		});
	}

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
	protected void beginInFlight(String channel)
	{
		// CHM.compute is atomic for a given key: no extra lock needed.
		this.inFlightPerChannel.compute(channel, (k, v) -> v == null ? 1 : v + 1);
	}

	protected void finishInFlight(String channel)
	{
		this.inFlightPerChannel.compute(channel, (k, v) -> {
			if (v == null) return 0;
			int next = v - 1;
			return next < 0 ? 0 : next;
		});
	}

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
	 * <p>Phase C.5: factored out so bulk publish can reuse a single
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
					ReceivingOutboundPort out = this.receptionPortsOUT.get(subscriberURI);
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
	 * <p>Phase C.5: extracted from {@link #receptionStage} so bulk publish
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

	@Override
	public void start() throws ComponentStartException {
		super.start();
		// Connecter les ports sortants gossip vers chaque voisin.
		// On indexe les sender par URI de port d'écoute du voisin
		// (e.g. "broker-1-gossip-in") afin de pouvoir, dans update(),
		// retrouver à l'envers le voisin qui vient de nous transmettre
		// un message et l'exclure de la re-diffusion (soutenance §6.2).
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

	@Override
	public void finalise() throws Exception {

		for (ReceivingOutboundPort out : this.receptionPortsOUT.values()) {
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

	@Override
	public synchronized void shutdown() throws ComponentShutdownException {
		try {
			for (ReceivingOutboundPort out : this.receptionPortsOUT.values()) {
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
	 * (Phase C.3). Replaces the per-JVM static {@code REGISTRATION_PORT_URI}.
	 */
	public static String registrationPortURIFor(String brokerReflectionURI)
	{
		Objects.requireNonNull(brokerReflectionURI, "brokerReflectionURI");
		return brokerReflectionURI + REGISTRATION_PORT_URI_SUFFIX;
	}

	/** Deterministic publishing inbound port URI for the given broker. */
	public static String publishingPortURIFor(String brokerReflectionURI)
	{
		Objects.requireNonNull(brokerReflectionURI, "brokerReflectionURI");
		return brokerReflectionURI + PUBLISHING_PORT_URI_SUFFIX;
	}

	/** Deterministic privileged inbound port URI for the given broker. */
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
	private boolean channelExistLocked(String channel)
	{
		// Internal contract: caller must hold channelsLock (read or write).
		assert this.channelsLock.getReadHoldCount() > 0
				|| this.channelsLock.isWriteLockedByCurrentThread()
				: "channelsLock (read or write) must be held by current thread";
		return this.channels.contains(channel);
	}

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
	 * raise {@link UnknownClientException} (soutenance §2.4) — silently
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
		ReceivingOutboundPort out = new ReceivingOutboundPort(this);
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
				this.getReflectionInboundPortURI(),     // émetteur = ce courtier
				receptionPortURI,                        // client qui s'enregistre
				rc);

		this.gossipToNeighbours(new GossipMessageI[]{ gossip }); //initiation de la propagation

		// Soutenance §2.5 : retourner l'URI du port adapté à la classe de service.
		// FREE : seulement publish ; STANDARD/PREMIUM : privileged
		// (qui hérite de PublishingCI, donc couvre publish également).
		if (rc == RegistrationClass.FREE) {
			return publishingPortIN.getPortURI();
		}else{
			return privilegedPortIN.getPortURI();
		}
	}

	public String modifyServiceClass(String receptionPortURI, RegistrationClass rc) throws Exception
	{
		if (!this.registered(receptionPortURI)) {
			throw new UnknownClientException(receptionPortURI);
		}
		if (rc == null) {
			throw new IllegalArgumentException("rc cannot be null.");
		}
		this.registrationLock.writeLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			this.registeredClients.put(receptionPortURI, rc);
		} finally {
			this.registrationLock.writeLock().unlock();
		}


		// Allowing service class upgrade/downgrade
		if (rc == RegistrationClass.FREE) {
			// Destroy channels created by user
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


			this.registrationLock.writeLock().lock();
			try {
				this.createdPrivilegedChannelsCount.remove(receptionPortURI);
			} finally {
				this.registrationLock.writeLock().unlock();
			}
			this.gossipToNeighbours(gossipMessages.toArray(new GossipMessageI[0]));
			return publishingPortIN.getPortURI();

		} else {
			this.registrationLock.writeLock().lock();
			try {
				this.createdPrivilegedChannelsCount.putIfAbsent(receptionPortURI, 0);
			} finally {
				this.registrationLock.writeLock().unlock();
			}
			return privilegedPortIN.getPortURI();
		}
	}

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
		ReceivingOutboundPort out;
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
	 * empty {@code channel} arguments before any lock is taken (Phase C.2).
	 *
	 * @throws IllegalArgumentException if {@code channel} is {@code null}
	 *                                  or empty.
	 */
	private static void requireChannel(String channel) {
		if (channel == null || channel.isEmpty()) {
			throw new IllegalArgumentException("channel cannot be null or empty.");
		}
	}

	/**
	 * Pre-check used by every method that identifies a client by its
	 * reception port URI (Phase C.2). Rejects {@code null}/empty before any
	 * lock is taken.
	 *
	 * @throws IllegalArgumentException if {@code receptionPortURI} is
	 *                                  {@code null} or empty.
	 */
	private static void requireClient(String receptionPortURI) {
		if (receptionPortURI == null || receptionPortURI.isEmpty()) {
			throw new IllegalArgumentException(
					"receptionPortURI cannot be null or empty.");
		}
	}

	/**
	 * Normalise a {@code null} filter to the canonical accept-all
	 * singleton (Phase C.2). The CDC §3.4 contract allows {@code null}
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
	 * @throws IllegalArgumentException     if a parameter is null/empty.
	 * @throws UnknownClientException       if {@code receptionPortURI} is unknown.
	 * @throws UnknownChannelException      if {@code channel} does not exist.
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
	 * @throws IllegalArgumentException     if a parameter is null/empty.
	 * @throws UnknownClientException       if {@code receptionPortURI} is unknown.
	 * @throws UnknownChannelException      if {@code channel} does not exist.
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
	 * so the delivery hot path never has to null-check (Phase C.2).</p>
	 *
	 * @throws IllegalArgumentException     if a string parameter is null/empty.
	 * @throws UnknownClientException       if the client is not registered.
	 * @throws UnknownChannelException      if the channel does not exist.
	 * @throws UnauthorisedClientException  if the client lacks permission on
	 *                                      a privileged channel.
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
	 * @throws IllegalArgumentException        if a parameter is null/empty.
	 * @throws UnknownClientException          if the client is not registered.
	 * @throws UnknownChannelException         if the channel does not exist.
	 * @throws NotSubscribedChannelException   if the client is not subscribed.
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
	 * (Phase C.2).
	 *
	 * @throws IllegalArgumentException        if a string parameter is null/empty.
	 * @throws UnknownClientException          if the client is not registered.
	 * @throws UnknownChannelException         if the channel does not exist.
	 * @throws NotSubscribedChannelException   if the client is not subscribed.
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
	 * stage and surfaced via the broker logger (Phase E.5 will route them
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
	 * <p>Phase C.5: builds the subscriber snapshot ONCE before iterating
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
	 * @throws IllegalArgumentException     if a parameter is null/empty.
	 * @throws UnknownClientException       if the client is not registered.
	 * @throws UnknownChannelException      if the channel does not exist.
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
	 * @throws UnknownClientException   if the client is not registered.
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
	 * @throws IllegalArgumentException        if a string parameter is null/empty.
	 * @throws UnknownClientException          if the client is not registered.
	 * @throws UnauthorisedClientException     if the client is FREE
	 *                                         (only STANDARD/PREMIUM may
	 *                                         create privileged channels).
	 * @throws ChannelQuotaExceededException   if the client already owns
	 *                                         their quota of channels.
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
	 * @throws IllegalArgumentException     if a parameter is null/empty.
	 * @throws UnknownClientException       if the client is not registered.
	 * @throws UnknownChannelException      if the channel does not exist.
	 * @throws UnauthorisedClientException  if the client does not own
	 *                                      this privileged channel.
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
	 * @throws IllegalArgumentException     if a parameter is null/empty.
	 * @throws UnknownClientException       if the client is not registered.
	 * @throws UnknownChannelException      if the channel does not exist.
	 * @throws UnauthorisedClientException  if the client does not own this channel.
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
				// Attente que les messages en vol se terminent
				while (true) {
					int inFlight = ((Broker) o).inFlightPerChannel
							.getOrDefault(channel, 0);
					if (inFlight == 0) break;
					Thread.sleep(10);
				}
				((Broker) o).destroyChannelCleanup(receptionPortURI, channel);
			} catch (Exception e) {
				((Broker) o).logMessage(
						"[Broker] destroyChannel cleanup failed: " + e + "\n");
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
	 * @throws IllegalArgumentException     if a parameter is null/empty.
	 * @throws UnknownClientException       if the client is not registered.
	 * @throws UnknownChannelException      if the channel does not exist.
	 * @throws UnauthorisedClientException  if the client does not own this channel.
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
	public void destroyChannelNow(String receptionPortURI, String channel)
			throws Exception
	{
		this.destroyChannelNow(receptionPortURI, channel, true);
	}



	// -------------------------------------------------------------------------
	// Gossip methodes
	// -------------------------------------------------------------------------

	@Override
	public void update(GossipMessageI[] fromSender) {
		assert fromSender != null : "gossip array must not be null";
		for (GossipMessageI msg : fromSender) {
			// Soutenance §6.6 / Phase F.5 : déduplication atomique.
			// putIfAbsent renvoie l'ancienne valeur si la clé existait déjà
			// (donc déjà traité). Aucun verrou nécessaire : ConcurrentHashMap.
			if (this.processedGossipURIs.putIfAbsent(
					msg.gossipMessageURI(), msg.timestamp()) != null) {
				continue;
			}
			// Post-dedup invariant: the URI is now memoised so that any
			// subsequent putIfAbsent for the same key is a no-op.
			assert this.processedGossipURIs.containsKey(msg.gossipMessageURI())
					: "gossip URI must be memoised after successful putIfAbsent: "
					+ msg.gossipMessageURI();

			// Mémoriser l'émetteur immédiat AVANT de remplacer son URI :
			// soutenance §6.2 — il ne faut pas renvoyer le message au broker
			// qui vient de nous le transmettre. Tous nos messages gossip
			// implémentent EmitterAwareGossipMessageI ; le test instanceof
			// reste défensif au cas où un test fournirait un message tiers.
			final String immediateSenderReflectionURI =
					(msg instanceof EmitterAwareGossipMessageI)
							? ((EmitterAwareGossipMessageI) msg).getEmitterURI()
							: null;

			// Appliquer le message localement
			if (msg instanceof RegisterGossipMessage) {
				RegisterGossipMessage regMsg = (RegisterGossipMessage) msg;
				// Mémoriser localement — sans créer de port outbound
				// car ce client n'est PAS enregistré chez nous, juste connu
				this.registrationLock.writeLock().lock();
				try {
					this.registeredClients.putIfAbsent(
							regMsg.getClientReceptionPortURI(),
							regMsg.getRegistrationClass());
					this.createdPrivilegedChannelsCount.putIfAbsent(regMsg.getClientReceptionPortURI(), 0);
				} finally {
					this.registrationLock.writeLock().unlock();
				}
			}
			if (msg instanceof PublishGossipMessage) {
				PublishGossipMessage pubMsg = (PublishGossipMessage) msg;
				this.beginInFlight(pubMsg.getChannel());
				this.runTask(this.esPropagationIndex, o -> {
					try {
						((Broker) o).propagationStage(
								pubMsg.getChannel(),
								pubMsg.getPubMessage());
					} catch (Exception e) {
						this.logMessage("[Broker] gossip propagation failed: " + e + "\n");
						((Broker) o).finishInFlight(pubMsg.getChannel());
					}
				});
			}
			if (msg instanceof CreateChannelGossipMessage) {
				CreateChannelGossipMessage ccMsg = (CreateChannelGossipMessage) msg;
				Pattern p = null;
				if (ccMsg.getAuthorisedUsers() != null && !ccMsg.getAuthorisedUsers().isEmpty()) {
					p = Pattern.compile(ccMsg.getAuthorisedUsers());
				}
				final Pattern pattern = p;

				this.registrationLock.writeLock().lock();
				try {
					// S'assurer que le propriétaire est connu localement
					this.registeredClients.putIfAbsent(
							ccMsg.getOwnerReceptionPortURI(),
							ccMsg.getOwnerClass());
					this.createdPrivilegedChannelsCount.merge(
							ccMsg.getOwnerReceptionPortURI(), 1, Integer::sum);

					this.channelsLock.writeLock().lock();
					try {
						// Ne créer que si pas déjà présent
						if (!this.channelExistLocked(ccMsg.getChannel())) {
							this.subscriptionsLock.writeLock().lock();
							try {
								// Tout mettre à jour
								this.channels.add(ccMsg.getChannel());
								this.privilegedChannels.put(ccMsg.getChannel(),
										new PrivilegedChannelInfo(
												ccMsg.getOwnerReceptionPortURI(), pattern));
								this.subscriptions.put(ccMsg.getChannel(), new HashMap<>());
								this.inFlightPerChannel.put(ccMsg.getChannel(), 0);
							} finally {
								this.subscriptionsLock.writeLock().unlock();
							}
						} else {
							this.createdPrivilegedChannelsCount.merge(
									ccMsg.getOwnerReceptionPortURI(), -1, Integer::sum);
						}
					} finally {
						this.channelsLock.writeLock().unlock();
					}
				} finally {
					this.registrationLock.writeLock().unlock();
				}
			}

			if (msg instanceof ModifyServiceClassGossipMessage) {
				ModifyServiceClassGossipMessage mscMsg =
						(ModifyServiceClassGossipMessage) msg;

				this.registrationLock.writeLock().lock();
				try {
					//Mettre à jour la classe de service localement
					this.registeredClients.put(
							mscMsg.getClientReceptionPortURI(),
							mscMsg.getNewRegistrationClass());

					// Si downgrade vers FREE — supprimer le quota
					if (mscMsg.getNewRegistrationClass() == RegistrationClass.FREE) {
						this.createdPrivilegedChannelsCount.remove(
								mscMsg.getClientReceptionPortURI());
					} else {
						// Si upgrade — initialiser le quota si pas encore présent
						this.createdPrivilegedChannelsCount.putIfAbsent(
								mscMsg.getClientReceptionPortURI(), 0);
					}
				} finally {
					this.registrationLock.writeLock().unlock();
				}
			}
			if (msg instanceof DestroyChannelGossipMessage) {
				DestroyChannelGossipMessage dcMsg = (DestroyChannelGossipMessage) msg;

				// Détruire la copie locale directement
				this.registrationLock.writeLock().lock();
				try {
					this.channelsLock.writeLock().lock();
					try {
						// Ne détruire que si le canal existe localement
						if (this.channelExistLocked(dcMsg.getChannel())) {
							this.subscriptionsLock.writeLock().lock();
							try {
								this.subscriptions.remove(dcMsg.getChannel());
							} finally {
								this.subscriptionsLock.writeLock().unlock();
							}
							this.channels.remove(dcMsg.getChannel());
							this.privilegedChannels.remove(dcMsg.getChannel());
							this.inFlightPerChannel.remove(dcMsg.getChannel());
							// Décrémenter le quota du propriétaire
							this.createdPrivilegedChannelsCount.merge(
									dcMsg.getOwnerReceptionPortURI(), -1, Integer::sum);
						}
					} finally {
						this.channelsLock.writeLock().unlock();
					}
				} finally {
					this.registrationLock.writeLock().unlock();
				}
			}
            if (msg instanceof ModifyAuthorisedUsersGossipMessage) {
                ModifyAuthorisedUsersGossipMessage mauMsg =
                        (ModifyAuthorisedUsersGossipMessage) msg;
                Pattern newPattern = Pattern.compile(mauMsg.getAuthorisedUsers());
                this.channelsLock.writeLock().lock();
                try {
                    PrivilegedChannelInfo info =
                            this.privilegedChannels.get(mauMsg.getChannel());
                    if (info != null) {
                        info.authorisedUsersPattern = newPattern;
                    }
                } finally {
                    this.channelsLock.writeLock().unlock();
                }
            }

			if (msg instanceof UnregisterGossipMessage){
				UnregisterGossipMessage unregMsg = (UnregisterGossipMessage) msg;


				// retirer subscriptions
				this.subscriptionsLock.writeLock().lock();
				try {
					for (Map<String, MessageFilterI> subs : this.subscriptions.values()) {
						subs.remove(unregMsg.getClientReceptionPortURI());
					}
				} finally {
					this.subscriptionsLock.writeLock().unlock();
				}

				// maintenant retirer le client
				ReceivingOutboundPort out;
				this.registrationLock.writeLock().lock();
				try {
					this.registeredClients.remove(unregMsg.getClientReceptionPortURI());
					this.createdPrivilegedChannelsCount.remove(unregMsg.getClientReceptionPortURI());
				} finally {
					this.registrationLock.writeLock().unlock();
				}


			}

			// Propager aux voisins avec nouvel émetteur — sauf au voisin
			// qui vient de nous transmettre le message (soutenance §6.2).
			GossipMessageI forwarded = msg.copyWithNewEmitterURI(this.getReflectionInboundPortURI());
			for (Map.Entry<String, GossipSenderOutboundPort> e
					: this.sendersByNeighbour.entrySet()) {
				if (immediateSenderReflectionURI != null
						&& e.getKey().equals(immediateSenderReflectionURI)) {
					continue; // skip echo
				}
				final GossipSenderOutboundPort sender = e.getValue();
				try {
					this.runTask(this.esGossipIndex, o -> {
						try {
							sender.send(new GossipMessageI[]{forwarded});
						} catch (Exception ex) {
							((Broker) o).logMessage(
									"[Broker] gossip forward failed: " + ex + "\n");
						}
					});
				} catch (Exception ex) {
					this.logMessage("[Broker] gossip forward submit failed: " + ex + "\n");
				}
			}
		}
	}

	@Override
	public void receive(GossipMessageI[] gossipMessages) throws Exception {
		update(gossipMessages);
	}

	/**
	 * Diffuser un lot de messages gossip à <strong>tous</strong> les voisins
	 * (utilisé pour les messages générés localement, donc sans émetteur
	 * immédiat à exclure). Pour les retransmissions à partir de {@code update},
	 * voir le filtrage explicite dans {@link #update(GossipMessageI[])}
	 * (soutenance §6.2).
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

	private void cleanupGossipMemory() {
		Instant threshold = Instant.now().minusSeconds(120);
		// ConcurrentHashMap : nettoyage sans verrou (cf. F.5).
		this.processedGossipURIs.entrySet()
				.removeIf(e -> e.getValue().isBefore(threshold));
	}

}
