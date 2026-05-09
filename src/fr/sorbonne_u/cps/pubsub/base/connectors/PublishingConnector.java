package fr.sorbonne_u.cps.pubsub.base.connectors;


import java.rmi.RemoteException;
import java.util.ArrayList;


import fr.sorbonne_u.components.connectors.AbstractConnector;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;

/**
 * Connector used by clients to call the broker publishing service.
 *
 * <p>
 * Phase D.5: technical exceptions raised on the offering side are wrapped
 * in {@link RemoteException} (the CI declares {@code throws Exception}
 * only and {@link PublishingCI} carries no business exceptions).
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class PublishingConnector extends AbstractConnector implements PublishingCI {

	@Override
	public void publish(String receptionPortURI, String channel, MessageI message) throws Exception {
		try {
			((PublishingCI) this.offering).publish(receptionPortURI, channel, message);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void publish(String receptionPortURI, String channel, ArrayList<MessageI> messages) throws Exception {
		try {
			((PublishingCI) this.offering).publish(receptionPortURI, channel, messages);
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
			((PublishingCI) this.offering).asyncPublishAndNotify(
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
			((PublishingCI) this.offering).asyncPublishAndNotify(
					receptionPortURI, channel, messages, notificationInbounhdPortURI);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
