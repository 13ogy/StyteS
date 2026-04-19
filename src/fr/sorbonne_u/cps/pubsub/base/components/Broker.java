package fr.sorbonne_u.cps.pubsub.base.components;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.exceptions.ComponentShutdownException;
import fr.sorbonne_u.cps.pubsub.base.connectors.BrokerClientReceivingConnector;
import fr.sorbonne_u.cps.pubsub.base.ports.BrokerPrivilegedInboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.BrokerPublishingInboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.BrokerReceptionOutboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.BrokerRegistrationInboundPort;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyExistingChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.exceptions.ChannelQuotaExceededException;
import fr.sorbonne_u.cps.pubsub.exceptions.NotSubscribedChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;

import fr.sorbonne_u.components.annotations.OfferedInterfaces;
import fr.sorbonne_u.components.annotations.RequiredInterfaces;

import java.util.regex.Pattern;

/**
 * Composant courtier d'un système publication/souscription.
 *
 * <p>
 * Fonctionnalités : enregistrement (FREE/STANDARD/PREMIUM), canaux libres
 * pré-créés, canaux privilégiés avec quotas et autorisation par expression
 * régulière, souscription filtrée et publication asynchrone via trois
 * pools de threads distincts (réception → propagation → livraison).
 * </p>
 *
 * <p>
 * <strong>Concurrence.</strong> L'état est partitionné en trois domaines, chacun
 * protégé par un {@link ReentrantReadWriteLock} indépendant : enregistrement,
 * canaux et souscriptions. Le compteur in-flight est sous son propre moniteur
 * pour ne pas bloquer le pipeline. Ordre d'acquisition obligatoire pour éviter
 * les inter-blocages :
 * </p>
 * <pre>
 *   registrationLock  &lt;  channelsLock  &lt;  subscriptionsLock
 * </pre>
 * <p>
 * Aucun verrou n'est conservé pendant un appel distant.
 * </p>
 *
 * @author Bogdan Styn
 */
@OfferedInterfaces(offered = {
	RegistrationCI.class,
	PublishingCI.class,
	PrivilegedClientCI.class
})
@RequiredInterfaces(required = {
	ReceivingCI.class
})
public class Broker extends AbstractComponent
{
	// -------------------------------------------------------------------------
	// Pools d'exécuteurs (audit 2)
	// -------------------------------------------------------------------------

	public static final String ES_RECEPTION_URI = "broker-reception-es";
	public static final String ES_PROPAGATION_URI = "broker-propagation-es";
	public static final String ES_DELIVERY_URI = "broker-delivery-es";

	protected int esReceptionIndex;
	protected int esPropagationIndex;
	protected int esDeliveryIndex;

	// -------------------------------------------------------------------------
	// Verrous par domaine (cf. en-tête de classe)
	// -------------------------------------------------------------------------

	// Mode équitable : protège les écritures longues contre la famine.
	protected final ReentrantReadWriteLock registrationLock = new ReentrantReadWriteLock(true);
	protected final ReentrantReadWriteLock channelsLock = new ReentrantReadWriteLock(true);
	protected final ReentrantReadWriteLock subscriptionsLock = new ReentrantReadWriteLock(true);

	// -------------------------------------------------------------------------
	// PublishingCI asynchrone
	// -------------------------------------------------------------------------

	public void asyncPublishAndNotify(
	String publisherReceptionPortURI,
	String channel,
	MessageI message,
	String notificationInbounhdPortURI
	) throws Exception
	{
		this.submitPublish(publisherReceptionPortURI, channel, message, notificationInbounhdPortURI);
	}

	public void asyncPublishAndNotify(
	String publisherReceptionPortURI,
	String channel,
	ArrayList<MessageI> messages,
	String notificationInbounhdPortURI
	) throws Exception
	{
		for (MessageI m : messages) {
			this.submitPublish(publisherReceptionPortURI, channel, m, notificationInbounhdPortURI);
		}
	}

	// -------------------------------------------------------------------------
	// Configuration
	// -------------------------------------------------------------------------

	public static final int NB_FREE_CHANNELS = 3;

	// -------------------------------------------------------------------------
	// Ports
	// -------------------------------------------------------------------------

	private static BrokerRegistrationInboundPort registrationPortIN;
	private BrokerPublishingInboundPort publishingPortIN;
	private static BrokerPrivilegedInboundPort privilegedPortIN;

	// -------------------------------------------------------------------------
	// État
	// -------------------------------------------------------------------------

	private final Map<String, RegistrationClass> registeredClients = new HashMap<>();
	private final Map<String, BrokerReceptionOutboundPort> receptionPortsOUT = new HashMap<>();
	private final Set<String> channels = new HashSet<>();

	private static class PrivilegedChannelInfo
	{
		final String ownerReceptionPortURI;
		Pattern authorisedUsersPattern;

		PrivilegedChannelInfo(String ownerReceptionPortURI, Pattern authorisedUsersPattern)
		{
			this.ownerReceptionPortURI = ownerReceptionPortURI;
			this.authorisedUsersPattern = authorisedUsersPattern;
		}
	}

	private final Map<String, PrivilegedChannelInfo> privilegedChannels = new HashMap<>();
	private final Map<String, Integer> createdPrivilegedChannelsCount = new HashMap<>();

	public static final int STANDARD_PRIVILEGED_CHANNEL_QUOTA = 2;
	public static final int PREMIUM_PRIVILEGED_CHANNEL_QUOTA = 5;

	private final Map<String, Map<String, MessageFilterI>> subscriptions = new HashMap<>();

	// Compteur in-flight par canal : ConcurrentHashMap + AtomicInteger pour
	// rester totalement lock-free et hors des verrous lourds du pipeline.
	private final ConcurrentHashMap<String, AtomicInteger> inFlightPerChannel = new ConcurrentHashMap<>();

	// -------------------------------------------------------------------------
	// Constructeur
	// -------------------------------------------------------------------------

	protected Broker(int nbThreads, int nbSchedulableThreads) throws Exception
	{
		super(nbThreads, nbSchedulableThreads);

		this.esReceptionIndex = this.createNewExecutorService(ES_RECEPTION_URI, Math.max(1, nbThreads), false);
		this.esPropagationIndex = this.createNewExecutorService(ES_PROPAGATION_URI, Math.max(1, nbThreads), false);
		this.esDeliveryIndex = this.createNewExecutorService(ES_DELIVERY_URI, Math.max(1, nbThreads), false);

		for (int i = 0; i < NB_FREE_CHANNELS; i++) {
			String c = "channel" + i;
			this.channels.add(c);
			this.subscriptions.put(c, new HashMap<>());
			this.inFlightPerChannel.put(c, new AtomicInteger(0));
		}

		registrationPortIN = new BrokerRegistrationInboundPort(this);
		registrationPortIN.publishPort();

		publishingPortIN = new BrokerPublishingInboundPort(this);
		publishingPortIN.publishPort();

		privilegedPortIN = new BrokerPrivilegedInboundPort(this);
		privilegedPortIN.publishPort();
	}

	// -------------------------------------------------------------------------
	// Pipeline asynchrone
	// -------------------------------------------------------------------------

	protected static class DeliveryTarget
	{
		final String subscriberURI;
		final BrokerReceptionOutboundPort out;
		final MessageFilterI filter;

		DeliveryTarget(String subscriberURI, BrokerReceptionOutboundPort out, MessageFilterI filter)
		{
			this.subscriberURI = subscriberURI;
			this.out = out;
			this.filter = filter;
		}
	}

	protected void submitPublish(
	String publisherReceptionPortURI,
	String channel,
	MessageI message,
	String notificationInboundPortURI
	)
	{
		final Broker self = this;
		this.runTask(this.esReceptionIndex, o -> {
			try {
				((Broker) o).receptionStage(publisherReceptionPortURI, channel, message, notificationInboundPortURI);
			} catch (Exception e) {
				self.logMessage("[Broker] receptionStage exception: " + e + "\n");
			}
		});
	}

	protected void receptionStage(
	String publisherReceptionPortURI,
	String channel,
	MessageI message,
	String notificationInboundPortURI
	) throws Exception
	{
		// Incrémenter avant validation : destroyChannel observe ainsi ce
		// message même si une vérification jette une exception en aval.
		this.beginInFlight(channel);

		this.registrationLock.readLock().lock();
		try {
			if (!this.registeredClients.containsKey(publisherReceptionPortURI)) {
				this.finishInFlight(channel);
				throw new UnknownClientException(publisherReceptionPortURI);
			}
			this.channelsLock.readLock().lock();
			try {
				if (!this.channels.contains(channel)) {
					this.finishInFlight(channel);
					throw new UnknownChannelException(channel);
				}
				if (!this.channelAuthorisedLocked(publisherReceptionPortURI, channel)) {
					this.finishInFlight(channel);
					throw new UnauthorisedClientException();
				}
			} finally {
				this.channelsLock.readLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}

		this.runTask(this.esPropagationIndex, o -> {
			try {
				((Broker) o).propagationStage(channel, message);
			} catch (Exception e) {
				this.logMessage("[Broker] propagationStage exception: " + e + "\n");
				((Broker) o).finishInFlight(channel);
			}
		});
	}

	protected void beginInFlight(String channel)
	{
		this.inFlightPerChannel.computeIfAbsent(channel, k -> new AtomicInteger(0)).incrementAndGet();
	}

	protected void finishInFlight(String channel)
	{
		AtomicInteger a = this.inFlightPerChannel.get(channel);
		if (a == null) return;
		// Borne basse à 0 : une décrémentation tardive après destroyChannel ne
		// doit pas faire passer le compteur en négatif.
		a.updateAndGet(v -> Math.max(0, v - 1));
	}

	protected void propagationStage(String channel, MessageI message) throws Exception
	{
		// Snapshot des destinataires sous deux verrous lecture (ordre canonique
		// registration → subscriptions) ; livraison réseau ensuite hors verrou.
		List<DeliveryTarget> targets = new ArrayList<>();
		this.registrationLock.readLock().lock();
		try {
			this.subscriptionsLock.readLock().lock();
			try {
				Map<String, MessageFilterI> subs = this.subscriptions.get(channel);
				if (subs == null) {
					throw new UnknownChannelException(channel);
				}
				for (Map.Entry<String, MessageFilterI> e : subs.entrySet()) {
					String subscriberURI = e.getKey();
					MessageFilterI filter = e.getValue();
					BrokerReceptionOutboundPort out = this.receptionPortsOUT.get(subscriberURI);
					if (out != null) {
						targets.add(new DeliveryTarget(subscriberURI, out, filter));
					}
				}
			} finally {
				this.subscriptionsLock.readLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}

		final int expected = targets.size();
		if (expected == 0) {
			this.finishInFlight(channel);
			return;
		}
		final java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(expected);
		for (DeliveryTarget t : targets) {
			this.runTask(this.esDeliveryIndex, o -> {
				try {
					MessageFilterI f = t.filter;
					if (f != null && f.match(message)) {
						t.out.receive(channel, message);
					}
				} catch (Exception e) {
					this.logMessage("[Broker] delivery exception to " + t.subscriberURI + ": " + e + "\n");
				} finally {
					if (remaining.decrementAndGet() == 0) {
						((Broker) o).finishInFlight(channel);
					}
				}
			});
		}
	}

	// -------------------------------------------------------------------------
	// Cycle de vie
	// -------------------------------------------------------------------------

	@Override
	public synchronized void shutdown() throws ComponentShutdownException
	{
		try {
			for (BrokerReceptionOutboundPort out : this.receptionPortsOUT.values()) {
				try {
					if (out.connected()) {
						this.doPortDisconnection(out.getPortURI());
					}
				} catch (Exception ignored) {}
				try { out.unpublishPort(); } catch (Exception ignored) {}
				try { out.destroyPort(); } catch (Exception ignored) {}
			}
			this.receptionPortsOUT.clear();

			try {
				if (this.publishingPortIN != null) {
					this.publishingPortIN.unpublishPort();
					this.publishingPortIN.destroyPort();
				}
			} catch (Exception ignored) {}
			try {
				if (registrationPortIN != null) {
					registrationPortIN.unpublishPort();
					registrationPortIN.destroyPort();
				}
			} catch (Exception ignored) {}
			try {
				if (privilegedPortIN != null) {
					privilegedPortIN.unpublishPort();
					privilegedPortIN.destroyPort();
				}
			} catch (Exception ignored) {}
		} catch (Throwable t) {
			throw new ComponentShutdownException(t);
		}
		super.shutdown();
	}

	// -------------------------------------------------------------------------
	// Accès aux URIs des ports entrants
	// -------------------------------------------------------------------------

	public static String registrationPortURI() throws Exception
	{
		return registrationPortIN.getPortURI();
	}

	public String publishingPortURI() throws Exception
	{
		return publishingPortIN.getPortURI();
	}

	public static String privilegedPortURI() throws Exception
	{
		return privilegedPortIN.getPortURI();
	}

	// -------------------------------------------------------------------------
	// Variantes lock-free : le verrou de lecture/écriture correspondant doit
	// déjà être détenu par l'appelant. Évite la double prise de verrou et
	// rend explicite l'invariant "j'ai déjà le verrou".
	// -------------------------------------------------------------------------

	private boolean registeredLocked(String receptionPortURI)
	{
		return this.registeredClients.containsKey(receptionPortURI);
	}

	private boolean channelExistLocked(String channel)
	{
		return this.channels.contains(channel);
	}

	// -------------------------------------------------------------------------
	// Enregistrement (RegistrationCI)
	// -------------------------------------------------------------------------

	public boolean registered(String receptionPortURI) throws Exception
	{
		this.registrationLock.readLock().lock();
		try {
			return this.registeredLocked(receptionPortURI);
		} finally {
			this.registrationLock.readLock().unlock();
		}
	}

	public boolean registered(String receptionPortURI, RegistrationClass rc) throws Exception
	{
		this.registrationLock.readLock().lock();
		try {
			return rc != null && rc.equals(this.registeredClients.get(receptionPortURI));
		} finally {
			this.registrationLock.readLock().unlock();
		}
	}

	public String register(String receptionPortURI, RegistrationClass rc) throws Exception
	{
		if (receptionPortURI == null || receptionPortURI.isEmpty()) {
			throw new IllegalArgumentException("receptionPortURI cannot be null or empty.");
		}
		if (rc == null) {
			throw new IllegalArgumentException("rc cannot be null.");
		}

		// Réservation atomique de l'entrée afin que deux register() concurrents
		// sur la même URI ne réussissent pas tous les deux.
		this.registrationLock.writeLock().lock();
		try {
			if (this.registeredLocked(receptionPortURI)) {
				throw new AlreadyRegisteredException();
			}
			this.registeredClients.put(receptionPortURI, rc);
			this.createdPrivilegedChannelsCount.putIfAbsent(receptionPortURI, 0);
		} finally {
			this.registrationLock.writeLock().unlock();
		}

		// Connexion distante hors verrou : règle "no remote call under lock".
		BrokerReceptionOutboundPort out = new BrokerReceptionOutboundPort(this);
		try {
			out.publishPort();
			this.doPortConnection(
			out.getPortURI(),
			receptionPortURI,
			BrokerClientReceivingConnector.class.getCanonicalName());
		} catch (Exception e) {
			// Rollback : on retire la réservation pour ne pas laisser un
			// client "enregistré" mais sans port de livraison.
			this.registrationLock.writeLock().lock();
			try {
				this.registeredClients.remove(receptionPortURI);
				this.createdPrivilegedChannelsCount.remove(receptionPortURI);
			} finally {
				this.registrationLock.writeLock().unlock();
			}
			try { out.unpublishPort(); } catch (Exception ignored) {}
			try { out.destroyPort(); } catch (Exception ignored) {}
			throw e;
		}

		this.registrationLock.writeLock().lock();
		try {
			this.receptionPortsOUT.put(receptionPortURI, out);
		} finally {
			this.registrationLock.writeLock().unlock();
		}

		return publishingPortIN.getPortURI();
	}

	public String modifyServiceClass(String receptionPortURI, RegistrationClass rc) throws Exception
	{
		if (rc == null) {
			throw new IllegalArgumentException("rc cannot be null.");
		}
		this.registrationLock.writeLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			this.registeredClients.put(receptionPortURI, rc);
		} finally {
			this.registrationLock.writeLock().unlock();
		}
		return publishingPortIN.getPortURI();
	}

	public void unregister(String receptionPortURI) throws Exception
	{
		// Mutations registration + subscriptions ; déconnexion distante après
		// libération des verrous.
		BrokerReceptionOutboundPort out;
		this.registrationLock.writeLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			out = this.receptionPortsOUT.remove(receptionPortURI);
			this.registeredClients.remove(receptionPortURI);
			this.createdPrivilegedChannelsCount.remove(receptionPortURI);
		} finally {
			this.registrationLock.writeLock().unlock();
		}

		this.subscriptionsLock.writeLock().lock();
		try {
			for (Map<String, MessageFilterI> subs : this.subscriptions.values()) {
				subs.remove(receptionPortURI);
			}
		} finally {
			this.subscriptionsLock.writeLock().unlock();
		}

		if (out != null) {
			try {
				if (out.connected()) {
					this.doPortDisconnection(out.getPortURI());
				}
			} catch (Exception ignored) {
			}
			try { out.unpublishPort(); } catch (Exception ignored) {}
			try { out.destroyPort(); } catch (Exception ignored) {}
		}
	}

	// -------------------------------------------------------------------------
	// Canaux et souscriptions (RegistrationCI)
	// -------------------------------------------------------------------------

	public boolean channelExist(String channel) throws Exception
	{
		this.channelsLock.readLock().lock();
		try {
			return this.channelExistLocked(channel);
		} finally {
			this.channelsLock.readLock().unlock();
		}
	}

	// Pré-requis : registrationLock.read et channelsLock.read déjà détenus.
	private boolean channelAuthorisedLocked(String receptionPortURI, String channel) throws Exception
	{
		if (!this.registeredLocked(receptionPortURI)) {
			throw new UnknownClientException(receptionPortURI);
		}
		if (!this.channelExistLocked(channel)) {
			throw new UnknownChannelException(channel);
		}
		if (!this.privilegedChannels.containsKey(channel)) {
			return true;
		}
		PrivilegedChannelInfo info = this.privilegedChannels.get(channel);
		if (info == null || info.authorisedUsersPattern == null) {
			return true;
		}
		return info.authorisedUsersPattern.matcher(receptionPortURI).matches();
	}

	public boolean channelAuthorised(String receptionPortURI, String channel) throws Exception
	{
		this.registrationLock.readLock().lock();
		try {
			this.channelsLock.readLock().lock();
			try {
				return this.channelAuthorisedLocked(receptionPortURI, channel);
			} finally {
				this.channelsLock.readLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}
	}

	public boolean subscribed(String receptionPortURI, String channel) throws Exception
	{
		this.registrationLock.readLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			this.channelsLock.readLock().lock();
			try {
				if (!this.channelExistLocked(channel)) {
					throw new UnknownChannelException(channel);
				}
				this.subscriptionsLock.readLock().lock();
				try {
					Map<String, MessageFilterI> subs = this.subscriptions.get(channel);
					return subs != null && subs.containsKey(receptionPortURI);
				} finally {
					this.subscriptionsLock.readLock().unlock();
				}
			} finally {
				this.channelsLock.readLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}
	}

	public void subscribe(String receptionPortURI, String channel, MessageFilterI filter) throws Exception
	{
		if (filter == null) {
			throw new IllegalArgumentException("filter cannot be null.");
		}
		// Validation sous deux verrous lecture, puis souscription en écriture
		// (ordre canonique respecté).
		this.registrationLock.readLock().lock();
		try {
			this.channelsLock.readLock().lock();
			try {
				if (!this.channelAuthorisedLocked(receptionPortURI, channel)) {
					throw new UnauthorisedClientException();
				}
				this.subscriptionsLock.writeLock().lock();
				try {
					Map<String, MessageFilterI> subs = this.subscriptions.get(channel);
					if (subs == null) {
						// Course possible : le canal a été détruit entre la
						// vérification ci-dessus et la prise du verrou.
						throw new UnknownChannelException(channel);
					}
					subs.put(receptionPortURI, filter);
				} finally {
					this.subscriptionsLock.writeLock().unlock();
				}
			} finally {
				this.channelsLock.readLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}
	}

	public void unsubscribe(String receptionPortURI, String channel) throws Exception
	{
		this.registrationLock.readLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			this.channelsLock.readLock().lock();
			try {
				if (!this.channelExistLocked(channel)) {
					throw new UnknownChannelException(channel);
				}
				this.subscriptionsLock.writeLock().lock();
				try {
					Map<String, MessageFilterI> subs = this.subscriptions.get(channel);
					if (subs == null || !subs.containsKey(receptionPortURI)) {
						throw new NotSubscribedChannelException(
						"Client " + receptionPortURI + " not subscribed to " + channel);
					}
					subs.remove(receptionPortURI);
				} finally {
					this.subscriptionsLock.writeLock().unlock();
				}
			} finally {
				this.channelsLock.readLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}
	}

	public boolean modifyFilter(String receptionPortURI, String channel, MessageFilterI filter) throws Exception
	{
		if (filter == null) {
			throw new IllegalArgumentException("filter cannot be null.");
		}
		this.registrationLock.readLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			this.channelsLock.readLock().lock();
			try {
				if (!this.channelExistLocked(channel)) {
					throw new UnknownChannelException(channel);
				}
				this.subscriptionsLock.writeLock().lock();
				try {
					Map<String, MessageFilterI> subs = this.subscriptions.get(channel);
					if (subs == null || !subs.containsKey(receptionPortURI)) {
						throw new NotSubscribedChannelException(
						"Client " + receptionPortURI + " not subscribed to " + channel);
					}
					subs.put(receptionPortURI, filter);
					return true;
				} finally {
					this.subscriptionsLock.writeLock().unlock();
				}
			} finally {
				this.channelsLock.readLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}
	}

	// -------------------------------------------------------------------------
	// Publication (PublishingCI)
	// -------------------------------------------------------------------------

	public void publish(String publisherReceptionPortURI, String channel, MessageI message) throws Exception
	{
		this.submitPublish(publisherReceptionPortURI, channel, message, null);
	}

	public void publish(String publisherReceptionPortURI, String channel, ArrayList<MessageI> messages) throws Exception
	{
		if (messages == null || messages.isEmpty()) {
			throw new IllegalArgumentException("messages cannot be null or empty.");
		}
		for (MessageI m : messages) {
			this.submitPublish(publisherReceptionPortURI, channel, m, null);
		}
	}

	// -------------------------------------------------------------------------
	// Canaux privilégiés (PrivilegedClientCI)
	// -------------------------------------------------------------------------

	public boolean hasCreatedChannel(String receptionPortURI, String channel) throws Exception
	{
		this.registrationLock.readLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			this.channelsLock.readLock().lock();
			try {
				if (!this.channelExistLocked(channel)) {
					throw new UnknownChannelException(channel);
				}
				PrivilegedChannelInfo info = this.privilegedChannels.get(channel);
				return info != null && info.ownerReceptionPortURI.equals(receptionPortURI);
			} finally {
				this.channelsLock.readLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}
	}

	public boolean channelQuotaReached(String receptionPortURI) throws Exception
	{
		this.registrationLock.readLock().lock();
		try {
			return this.channelQuotaReachedLocked(receptionPortURI);
		} finally {
			this.registrationLock.readLock().unlock();
		}
	}

	// Pré-requis : registrationLock.read (ou .write) déjà détenu.
	private boolean channelQuotaReachedLocked(String receptionPortURI) throws Exception
	{
		if (!this.registeredLocked(receptionPortURI)) {
			throw new UnknownClientException(receptionPortURI);
		}
		RegistrationClass rc = this.registeredClients.get(receptionPortURI);
		int created = this.createdPrivilegedChannelsCount.getOrDefault(receptionPortURI, 0);

		switch (rc) {
			case FREE:
			return true;
			case STANDARD:
			return created >= STANDARD_PRIVILEGED_CHANNEL_QUOTA;
			case PREMIUM:
			return created >= PREMIUM_PRIVILEGED_CHANNEL_QUOTA;
			default:
			return true;
		}
	}

	public void createChannel(String receptionPortURI, String channel, String autorisedUsers) throws Exception
	{
		if (channel == null || channel.isEmpty()) {
			throw new IllegalArgumentException("channel cannot be null or empty.");
		}
		// Compilation regex hors verrou : section critique courte.
		Pattern p = null;
		if (autorisedUsers != null && !autorisedUsers.isEmpty()) {
			p = Pattern.compile(autorisedUsers);
		}

		this.registrationLock.writeLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			RegistrationClass rc = this.registeredClients.get(receptionPortURI);
			if (rc == RegistrationClass.FREE) {
				throw new UnauthorisedClientException();
			}
			if (this.channelQuotaReachedLocked(receptionPortURI)) {
				throw new ChannelQuotaExceededException(
				"Channel quota reached for " + receptionPortURI + " (" + rc + ")");
			}

			this.channelsLock.writeLock().lock();
			try {
				if (this.channelExistLocked(channel)) {
					throw new AlreadyExistingChannelException("Channel already exists: " + channel);
				}
				this.subscriptionsLock.writeLock().lock();
				try {
					this.channels.add(channel);
					this.privilegedChannels.put(channel, new PrivilegedChannelInfo(receptionPortURI, p));
					this.subscriptions.put(channel, new HashMap<>());
					this.inFlightPerChannel.put(channel, new AtomicInteger(0));
					this.createdPrivilegedChannelsCount.put(
					receptionPortURI,
					this.createdPrivilegedChannelsCount.getOrDefault(receptionPortURI, 0) + 1);
				} finally {
					this.subscriptionsLock.writeLock().unlock();
				}
			} finally {
				this.channelsLock.writeLock().unlock();
			}
		} finally {
			this.registrationLock.writeLock().unlock();
		}
	}

	// Helper conservé : la méthode correspondante n'est plus dans PrivilegedClientCI.
	public boolean isAuthorisedUser(String channel, String uri) throws Exception
	{
		if (channel == null || channel.isEmpty()) {
			throw new IllegalArgumentException("channel cannot be null or empty.");
		}
		if (uri == null || uri.isEmpty()) {
			throw new IllegalArgumentException("uri cannot be null or empty.");
		}
		this.registrationLock.readLock().lock();
		try {
			if (!this.registeredLocked(uri)) {
				throw new UnknownClientException(uri);
			}
			this.channelsLock.readLock().lock();
			try {
				if (!this.channelExistLocked(channel)) {
					throw new UnknownChannelException(channel);
				}
				if (!this.privilegedChannels.containsKey(channel)) {
					return true;
				}
				PrivilegedChannelInfo info = this.privilegedChannels.get(channel);
				if (info == null || info.authorisedUsersPattern == null) {
					return true;
				}
				return info.authorisedUsersPattern.matcher(uri).matches();
			} finally {
				this.channelsLock.readLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}
	}

	public void modifyAuthorisedUsers(String receptionPortURI, String channel, String autorisedUsers) throws Exception
	{
		if (autorisedUsers == null || autorisedUsers.isEmpty()) {
			throw new IllegalArgumentException("autorisedUsers cannot be null/empty for modifyAuthorisedUsers.");
		}
		Pattern newPattern = Pattern.compile(autorisedUsers);

		this.registrationLock.readLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			this.channelsLock.writeLock().lock();
			try {
				if (!this.channelExistLocked(channel)) {
					throw new UnknownChannelException(channel);
				}
				PrivilegedChannelInfo info = this.privilegedChannels.get(channel);
				if (info == null) {
					throw new UnauthorisedClientException();
				}
				if (!info.ownerReceptionPortURI.equals(receptionPortURI)) {
					throw new UnauthorisedClientException();
				}
				info.authorisedUsersPattern = newPattern;
			} finally {
				this.channelsLock.writeLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}
	}

	// Helper conservé : sémantique sous-spécifiée dans le CI ; on interdit les
	// URIs matchant via un negative lookahead ajouté au motif courant.
	public void removeAuthorisedUsers(String receptionPortURI, String channel, String regularExpression) throws Exception
	{
		if (regularExpression == null || regularExpression.isEmpty()) {
			throw new IllegalArgumentException("regularExpression cannot be null/empty.");
		}
		Pattern toRemove = Pattern.compile(regularExpression);

		this.registrationLock.readLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			this.channelsLock.writeLock().lock();
			try {
				if (!this.channelExistLocked(channel)) {
					throw new UnknownChannelException(channel);
				}
				PrivilegedChannelInfo info = this.privilegedChannels.get(channel);
				if (info == null) {
					throw new UnauthorisedClientException();
				}
				if (!info.ownerReceptionPortURI.equals(receptionPortURI)) {
					throw new UnauthorisedClientException();
				}
				Pattern current = info.authorisedUsersPattern;
				String currentRegex = current == null ? ".*" : current.pattern();
				String newRegex = "^(?!(" + toRemove.pattern() + ")$)" + currentRegex;
				info.authorisedUsersPattern = Pattern.compile(newRegex);
			} finally {
				this.channelsLock.writeLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}
	}

	public void destroyChannel(String receptionPortURI, String channel) throws Exception
	{
		// Vérification d'autorisation sous verrous lecture, puis attente
		// active sur le compteur in-flight (hors verrous lourds).
		this.registrationLock.readLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			this.channelsLock.readLock().lock();
			try {
				if (!this.channelExistLocked(channel)) {
					throw new UnknownChannelException(channel);
				}
				PrivilegedChannelInfo info = this.privilegedChannels.get(channel);
				if (info == null || !info.ownerReceptionPortURI.equals(receptionPortURI)) {
					throw new UnauthorisedClientException();
				}
			} finally {
				this.channelsLock.readLock().unlock();
			}
		} finally {
			this.registrationLock.readLock().unlock();
		}

		while (true) {
			AtomicInteger a = this.inFlightPerChannel.get(channel);
			if (a == null || a.get() == 0) break;
			Thread.sleep(10);
		}
		this.destroyChannelNow(receptionPortURI, channel);
	}

	public void destroyChannelNow(String receptionPortURI, String channel) throws Exception
	{
		this.registrationLock.writeLock().lock();
		try {
			if (!this.registeredLocked(receptionPortURI)) {
				throw new UnknownClientException(receptionPortURI);
			}
			this.channelsLock.writeLock().lock();
			try {
				if (!this.channelExistLocked(channel)) {
					throw new UnknownChannelException(channel);
				}
				PrivilegedChannelInfo info = this.privilegedChannels.get(channel);
				if (info == null) {
					throw new UnauthorisedClientException();
				}
				if (!info.ownerReceptionPortURI.equals(receptionPortURI)) {
					throw new UnauthorisedClientException();
				}

				this.subscriptionsLock.writeLock().lock();
				try {
					this.subscriptions.remove(channel);
				} finally {
					this.subscriptionsLock.writeLock().unlock();
				}
				this.channels.remove(channel);
				this.privilegedChannels.remove(channel);
				this.inFlightPerChannel.remove(channel);
				this.createdPrivilegedChannelsCount.put(
				receptionPortURI,
				Math.max(0, this.createdPrivilegedChannelsCount.getOrDefault(receptionPortURI, 1) - 1));
			} finally {
				this.channelsLock.writeLock().unlock();
			}
		} finally {
			this.registrationLock.writeLock().unlock();
		}
	}
}

