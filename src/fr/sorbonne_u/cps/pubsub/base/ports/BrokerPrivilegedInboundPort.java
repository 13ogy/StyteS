package fr.sorbonne_u.cps.pubsub.base.ports;

import fr.sorbonne_u.components.ComponentI;
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
 * @author Bogdan Styn
 */
public class BrokerPrivilegedInboundPort extends BrokerPublishingInboundPort implements PrivilegedClientCI
{
	public BrokerPrivilegedInboundPort(ComponentI owner) throws Exception
	{
		super(PrivilegedClientCI.class, owner);
	}

	@Override
	public boolean hasCreatedChannel(String receptionPortURI, String channel) throws Exception
	{
		return this.getOwner().handleRequest(
				o -> ((Broker) o).hasCreatedChannel(receptionPortURI, channel)
		);
	}

	@Override
	public boolean channelQuotaReached(String receptionPortURI) throws Exception
	{
		return this.getOwner().handleRequest(
				o -> ((Broker) o).channelQuotaReached(receptionPortURI)
		);
	}

	@Override
	public void createChannel(String receptionPortURI, String channel, String autorisedUsers)
			throws Exception
	{
		this.getOwner().handleRequest(o -> {
			((Broker) o).createChannel(receptionPortURI, channel, autorisedUsers);
			return null;
		});
	}

	@Override
	public void modifyAuthorisedUsers(String receptionPortURI, String channel, String autorisedUsers)
			throws Exception
	{
		this.getOwner().handleRequest(o -> {
			((Broker) o).modifyAuthorisedUsers(receptionPortURI, channel, autorisedUsers);
			return null;
		});
	}

	@Override
	public void destroyChannel(String receptionPortURI, String channel)
			throws Exception
	{
		this.getOwner().handleRequest(o -> {
			((Broker) o).destroyChannel(receptionPortURI, channel);
			return null;
		});
	}

	@Override
	public void destroyChannelNow(String receptionPortURI, String channel)
			throws Exception
	{
		this.getOwner().handleRequest(o -> {
			((Broker) o).destroyChannelNow(receptionPortURI, channel);
			return null;
		});
	}
}
