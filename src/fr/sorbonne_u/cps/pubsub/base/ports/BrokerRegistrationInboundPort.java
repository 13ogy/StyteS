package fr.sorbonne_u.cps.pubsub.base.ports;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.exceptions.NotSubscribedChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;

import java.rmi.RemoteException;

/**
 * Port inbound exposant le service d'enregistrement et de souscription du broker via l'interface
 * {@link RegistrationCI}.
 *
 * <p><strong>Propriétaire</strong> : {@link Broker} (les méthodes castent {@code getOwner()} vers
 * ce type concret).
 *
 * <p>Convention : les exceptions métier déclarées sur la CI sont rejetées telles quelles ; toute
 * autre {@link Exception} technique est encapsulée dans une {@link RemoteException}. : les méthodes
 * void asynchrones ({@link #unregister(String)}, {@link #subscribe(String,String,MessageFilterI)},
 * {@link #unsubscribe(String,String)}) sont dispatchées sur l'executor de réception du broker pour
 * libérer immédiatement le thread RMI ; les exceptions levées dans la lambda sont logguées (le
 * contrat est asynchrone, l'appelant ne peut pas les observer).
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class BrokerRegistrationInboundPort extends AbstractInboundPort implements RegistrationCI {

	/**
	 * Constructeur sans URI explicite (BCM4Java en génère une).
	 *
	 * @param owner composant propriétaire — doit être un {@link Broker}.
	 */
	public BrokerRegistrationInboundPort(ComponentI owner) throws Exception {
		super(RegistrationCI.class, owner);
	}

	/**
	 * Crée le port inbound avec une URI explicite et déterministe (Phase C.3). Permet au broker de
	 * dériver l'URI du port d'enregistrement depuis son URI de réflexion, supprimant le besoin d'un
	 * statique global.
	 *
	 * @param uri URI à publier pour ce port.
	 * @param owner composant propriétaire — doit être un {@link Broker}.
	 */
	public BrokerRegistrationInboundPort(String uri, ComponentI owner) throws Exception {
		super(uri, RegistrationCI.class, owner);
	}

	/**
	 * @see RegistrationCI#registered(String)
	 */
	@Override
	public boolean registered(String receptionPortURI) throws Exception {
		try {
			return this.getOwner().handleRequest(o -> ((Broker) o).registered(receptionPortURI));
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/**
	 * @see RegistrationCI#registered(String, RegistrationClass)
	 */
	@Override
	public boolean registered(String receptionPortURI, RegistrationClass rc) throws Exception {
		try {
			return this.getOwner()
					.handleRequest(o -> ((Broker) o).registered(receptionPortURI, rc));
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/**
	 * @see RegistrationCI#register(String, RegistrationClass)
	 */
	@Override
	public String register(String receptionPortURI, RegistrationClass rc) throws Exception {
		try {
			return this.getOwner().handleRequest(o -> ((Broker) o).register(receptionPortURI, rc));
		} catch (AlreadyRegisteredException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/**
	 * @see RegistrationCI#modifyServiceClass(String, RegistrationClass)
	 */
	@Override
	public String modifyServiceClass(String receptionPortURI, RegistrationClass rc)
			throws Exception {
		try {
			return this.getOwner()
					.handleRequest(o -> ((Broker) o).modifyServiceClass(receptionPortURI, rc));
		} catch (UnknownClientException | AlreadyRegisteredException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/**
	 * Désenregistrement asynchrone : dispatché sur l'executor de réception du broker pour libérer
	 * immédiatement le thread RMI.
	 *
	 * @see RegistrationCI#unregister(String)
	 */
	@Override
	public void unregister(String receptionPortURI) throws Exception {
		try {
			final Broker broker = (Broker) this.getOwner();
			broker.runTask(
					broker.getReceptionExecutorIndex(),
					o -> {
						try {
							((Broker) o).unregister(receptionPortURI);
						} catch (Exception e) {
							((Broker) o).logMessage("[unregister async] " + e);
						}
					});
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/**
	 * @see RegistrationCI#channelExist(String)
	 */
	@Override
	public boolean channelExist(String channel) throws Exception {
		try {
			return this.getOwner().handleRequest(o -> ((Broker) o).channelExist(channel));
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/**
	 * @see RegistrationCI#channelAuthorised(String, String)
	 */
	@Override
	public boolean channelAuthorised(String receptionPortURI, String channel) throws Exception {
		try {
			return this.getOwner()
					.handleRequest(o -> ((Broker) o).channelAuthorised(receptionPortURI, channel));
		} catch (UnknownClientException | UnknownChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/**
	 * @see RegistrationCI#subscribed(String, String)
	 */
	@Override
	public boolean subscribed(String receptionPortURI, String channel) throws Exception {
		try {
			return this.getOwner()
					.handleRequest(o -> ((Broker) o).subscribed(receptionPortURI, channel));
		} catch (UnknownClientException | UnknownChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/**
	 * Souscription asynchrone : dispatchée sur l'executor de réception . L'appelant ne voit pas les
	 * exceptions métier (logguées).
	 *
	 * @see RegistrationCI#subscribe(String, String, MessageFilterI)
	 */
	@Override
	public void subscribe(String receptionPortURI, String channel, MessageFilterI filter)
			throws Exception {
		try {
			final Broker broker = (Broker) this.getOwner();
			broker.runTask(
					broker.getReceptionExecutorIndex(),
					o -> {
						try {
							((Broker) o).subscribe(receptionPortURI, channel, filter);
						} catch (Exception e) {
							((Broker) o).logMessage("[subscribe async] " + e);
						}
					});
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/**
	 * Désabonnement asynchrone.
	 *
	 * @see RegistrationCI#unsubscribe(String, String)
	 */
	@Override
	public void unsubscribe(String receptionPortURI, String channel) throws Exception {
		try {
			final Broker broker = (Broker) this.getOwner();
			broker.runTask(
					broker.getReceptionExecutorIndex(),
					o -> {
						try {
							((Broker) o).unsubscribe(receptionPortURI, channel);
						} catch (Exception e) {
							((Broker) o).logMessage("[unsubscribe async] " + e);
						}
					});
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/**
	 * @see RegistrationCI#modifyFilter(String, String, MessageFilterI)
	 */
	@Override
	public boolean modifyFilter(String receptionPortURI, String channel, MessageFilterI filter)
			throws Exception {
		try {
			return this.getOwner()
					.handleRequest(
							o -> ((Broker) o).modifyFilter(receptionPortURI, channel, filter));
		} catch (UnknownClientException
				| UnknownChannelException
				| NotSubscribedChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
