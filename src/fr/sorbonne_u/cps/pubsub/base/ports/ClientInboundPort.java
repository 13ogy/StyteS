package fr.sorbonne_u.cps.pubsub.base.ports;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;
import fr.sorbonne_u.cps.pubsub.base.components.Client;
import fr.sorbonne_u.cps.pubsub.base.components.PluginClient;

public class ClientInboundPort extends AbstractInboundPort implements ReceivingCI {
	
	

	public ClientInboundPort(ComponentI owner) throws Exception {
		super(ReceivingCI.class, owner);
	}

	@Override
	public void receive(String channel, MessageI message) throws RemoteException
	{
		try {
			if (this.getOwner() instanceof Client) {
				((Client) this.getOwner()).receive(channel, message);
			} else if (this.getOwner() instanceof PluginClient) {
				((PluginClient) this.getOwner()).onReceive(channel, message);
			} else {
				throw new IllegalStateException(
					"ClientInboundPort owner must be Client or PluginClient, got "
						+ this.getOwner().getClass().getCanonicalName());
			}
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void receive(String channel, MessageI[] messages) throws RemoteException
	{
		try {
			if (this.getOwner() instanceof Client) {
				((Client) this.getOwner()).receive(channel, messages);
			} else if (this.getOwner() instanceof PluginClient) {
				((PluginClient) this.getOwner()).onReceive(channel, messages != null && messages.length > 0 ? messages[0] : null);
			} else {
				throw new IllegalStateException(
					"ClientInboundPort owner must be Client or PluginClient, got "
						+ this.getOwner().getClass().getCanonicalName());
			}
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

}
