package fr.sorbonne_u.cps.pubsub.base.connectors;

import java.rmi.RemoteException;

import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyExistingChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.ChannelQuotaExceededException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;

/**
 * Connecteur reliant un port outbound client {@link PrivilegedClientCI} au
 * port inbound {@link PrivilegedClientCI} du broker.
 *
 * <p><strong>Sens du raccordement</strong> : la référence {@link #offering}
 * pointe vers le port inbound du broker qui offre {@link PrivilegedClientCI}.
 * Les opérations de publication sont héritées de {@link PublishingConnector}
 * (Phase D.2) : {@link PrivilegedClientCI} étend
 * {@link fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI}, donc le
 * connecteur privilégié est-un connecteur de publication.</p>
 *
 * <p>
 * Phase D.5 : les exceptions métier déclarées sur la CI sont propagées
 * telles quelles ; toute autre {@link Exception} technique est encapsulée
 * dans une {@link RemoteException}.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class PrivilegedClientConnector extends PublishingConnector
	implements PrivilegedClientCI {
	/** @see PrivilegedClientCI#hasCreatedChannel(String, String) */
	@Override
	public boolean hasCreatedChannel(String receptionPortURI, String channel)
			throws Exception {
		try {
			return ((PrivilegedClientCI) this.offering).hasCreatedChannel(receptionPortURI, channel);
		} catch (UnknownClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see PrivilegedClientCI#channelQuotaReached(String) */
	@Override
	public boolean channelQuotaReached(String receptionPortURI) throws Exception {
		try {
			return ((PrivilegedClientCI) this.offering).channelQuotaReached(receptionPortURI);
		} catch (UnknownClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see PrivilegedClientCI#createChannel(String, String, String) */
	@Override
	public void createChannel(String receptionPortURI, String channel, String autorisedUsers)
			throws Exception {
		try {
			((PrivilegedClientCI) this.offering).createChannel(receptionPortURI, channel, autorisedUsers);
		} catch (UnknownClientException | AlreadyExistingChannelException
				| ChannelQuotaExceededException | UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see PrivilegedClientCI#modifyAuthorisedUsers(String, String, String) */
	@Override
	public void modifyAuthorisedUsers(String receptionPortURI, String channel, String autorisedUsers)
			throws Exception {
		try {
			((PrivilegedClientCI) this.offering).modifyAuthorisedUsers(receptionPortURI, channel, autorisedUsers);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see PrivilegedClientCI#destroyChannel(String, String) */
	@Override
	public void destroyChannel(String receptionPortURI, String channel) throws Exception {
		try {
			((PrivilegedClientCI) this.offering).destroyChannel(receptionPortURI, channel);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see PrivilegedClientCI#destroyChannelNow(String, String) */
	@Override
	public void destroyChannelNow(String receptionPortURI, String channel) throws Exception {
		try {
			((PrivilegedClientCI) this.offering).destroyChannelNow(receptionPortURI, channel);
		} catch (UnknownClientException | UnknownChannelException
				| UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
