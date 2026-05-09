package fr.sorbonne_u.cps.pubsub.base.ports;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;
import fr.sorbonne_u.cps.pubsub.base.components.Client;
import fr.sorbonne_u.cps.pubsub.base.components.PluginClient;
import fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin;

/**
 * Inbound port used by the broker to deliver messages to a client.
 * Works with both the legacy {@link Client} and the plugin-based {@link PluginClient}.
 *
 * @author Bogdan Styn
 */
public class ReceivingInboundPort extends AbstractInboundPort implements ReceivingCI {

	public ReceivingInboundPort(ComponentI owner) throws Exception {
		super(ReceivingCI.class, owner);
        this.pluginUri = null;
    }
	// With Plugin
	public ReceivingInboundPort(ComponentI owner, String pluginURI) throws Exception {
		super(pluginURI, ReceivingCI.class, owner);
		this.pluginUri =pluginURI;
	}
	private final String pluginUri;

	@Override
	public void receive(String channel, MessageI message) throws Exception {

		if (this.pluginUri != null) {
			try {
				this.getOwner().handleRequest(
						new AbstractComponent.AbstractService<Void>(this.pluginUri) {
							@Override
							public Void call() throws Exception {
								Object ref = this.getServiceProviderReference();
								((ClientRegistrationPlugin) ref).receive(channel, message);
								return null;
							}
						}
				);
				System.out.println("[ReceivingInboundPort] handleRequest returned");
			} catch (Exception e) {
				System.out.println("[ReceivingInboundPort] handleRequest EXCEPTION: " + e);
				e.printStackTrace();
			}
		} else {
			System.out.println("[ReceivingInboundPort] no pluginURI — legacy path");
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
