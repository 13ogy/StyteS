package fr.sorbonne_u.cps.pubsub.base.ports;

import java.rmi.RemoteException;
import java.util.ArrayList;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;

public class BrokerPublishingInboundPort extends AbstractInboundPort implements PublishingCI {

	public BrokerPublishingInboundPort(ComponentI owner) throws Exception {
		super(PublishingCI.class, owner);

	}

	@Override
	public void publish(String receptionPortURI, String channel, MessageI message)
		throws RemoteException
	{
		try {
			((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.publish(receptionPortURI, channel, message);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void publish(
		String receptionPortURI,
		String channel,
		ArrayList<MessageI> messages
		) throws RemoteException
	{
		try {
			((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.publish(receptionPortURI, channel, messages);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
