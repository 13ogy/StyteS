package fr.sorbonne_u.cps.pubsub.base.ports;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractOutboundPort;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyExistingChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.ChannelQuotaExceededException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;

import java.rmi.RemoteException;
import java.util.ArrayList;

/**
 * Outbound port used by clients to call privileged channel-management operations
 * offered by the broker through {@link PrivilegedClientCI}.
 *
 * <p>
 * Methods forward calls to the connected connector using {@link #getConnector()}.
 * </p>
 */
public class ClientPrivilegedOutboundPort extends AbstractOutboundPort implements PrivilegedClientCI
{
	public ClientPrivilegedOutboundPort(ComponentI owner) throws Exception
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
	public boolean isAuthorisedUser(String channel, String uri) throws RemoteException
	{
		try {
			return ((PrivilegedClientCI) this.getConnector()).isAuthorisedUser(channel, uri);
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
	public void removeAuthorisedUsers(String receptionPortURI, String channel, String regularExpression) throws RemoteException
	{
		try {
			((PrivilegedClientCI) this.getConnector()).removeAuthorisedUsers(receptionPortURI, channel, regularExpression);
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
	// PublishingCI (inherited by PrivilegedClientCI)
	// -------------------------------------------------------------------------

	@Override
	public void publish(String receptionPortURI, String channel, MessageI message) throws RemoteException
	{
		try {
			((PrivilegedClientCI) this.getConnector()).publish(receptionPortURI, channel, message);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void publish(String receptionPortURI, String channel, ArrayList<MessageI> messages) throws RemoteException
	{
		try {
			((PrivilegedClientCI) this.getConnector()).publish(receptionPortURI, channel, messages);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
