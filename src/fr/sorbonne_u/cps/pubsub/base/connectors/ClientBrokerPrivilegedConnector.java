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
 *
 * @author Bogdan Styn
 */
public class ClientBrokerPrivilegedConnector extends AbstractConnector
	implements PrivilegedClientCI
{
	@Override
	public boolean hasCreatedChannel(String receptionPortURI, String channel)
	throws Exception
	{
		return ((PrivilegedClientCI) this.offering).hasCreatedChannel(receptionPortURI, channel);
	}

	@Override
	public boolean channelQuotaReached(String receptionPortURI) throws Exception
	{
		return ((PrivilegedClientCI) this.offering).channelQuotaReached(receptionPortURI);
	}

	@Override
	public void createChannel(String receptionPortURI, String channel, String autorisedUsers)
	throws Exception
	{
		((PrivilegedClientCI) this.offering).createChannel(receptionPortURI, channel, autorisedUsers);
	}

	@Override
	public void modifyAuthorisedUsers(String receptionPortURI, String channel, String autorisedUsers)
	throws Exception
	{
		((PrivilegedClientCI) this.offering).modifyAuthorisedUsers(receptionPortURI, channel, autorisedUsers);
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

	// -------------------------------------------------------------------------
	// PublishingCI async (added in latest interface)
	// -------------------------------------------------------------------------

	@Override
	public void asyncPublishAndNotify(
		String receptionPortURI,
		String channel,
		MessageI message,
		String notificationInbounhdPortURI
		) throws Exception
	{
		((PrivilegedClientCI) this.offering).asyncPublishAndNotify(
			receptionPortURI, channel, message, notificationInbounhdPortURI);
	}

	@Override
	public void asyncPublishAndNotify(
		String receptionPortURI,
		String channel,
		ArrayList<MessageI> messages,
		String notificationInbounhdPortURI
		) throws Exception
	{
		((PrivilegedClientCI) this.offering).asyncPublishAndNotify(
			receptionPortURI, channel, messages, notificationInbounhdPortURI);
	}
}
