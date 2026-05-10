package fr.sorbonne_u.cps.pubsub.base.ports;


import java.rmi.RemoteException;

import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.exceptions.NotSubscribedChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractOutboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;

/**
 * Port outbound utilisé par un composant client pour invoquer le service
 * d'enregistrement du broker via l'interface {@link RegistrationCI}.
 *
 * <p><strong>Propriétaire</strong> : le {@link
 * fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin} installé sur le
 * composant client.</p>
 *
 * <p>
 * les exceptions métier déclarées sur la CI sont propagées
 * telles quelles ; toute autre {@link Exception} technique est encapsulée
 * dans une {@link RemoteException}.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class ClientRegistrationOutboundPort extends AbstractOutboundPort implements RegistrationCI{

	/** Constructeur — owner = composant client qui détient ce port. */
	public ClientRegistrationOutboundPort(ComponentI owner) throws Exception {
		super(RegistrationCI.class, owner);

	}


	/** @see RegistrationCI#registered(String) */
	@Override
	public boolean registered(String receptionPortURI) throws Exception {
		try {
			return ((RegistrationCI) this.getConnector()).registered(receptionPortURI);
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see RegistrationCI#registered(String, RegistrationClass) */
	@Override
	public boolean registered(String receptionPortURI, RegistrationClass rc) throws Exception {
		try {
			return ((RegistrationCI) this.getConnector()).registered(receptionPortURI, rc);
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see RegistrationCI#register(String, RegistrationClass) */
	@Override
	public String register(String receptionPortURI, RegistrationClass rc) throws Exception {
		try {
			return ((RegistrationCI) this.getConnector()).register(receptionPortURI, rc);
		} catch (AlreadyRegisteredException e) {
			throw e;
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see RegistrationCI#modifyServiceClass(String, RegistrationClass) */
	@Override
	public String modifyServiceClass(String receptionPortURI, RegistrationClass rc) throws Exception, AlreadyRegisteredException {
		try {
			return ((RegistrationCI) this.getConnector()).modifyServiceClass(receptionPortURI, rc);
		} catch (UnknownClientException | AlreadyRegisteredException e) {
			throw e;
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see RegistrationCI#unregister(String) */
	@Override
	public void unregister(String receptionPortURI) throws Exception {
		try {
			((RegistrationCI) this.getConnector()).unregister(receptionPortURI);
		} catch (UnknownClientException e) {
			throw e;
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see RegistrationCI#channelExist(String) */
	@Override
	public boolean channelExist(String channel) throws Exception {
		try {
			return ((RegistrationCI) this.getConnector()).channelExist(channel);
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see RegistrationCI#channelAuthorised(String, String) */
	@Override
	public boolean channelAuthorised(String receptionPortURI, String channel) throws Exception {
		try {
			return ((RegistrationCI) this.getConnector()).channelAuthorised(receptionPortURI, channel);
		} catch (UnknownClientException | UnknownChannelException e) {
			throw e;
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see RegistrationCI#subscribed(String, String) */
	@Override
	public boolean subscribed(String receptionPortURI, String channel) throws Exception{
		try {
			return ((RegistrationCI) this.getConnector()).subscribed(receptionPortURI, channel);
		} catch (UnknownClientException | UnknownChannelException e) {
			throw e;
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see RegistrationCI#subscribe(String, String, MessageFilterI) */
	@Override
	public void subscribe(String receptionPortURI, String channel, MessageFilterI filter) throws Exception{
		try {
			((RegistrationCI) this.getConnector()).subscribe(receptionPortURI, channel, filter);
		} catch (UnknownClientException | UnknownChannelException e) {
			throw e;
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see RegistrationCI#unsubscribe(String, String) */
	@Override
	public void unsubscribe(String receptionPortURI, String channel) throws Exception {
		try {
			((RegistrationCI) this.getConnector()).unsubscribe(receptionPortURI, channel);
		} catch (UnknownClientException | UnknownChannelException | NotSubscribedChannelException e) {
			throw e;
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see RegistrationCI#modifyFilter(String, String, MessageFilterI) */
	@Override
	public boolean modifyFilter(String receptionPortURI, String channel, MessageFilterI filter) throws Exception {
		try {
			return ((RegistrationCI) this.getConnector()).modifyFilter(receptionPortURI, channel, filter);
		} catch (UnknownClientException | UnknownChannelException | NotSubscribedChannelException e) {
			throw e;
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

}
