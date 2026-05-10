package fr.sorbonne_u.cps.pubsub.base.ports;


import java.rmi.RemoteException;
import java.util.ArrayList;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;

/**
 * Port inbound exposant le service de publication du broker via l'interface
 * {@link PublishingCI}.
 *
 * <p><strong>Propriétaire</strong> : {@link Broker} (cast effectué dans
 * chaque méthode pour récupérer le composant concret).</p>
 *
 * <p>
 * chaque méthode void route l'appel broker effectif via
 * {@link Broker#getReceptionExecutorIndex()} dans
 * {@link fr.sorbonne_u.components.AbstractComponent#runTask(int, fr.sorbonne_u.components.ComponentI.ComponentTask)},
 * de sorte que la thread RMI rend la main immédiatement. Toute exception
 * levée dans la lambda (y compris les violations de précondition CDC §C.2)
 * est logguée sur le tracer du broker plutôt que propagée — la publication
 * est fire-and-forget (CDC §3.5).
 * </p>
 *
 * <p>
 * la convention du projet est respectée — les exceptions métier
 * déclarées sur la CI sont propagées telles quelles ; toute autre
 * {@link Exception} technique est encapsulée dans une
 * {@link RemoteException}.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class BrokerPublishingInboundPort extends AbstractInboundPort implements PublishingCI {

	/** Constructeur sans URI explicite — owner doit être un {@link Broker}. */
	public BrokerPublishingInboundPort(ComponentI owner) throws Exception {
		super(PublishingCI.class, owner);

	}
	/**
	 * Constructeur exposant une CI plus spécifique (utilisé par les
	 * sous-classes comme {@link BrokerPrivilegedInboundPort}).
	 */
	public BrokerPublishingInboundPort(Class c, ComponentI owner) throws Exception{
		super (c, owner);
	}

	/**
	 * Constructeur à URI explicite : permet au broker de choisir
	 * une URI déterministe dérivée de son URI de réflexion.
	 */
	public BrokerPublishingInboundPort(String uri, ComponentI owner) throws Exception {
		super(uri, PublishingCI.class, owner);
	}

	/**
	 * Constructeur à URI explicite + interface utilisé par les
	 * sous-classes (par exemple {@link BrokerPrivilegedInboundPort}) qui
	 * souhaitent une URI déterministe tout en déclarant une CI plus spécifique.
	 */
	public BrokerPublishingInboundPort(String uri, Class c, ComponentI owner) throws Exception {
		super(uri, c, owner);
	}

	/** @see PublishingCI#publish(String, String, MessageI) */
	@Override
	public void publish(String receptionPortURI, String channel, MessageI message)
		throws Exception
	{
		try {
			final Broker broker = (Broker) this.getOwner();
			broker.runTask(broker.getReceptionExecutorIndex(), o -> {
				try {
					((Broker) o).publish(receptionPortURI, channel, message);
				} catch (Exception e) {
					((Broker) o).logMessage("[publish async] " + e);
				}
			});
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see PublishingCI#publish(String, String, ArrayList) */
	@Override
	public void publish(
		String receptionPortURI,
		String channel,
		ArrayList<MessageI> messages
		) throws Exception
	{
		try {
			final Broker broker = (Broker) this.getOwner();
			broker.runTask(broker.getReceptionExecutorIndex(), o -> {
				try {
					((Broker) o).publish(receptionPortURI, channel, messages);
				} catch (Exception e) {
					((Broker) o).logMessage("[publish(bulk) async] " + e);
				}
			});
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
			final Broker broker = (Broker) this.getOwner();
			broker.runTask(broker.getReceptionExecutorIndex(), o -> {
				try {
					((Broker) o).asyncPublishAndNotify(
							receptionPortURI, channel, message, notificationInbounhdPortURI);
				} catch (Exception e) {
					((Broker) o).logMessage("[asyncPublishAndNotify async] " + e);
				}
			});
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
			final Broker broker = (Broker) this.getOwner();
			broker.runTask(broker.getReceptionExecutorIndex(), o -> {
				try {
					((Broker) o).asyncPublishAndNotify(
							receptionPortURI, channel, messages, notificationInbounhdPortURI);
				} catch (Exception e) {
					((Broker) o).logMessage("[asyncPublishAndNotify(bulk) async] " + e);
				}
			});
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
