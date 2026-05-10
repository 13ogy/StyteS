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
 * Plugin client implémentant les opérations de souscription (CDC §3.5),
 * y compris l'API de réception avancée (CDC §3.5.3).
 *
 * <p><strong>Description</strong></p>
 *
 * <p>
 * Comme {@link ClientPublicationPlugin}, ce plugin <em>ne possède pas
 * de port en propre</em> : les opérations
 * {@code subscribe} / {@code unsubscribe} / {@code modifyFilter} sont
 * relayées au broker via le port outbound {@code RegistrationCI} possédé
 * par {@link ClientRegistrationPlugin}. Les méthodes {@link #receive}
 * sont quant à elles appelées par le {@code ClientRegistrationPlugin}
 * lorsqu'un message arrive sur son port {@code ReceivingCI} (le plugin
 * de registration agit comme dispatcher).
 * </p>
 *
 * <p><strong>Réception avancée (CDC §3.5.3)</strong></p>
 * <p>
 * En plus de la livraison passive via le {@link ReceivingI handler}
 * fourni au constructeur, ce plugin offre deux modes pull :
 * </p>
 * <ul>
 * <li>{@link #waitForNextMessage(String)} et
 * {@link #waitForNextMessage(String, Duration)} : prélèvement
 * bloquant, équitable (FIFO) et thread-safe sur le canal donné.</li>
 * <li>{@link #getNextMessage(String)} : retourne un
 * {@link CompletableFuture} résolu lorsque le prochain message
 * arrive (ou immédiatement si la file contient déjà un message).</li>
 * </ul>
 *
 * <p>
 * Les files par canal sont des {@link LinkedBlockingQueue}.
 * Cette structure :
 * </p>
 * <ul>
 * <li>est intrinsèquement thread-safe : aucune synchronisation
 * supplémentaire n'est nécessaire pour {@code add} / {@code take}
 * / {@code poll} ;</li>
 * <li>fournit une garantie d'équité FIFO entre consommateurs
 * concurrents, contrairement à l'ancien
 * {@code ArrayDeque + wait/notifyAll} qui mêlait insertion FIFO
 * et réveil non FIFO.</li>
 * </ul>
 *
 * <p>L'URI du plugin suit la convention
 * {@code <reflectionURI>-subscription-plugin}.</p>
 *
 * <p>Created on : 2026-02-04</p>
 *
 * @author Bogdan Styn, Setbel Mélissa
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
	 * Backed by {@link LinkedBlockingQueue} so that
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

	/**
	 * {@inheritDoc}
	 *
	 * <p>Délègue au port {@code RegistrationCI} du plugin de registration.</p>
	 */
	@Override
	public boolean channelExist(String channel)
	{
		try {
			return this.registrationPlugin.getRegistrationPortOUT().channelExist(channel);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws UnknownClientException	si le client n'est pas enregistré.
	 * @throws UnknownChannelException	si {@code channel} n'existe pas.
	 */
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

	/**
	 * {@inheritDoc}
	 *
	 * @throws UnknownClientException	si le client n'est pas enregistré.
	 * @throws UnknownChannelException	si {@code channel} n'existe pas.
	 */
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

	/**
	 * Souscrit ce client à {@code channel} avec le filtre {@code filter}.
	 * Délègue au port {@code RegistrationCI} en passant l'URI de réception
	 * comme identité.
	 *
	 * @param channel	canal cible.
	 * @param filter	filtre de messages ; peut être {@code null} pour
	 *					souscrire sans filtre.
	 * @throws UnknownClientException		si le client n'est pas enregistré.
	 * @throws UnknownChannelException		si {@code channel} n'existe pas.
	 * @throws UnauthorisedClientException	si le client n'est pas autorisé
	 *										à souscrire à {@code channel}.
	 */
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

	/**
	 * Retire la souscription de ce client à {@code channel}.
	 *
	 * @param channel	canal cible.
	 * @throws UnknownClientException			si le client n'est pas enregistré.
	 * @throws UnknownChannelException			si {@code channel} n'existe pas.
	 * @throws UnauthorisedClientException		si le client n'est pas autorisé
	 *											à utiliser {@code channel}.
	 * @throws NotSubscribedChannelException	si le client n'est pas
	 *											souscrit à {@code channel}.
	 */
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

	/**
	 * Remplace le filtre associé à la souscription courante de ce client
	 * sur {@code channel}.
	 *
	 * @param channel	canal cible.
	 * @param filter	nouveau filtre de messages.
	 * @throws UnknownClientException			si le client n'est pas enregistré.
	 * @throws UnknownChannelException			si {@code channel} n'existe pas.
	 * @throws UnauthorisedClientException		si le client n'est pas autorisé
	 *											à utiliser {@code channel}.
	 * @throws NotSubscribedChannelException	si le client n'est pas
	 *											souscrit à {@code channel}.
	 */
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

	/**
	 * Point d'entrée appelé par le {@link ClientRegistrationPlugin}
	 * (qui possède le port {@code ReceivingCI}) lorsqu'un message arrive.
	 *
	 * <p>Logique de routage :</p>
	 * <ol>
	 * <li>si un {@link CompletableFuture} non terminé est en attente
	 * sur ce canal (mode {@link #getNextMessage}), il est complété
	 * avec le message et retiré du registre ; le handler passif
	 * est ensuite notifié et la méthode retourne ;</li>
	 * <li>sinon, le message est ajouté à la {@link LinkedBlockingQueue}
	 * du canal (créée à la demande) ; cet ajout débloque tout
	 * consommateur en attente sur
	 * {@link #waitForNextMessage(String)} dans l'ordre FIFO ;</li>
	 * <li>le handler passif {@link ReceivingI} est ensuite notifié
	 * (s'il est non {@code null}).</li>
	 * </ol>
	 *
	 * <p>L'enqueue est volontairement réalisé hors du moniteur : la
	 * {@link LinkedBlockingQueue} est elle-même thread-safe et garantit
	 * la sémantique FIFO + bloquante requise par
	 * {@code waitForNextMessage}.</p>
	 *
	 * @param channel	canal de provenance.
	 * @param message	message reçu.
	 */
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

	/**
	 * Variante batch de {@link #receive(String, MessageI)} : itère sur le
	 * tableau et appelle {@code receive(channel, m)} pour chaque message
	 * non {@code null}.
	 *
	 * @param channel	canal de provenance.
	 * @param messages	messages reçus (peut être {@code null}, auquel cas
	 *					seul le handler passif est notifié avec un tableau
	 *					{@code null}).
	 */
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

	/**
	 * Bloque le thread appelant jusqu'à l'arrivée du prochain message
	 * sur {@code channel}.
	 *
	 * <p>
	 * <strong>Équité FIFO :</strong> implémenté via
	 * {@link LinkedBlockingQueue#take()} ; lorsque plusieurs threads
	 * appellent simultanément cette méthode pour le même canal, ils sont
	 * servis dans l'ordre d'arrivée des messages, sans risque de famine.
	 * </p>
	 *
	 * @param channel	canal cible (non {@code null}, non vide).
	 * @return			le prochain message reçu, ou {@code null} si le
	 *					thread appelant a été interrompu pendant l'attente.
	 * @throws IllegalArgumentException	si {@code channel} est {@code null}
	 *									ou vide.
	 */
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

	/**
	 * Variante avec délai maximal : bloque jusqu'à l'arrivée du prochain
	 * message sur {@code channel} ou expiration de {@code d}.
	 *
	 * @param channel	canal cible (non {@code null}, non vide).
	 * @param d			délai maximal d'attente (non {@code null}).
	 * @return			le prochain message reçu, ou {@code null} si le
	 *					délai expire ou si le thread est interrompu.
	 * @throws IllegalArgumentException	si {@code channel} ou {@code d} sont
	 *									invalides.
	 */
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

	/**
	 * Retourne un {@link Future} (concrètement un
	 * {@link CompletableFuture}) résolu au prochain message disponible
	 * sur {@code channel}.
	 *
	 * <p><strong>Sémantique</strong></p>
	 * <ul>
	 * <li>si la file du canal contient déjà un message, il est consommé
	 * et le future retourné est <em>déjà complété</em>
	 * ({@code CompletableFuture.completedFuture(head)}) ;</li>
	 * <li>sinon, un future en attente est mémorisé pour ce canal et
	 * partagé entre appelants successifs tant qu'il n'est pas
	 * complété ; le prochain {@link #receive} sur ce canal le
	 * complète puis l'efface du registre.</li>
	 * </ul>
	 *
	 * <p>
	 * <strong>Note d'équité :</strong> contrairement à
	 * {@link #waitForNextMessage(String)} (FIFO via
	 * {@link LinkedBlockingQueue}), plusieurs appels simultanés à
	 * {@code getNextMessage} sur le même canal partagent le même future ;
	 * ils sont donc tous résolus avec le même message lorsqu'il arrive.
	 * </p>
	 *
	 * @param channel	canal cible (non {@code null}, non vide).
	 * @return			future résolu avec le prochain message reçu.
	 * @throws IllegalArgumentException	si {@code channel} est {@code null}
	 *									ou vide.
	 */
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
