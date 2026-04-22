package fr.sorbonne_u.cps.pubsub.base.ports;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;
import fr.sorbonne_u.cps.pubsub.base.components.Client;
import fr.sorbonne_u.cps.pubsub.base.components.PluginClient;
import fr.sorbonne_u.cps.pubsub.plugins.ClientSubscriptionPlugin;

/**
 * Inbound port used by the broker to deliver messages to a client.
 * Works with both the legacy {@link Client} and the plugin-based {@link PluginClient}.
 *
 * @author Bogdan Styn
 */
public class ClientInboundPort extends AbstractInboundPort implements ReceivingCI {

	public ClientInboundPort(ComponentI owner) throws Exception {
		super(ReceivingCI.class, owner);
        this.pluginUri = null;
    }
	// With Plugin
	public ClientInboundPort(ComponentI owner, String pluginURI) throws Exception {
		super(pluginURI, ReceivingCI.class, owner);
		this.pluginUri =pluginURI;
	}
	private final String pluginUri;

	@Override
	public void receive(String channel, MessageI message) throws Exception {
		System.out.println("[ClientInboundPort] receive called on channel="
				+ channel + " owner=" + this.getOwner().getReflectionInboundPortURI());

		System.out.println("[ClientInboundPort] pluginURI=" + this.pluginUri);

		if (this.pluginUri != null) {
			System.out.println("[ClientInboundPort] about to handleRequest");
			try {
				this.getOwner().handleRequest(
						new AbstractComponent.AbstractService<Void>(this.pluginUri) {
							@Override
							public Void call() throws Exception {
								System.out.println("[ClientInboundPort] inside call()");
								Object ref = this.getServiceProviderReference();
								System.out.println("[ClientInboundPort] ref=" + ref);
								((ClientSubscriptionPlugin) ref).receive(channel, message);
								System.out.println("[ClientInboundPort] receive done");
								return null;
							}
						}
				);
				System.out.println("[ClientInboundPort] handleRequest returned");
			} catch (Exception e) {
				System.out.println("[ClientInboundPort] handleRequest EXCEPTION: " + e);
				e.printStackTrace();
			}
		} else {
			System.out.println("[ClientInboundPort] no pluginURI — legacy path");
			this.getOwner().handleRequest(
					o -> { ((Client) o).receive(channel, message); return null; }
			);
		}
	}

	@Override
	public void receive(String channel, MessageI[] messages) throws Exception
	{
		if (messages != null) {
			for (MessageI m : messages) {
				receive(channel, m);
			}
		}
	}

}
