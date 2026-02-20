package fr.sorbonne_u.cps.pubsub.base.components;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.cps.pubsub.base.connectors.BrokerClientReceivingConnector;
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

/**
 * Minimal broker implementation for Audit 1 (step 1): FREE clients only.
 *
 * <p>
 * Implements: registration, free channels creation, subscriptions with filters,
 * and message publication with filtered delivery.
 * </p>
 */
public class Broker extends AbstractComponent
{
	// -------------------------------------------------------------------------
	// Configuration
	// -------------------------------------------------------------------------

	/** Number of FREE channels: channel0..channel{NB_FREE_CHANNELS-1}. */
	public static final int NB_FREE_CHANNELS = 3;
	public static final int lIMIT_CHANNELS_STANDARD= 5;
	public static final int LIMIT_CHANNELS_PREMIUM = 10;

	// -------------------------------------------------------------------------
	// Ports
	// -------------------------------------------------------------------------

	private static BrokerRegistrationInboundPort registrationPortIN;
	private BrokerPublishingInboundPort publishingPortIN;

	// -------------------------------------------------------------------------
	// State
	// -------------------------------------------------------------------------

	/** Registered clients: receptionPortURI -> registration class. */
	private final Map<String, RegistrationClass> registeredClients = new HashMap<>();
	/** Per-client outbound port to deliver messages. */
	private final Map<String, BrokerReceptionOutboundPort> receptionPortsOUT = new HashMap<>();
	/** FREE channels. */
	private final Set<String> channels = new HashSet<>();
	/** Subscriptions: channel -> (client receptionPortURI -> filter). */
	private final Map<String, Map<String, MessageFilterI>> subscriptions = new HashMap<>();
	/** privChannels: piviledged Client -> (channels) */
	private final Map<String,  Set<String>> privChannels = new HashMap<>();

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

		// Audit 1 scope: FREE only.
		if (rc != RegistrationClass.FREE) {
			// could be supported later, but reject for audit 1 to keep semantics clear.
			throw new UnauthorisedClientException();
		}

		this.registeredClients.put(receptionPortURI, rc);

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
		// Audit 1 scope: do nothing (keep FREE).
		this.registeredClients.put(receptionPortURI, RegistrationClass.FREE);
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
		// Audit 1: all registered FREE clients are authorised on FREE channels.
		return true;
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
	
	public void createChannel(String receptionPortURI, String channel, String autorisedUsers) throws Exception {
		
		if( ! this.registered(receptionPortURI)) {
			throw new UnknownClientException(receptionPortURI);
		}
		if (this.channelExist(channel) ) {
			throw new AlreadyExistingChannelException(channel);
		}
		if (this.privChannels.containsKey(receptionPortURI)) {
			if((this.registered(receptionPortURI, RegistrationClass.STANDARD)) && ((this.privChannels.get(receptionPortURI).size())==lIMIT_CHANNELS_STANDARD)) {
				throw new ChannelQuotaExceededException(receptionPortURI);
			}
			if((this.registered(receptionPortURI, RegistrationClass.PREMIUM)) && ((this.privChannels.get(receptionPortURI).size())==LIMIT_CHANNELS_PREMIUM)) {
				throw new ChannelQuotaExceededException(receptionPortURI);
			}
			else {
				privChannels.get(receptionPortURI).add(channel);
			}
		}
		Set <String> newChannels=new HashSet<>();
		newChannels.add(channel);
		privChannels.put(autorisedUsers, newChannels);
	}
}
