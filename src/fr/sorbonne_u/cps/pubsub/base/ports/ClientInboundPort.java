package fr.sorbonne_u.cps.pubsub.base.ports;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;
import fr.sorbonne_u.cps.pubsub.base.components.Client;
import fr.sorbonne_u.cps.pubsub.base.components.PluginClient;

/**
 * Inbound port used by the broker to deliver messages to a client.
 * Works with both the legacy {@link Client} and the plugin-based {@link PluginClient}.
 *
 * @author Bogdan Styn
 */
public class ClientInboundPort extends AbstractInboundPort implements ReceivingCI {
	
	

	public ClientInboundPort(ComponentI owner) throws Exception {
		super(ReceivingCI.class, owner);
	}

	@Override
	public void receive(String channel, MessageI message) throws RemoteException
	{
		try {
			if (this.getOwner() instanceof Client) {
				this.getOwner().runTask(o -> ((Client) o).receive(channel, message));
			} else if (this.getOwner() instanceof PluginClient) {
				this.getOwner().runTask(o -> ((PluginClient) o).onReceive(channel, message));
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
				this.getOwner().runTask(o -> ((Client) o).receive(channel, messages));
			} else if (this.getOwner() instanceof PluginClient) {
				this.getOwner().runTask(o -> {
					PluginClient pc = (PluginClient) o;
					if (messages != null) {
						for (MessageI m : messages) {
							pc.onReceive(channel, m);
						}
					} else {
						pc.onReceive(channel, null);
					}
				});
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
