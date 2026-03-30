package fr.sorbonne_u.cps.pubsub.base.components;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.exceptions.ComponentShutdownException;
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
	RegistrationCI.class,
	PublishingCI.class,
	PrivilegedClientCI.class
})
@RequiredInterfaces(required = {
	ReceivingCI.class
})
public class Broker extends AbstractComponent
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

	/** Protect broker shared state; never hold this lock while doing remote calls. */
	protected final ReentrantReadWriteLock stateLock = new ReentrantReadWriteLock(true);
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
	// Configuration
	// -------------------------------------------------------------------------

	public static final int NB_FREE_CHANNELS = 3;

	// -------------------------------------------------------------------------
	// Ports
	// -------------------------------------------------------------------------

	private static BrokerRegistrationInboundPort registrationPortIN;
	private BrokerPublishingInboundPort publishingPortIN;
	private static BrokerPrivilegedInboundPort privilegedPortIN;

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

	/** Quotas by registration class. */
	public static final int STANDARD_PRIVILEGED_CHANNEL_QUOTA = 2;
	public static final int PREMIUM_PRIVILEGED_CHANNEL_QUOTA = 5;

	/** Subscriptions: channel -> (client receptionPortURI -> filter). */
	private final Map<String, Map<String, MessageFilterI>> subscriptions = new HashMap<>();

	/** Number of messages currently in-flight per channel. */
	private final Map<String, Integer> inFlightPerChannel = new HashMap<>();

	// -------------------------------------------------------------------------
	// Constructor
	// -------------------------------------------------------------------------

	protected Broker(int nbThreads, int nbSchedulableThreads) throws Exception
	{
		super(nbThreads, nbSchedulableThreads);

		// Create explicit thread pools for audit 2.
		this.esReceptionIndex = this.createNewExecutorService(ES_RECEPTION_URI, Math.max(1, nbThreads), false);
		this.esPropagationIndex = this.createNewExecutorService(ES_PROPAGATION_URI, Math.max(1, nbThreads), false);
		this.esDeliveryIndex = this.createNewExecutorService(ES_DELIVERY_URI, Math.max(1, nbThreads), false);

		for (int i = 0; i < NB_FREE_CHANNELS; i++) {
			String c = "channel" + i;
			this.channels.add(c);
			this.subscriptions.put(c, new HashMap<>());
			this.inFlightPerChannel.put(c, 0);
		}

		registrationPortIN = new BrokerRegistrationInboundPort(this);
		registrationPortIN.publishPort();

		publishingPortIN = new BrokerPublishingInboundPort(this);
		publishingPortIN.publishPort();

		privilegedPortIN = new BrokerPrivilegedInboundPort(this);
		privilegedPortIN.publishPort();
	}

	// -------------------------------------------------------------------------
	// Internal asynchronous pipeline (audit 2)
	// -------------------------------------------------------------------------

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
		// Lightweight validation under read lock.
		this.stateLock.writeLock().lock();
		try {
			this.inFlightPerChannel.put(channel, this.inFlightPerChannel.getOrDefault(channel, 0) + 1);
			if (!this.registeredClients.containsKey(publisherReceptionPortURI)) {
				throw new UnknownClientException(publisherReceptionPortURI);
			}
			if (!this.channels.contains(channel)) {
				throw new UnknownChannelException(channel);
			}
			// Enforce privileged channel auth (publish).
			// We can call channelAuthorised which itself checks registration/channel existence.
			if (!this.channelAuthorised(publisherReceptionPortURI, channel)) {
				throw new UnauthorisedClientException();
			}
		} finally {
			this.stateLock.writeLock().unlock();
		}

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

	protected void finishInFlight(String channel)
	{
		this.stateLock.writeLock().lock();
		try {
			int v = this.inFlightPerChannel.getOrDefault(channel, 0);
			this.inFlightPerChannel.put(channel, Math.max(0, v - 1));
		} finally {
			this.stateLock.writeLock().unlock();
		}
	}

	protected void propagationStage(String channel, MessageI message) throws Exception
	{
		// Snapshot recipients under read lock.
		List<DeliveryTarget> targets = new ArrayList<>();
		this.stateLock.readLock().lock();
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
			this.stateLock.readLock().unlock();
		}

		// Submit deliveries.
		final int expected = targets.size();
		if (expected == 0) {
			// no recipients => end of pipeline
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
	public synchronized void shutdown() throws ComponentShutdownException
	{
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

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	public static String registrationPortURI() throws Exception
	{
		return registrationPortIN.getPortURI();
	}

	public String publishingPortURI() throws Exception
	{
		return publishingPortIN.getPortURI();
	}

	public static String privilegedPortURI() throws Exception
	{
		return privilegedPortIN.getPortURI();
	}

	// -------------------------------------------------------------------------
	// Registration (RegistrationCI)
	// -------------------------------------------------------------------------

	public boolean registered(String receptionPortURI) throws Exception
	{
		return this.registeredClients.containsKey(receptionPortURI);
	}

	public boolean registered(String receptionPortURI, RegistrationClass rc) throws Exception
	{
		return rc != null && rc.equals(this.registeredClients.get(receptionPortURI));
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

		// Accepting all service classes (FREE, STANDARD, PREMIUM)
		this.registeredClients.put(receptionPortURI, rc);

		// Initializing privileged quota bookkeeping
		this.createdPrivilegedChannelsCount.putIfAbsent(receptionPortURI, 0);

		// Creating a dedicated outbound port for this client and connect it to
		// the client inbound port offering ReceivingCI.
		BrokerReceptionOutboundPort out = new BrokerReceptionOutboundPort(this);
		out.publishPort();
		this.doPortConnection(
			out.getPortURI(),
			receptionPortURI,
			BrokerClientReceivingConnector.class.getCanonicalName());
		this.receptionPortsOUT.put(receptionPortURI, out);

		return publishingPortIN.getPortURI();
	}

	public String modifyServiceClass(String receptionPortURI, RegistrationClass rc) throws Exception
	{
		if (!this.registered(receptionPortURI)) {
			throw new UnknownClientException(receptionPortURI);
		}
		if (rc == null) {
			throw new IllegalArgumentException("rc cannot be null.");
		}
		// Allowing service class upgrade/downgrade
		this.registeredClients.put(receptionPortURI, rc);
		return publishingPortIN.getPortURI();
	}

	public void unregister(String receptionPortURI) throws Exception
	{
		if (!this.registered(receptionPortURI)) {
			throw new UnknownClientException(receptionPortURI);
		}

		// Removing subscriptions
		for (Map<String, MessageFilterI> subs : this.subscriptions.values()) {
			subs.remove(receptionPortURI);
		}

		// Disconnecting and removing outbound port
		BrokerReceptionOutboundPort out = this.receptionPortsOUT.remove(receptionPortURI);
		if (out != null) {
			try {
				if (out.connected()) {
					this.doPortDisconnection(out.getPortURI());
				}
			} catch (Exception ignored) {
			}
			try { out.unpublishPort(); } catch (Exception ignored) {}
			try { out.destroyPort(); } catch (Exception ignored) {}
		}

		this.registeredClients.remove(receptionPortURI);
		this.createdPrivilegedChannelsCount.remove(receptionPortURI);
	}

	// -------------------------------------------------------------------------
	// Channel/subscriptions (RegistrationCI)
	// -------------------------------------------------------------------------

	public boolean channelExist(String channel) throws Exception
	{
		return this.channels.contains(channel);
	}

	public boolean channelAuthorised(String receptionPortURI, String channel) throws Exception
	{
		if (!this.registered(receptionPortURI)) {
			throw new UnknownClientException(receptionPortURI);
		}
		if (!this.channelExist(channel)) {
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

	public boolean subscribed(String receptionPortURI, String channel) throws Exception
	{
		if (!this.registered(receptionPortURI)) {
			throw new UnknownClientException(receptionPortURI);
		}
		if (!this.channelExist(channel)) {
			throw new UnknownChannelException(channel);
		}
		return this.subscriptions.get(channel).containsKey(receptionPortURI);
	}

	public void subscribe(String receptionPortURI, String channel, MessageFilterI filter) throws Exception
	{
		this.stateLock.writeLock().lock();
		try {
		if (filter == null) {
			throw new IllegalArgumentException("filter cannot be null.");
		}
		if (!this.channelAuthorised(receptionPortURI, channel)) {
			throw new UnauthorisedClientException();
		}
		this.subscriptions.get(channel).put(receptionPortURI, filter);
		} finally {
			this.stateLock.writeLock().unlock();
		}
	}

	public void unsubscribe(String receptionPortURI, String channel) throws Exception
	{
		this.stateLock.writeLock().lock();
		try {
		if (!this.registered(receptionPortURI)) {
			throw new UnknownClientException(receptionPortURI);
		}
		if (!this.channelExist(channel)) {
			throw new UnknownChannelException(channel);
		}
		if (!this.subscriptions.get(channel).containsKey(receptionPortURI)) {
			throw new NotSubscribedChannelException(
				"Client " + receptionPortURI + " not subscribed to " + channel);
		}
		this.subscriptions.get(channel).remove(receptionPortURI);
		} finally {
			this.stateLock.writeLock().unlock();
		}
	}

	public boolean modifyFilter(String receptionPortURI, String channel, MessageFilterI filter) throws Exception
	{
		this.stateLock.writeLock().lock();
		try {
		if (filter == null) {
			throw new IllegalArgumentException("filter cannot be null.");
		}
		if (!this.registered(receptionPortURI)) {
			throw new UnknownClientException(receptionPortURI);
		}
		if (!this.channelExist(channel)) {
			throw new UnknownChannelException(channel);
		}
		if (!this.subscriptions.get(channel).containsKey(receptionPortURI)) {
			throw new NotSubscribedChannelException(
				"Client " + receptionPortURI + " not subscribed to " + channel);
		}
		this.subscriptions.get(channel).put(receptionPortURI, filter);
		return true;
		} finally {
			this.stateLock.writeLock().unlock();
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
		for (MessageI m : messages) {
			this.submitPublish(publisherReceptionPortURI, channel, m, null);
		}
	}

	// -------------------------------------------------------------------------
	// Privileged channels management (PrivilegedClientCI)
	// -------------------------------------------------------------------------

	public boolean hasCreatedChannel(String receptionPortURI, String channel) throws Exception
	{
		if (!this.registered(receptionPortURI)) {
			throw new UnknownClientException(receptionPortURI);
		}
		if (!this.channelExist(channel)) {
			throw new UnknownChannelException(channel);
		}
		PrivilegedChannelInfo info = this.privilegedChannels.get(channel);
		return info != null && info.ownerReceptionPortURI.equals(receptionPortURI);
	}

	public boolean channelQuotaReached(String receptionPortURI) throws Exception
	{
		if (!this.registered(receptionPortURI)) {
			throw new UnknownClientException(receptionPortURI);
		}
		RegistrationClass rc = this.registeredClients.get(receptionPortURI);
		int created = this.createdPrivilegedChannelsCount.getOrDefault(receptionPortURI, 0);

		switch (rc) {
			case FREE:
				return true; // FREE cannot create privileged channels
			case STANDARD:
				return created >= STANDARD_PRIVILEGED_CHANNEL_QUOTA;
			case PREMIUM:
				return created >= PREMIUM_PRIVILEGED_CHANNEL_QUOTA;
			default:
				return true;
		}
	}

	public void createChannel(String receptionPortURI, String channel, String autorisedUsers) throws Exception
	{
		this.stateLock.writeLock().lock();
		try {
		if (!this.registered(receptionPortURI)) {
			throw new UnknownClientException(receptionPortURI);
		}
		if (channel == null || channel.isEmpty()) {
			throw new IllegalArgumentException("channel cannot be null or empty.");
		}
		if (this.channelExist(channel)) {
			throw new AlreadyExistingChannelException("Channel already exists: " + channel);
		}

		RegistrationClass rc = this.registeredClients.get(receptionPortURI);
		if (rc == RegistrationClass.FREE) {
			throw new UnauthorisedClientException();
		}
		if (this.channelQuotaReached(receptionPortURI)) {
			throw new ChannelQuotaExceededException("Channel quota reached for " + receptionPortURI + " (" + rc + ")");
		}

		Pattern p = null;
		if (autorisedUsers != null && !autorisedUsers.isEmpty()) {
			p = Pattern.compile(autorisedUsers);
		}

		this.channels.add(channel);
		this.subscriptions.put(channel, new HashMap<>());
		this.inFlightPerChannel.put(channel, 0);
		this.privilegedChannels.put(channel, new PrivilegedChannelInfo(receptionPortURI, p));
		this.createdPrivilegedChannelsCount.put(
			receptionPortURI,
			this.createdPrivilegedChannelsCount.getOrDefault(receptionPortURI, 0) + 1);
		} finally {
			this.stateLock.writeLock().unlock();
		}
	}

	// Kept as an internal helper; the corresponding method has been removed from
	// PrivilegedClientCI in the latest interfaces update.
	public boolean isAuthorisedUser(String channel, String uri) throws Exception
	{
		if (channel == null || channel.isEmpty()) {
			throw new IllegalArgumentException("channel cannot be null or empty.");
		}
		if (uri == null || uri.isEmpty()) {
			throw new IllegalArgumentException("uri cannot be null or empty.");
		}
		if (!this.registered(uri)) {
			throw new UnknownClientException(uri);
		}
		if (!this.channelExist(channel)) {
			throw new UnknownChannelException(channel);
		}

		// FREE channel => always authorised
		if (!this.privilegedChannels.containsKey(channel)) {
			return true;
		}

		PrivilegedChannelInfo info = this.privilegedChannels.get(channel);
		if (info == null || info.authorisedUsersPattern == null) {
			return true;
		}
		return info.authorisedUsersPattern.matcher(uri).matches();
	}

	public void modifyAuthorisedUsers(String receptionPortURI, String channel, String autorisedUsers) throws Exception
	{
		this.stateLock.writeLock().lock();
		try {
		if (!this.registered(receptionPortURI)) {
			throw new UnknownClientException(receptionPortURI);
		}
		if (!this.channelExist(channel)) {
			throw new UnknownChannelException(channel);
		}
		PrivilegedChannelInfo info = this.privilegedChannels.get(channel);
		if (info == null) {
			// only privileged channels can be modified
			throw new UnauthorisedClientException();
		}
		if (!info.ownerReceptionPortURI.equals(receptionPortURI)) {
			throw new UnauthorisedClientException();
		}
		if (autorisedUsers == null || autorisedUsers.isEmpty()) {
			throw new IllegalArgumentException("autorisedUsers cannot be null/empty for modifyAuthorisedUsers.");
		}
		info.authorisedUsersPattern = Pattern.compile(autorisedUsers);
		} finally {
			this.stateLock.writeLock().unlock();
		}
	}

	// Kept as an internal helper; the corresponding method has been removed from
	// PrivilegedClientCI in the latest interfaces update.
	public void removeAuthorisedUsers(String receptionPortURI, String channel, String regularExpression) throws Exception
	{
		if (!this.registered(receptionPortURI)) {
			throw new UnknownClientException(receptionPortURI);
		}
		if (!this.channelExist(channel)) {
			throw new UnknownChannelException(channel);
		}
		PrivilegedChannelInfo info = this.privilegedChannels.get(channel);
		if (info == null) {
			throw new UnauthorisedClientException();
		}
		if (!info.ownerReceptionPortURI.equals(receptionPortURI)) {
			throw new UnauthorisedClientException();
		}
		if (regularExpression == null || regularExpression.isEmpty()) {
			throw new IllegalArgumentException("regularExpression cannot be null/empty.");
		}

		// This operation is underspecified in the CI (regex of authorised users).
		// We implement a pragmatic behaviour: remove authorised users by forbidding
		// those matching the provided regex through a negative lookahead.
		Pattern toRemove = Pattern.compile(regularExpression);
		Pattern current = info.authorisedUsersPattern;
		String currentRegex = current == null ? ".*" : current.pattern();

		// If current already forbids removed ones, keep it; else add a negative lookahead.
		String newRegex = "^(?!(" + toRemove.pattern() + ")$)" + currentRegex;
		info.authorisedUsersPattern = Pattern.compile(newRegex);
	}

	public void destroyChannel(String receptionPortURI, String channel) throws Exception
	{
		if (!this.registered(receptionPortURI)) {
			throw new UnknownClientException(receptionPortURI);
		}
		if (!this.channelExist(channel)) {
			throw new UnknownChannelException(channel);
		}
		PrivilegedChannelInfo info = this.privilegedChannels.get(channel);
		if (info == null || !info.ownerReceptionPortURI.equals(receptionPortURI)) {
			throw new UnauthorisedClientException();
		}
		// Wait until no more in-flight messages on this channel.
		while (true) {
			this.stateLock.readLock().lock();
			try {
				int inFlight = this.inFlightPerChannel.getOrDefault(channel, 0);
				if (inFlight == 0) {
					break;
				}
			} finally {
				this.stateLock.readLock().unlock();
			}
			Thread.sleep(10);
		}
		this.destroyChannelNow(receptionPortURI, channel);
	}

	public void destroyChannelNow(String receptionPortURI, String channel) throws Exception
	{
		if (!this.registered(receptionPortURI)) {
			throw new UnknownClientException(receptionPortURI);
		}
		if (!this.channelExist(channel)) {
			throw new UnknownChannelException(channel);
		}

		PrivilegedChannelInfo info = this.privilegedChannels.get(channel);
		if (info == null) {
			// Only privileged channels can be destroyed through this interface
			throw new UnauthorisedClientException();
		}
		if (!info.ownerReceptionPortURI.equals(receptionPortURI)) {
			throw new UnauthorisedClientException();
		}

		// remove subscriptions
		this.subscriptions.remove(channel);
		this.channels.remove(channel);
		this.privilegedChannels.remove(channel);
		this.inFlightPerChannel.remove(channel);

		// update quota bookkeeping
		this.createdPrivilegedChannelsCount.put(
			receptionPortURI,
			Math.max(0, this.createdPrivilegedChannelsCount.getOrDefault(receptionPortURI, 1) - 1));
	}
}
