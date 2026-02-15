package fr.sorbonne_u.cps.pubsub.base.connectors;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.connectors.AbstractConnector;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;

public class BrokerClientReceivingConnector extends AbstractConnector implements ReceivingCI {

	@Override
	public void receive(String channel, MessageI message) throws RemoteException {
		try {
			((ReceivingCI) this.offering).receive(channel, message);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void receive(String channel, MessageI[] messages) throws RemoteException {
		try {
			((ReceivingCI) this.offering).receive(channel, messages);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
