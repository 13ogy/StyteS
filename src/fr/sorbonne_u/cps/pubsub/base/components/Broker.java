package fr.sorbonne_u.cps.pubsub.base.components;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.exceptions.ComponentShutdownException;
import fr.sorbonne_u.components.exceptions.ComponentStartException;
import fr.sorbonne_u.cps.pubsub.base.connectors.BrokerClientReceivingConnector;
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
 * @author Bogdan Styn
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

	// -------------------------------------------------------------------------
	// Concurrency control (audit 2)
	// -------------------------------------------------------------------------

	// Mode équitable : protège les écritures longues contre la famine.
	protected final ReentrantReadWriteLock registrationLock = new ReentrantReadWriteLock(true); // registeredClients, receptionPortsOUT, createdPrivilegedChannelsCount
	protected final ReentrantReadWriteLock channelsLock = new ReentrantReadWriteLock(true); // Channels, privilegedChannels
	protected final ReentrantReadWriteLock subscriptionsLock = new ReentrantReadWriteLock(true); // subscriptions
	protected final ReentrantReadWriteLock gossipLock = new ReentrantReadWriteLock(true); // processedGossipURIs

	// -------------------------------------------------------------------------
	// PublishingCI async (added in latest interface)
	// -------------------------------------------------------------------------

	public void asyncPublishAndNotify(
		String publisherReceptionPortURI,
		String channel,
		MessageI message,
		String notificationInbounhdPortURI
		) throws Exception
	{
		// Audit 2: asynchronous submission.
		this.submitPublish(publisherReceptionPortURI, channel, message, notificationInbounhdPortURI);
	}

	public void asyncPublishAndNotify(
		String publisherReceptionPortURI,
		String channel,
		ArrayList<MessageI> messages,
		String notificationInbounhdPortURI
		) throws Exception
	{
		for (MessageI m : messages) {
			this.submitPublish(publisherReceptionPortURI, channel, m, notificationInbounhdPortURI);
		}
	}


	// -------------------------------------------------------------------------
	// Ports
	// -------------------------------------------------------------------------

	private BrokerRegistrationInboundPort registrationPortIN;
	private BrokerPublishingInboundPort publishingPortIN;
	private BrokerPrivilegedInboundPort privilegedPortIN;
	private GossipReceiverInboundPort gossipPortIN;

	// URI statique d'enregistrement
	public static String REGISTRATION_PORT_URI;

	// -------------------------------------------------------------------------
	// State
	// -------------------------------------------------------------------------

	/** Registered clients: receptionPortURI -> registration class. */
	private final Map<String, RegistrationClass> registeredClients = new HashMap<>();
	/** Per-client outbound port to deliver messages. */
	private final Map<String, BrokerReceptionOutboundPort> receptionPortsOUT = new HashMap<>();
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

	/** Per-client created privileged channels count. */
	private final Map<String, Integer> createdPrivilegedChannelsCount = new HashMap<>();

	/** Channel quotas */
	private int standardQuota;
	private int premiumQuota;
	private int nbFreeChannels;

	/** Subscriptions: channel -> (client receptionPortURI -> filter). */
	private final Map<String, Map<String, MessageFilterI>> subscriptions = new HashMap<>();

	/** Number of messages currently in-flight per channel. */
	private final Map<String, Integer> inFlightPerChannel = new HashMap<>();

	// -------------------------------------------------------------------------
	// Gossip data
	// -------------------------------------------------------------------------

	// Un port sortant par voisin
	private List<GossipSenderOutboundPort> gossipSenders = new ArrayList<>();
	private List<String> gossipURIs;

	// Mémoire des URIs déjà traités (avec nettoyage périodique)
	private final  Map<String, Instant> processedGossipURIs = new HashMap<>();


	// -------------------------------------------------------------------------
	// Constructor
	// -------------------------------------------------------------------------

	protected Broker(int nbThreads, int nbSchedulableThreads,
					 int nbFreeChannels, int standardQuota, int premiumQuota,
					 int nbReceptionThreads, int nbPropagationThreads,
					 int nbDeliveryThreads,
					 List<String> neighborsGossipURIs) throws Exception
	{
		super(nbThreads, nbSchedulableThreads);

		// Create explicit thread pools for audit 2.
		this.esReceptionIndex = this.createNewExecutorService(ES_RECEPTION_URI, nbReceptionThreads, false);
		this.esPropagationIndex = this.createNewExecutorService(ES_PROPAGATION_URI, nbPropagationThreads, false);
		this.esDeliveryIndex = this.createNewExecutorService(ES_DELIVERY_URI, nbDeliveryThreads, false);

		this.nbFreeChannels=nbFreeChannels;
		for (int i = 0; i < nbFreeChannels; i++) {
			String c = "channel" + i;
			this.channels.add(c);
			this.subscriptions.put(c, new HashMap<>());
			this.inFlightPerChannel.put(c, 0);
		}
		this.standardQuota = standardQuota;
		this.premiumQuota = premiumQuota;

		registrationPortIN = new BrokerRegistrationInboundPort(this);
		registrationPortIN.publishPort();
		REGISTRATION_PORT_URI = registrationPortIN.getPortURI();

		publishingPortIN = new BrokerPublishingInboundPort(this);
		publishingPortIN.publishPort();

		privilegedPortIN = new BrokerPrivilegedInboundPort(this);
		privilegedPortIN.publishPort();

		gossipPortIN = new GossipReceiverInboundPort(this);
		gossipPortIN.publishPort();

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
		synchronized (this.inFlightPerChannel) {
			this.inFlightPerChannel.put(channel, this.inFlightPerChannel.getOrDefault(channel, 0) + 1);
		}
	}

	protected void finishInFlight(String channel)
	{
		synchronized (this.inFlightPerChannel) {
			int v = this.inFlightPerChannel.getOrDefault(channel, 0);
			this.inFlightPerChannel.put(channel, Math.max(0, v - 1));
		}
	}

	protected void propagationStage(String channel, MessageI message) throws Exception
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

	// -------------------------------------------------------------------------
	// Component life cycle
	// -------------------------------------------------------------------------

	@Override
	public void start() throws ComponentStartException {
		super.start();
		// connecter le ports sortants gossip
		try {
			for (String neighbourURI : this.gossipURIs) {
				GossipSenderOutboundPort out = new GossipSenderOutboundPort(this);
				out.publishPort();
				this.doPortConnection(
						out.getPortURI(),
						neighbourURI,
						GossipConnector.class.getCanonicalName());
				this.gossipSenders.add(out);
			}
		} catch (Exception e) {
			throw new ComponentStartException(e);
		}
	}

	@Override
	public void execute() throws Exception {
		super.execute();
		// Nettoyage périodique de la mémoire gossip toutes les 2 minutes
		// Il faut un thread ordonnançable
		this.scheduleTaskAtFixedRate(
				o -> ((Broker) o).cleanupGossipMemory(),
				2, 2, TimeUnit.MINUTES);
	}

	@Override
	public void finalise() throws Exception {

		for (BrokerReceptionOutboundPort out : this.receptionPortsOUT.values()) {
			if (out.connected()) {
				this.doPortDisconnection(out.getPortURI());
			}
		}
		for (GossipSenderOutboundPort out : this.gossipSenders) {
			if (out.connected()) {
				this.doPortDisconnection(out.getPortURI());
			}
		}

		super.finalise();
	}

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

			for (GossipSenderOutboundPort out : this.gossipSenders) {
				if (!out.isDestroyed()) {
					out.unpublishPort();
					out.destroyPort();
				}
			}
			this.gossipSenders.clear();

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
/*
		try {
			// Disconnect/unpublish per-client outbound ports.
			for (BrokerReceptionOutboundPort out : this.receptionPortsOUT.values()) {
				try {
					if (out.connected()) {
						this.doPortDisconnection(out.getPortURI());
					}
				} catch (Exception ignored) {}
				try { out.unpublishPort(); } catch (Exception ignored) {}
				try { out.destroyPort(); } catch (Exception ignored) {}
			}
			this.receptionPortsOUT.clear();

			// Unpublish broker inbound ports.
			try {
                if (this.publishingPortIN != null) {
                    this.publishingPortIN.unpublishPort();
                    this.publishingPortIN.destroyPort();
                }
            } catch (Exception ignored) {}
			try {
                if (registrationPortIN != null) {
                    registrationPortIN.unpublishPort();
                    registrationPortIN.destroyPort();
                }
            } catch (Exception ignored) {}
			try {
                if (privilegedPortIN != null) {
                    privilegedPortIN.unpublishPort();
                    privilegedPortIN.destroyPort();
                }
            } catch (Exception ignored) {}
		} catch (Throwable t) {
			throw new ComponentShutdownException(t);
		}
		super.shutdown();
	}
*/
	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	public static String registrationPortURI() throws Exception
	{
		return REGISTRATION_PORT_URI;
	}

	private String publishingPortURI() throws Exception
	{
		return publishingPortIN.getPortURI();
	}

	private String privilegedPortURI() throws Exception
	{
		return privilegedPortIN.getPortURI();
	}

	// -------------------------------------------------------------------------
	// Registration (RegistrationCI)
	// -------------------------------------------------------------------------

	private boolean registeredLocked(String receptionPortURI){
		return this.registeredClients.containsKey(receptionPortURI);
	}
	private boolean channelExistLocked(String channel)
	{
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

	public boolean registered(String receptionPortURI, RegistrationClass rc) throws Exception
	{
		this.registrationLock.readLock().lock();
		try {
			return rc != null && rc.equals(this.registeredClients.get(receptionPortURI));
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
		if (this.registered(receptionPortURI)) {
			throw new AlreadyRegisteredException();
		}

		// Réservation atomique de l'entrée afin que deux register() concurrents
		// sur la même URI ne réussissent pas tous les deux.
		this.registrationLock.writeLock().lock();
		try {
			if (this.registeredLocked(receptionPortURI)) {
				throw new AlreadyRegisteredException();
			}
			this.registeredClients.put(receptionPortURI, rc);
			this.createdPrivilegedChannelsCount.putIfAbsent(receptionPortURI, 0);
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
			BrokerClientReceivingConnector.class.getCanonicalName());
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


		RegisterGossipMessage gossip = new RegisterGossipMessage(
				java.util.UUID.randomUUID().toString(), // URI unique
				Instant.now(),
				this.getReflectionInboundPortURI(),     // émetteur = ce courtier
				receptionPortURI,                        // client qui s'enregistre
				rc);

		this.gossipToNeighbours(new GossipMessageI[]{ gossip }); //initiation de la propagation

		if (rc == RegistrationClass.FREE) {
			return publishingPortIN.getPortURI();
		}else{
			this.createdPrivilegedChannelsCount.putIfAbsent(receptionPortURI, 0);
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

		ModifyServiceClassGossipMessage gossip = new ModifyServiceClassGossipMessage(
				java.util.UUID.randomUUID().toString(),
				Instant.now(),
				this.getReflectionInboundPortURI(),
				receptionPortURI,
				rc);

		this.gossipToNeighbours(new GossipMessageI[]{ gossip });

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

			this.registrationLock.writeLock().lock();
			try {
				this.createdPrivilegedChannelsCount.remove(receptionPortURI);
			} finally {
				this.registrationLock.writeLock().unlock();
			}
			if (!gossipMessages.isEmpty()) {
				this.gossipToNeighbours(
						gossipMessages.toArray(new GossipMessageI[0]));
			}
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
			this.destroyChannelNow(receptionPortURI, ch);
		}

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

	public boolean channelExist(String channel) throws Exception
	{
		this.channelsLock.readLock().lock();
		try {
			return this.channelExistLocked(channel);
		} finally {
			this.channelsLock.readLock().unlock();
		}	}


	private boolean channelAuthorisedLocked(String receptionPortURI, String channel) throws Exception{

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
		return info.authorisedUsersPattern.matcher(receptionPortURI).matches();


	}

	public boolean channelAuthorised(String receptionPortURI, String channel) throws Exception {

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

	public boolean subscribed(String receptionPortURI, String channel) throws Exception
	{
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

	public void subscribe(String receptionPortURI, String channel, MessageFilterI filter) throws Exception
	{

		if (filter == null) {
			throw new IllegalArgumentException("filter cannot be null.");
		}

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
					subs.put(receptionPortURI, filter);
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

	public void unsubscribe(String receptionPortURI, String channel) throws Exception
	{
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

	public boolean modifyFilter(String receptionPortURI, String channel, MessageFilterI filter) throws Exception
	{

		if (filter == null) {
			throw new IllegalArgumentException("filter cannot be null.");
		}
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
					subs.put(receptionPortURI, filter);
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

	public void publish(String publisherReceptionPortURI, String channel, MessageI message) throws Exception
	{
		// Audit 2: publish must be asynchronous (fire-and-forget submission).
		this.submitPublish(publisherReceptionPortURI, channel, message, null);
	}

	public void publish(String publisherReceptionPortURI, String channel, ArrayList<MessageI> messages) throws Exception
	{
		if (messages == null || messages.isEmpty()) {
			throw new IllegalArgumentException("messages cannot be null or empty.");
		}
		this.runTask(this.esReceptionIndex, o -> {
			try {
				for (MessageI m : messages) {
					((Broker) o).receptionStage(publisherReceptionPortURI, channel, m, null);
				}
			} catch (Exception e) { e.printStackTrace(); }
		});
	}

	// -------------------------------------------------------------------------
	// Privileged channels management (PrivilegedClientCI)
	// -------------------------------------------------------------------------

	public boolean hasCreatedChannel(String receptionPortURI, String channel) throws Exception
	{
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

	public boolean channelQuotaReached(String receptionPortURI) throws Exception
	{

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

	public void createChannel(String receptionPortURI, String channel, String autorisedUsers) throws Exception
	{
		if (channel == null || channel.isEmpty()) {
			throw new IllegalArgumentException("channel cannot be null or empty.");
		}
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
					synchronized (this.inFlightPerChannel) {
						this.inFlightPerChannel.put(channel, 0);
					}
					this.createdPrivilegedChannelsCount.merge(receptionPortURI, 1, Integer::sum);

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

	public void modifyAuthorisedUsers(String receptionPortURI, String channel, String autorisedUsers) throws Exception
	{

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
			} finally {
				this.channelsLock.writeLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}
	}


	public void destroyChannel(String receptionPortURI, String channel) throws Exception
	{
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
					int inFlight;
					synchronized (((Broker) o).inFlightPerChannel) {
						inFlight = ((Broker) o).inFlightPerChannel
								.getOrDefault(channel, 0);
					}
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
				synchronized (this.inFlightPerChannel) {
					this.inFlightPerChannel.remove(channel);
				}
				this.createdPrivilegedChannelsCount.merge(
						receptionPortURI, -1, Integer::sum);
			} finally {
				this.channelsLock.writeLock().unlock();
			}
		} finally {
			this.registrationLock.writeLock().unlock();
		}
	}

	public void destroyChannelNow(String receptionPortURI, String channel, boolean propagate) throws Exception {
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
				synchronized (this.inFlightPerChannel) {
					this.inFlightPerChannel.remove(channel);
				}
				this.createdPrivilegedChannelsCount.put(
						receptionPortURI,
						Math.max(0, this.createdPrivilegedChannelsCount.getOrDefault(receptionPortURI, 1) - 1));
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

	// Gossip methodes

	@Override
	public void update(GossipMessageI[] fromSender) {

		for (GossipMessageI msg : fromSender) {
			//Vérifier si déjà traité
			boolean alreadyProcessed;
			this.gossipLock.writeLock().lock();
			try {
				alreadyProcessed = this.processedGossipURIs.containsKey(msg.gossipMessageURI());
				if (!alreadyProcessed) {
					this.processedGossipURIs.put(msg.gossipMessageURI(), msg.timestamp());
				}
			} finally {
				this.gossipLock.writeLock().unlock();
			}
			if (alreadyProcessed) continue;

			//traiter le message
			//TO-DO
			if (msg instanceof RegisterGossipMessage) {
				RegisterGossipMessage regMsg = (RegisterGossipMessage) msg;
				// Mémoriser localement — sans créer de port outbound
				// car ce client n'est PAS enregistré chez nous, juste connu
				this.registrationLock.writeLock().lock();
				try {
					this.registeredClients.putIfAbsent(
							regMsg.getClientReceptionPortURI(),
							regMsg.getRegistrationClass());
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
								synchronized (this.inFlightPerChannel) {
									this.inFlightPerChannel.put(ccMsg.getChannel(), 0);
								}
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
							synchronized (this.inFlightPerChannel) {
								this.inFlightPerChannel.remove(dcMsg.getChannel());
							}
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

			// Propager aux voisins avec nouvel émetteur
			GossipMessageI forwarded = msg.copyWithNewEmitterURI(this.getReflectionInboundPortURI());
			try {
				for (GossipSenderOutboundPort sender : this.gossipSenders) {
					this.runTask(this.esReceptionIndex, o -> {
						try {
							sender.send(new GossipMessageI[]{forwarded});
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					});
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public void receive(GossipMessageI[] gossipMessages) throws Exception {
		this.handleRequest(o -> {
			((Broker) o).update(gossipMessages);
			return null;
		});
	}

	public void gossipToNeighbours(GossipMessageI[] messages){
		for (GossipSenderOutboundPort sender : this.gossipSenders) {
			this.runTask(this.esReceptionIndex, o -> {
				try {
					sender.send(messages);
				} catch (Exception e) {
					((Broker) o).logMessage("[Broker] gossip send failed: " + e + "\n");
				}
			});
		}
	}

	private void cleanupGossipMemory() {
		Instant threshold = Instant.now().minusSeconds(120);
		this.gossipLock.writeLock().lock();
		try {
			this.processedGossipURIs.entrySet()
					.removeIf(e -> e.getValue().isBefore(threshold));
		} finally {
			this.gossipLock.writeLock().unlock();
		}
	}

}
