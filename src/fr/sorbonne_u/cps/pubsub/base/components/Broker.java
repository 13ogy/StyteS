package fr.sorbonne_u.cps.pubsub.base.components;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import fr.sorbonne_u.components.AbstractComponent;
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
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;

import java.util.regex.Pattern;

/**
 * Broker component implementing a publication/subscription system.
 *
 * <p>
 * Functionality implemented:
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
 */
public class Broker extends AbstractComponent
{
	// -------------------------------------------------------------------------
	// Configuration
	// -------------------------------------------------------------------------

	/** Number of FREE channels: channel0..channel{NB_FREE_CHANNELS-1}. */
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

	/** Per-client created privileged channels count (quota enforcement). */
	private final Map<String, Integer> createdPrivilegedChannelsCount = new HashMap<>();

	/** Quotas by registration class (step 2). */
	public static final int STANDARD_PRIVILEGED_CHANNEL_QUOTA = 2;
	public static final int PREMIUM_PRIVILEGED_CHANNEL_QUOTA = 5;
	/** Subscriptions: channel -> (client receptionPortURI -> filter). */
	private final Map<String, Map<String, MessageFilterI>> subscriptions = new HashMap<>();

	// -------------------------------------------------------------------------
	// Constructor
	// -------------------------------------------------------------------------

	protected Broker(int nbThreads, int nbSchedulableThreads) throws Exception
	{
		super(nbThreads, nbSchedulableThreads);

		// Create FREE channels.
		for (int i = 0; i < NB_FREE_CHANNELS; i++) {
			String c = "channel" + i;
			this.channels.add(c);
			this.subscriptions.put(c, new HashMap<>());
		}

		registrationPortIN = new BrokerRegistrationInboundPort(this);
		registrationPortIN.publishPort();

		publishingPortIN = new BrokerPublishingInboundPort(this);
		publishingPortIN.publishPort();

		privilegedPortIN = new BrokerPrivilegedInboundPort(this);
		privilegedPortIN.publishPort();
	}

	// -------------------------------------------------------------------------
	// Static helper
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

		// Step 2: accept all service classes (FREE, STANDARD, PREMIUM).
		this.registeredClients.put(receptionPortURI, rc);
		// initialise privileged quota bookkeeping
		this.createdPrivilegedChannelsCount.putIfAbsent(receptionPortURI, 0);

		// Create a dedicated outbound port for this client and connect it to
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
		// Step 2: allow service class upgrade/downgrade.
		this.registeredClients.put(receptionPortURI, rc);
		return publishingPortIN.getPortURI();
	}

	public void unregister(String receptionPortURI) throws Exception
	{
		if (!this.registered(receptionPortURI)) {
			throw new UnknownClientException(receptionPortURI);
		}

		// Remove subscriptions.
		for (Map<String, MessageFilterI> subs : this.subscriptions.values()) {
			subs.remove(receptionPortURI);
		}

		// Disconnect and remove outbound port.
		BrokerReceptionOutboundPort out = this.receptionPortsOUT.remove(receptionPortURI);
		if (out != null) {
			try {
				this.doPortDisconnection(out.getPortURI());
			} catch (Exception ignored) {
			}
			try {
				out.unpublishPort();
			} catch (Exception ignored) {
			}
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
		if (filter == null) {
			throw new IllegalArgumentException("filter cannot be null.");
		}
		if (!this.channelAuthorised(receptionPortURI, channel)) {
			throw new UnauthorisedClientException();
		}
		this.subscriptions.get(channel).put(receptionPortURI, filter);
	}

	public void unsubscribe(String receptionPortURI, String channel) throws Exception
	{
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
	}

	public boolean modifyFilter(String receptionPortURI, String channel, MessageFilterI filter) throws Exception
	{
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
	}

	// -------------------------------------------------------------------------
	// Publishing (PublishingCI)
	// -------------------------------------------------------------------------

	public void publish(String publisherReceptionPortURI, String channel, MessageI message) throws Exception
	{
		if (message == null) {
			throw new IllegalArgumentException("message cannot be null.");
		}
		if (!this.registered(publisherReceptionPortURI)) {
			throw new UnknownClientException(publisherReceptionPortURI);
		}
		if (!this.channelExist(channel)) {
			throw new UnknownChannelException(channel);
		}
		// Enforce privileged channel authorisation for publishers as well.
		if (!this.channelAuthorised(publisherReceptionPortURI, channel)) {
			throw new UnauthorisedClientException();
		}

		Map<String, MessageFilterI> subs = this.subscriptions.get(channel);
		for (Map.Entry<String, MessageFilterI> e : subs.entrySet()) {
			String subscriberURI = e.getKey();
			MessageFilterI filter = e.getValue();
			if (filter != null && filter.match(message)) {
				ReceivingCI out = this.receptionPortsOUT.get(subscriberURI);
				if (out != null) {
					out.receive(channel, message);
				}
			}
		}
	}

	public void publish(String publisherReceptionPortURI, String channel, ArrayList<MessageI> messages) throws Exception
	{
		if (messages == null || messages.isEmpty()) {
			throw new IllegalArgumentException("messages cannot be null or empty.");
		}
		for (MessageI m : messages) {
			this.publish(publisherReceptionPortURI, channel, m);
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
		this.privilegedChannels.put(channel, new PrivilegedChannelInfo(receptionPortURI, p));
		this.createdPrivilegedChannelsCount.put(
			receptionPortURI,
			this.createdPrivilegedChannelsCount.getOrDefault(receptionPortURI, 0) + 1);
	}

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
	}

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
		// For this project, we do not maintain per-channel message queues.
		// destroyChannel behaves like destroyChannelNow.
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

		// update quota bookkeeping
		this.createdPrivilegedChannelsCount.put(
			receptionPortURI,
			Math.max(0, this.createdPrivilegedChannelsCount.getOrDefault(receptionPortURI, 1) - 1));
	}
}
