package fr.sorbonne_u.cps.pubsub.base.ports;

import java.rmi.RemoteException;
import java.util.ArrayList;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractOutboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;

/**
 * Outbound port used by clients to access the broker publishing service.
 *
 * <p>
 * Phase D.5: technical exceptions raised by the connector are wrapped in
 * {@link RemoteException}; {@link PublishingCI} declares no business
 * exceptions on its methods so the wrap-all path is sufficient.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class PublishingOutboundPort extends AbstractOutboundPort implements PublishingCI {

	public PublishingOutboundPort(ComponentI owner)
			throws Exception {
		super(PublishingCI.class, owner);
	}

	public PublishingOutboundPort(Class c, ComponentI owner) throws Exception{
		super(c, owner);
	}


	@Override
	public void publish(String receptionPortURI, String channel, MessageI message) throws Exception {
		try {
			((PublishingCI) this.getConnector()).publish(receptionPortURI, channel, message);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void publish(String receptionPortURI, String channel, ArrayList<MessageI> messages) throws Exception {
		try {
			((PublishingCI) this.getConnector()).publish(receptionPortURI, channel, messages);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
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
		try {
			((PublishingCI) this.getConnector()).asyncPublishAndNotify(
					receptionPortURI, channel, message, notificationInbounhdPortURI);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void asyncPublishAndNotify(
		String receptionPortURI,
		String channel,
		ArrayList<MessageI> messages,
		String notificationInbounhdPortURI
		) throws Exception
	{
		try {
			((PublishingCI) this.getConnector()).asyncPublishAndNotify(
					receptionPortURI, channel, messages, notificationInbounhdPortURI);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
