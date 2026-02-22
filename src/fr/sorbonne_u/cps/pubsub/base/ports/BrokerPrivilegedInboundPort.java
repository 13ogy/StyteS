package fr.sorbonne_u.cps.pubsub.base.ports;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyExistingChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.ChannelQuotaExceededException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;

import java.rmi.RemoteException;
import java.util.ArrayList;

/**
 * Inbound port exposing privileged channel-management operations of the broker.
 *
 * <p>
 * This port forwards calls to its owner {@code Broker} and implements the
 * component interface {@link PrivilegedClientCI}. It also inherits publishing
 * operations because {@link PrivilegedClientCI} extends {@link fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI}.
 * </p>
 */
public class BrokerPrivilegedInboundPort extends AbstractInboundPort implements PrivilegedClientCI
{
	public BrokerPrivilegedInboundPort(ComponentI owner) throws Exception
	{
		super(PrivilegedClientCI.class, owner);
	}

	@Override
	public boolean hasCreatedChannel(String receptionPortURI, String channel) throws RemoteException
	{
		try {
			return ((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.hasCreatedChannel(receptionPortURI, channel);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public boolean channelQuotaReached(String receptionPortURI) throws RemoteException
	{
		try {
			return ((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.channelQuotaReached(receptionPortURI);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void createChannel(String receptionPortURI, String channel, String autorisedUsers)
		throws RemoteException, AlreadyExistingChannelException, ChannelQuotaExceededException
	{
		try {
			((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.createChannel(receptionPortURI, channel, autorisedUsers);
		} catch (AlreadyExistingChannelException | ChannelQuotaExceededException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public boolean isAuthorisedUser(String channel, String uri)
		throws RemoteException, UnknownChannelException, UnknownClientException
	{
		try {
			return ((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.isAuthorisedUser(channel, uri);
		} catch (UnknownChannelException | UnknownClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void modifyAuthorisedUsers(String receptionPortURI, String channel, String autorisedUsers)
		throws RemoteException, UnknownClientException, UnknownChannelException, UnauthorisedClientException
	{
		try {
			((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.modifyAuthorisedUsers(receptionPortURI, channel, autorisedUsers);
		} catch (UnknownClientException | UnknownChannelException | UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void removeAuthorisedUsers(String receptionPortURI, String channel, String regularExpression)
		throws RemoteException, UnknownClientException, UnknownChannelException, UnauthorisedClientException
	{
		try {
			((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.removeAuthorisedUsers(receptionPortURI, channel, regularExpression);
		} catch (UnknownClientException | UnknownChannelException | UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void destroyChannel(String receptionPortURI, String channel)
		throws RemoteException, UnknownClientException, UnknownChannelException, UnauthorisedClientException
	{
		try {
			((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.destroyChannel(receptionPortURI, channel);
		} catch (UnknownClientException | UnknownChannelException | UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void destroyChannelNow(String receptionPortURI, String channel)
		throws RemoteException, UnknownClientException, UnknownChannelException, UnauthorisedClientException
	{
		try {
			((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.destroyChannelNow(receptionPortURI, channel);
		} catch (UnknownClientException | UnknownChannelException | UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	// -------------------------------------------------------------------------
	// PublishingCI (inherited by PrivilegedClientCI)
	// -------------------------------------------------------------------------

	@Override
	public void publish(String receptionPortURI, String channel, fr.sorbonne_u.cps.pubsub.interfaces.MessageI message)
		throws RemoteException
	{
		try {
			((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.publish(receptionPortURI, channel, message);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void publish(String receptionPortURI, String channel, ArrayList<fr.sorbonne_u.cps.pubsub.interfaces.MessageI> messages)
		throws RemoteException
	{
		try {
			((fr.sorbonne_u.cps.pubsub.base.components.Broker) this.getOwner())
				.publish(receptionPortURI, channel, messages);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
