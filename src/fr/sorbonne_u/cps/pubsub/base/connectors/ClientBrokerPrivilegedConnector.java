package fr.sorbonne_u.cps.pubsub.base.connectors;

import fr.sorbonne_u.components.connectors.AbstractConnector;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;

import java.util.ArrayList;

/**
 * Connector from a client privileged outbound port to the broker privileged
 * inbound port.
 *
 * <p>
 * The connector forwards calls to the offering component through
 * {@link #offering}.
 * </p>
 */
public class ClientBrokerPrivilegedConnector extends AbstractConnector implements PrivilegedClientCI
{
	@Override
	public boolean hasCreatedChannel(String receptionPortURI, String channel) throws Exception
	{
		return ((PrivilegedClientCI) this.offering).hasCreatedChannel(receptionPortURI, channel);
	}

	@Override
	public boolean channelQuotaReached(String receptionPortURI) throws Exception
	{
		return ((PrivilegedClientCI) this.offering).channelQuotaReached(receptionPortURI);
	}

	@Override
	public void createChannel(String receptionPortURI, String channel, String autorisedUsers) throws Exception
	{
		((PrivilegedClientCI) this.offering).createChannel(receptionPortURI, channel, autorisedUsers);
	}

	@Override
	public boolean isAuthorisedUser(String channel, String uri) throws Exception
	{
		return ((PrivilegedClientCI) this.offering).isAuthorisedUser(channel, uri);
	}

	@Override
	public void modifyAuthorisedUsers(String receptionPortURI, String channel, String autorisedUsers) throws Exception
	{
		((PrivilegedClientCI) this.offering).modifyAuthorisedUsers(receptionPortURI, channel, autorisedUsers);
	}

	@Override
	public void removeAuthorisedUsers(String receptionPortURI, String channel, String regularExpression) throws Exception
	{
		((PrivilegedClientCI) this.offering).removeAuthorisedUsers(receptionPortURI, channel, regularExpression);
	}

	@Override
	public void destroyChannel(String receptionPortURI, String channel) throws Exception
	{
		((PrivilegedClientCI) this.offering).destroyChannel(receptionPortURI, channel);
	}

	@Override
	public void destroyChannelNow(String receptionPortURI, String channel) throws Exception
	{
		((PrivilegedClientCI) this.offering).destroyChannelNow(receptionPortURI, channel);
	}

	// -------------------------------------------------------------------------
	// PublishingCI
	// -------------------------------------------------------------------------

	@Override
	public void publish(String receptionPortURI, String channel, MessageI message) throws Exception
	{
		((PrivilegedClientCI) this.offering).publish(receptionPortURI, channel, message);
	}

	@Override
	public void publish(String receptionPortURI, String channel, ArrayList<MessageI> messages) throws Exception
	{
		((PrivilegedClientCI) this.offering).publish(receptionPortURI, channel, messages);
	}
}
