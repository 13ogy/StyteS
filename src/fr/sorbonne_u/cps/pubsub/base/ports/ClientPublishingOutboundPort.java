package fr.sorbonne_u.cps.pubsub.base.ports;

import java.rmi.RemoteException;
import java.util.ArrayList;

import fr.sorbonne_u.cps.pubsub.base.connectors.ClientBrokerPublishingConnector;
import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractOutboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;

public class ClientPublishingOutboundPort extends AbstractOutboundPort implements PublishingCI {

	public ClientPublishingOutboundPort(ComponentI owner)
			throws Exception {
		super(PublishingCI.class, owner);
		this.connecteur= (ClientBrokerPublishingConnector) this.getConnector();
	}

	private ClientBrokerPublishingConnector connecteur;
	
	@Override
	public void publish(String receptionPortURI, String channel, MessageI message) throws RemoteException {
		try {
			((PublishingCI) this.getConnector()).publish(receptionPortURI, channel, message);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void publish(String receptionPortURI, String channel, ArrayList<MessageI> messages) throws RemoteException {
		try {
			((PublishingCI) this.getConnector()).publish(receptionPortURI, channel, messages);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
	
	



}
