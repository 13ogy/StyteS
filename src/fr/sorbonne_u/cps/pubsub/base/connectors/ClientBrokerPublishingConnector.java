package fr.sorbonne_u.cps.pubsub.base.connectors;


import java.rmi.RemoteException;
import java.util.ArrayList;


import fr.sorbonne_u.components.connectors.AbstractConnector;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;

/**
 * Connecteur reliant un port outbound client {@link PublishingCI} au
 * port inbound {@link PublishingCI} du broker.
 *
 * <p><strong>Sens du raccordement</strong> : la référence {@link #offering}
 * pointe vers le port inbound du broker qui offre {@link PublishingCI}.</p>
 *
 * <p>
 * Phase D.5 : les exceptions techniques levées côté offering sont
 * encapsulées dans {@link RemoteException} (la CI ne déclare que
 * {@code throws Exception} et {@link PublishingCI} ne porte aucune
 * exception métier).
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class ClientBrokerPublishingConnector extends AbstractConnector implements PublishingCI {

	/** @see PublishingCI#publish(String, String, MessageI) */
	@Override
	public void publish(String receptionPortURI, String channel, MessageI message) throws Exception {
		try {
			((PublishingCI) this.offering).publish(receptionPortURI, channel, message);
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see PublishingCI#publish(String, String, ArrayList) */
	@Override
	public void publish(String receptionPortURI, String channel, ArrayList<MessageI> messages) throws Exception {
		try {
			((PublishingCI) this.offering).publish(receptionPortURI, channel, messages);
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	// -------------------------------------------------------------------------
	// PublishingCI async (added in latest interface)
	// -------------------------------------------------------------------------

	/** @see PublishingCI#asyncPublishAndNotify(String, String, MessageI, String) */
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
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see PublishingCI#asyncPublishAndNotify(String, String, ArrayList, String) */
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
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
