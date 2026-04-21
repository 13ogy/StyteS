package fr.sorbonne_u.cps.pubsub.base.ports;

import java.rmi.RemoteException;
import java.util.ArrayList;

import fr.sorbonne_u.cps.pubsub.base.connectors.ClientBrokerPublishingConnector;
import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractOutboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;

/**
 * Outbound port used by clients to access the broker publishing service.
 *
 * @author Bogdan Styn
 */
public class ClientPublishingOutboundPort extends AbstractOutboundPort implements PublishingCI {

	public ClientPublishingOutboundPort(ComponentI owner)
			throws Exception {
		super(PublishingCI.class, owner);
	}

	public ClientPublishingOutboundPort(Class c, ComponentI owner) throws Exception{
		super(c, owner);
	}

	
	@Override
	public void publish(String receptionPortURI, String channel, MessageI message) throws Exception {
		((PublishingCI) this.getConnector()).publish(receptionPortURI, channel, message);

	}

	@Override
	public void publish(String receptionPortURI, String channel, ArrayList<MessageI> messages) throws Exception {
		((PublishingCI) this.getConnector()).publish(receptionPortURI, channel, messages);

	}

	// -------------------------------------------------------------------------
	// PublishingCI async (added in latest interface)
	// -------------------------------------------------------------------------

	@Override
	public void asyncPublishAndNotify(
		String receptionPortURI,
		String channel,
		MessageI message,
		String notificationInbounhdPortURI
		) throws Exception
	{
		((PublishingCI) this.getConnector()).asyncPublishAndNotify(
				receptionPortURI, channel, message, notificationInbounhdPortURI);

	}

	@Override
	public void asyncPublishAndNotify(
		String receptionPortURI,
		String channel,
		ArrayList<MessageI> messages,
		String notificationInbounhdPortURI
		) throws Exception
	{

		((PublishingCI) this.getConnector()).asyncPublishAndNotify(
				receptionPortURI, channel, messages, notificationInbounhdPortURI);

	}
	
	



}
