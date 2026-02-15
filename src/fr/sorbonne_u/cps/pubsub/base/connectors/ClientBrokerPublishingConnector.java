package fr.sorbonne_u.cps.pubsub.base.connectors;


import java.rmi.RemoteException;
import java.util.ArrayList;


import fr.sorbonne_u.components.connectors.AbstractConnector;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;

public class ClientBrokerPublishingConnector extends AbstractConnector implements PublishingCI {

	@Override
	public void publish(String receptionPortURI, String channel, MessageI message) throws RemoteException {
		try {
			((PublishingCI) this.offering).publish(receptionPortURI, channel, message);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void publish(String receptionPortURI, String channel, ArrayList<MessageI> messages) throws RemoteException {
		try {
			((PublishingCI) this.offering).publish(receptionPortURI, channel, messages);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
