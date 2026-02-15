package fr.sorbonne_u.cps.pubsub.base.ports;

import java.rmi.RemoteException;

import fr.sorbonne_u.cps.pubsub.base.connectors.BrokerClientReceivingConnector;
import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.interfaces.RequiredCI;
import fr.sorbonne_u.components.ports.AbstractOutboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;

public class BrokerReceptionOutboundPort extends AbstractOutboundPort implements ReceivingCI {

	public BrokerReceptionOutboundPort(ComponentI owner) throws Exception {
		super(ReceivingCI.class, owner);
	}

	@Override
	public void receive(String channel, MessageI message) throws RemoteException {
		try {
			((ReceivingCI) this.getConnector()).receive(channel, message);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void receive(String channel, MessageI[] messages) throws RemoteException {
		try {
			((ReceivingCI) this.getConnector()).receive(channel, messages);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

}
