package fr.sorbonne_u.cps.pubsub.base.components;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.exceptions.ComponentShutdownException;
import fr.sorbonne_u.components.exceptions.ComponentStartException;
import fr.sorbonne_u.cps.pubsub.base.connectors.ClientBrokerPublishingConnector;
import fr.sorbonne_u.cps.pubsub.base.connectors.ClientBrokerRegistrationConnector;

import fr.sorbonne_u.cps.pubsub.base.connectors.ClientBrokerPrivilegedConnector;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientInboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientPrivilegedOutboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientPublishingOutboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientRegistrationOutboundPort;


import fr.sorbonne_u.components.AbstractComponent;

import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;

/**
 * Pub/sub client component.
 *
 * <p>
 * The client owns:
 * </p>
 * <ul>
 *   <li>an inbound port offering {@code ReceivingCI} to receive deliveries from the broker;</li>
 *   <li>an outbound port requiring {@code RegistrationCI} to register and manage subscriptions;</li>
 *   <li>an outbound port requiring {@code PublishingCI} to publish messages;</li>
 *   <li>an outbound port requiring {@code PrivilegedClientCI} to manage privileged channels (STANDARD/PREMIUM).</li>
 * </ul>
 *
 * <p>
 * The method {@link #register(fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass)} establishes the
 * required connections to the broker.
 * </p>
 *
 * @author Bogdan Styn
 */
public class Client extends AbstractComponent {

	private ClientInboundPort receptionPortIN;

	/**
	 * Return the URI of this client's inbound port offering ReceivingCI.
	 */
	public String getReceptionPortURI()
	{
		try {
			return this.receptionPortIN.getPortURI();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private ClientPublishingOutboundPort publishingPortOUT;
	private ClientRegistrationOutboundPort registrationPortOUT;
	private ClientPrivilegedOutboundPort privilegedPortOUT;

	private RegistrationClass rcCurrent;

	private boolean registered;
	
	public Client(int nbThreads, int nbSchedulableThreads )throws Exception {
		super(nbThreads, nbSchedulableThreads);
		this.registered = false;
		this.receptionPortIN = new ClientInboundPort(this);
		this.receptionPortIN.publishPort();
		
		this.publishingPortOUT = new ClientPublishingOutboundPort(this);
		this.publishingPortOUT.publishPort();
		
		this.registrationPortOUT = new ClientRegistrationOutboundPort(this);
		this.registrationPortOUT.publishPort();

		this.privilegedPortOUT = new ClientPrivilegedOutboundPort(this);
		this.privilegedPortOUT.publishPort();
		
	}
	// Component life cycle
	@Override
	public void start() throws ComponentStartException {
		super.start();
		try {
			this.doPortConnection(
					this.registrationPortOUT.getPortURI(),
					Broker.registrationPortURI(),
					ClientBrokerRegistrationConnector.class.getCanonicalName());
		}catch (Exception e) { throw new ComponentStartException();}
	}
	@Override
	public void execute() throws Exception {
		super.execute();
	}

	@Override
	public void finalise() throws Exception {
		if (this.registrationPortOUT.connected())
			this.doPortDisconnection(this.registrationPortOUT.getPortURI());
		if (this.publishingPortOUT.connected())
			this.doPortDisconnection(this.publishingPortOUT.getPortURI());
		if (this.privilegedPortOUT.connected())
			this.doPortDisconnection(this.privilegedPortOUT.getPortURI());
		super.finalise();
	}

	@Override
	public void shutdown() throws ComponentShutdownException {
		try {
			this.receptionPortIN.unpublishPort();
			this.publishingPortOUT.unpublishPort();
			this.registrationPortOUT.unpublishPort();
			this.privilegedPortOUT.unpublishPort();
		} catch (Exception e) {
			throw new ComponentShutdownException(e);
		}
		super.shutdown();
	}
	
	public void register(RegistrationClass rc) throws Exception {
		if (this.registered) {
			return;
		}

		
		this.logMessage("Registering client.") ;

		// Registering
		String portINURI = registrationPortOUT.register(receptionPortIN.getPortURI(), rc);

		if (rc == RegistrationClass.FREE) {
			// FREE clients only get publishing
			this.doPortConnection(
					this.publishingPortOUT.getPortURI(),
					portINURI,
					ClientBrokerPublishingConnector.class.getCanonicalName());
		} else {
			// STANDARD/PREMIUM get privileged (which also covers publishing)
			this.doPortConnection(
					this.privilegedPortOUT.getPortURI(),
					portINURI,
					ClientBrokerPrivilegedConnector.class.getCanonicalName());
		}
		this.rcCurrent = rc;

		this.logMessage("Client registered");
		this.registered = true;
	}
	
	public void modifyServiceClass(RegistrationClass rc) throws Exception {
		
		this.logMessage("Modifying registration class");
		
		if (rc == this.rcCurrent) {
			throw new AlreadyRegisteredException();
		}

		String portINURI =
			registrationPortOUT.modifyServiceClass(receptionPortIN.getPortURI(), rc);

		if (this.rcCurrent == RegistrationClass.FREE) {
			this.doPortDisconnection(this.publishingPortOUT.getPortURI());
		} else {
			this.doPortDisconnection(this.privilegedPortOUT.getPortURI());
		}

		if (rc == RegistrationClass.FREE) {
			this.doPortConnection(
					this.publishingPortOUT.getPortURI(),
					portINURI,
					ClientBrokerPublishingConnector.class.getCanonicalName());
		} else {
			this.doPortConnection(
					this.privilegedPortOUT.getPortURI(),
					portINURI,
					ClientBrokerPrivilegedConnector.class.getCanonicalName());
		}
		this.rcCurrent = rc;
		this.logMessage("Client service class modified (audit1 minimal).");
		
	}

	public void subscribe(String channel, MessageFilterI filter) throws Exception
	{
		this.registrationPortOUT.subscribe(
			this.receptionPortIN.getPortURI(),
			channel,
			filter);
	}

	public void publish(String channel, MessageI message) throws Exception
	{
		this.publishingPortOUT.publish(
			this.receptionPortIN.getPortURI(),
			channel,
			message);
	}

	// -------------------------------------------------------------------------
	// Privileged channel management API
	// -------------------------------------------------------------------------

	public boolean hasCreatedChannel(String channel) throws Exception
	{
		return this.privilegedPortOUT.hasCreatedChannel(this.receptionPortIN.getPortURI(), channel);
	}

	public boolean channelQuotaReached() throws Exception
	{
		return this.privilegedPortOUT.channelQuotaReached(this.receptionPortIN.getPortURI());
	}

	public void createChannel(String channel, String authorisedUsersRegex) throws Exception
	{
		this.privilegedPortOUT.createChannel(this.receptionPortIN.getPortURI(), channel, authorisedUsersRegex);
	}

	public void modifyAuthorisedUsers(String channel, String authorisedUsersRegex) throws Exception
	{
		this.privilegedPortOUT.modifyAuthorisedUsers(this.receptionPortIN.getPortURI(), channel, authorisedUsersRegex);
	}

	public void destroyChannel(String channel) throws Exception
	{
		this.privilegedPortOUT.destroyChannel(this.receptionPortIN.getPortURI(), channel);
	}

	public void destroyChannelNow(String channel) throws Exception
	{
		this.privilegedPortOUT.destroyChannelNow(this.receptionPortIN.getPortURI(), channel);
	}


	public void receive(String channel, MessageI message)
	{
		this.traceMessage(
			"Client " + this.getReflectionInboundPortURI()
				+ " received on " + channel + " payload=" + message.getPayload()
				+ " properties=" + java.util.Arrays.toString(message.getProperties())
				+ " timestamp=" + message.getTimeStamp() + "\n");
	}

	public void receive(String channel, MessageI[] messages)
	{
		this.traceMessage(
			"Client " + this.getReflectionInboundPortURI()
				+ " received batch(" + (messages == null ? 0 : messages.length)
				+ ") on " + channel + "\n");
		if (messages != null) {
			for (MessageI m : messages) {
				receive(channel, m);
			}
		}
	}
	
}
