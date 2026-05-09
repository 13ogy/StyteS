package fr.sorbonne_u.cps.pubsub.base.ports;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyExistingChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.ChannelQuotaExceededException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;

import java.rmi.RemoteException;

/**
 * Port outbound utilisé par un composant client pour appeler les opérations
 * de gestion de canaux privilégiés du broker via {@link PrivilegedClientCI}.
 *
 * <p><strong>Propriétaire</strong> : composant client (porteur du
 * {@link fr.sorbonne_u.cps.pubsub.plugins.ClientPrivilegedPlugin}).</p>
 *
 * <p>
 * Les méthodes délèguent au connecteur via {@link #getConnector()}.
 * Les opérations de publication ({@code publish}, {@code asyncPublishAndNotify})
 * sont héritées de {@link PublishingOutboundPort} (Phase D.2) :
 * {@link PrivilegedClientCI} étend
 * {@link fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI}, donc le port
 * outbound privilégié est-un port outbound de publication.
 * </p>
 *
 * <p>
 * Phase D.5 : les exceptions métier déclarées sur la CI sont propagées
 * telles quelles ; toute autre {@link Exception} technique est encapsulée
 * dans une {@link RemoteException}.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class PrivilegedClientOutboundPort extends PublishingOutboundPort implements PrivilegedClientCI
{
	/** Constructeur utilisé par les composants clients. */
	public PrivilegedClientOutboundPort(ComponentI owner) throws Exception
	{
		super(PrivilegedClientCI.class, owner);
	}

	/** @see PrivilegedClientCI#hasCreatedChannel(String, String) */
	@Override
	public boolean hasCreatedChannel(String receptionPortURI, String channel) throws RemoteException
	{
		try {
			return ((PrivilegedClientCI) this.getConnector()).hasCreatedChannel(receptionPortURI, channel);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see PrivilegedClientCI#channelQuotaReached(String) */
	@Override
	public boolean channelQuotaReached(String receptionPortURI) throws RemoteException
	{
		try {
			return ((PrivilegedClientCI) this.getConnector()).channelQuotaReached(receptionPortURI);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see PrivilegedClientCI#createChannel(String, String, String) */
	@Override
	public void createChannel(String receptionPortURI, String channel, String autorisedUsers)
		throws RemoteException, AlreadyExistingChannelException, ChannelQuotaExceededException
	{
		try {
			((PrivilegedClientCI) this.getConnector()).createChannel(receptionPortURI, channel, autorisedUsers);
		} catch (AlreadyExistingChannelException | ChannelQuotaExceededException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see PrivilegedClientCI#modifyAuthorisedUsers(String, String, String) */
	@Override
	public void modifyAuthorisedUsers(String receptionPortURI, String channel, String autorisedUsers) throws RemoteException
	{
		try {
			((PrivilegedClientCI) this.getConnector()).modifyAuthorisedUsers(receptionPortURI, channel, autorisedUsers);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see PrivilegedClientCI#destroyChannel(String, String) */
	@Override
	public void destroyChannel(String receptionPortURI, String channel) throws RemoteException
	{
		try {
			((PrivilegedClientCI) this.getConnector()).destroyChannel(receptionPortURI, channel);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see PrivilegedClientCI#destroyChannelNow(String, String) */
	@Override
	public void destroyChannelNow(String receptionPortURI, String channel) throws RemoteException
	{
		try {
			((PrivilegedClientCI) this.getConnector()).destroyChannelNow(receptionPortURI, channel);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	// -------------------------------------------------------------------------
	// PublishingCI publish/asyncPublishAndNotify are inherited from
	// PublishingOutboundPort (Phase D.2): PrivilegedClientCI extends PublishingCI
	// so the privileged outbound port is-a publishing outbound port.
	// -------------------------------------------------------------------------
}
