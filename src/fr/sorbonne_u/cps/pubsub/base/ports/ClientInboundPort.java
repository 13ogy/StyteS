package fr.sorbonne_u.cps.pubsub.base.ports;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;

public class ClientInboundPort extends AbstractInboundPort implements ReceivingCI {
	
	

	public ClientInboundPort(ComponentI owner) throws Exception {
		super(ReceivingCI.class, owner);
	}

	@Override
	public void receive(String channel, MessageI message) throws RemoteException
	{
		try {
			((fr.sorbonne_u.cps.pubsub.base.components.Client) this.getOwner())
				.receive(channel, message);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void receive(String channel, MessageI[] messages) throws RemoteException
	{
		try {
			((fr.sorbonne_u.cps.pubsub.base.components.Client) this.getOwner())
				.receive(channel, messages);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

}
