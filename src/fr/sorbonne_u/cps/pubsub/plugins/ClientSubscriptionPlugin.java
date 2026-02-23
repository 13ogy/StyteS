package fr.sorbonne_u.cps.pubsub.plugins;

import fr.sorbonne_u.components.AbstractPlugin;
import fr.sorbonne_u.cps.pubsub.exceptions.NotSubscribedChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;

import java.time.Duration;
import java.util.concurrent.Future;

/**
 * Client-side plugin implementing subscription operations (CDC §3.5).
 *
 * Note: CDC §3.5.3 (wait/get next message) is explicitly excluded.
 */
public class ClientSubscriptionPlugin extends AbstractPlugin implements ClientSubscriptionI
{
	private static final long serialVersionUID = 1L;

	protected final ClientRegistrationPlugin registrationPlugin;
	protected final MessageDeliveryHandler handler;

	/** Owner-side callback for received messages. */
	@FunctionalInterface
	public interface MessageDeliveryHandler
	{
		void onReceive(String channel, MessageI message);
		default void onReceive(String channel, MessageI[] messages)
		{
			if (messages != null) {
				for (MessageI m : messages) {
					onReceive(channel, m);
				}
			}
		}
	}

	public ClientSubscriptionPlugin(ClientRegistrationPlugin registrationPlugin, MessageDeliveryHandler handler)
	{
		super();
		this.registrationPlugin = registrationPlugin;
		this.handler = handler;
	}

	@Override
	public boolean channelExist(String channel)
	{
		try {
			return this.registrationPlugin.getRegistrationPortOUT().channelExist(channel);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean channelAuthorised(String channel) throws UnknownClientException, UnknownChannelException
	{
		try {
			return this.registrationPlugin.getRegistrationPortOUT().channelAuthorised(
				this.registrationPlugin.getReceptionPortURI(),
				channel);
		} catch (UnknownClientException | UnknownChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean subscribed(String channel) throws UnknownClientException, UnknownChannelException
	{
		try {
			return this.registrationPlugin.getRegistrationPortOUT().subscribed(
				this.registrationPlugin.getReceptionPortURI(),
				channel);
		} catch (UnknownClientException | UnknownChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void subscribe(String channel, MessageFilterI filter)
	throws UnknownClientException, UnknownChannelException, UnauthorisedClientException
	{
		try {
			this.registrationPlugin.getRegistrationPortOUT().subscribe(
				this.registrationPlugin.getReceptionPortURI(),
				channel,
				filter);
		} catch (UnknownClientException | UnknownChannelException | UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void unsubscribe(String channel)
	throws UnknownClientException, UnknownChannelException, UnauthorisedClientException, NotSubscribedChannelException
	{
		try {
			this.registrationPlugin.getRegistrationPortOUT().unsubscribe(
				this.registrationPlugin.getReceptionPortURI(),
				channel);
		} catch (UnknownClientException | UnknownChannelException | UnauthorisedClientException | NotSubscribedChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void modifyFilter(String channel, MessageFilterI filter)
	throws UnknownClientException, UnknownChannelException, UnauthorisedClientException, NotSubscribedChannelException
	{
		try {
			this.registrationPlugin.getRegistrationPortOUT().modifyFilter(
				this.registrationPlugin.getReceptionPortURI(),
				channel,
				filter);
		} catch (UnknownClientException | UnknownChannelException | UnauthorisedClientException | NotSubscribedChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void receive(String channel, MessageI message)
	{
		if (this.handler != null) {
			this.handler.onReceive(channel, message);
		}
	}

	@Override
	public void receive(String channel, MessageI[] messages)
	{
		if (this.handler != null) {
			this.handler.onReceive(channel, messages);
		}
	}

	// ---------------------------------------------------------------------
	// CDC §3.5.3 excluded
	// ---------------------------------------------------------------------

	@Override
	public MessageI waitForNextMessage(String channel)
	{
		throw new UnsupportedOperationException("CDC §3.5.3 not implemented");
	}

	@Override
	public MessageI waitForNextMessage(String channel, Duration d)
	{
		throw new UnsupportedOperationException("CDC §3.5.3 not implemented");
	}

	@Override
	public Future<MessageI> getNextMessage(String channel)
	{
		throw new UnsupportedOperationException("CDC §3.5.3 not implemented");
	}
}
