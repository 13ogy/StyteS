package fr.sorbonne_u.cps.pubsub.base.ports;

import java.rmi.RemoteException;
import java.util.ArrayList;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractOutboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;

/**
 * Port outbound utilisé par un composant client pour appeler le service de
 * publication du broker (interface {@link PublishingCI}).
 *
 * <p><strong>Propriétaire</strong> : composant client (par exemple
 * {@link fr.sorbonne_u.cps.pubsub.base.components.Client} ou un porteur du
 * {@link fr.sorbonne_u.cps.pubsub.plugins.ClientPublicationPlugin}).</p>
 *
 * <p>
 * Phase D.5 : les exceptions techniques levées par le connecteur sont
 * encapsulées dans une {@link RemoteException} ; {@link PublishingCI} ne
 * déclare aucune exception métier sur ses méthodes, le wrap-tout suffit.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class ClientPublishingOutboundPort extends AbstractOutboundPort implements PublishingCI {

	/** Constructeur utilisé par les composants clients. */
	public ClientPublishingOutboundPort(ComponentI owner)
			throws Exception {
		super(PublishingCI.class, owner);
	}

	/**
	 * Constructeur exposant une CI plus spécifique (utilisé par les
	 * sous-classes comme {@link ClientPrivilegedOutboundPort}).
	 */
	public ClientPublishingOutboundPort(Class c, ComponentI owner) throws Exception{
		super(c, owner);
	}


	/** @see PublishingCI#publish(String, String, MessageI) */
	@Override
	public void publish(String receptionPortURI, String channel, MessageI message) throws Exception {
		try {
			((PublishingCI) this.getConnector()).publish(receptionPortURI, channel, message);
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
			((PublishingCI) this.getConnector()).publish(receptionPortURI, channel, messages);
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
			((PublishingCI) this.getConnector()).asyncPublishAndNotify(
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
			((PublishingCI) this.getConnector()).asyncPublishAndNotify(
					receptionPortURI, channel, messages, notificationInbounhdPortURI);
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
