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
 * <p>
 * Phase D.3: delivery callbacks are dispatched on the client's default
 * executor through {@link AbstractComponent#runTask(fr.sorbonne_u.components.ComponentI.ComponentTask)
 * runTask}. The RMI dispatch thread (and, transitively, the broker's
 * delivery executor that opened the call) returns immediately, so a
 * slow client cannot back-pressure the broker. Exceptions raised by the
 * client-side callback are logged on the client tracer.
 * </p>
 *
 * <p>
 * Phase D.5: technical exceptions are wrapped in {@link RemoteException}
 * (the CI declares {@code throws Exception} only).
 * </p>
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
		try {
			if (this.pluginUri != null) {
				this.getOwner().runTask(
						new AbstractComponent.AbstractTask(this.pluginUri) {
							@Override
							public void run() {
								try {
									((ClientRegistrationPlugin) this.getTaskProviderReference())
											.receive(channel, message);
								} catch (Exception e) {
									((AbstractComponent) this.getTaskOwner()).logMessage(
											"[ReceivingInboundPort plugin async] " + e);
								}
							}
						});
			} else {
				this.getOwner().runTask(o -> {
					try {
						((Client) o).receive(channel, message);
					} catch (Exception e) {
						((AbstractComponent) o).logMessage(
								"[ReceivingInboundPort client async] " + e);
					}
				});
			}
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
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
