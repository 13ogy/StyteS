package fr.sorbonne_u.cps.pubsub.base.ports;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyExistingChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.ChannelQuotaExceededException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;


/**
 * Inbound port exposing privileged channel-management operations of the broker.
 *
 * <p>
 * This port forwards calls to its owner {@code Broker} and implements the
 * component interface {@link PrivilegedClientCI}. It also inherits publishing
 * operations because {@link PrivilegedClientCI} extends {@link fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI}.
 * </p>
 *
 * <p>
 * Phase D.5 convention: business exceptions declared on the CI propagate
 * verbatim; every other technical {@link Exception} is wrapped in a
 * {@link RemoteException}. Phase D.3: the truly fire-and-forget void
 * operations ({@link #modifyAuthorisedUsers}, {@link #destroyChannel})
 * are dispatched on the broker's reception executor; their lambda
 * exceptions are logged on the tracer (callers cannot observe them).
 * Operations whose synchronous semantics matter for the caller
 * ({@link #createChannel}: quota / uniqueness reporting,
 * {@link #destroyChannelNow}: callers expect the channel to be gone on
 * return) stay {@link fr.sorbonne_u.components.AbstractComponent#handleRequest
 * handleRequest}-based.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class PrivilegedClientInboundPort extends PublishingInboundPort implements PrivilegedClientCI
{
	public PrivilegedClientInboundPort(ComponentI owner) throws Exception
	{
		super(PrivilegedClientCI.class, owner);
	}

	/**
	 * Explicit-URI constructor (Phase C.3): the broker derives this URI
	 * deterministically from its reflection inbound port URI.
	 */
	public PrivilegedClientInboundPort(String uri, ComponentI owner) throws Exception
	{
		super(uri, PrivilegedClientCI.class, owner);
	}

	@Override
	public boolean hasCreatedChannel(String receptionPortURI, String channel) throws Exception
	{
		try {
			return this.getOwner().handleRequest(
					o -> ((Broker) o).hasCreatedChannel(receptionPortURI, channel)
			);
		} catch (UnknownClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public boolean channelQuotaReached(String receptionPortURI) throws Exception
	{
		try {
			return this.getOwner().handleRequest(
					o -> ((Broker) o).channelQuotaReached(receptionPortURI)
			);
		} catch (UnknownClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void createChannel(String receptionPortURI, String channel, String autorisedUsers)
			throws Exception
	{
		try {
			this.getOwner().handleRequest(o -> {
				((Broker) o).createChannel(receptionPortURI, channel, autorisedUsers);
				return null;
			});
		} catch (UnknownClientException | AlreadyExistingChannelException
				| ChannelQuotaExceededException | UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void modifyAuthorisedUsers(String receptionPortURI, String channel, String autorisedUsers)
			throws Exception
	{
		try {
			final Broker broker = (Broker) this.getOwner();
			broker.runTask(broker.getReceptionExecutorIndex(), o -> {
				try {
					((Broker) o).modifyAuthorisedUsers(receptionPortURI, channel, autorisedUsers);
				} catch (Exception e) {
					((Broker) o).logMessage("[modifyAuthorisedUsers async] " + e);
				}
			});
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void destroyChannel(String receptionPortURI, String channel)
			throws Exception
	{
		try {
			final Broker broker = (Broker) this.getOwner();
			broker.runTask(broker.getReceptionExecutorIndex(), o -> {
				try {
					((Broker) o).destroyChannel(receptionPortURI, channel);
				} catch (Exception e) {
					((Broker) o).logMessage("[destroyChannel async] " + e);
				}
			});
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void destroyChannelNow(String receptionPortURI, String channel)
			throws Exception
	{
		try {
			this.getOwner().handleRequest(o -> {
				((Broker) o).destroyChannelNow(receptionPortURI, channel);
				return null;
			});
		} catch (UnknownClientException | UnknownChannelException
				| UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
