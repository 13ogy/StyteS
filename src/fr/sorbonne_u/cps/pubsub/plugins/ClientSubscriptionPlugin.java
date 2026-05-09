package fr.sorbonne_u.cps.pubsub.plugins;

import fr.sorbonne_u.components.AbstractPlugin;
import fr.sorbonne_u.cps.pubsub.exceptions.NotSubscribedChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingI;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
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
	protected final ReceivingI handler;

	// ---------------------------------------------------------------------
	// Advanced reception (CDC §3.5.3) state
	// ---------------------------------------------------------------------

	/**
	 * Per-channel FIFO of received messages not yet consumed by advanced calls.
	 * Backed by {@link LinkedBlockingQueue} (Phase E.4) so that
	 * {@link #waitForNextMessage(String)} and
	 * {@link #waitForNextMessage(String, Duration)} provide fair, blocking
	 * FIFO semantics when multiple consumers race on the same channel; this
	 * supersedes the previous {@code ArrayDeque} + {@code wait/notifyAll}
	 * combination, which mixed FIFO insertion with non-FIFO wakeup.
	 */
	protected final Map<String, LinkedBlockingQueue<MessageI>> pendingMessages = new HashMap<>();
	/** Per-channel next-message future to complete when a message arrives. */
	protected final Map<String, CompletableFuture<MessageI>> nextMessageFutures = new HashMap<>();

	/**
	 * Owner-side callback for received messages. Kept as a named sub-interface
	 * of {@link ReceivingI} for source/binary compatibility with existing call
	 * sites that reference the type by name; new code may use {@link ReceivingI}
	 * directly. Both expose the same single abstract method
	 * {@code onReceive(String, MessageI)}.
	 */
	@FunctionalInterface
	public interface MessageDeliveryHandler extends ReceivingI
	{
	}

	public ClientSubscriptionPlugin(ClientRegistrationPlugin registrationPlugin, ReceivingI handler)
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

		System.out.println("[CLientSubscribtionPlugin] received message : " + message.getPayload());
		// First, satisfy advanced reception consumers (if any).
		LinkedBlockingQueue<MessageI> queue;
		synchronized (this) {
			CompletableFuture<MessageI> f = this.nextMessageFutures.remove(channel);
			if (f != null && !f.isDone()) {
				f.complete(message);
				if (this.handler != null) {
					this.handler.onReceive(channel, message);
				}
				return;
			}
			queue = this.pendingMessages.computeIfAbsent(
				channel, c -> new LinkedBlockingQueue<>());
		}
		// Enqueue outside the monitor: LinkedBlockingQueue is itself
		// thread-safe and provides the FIFO + blocking semantics needed by
		// waitForNextMessage().
		queue.add(message);
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
		LinkedBlockingQueue<MessageI> q;
		synchronized (this) {
			q = this.pendingMessages.computeIfAbsent(
				channel, c -> new LinkedBlockingQueue<>());
		}
		try {
			// take() blocks fairly (FIFO) until a message is enqueued by
			// receive(); concurrent waiters on the same channel are served
			// in arrival order by LinkedBlockingQueue's internal lock.
			return q.take();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
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
		LinkedBlockingQueue<MessageI> q;
		synchronized (this) {
			q = this.pendingMessages.computeIfAbsent(
				channel, c -> new LinkedBlockingQueue<>());
		}
		try {
			return q.poll(d.toNanos(), TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	@Override
	public Future<MessageI> getNextMessage(String channel)
	{
		if (channel == null || channel.isEmpty()) {
			throw new IllegalArgumentException("channel cannot be null/empty");
		}
		synchronized (this) {
			LinkedBlockingQueue<MessageI> q = this.pendingMessages.get(channel);
			if (q != null) {
				MessageI head = q.poll();
				if (head != null) {
					return CompletableFuture.completedFuture(head);
				}
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
