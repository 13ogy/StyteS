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
 * Outbound port used by clients to call privileged channel-management operations
 * offered by the broker through {@link PrivilegedClientCI}.
 *
 * <p>
 * Methods forward calls to the connected connector using {@link #getConnector()}.
 * Publishing operations ({@code publish}, {@code asyncPublishAndNotify}) are
 * inherited from {@link PublishingOutboundPort} since {@link PrivilegedClientCI}
 * specialises {@link fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI}; this
 * mirrors the CI specialisation in the OO port hierarchy (Phase D.2).
 * </p>
 *
 * <p>
 * Phase D.5: business exceptions declared on the CI propagate verbatim;
 * every other technical {@link Exception} is wrapped in a
 * {@link RemoteException}.
 * </p>
 *
 * @author Bogdan Styn
 */
public class PrivilegedClientOutboundPort extends PublishingOutboundPort implements PrivilegedClientCI
{
	public PrivilegedClientOutboundPort(ComponentI owner) throws Exception
	{
		super(PrivilegedClientCI.class, owner);
	}

	@Override
	public boolean hasCreatedChannel(String receptionPortURI, String channel) throws RemoteException
	{
		try {
			return ((PrivilegedClientCI) this.getConnector()).hasCreatedChannel(receptionPortURI, channel);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public boolean channelQuotaReached(String receptionPortURI) throws RemoteException
	{
		try {
			return ((PrivilegedClientCI) this.getConnector()).channelQuotaReached(receptionPortURI);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

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

	@Override
	public void modifyAuthorisedUsers(String receptionPortURI, String channel, String autorisedUsers) throws RemoteException
	{
		try {
			((PrivilegedClientCI) this.getConnector()).modifyAuthorisedUsers(receptionPortURI, channel, autorisedUsers);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void destroyChannel(String receptionPortURI, String channel) throws RemoteException
	{
		try {
			((PrivilegedClientCI) this.getConnector()).destroyChannel(receptionPortURI, channel);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

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
