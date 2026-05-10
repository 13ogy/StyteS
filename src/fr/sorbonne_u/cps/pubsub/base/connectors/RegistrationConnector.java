package fr.sorbonne_u.cps.pubsub.base.connectors;


import java.rmi.RemoteException;

import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.exceptions.NotSubscribedChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;

import fr.sorbonne_u.components.connectors.AbstractConnector;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;

/**
 * Connecteur reliant un port outbound client {@link RegistrationCI} au
 * port inbound {@link RegistrationCI} du broker.
 *
 * <p><strong>Sens du raccordement</strong> : la CI requise par le client
 * (offerte par le broker) est {@link RegistrationCI}. La référence
 * {@link #offering} pointe vers le port inbound du broker.</p>
 *
 * <p>
 * les exceptions métier déclarées sur la CI sont propagées
 * telles quelles ; toute autre {@link Exception} technique est encapsulée
 * dans une {@link RemoteException}.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class RegistrationConnector extends AbstractConnector implements RegistrationCI {

	/** @see RegistrationCI#registered(String) */
	@Override
	public boolean registered(String receptionPortURI) throws Exception {
		try {
			return ((RegistrationCI) this.offering).registered(receptionPortURI);
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
			return ((RegistrationCI) this.offering).registered(receptionPortURI, rc);
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
			return ((RegistrationCI) this.offering).register(receptionPortURI, rc);
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
	public String modifyServiceClass(String receptionPortURI, RegistrationClass rc) throws Exception {
		try {
			return ((RegistrationCI) this.offering).modifyServiceClass(receptionPortURI, rc);
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
			((RegistrationCI) this.offering).unregister(receptionPortURI);
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
			return ((RegistrationCI) this.offering).channelExist(channel);
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
			return ((RegistrationCI) this.offering).channelAuthorised(receptionPortURI, channel);
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
	public boolean subscribed(String receptionPortURI, String channel) throws Exception {
		try {
			return ((RegistrationCI) this.offering).subscribed(receptionPortURI, channel);
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
	public void subscribe(String receptionPortURI, String channel, MessageFilterI filter) throws Exception {
		try {
			((RegistrationCI) this.offering).subscribe(receptionPortURI, channel, filter);
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
			((RegistrationCI) this.offering).unsubscribe(receptionPortURI, channel);
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
	public boolean modifyFilter(String receptionPortURI, String channel, MessageFilterI filter)
		throws Exception
	{
		try {
			return ((RegistrationCI) this.offering).modifyFilter(receptionPortURI, channel, filter);
		} catch (UnknownClientException | UnknownChannelException | NotSubscribedChannelException e) {
			throw e;
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
