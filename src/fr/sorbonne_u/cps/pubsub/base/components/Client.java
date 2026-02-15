package fr.sorbonne_u.cps.pubsub.base.components;

import java.rmi.RemoteException;

import fr.sorbonne_u.cps.pubsub.base.connectors.ClientBrokerPublishingConnector;
import fr.sorbonne_u.cps.pubsub.base.connectors.ClientBrokerRegistrationConnector;

import fr.sorbonne_u.cps.pubsub.base.ports.ClientInboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientPublishingOutboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientRegistrationOutboundPort;

import fr.sorbonne_u.components.AbstractComponent;

import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;

public class Client extends AbstractComponent {

	private ClientInboundPort receptionPortIN;
	private ClientPublishingOutboundPort publishingPortOUT;

	private ClientRegistrationOutboundPort registrationPortOUT;

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
		
	}

	
	public void register(RegistrationClass rc) throws Exception {
		if (this.registered) {
			// idempotent register for application-level convenience.
			return;
		}
		this.rcCurrent = rc;
		
		this.logMessage("Registering client.") ;
		
		// 1) connect to the broker registration port
		this.doPortConnection(
			registrationPortOUT.getPortURI(),
			Broker.registrationPortURI(),
			ClientBrokerRegistrationConnector.class.getCanonicalName());

		// 2) register (audit 1: FREE only)
		String portINURI = registrationPortOUT.register(receptionPortIN.getPortURI(), rc);

		// 3) connect to the broker publishing port
		this.doPortConnection(
			publishingPortOUT.getPortURI(),
			portINURI,
			ClientBrokerPublishingConnector.class.getCanonicalName());
		this.logMessage("Client registered");
		this.registered = true;
	}
	
	public void modifyServiceClass(RegistrationClass rc) throws Exception {
		
		this.logMessage("Modifying registration class");
		
		if (rc == this.rcCurrent) {
			throw new AlreadyRegisteredException();
		}

		// Audit 1 (FREE) scope: service class upgrade/downgrade is not required.
		// Keep a minimal behaviour: disconnect and reconnect publishing port if URI changes.
		this.rcCurrent = rc;

		String portINURI =
			registrationPortOUT.modifyServiceClass(receptionPortIN.getPortURI(), rc);

		this.doPortDisconnection(publishingPortOUT.getPortURI());
		this.doPortConnection(
			publishingPortOUT.getPortURI(),
			portINURI,
			ClientBrokerPublishingConnector.class.getCanonicalName());
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
