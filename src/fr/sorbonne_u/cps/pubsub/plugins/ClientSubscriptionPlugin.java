package fr.sorbonne_u.cps.pubsub.plugins;

import fr.sorbonne_u.components.AbstractPlugin;
import fr.sorbonne_u.cps.pubsub.exceptions.NotSubscribedChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Client-side plugin implementing subscription operations (CDC §3.5).
 * 
 *
 * @author Bogdan Styn
 */

public class ClientSubscriptionPlugin extends AbstractPlugin implements ClientSubscriptionI
{
	private static final long serialVersionUID = 1L;

	protected final ClientRegistrationPlugin registrationPlugin;
	protected final MessageDeliveryHandler handler;

	// ---------------------------------------------------------------------
	// Advanced reception (CDC §3.5.3) state
	// ---------------------------------------------------------------------

	/** Per-channel FIFO of received messages not yet consumed by advanced calls. */
	protected final Map<String, Deque<MessageI>> pendingMessages = new HashMap<>();
	/** Per-channel next-message future to complete when a message arrives. */
	protected final Map<String, CompletableFuture<MessageI>> nextMessageFutures = new HashMap<>();

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
		// First, satisfy advanced reception consumers (if any).
		synchronized (this) {
			CompletableFuture<MessageI> f = this.nextMessageFutures.get(channel);
			if (f != null && !f.isDone()) {
				f.complete(message);
				return;
			}
			this.pendingMessages.computeIfAbsent(channel, c -> new ArrayDeque<>()).addLast(message);
			this.notifyAll();
		}
		if (this.handler != null) {
			this.handler.onReceive(channel, message);
		}
	}

	@Override
	public void receive(String channel, MessageI[] messages)
	{
		if (messages != null) {
			for (MessageI m : messages) {
				this.receive(channel, m);
			}
			return;
		}
		if (this.handler != null) {
			this.handler.onReceive(channel, messages);
		}
	}

	// ---------------------------------------------------------------------
	// Pas de CDC §3.5.3
	// ---------------------------------------------------------------------

	@Override
	public MessageI waitForNextMessage(String channel)
	{
		if (channel == null || channel.isEmpty()) {
			throw new IllegalArgumentException("channel cannot be null/empty");
		}
		synchronized (this) {
			Deque<MessageI> q = this.pendingMessages.get(channel);
			while (q == null || q.isEmpty()) {
				try {
					this.wait();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return null;
				}
				q = this.pendingMessages.get(channel);
			}
			return q.removeFirst();
		}
	}

	@Override
	public MessageI waitForNextMessage(String channel, Duration d)
	{
		if (channel == null || channel.isEmpty()) {
			throw new IllegalArgumentException("channel cannot be null/empty");
		}
		if (d == null) {
			throw new IllegalArgumentException("duration cannot be null");
		}
		long remainingNanos = d.toNanos();
		long deadline = System.nanoTime() + remainingNanos;
		synchronized (this) {
			Deque<MessageI> q = this.pendingMessages.get(channel);
			while (q == null || q.isEmpty()) {
				if (remainingNanos <= 0) {
					return null;
				}
				try {
					long ms = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
					int ns = (int) (remainingNanos - TimeUnit.MILLISECONDS.toNanos(ms));
					this.wait(ms, ns);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return null;
				}
				remainingNanos = deadline - System.nanoTime();
				q = this.pendingMessages.get(channel);
			}
			return q.removeFirst();
		}
	}

	@Override
	public Future<MessageI> getNextMessage(String channel)
	{
		if (channel == null || channel.isEmpty()) {
			throw new IllegalArgumentException("channel cannot be null/empty");
		}
		synchronized (this) {
			Deque<MessageI> q = this.pendingMessages.get(channel);
			if (q != null && !q.isEmpty()) {
				return CompletableFuture.completedFuture(q.removeFirst());
			}
			CompletableFuture<MessageI> f = this.nextMessageFutures.get(channel);
			if (f == null || f.isDone()) {
				f = new CompletableFuture<>();
				this.nextMessageFutures.put(channel, f);
			}
			return f;
		}
	}
}
