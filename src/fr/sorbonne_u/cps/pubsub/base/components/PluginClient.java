package fr.sorbonne_u.cps.pubsub.base.components;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.annotations.OfferedInterfaces;
import fr.sorbonne_u.components.annotations.RequiredInterfaces;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
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
import fr.sorbonne_u.cps.pubsub.plugins.ClientPublicationPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientPrivilegedPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientSubscriptionPlugin;

/**
 * Composant client basé sur les quatre plugins pub/sub (CDC §3.5.1 et §3.5.2).
 *
 * <p>
 * Ce composant compose explicitement quatre plugins (cf. paquetage
 * {@code fr.sorbonne_u.cps.pubsub.plugins}) plutôt que d'agréger
 * manuellement les ports comme {@link Client} :
 * </p>
 * <ul>
 *   <li>{@link ClientRegistrationPlugin} — porte le port inbound
 *       {@link ReceivingCI} et le port outbound {@link RegistrationCI} ;
 *       toutes les autres opérations passent par lui pour récupérer
 *       l'URI du port de réception (qui sert d'identité du client) ;</li>
 *   <li>{@link ClientSubscriptionPlugin} — souscriptions, modification
 *       de filtre et API de réception avancée ;</li>
 *   <li>{@link ClientPublicationPlugin} — port outbound
 *       {@link PublishingCI} ;</li>
 *   <li>{@link ClientPrivilegedPlugin} — port outbound
 *       {@link PrivilegedClientCI} pour la gestion des canaux privilégiés.</li>
 * </ul>
 *
 * <p>
 * Coexiste avec le composant historique {@link Client} pour ne pas casser
 * les démos déjà écrites. Préférer {@link AbstractPluginComponent} pour les
 * nouveaux composants spécialisés (publisher / subscriber dédiés).
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
@OfferedInterfaces(offered = { ReceivingCI.class })
@RequiredInterfaces(required = {
	RegistrationCI.class,
	PublishingCI.class,
	PrivilegedClientCI.class
})
public class PluginClient extends AbstractComponent
{
	protected final ClientRegistrationPlugin registrationPlugin;
	public final ClientSubscriptionPlugin subscriptionPlugin;
	protected final ClientPublicationPlugin publicationPlugin;
	protected final ClientPrivilegedPlugin privilegedPlugin;

	/**
	 * @deprecated use {@link #PluginClient(String, int, int, String)} so
	 *             that this client knows which broker to connect to in
	 *             a multi-broker environment (Phase C.3).
	 */
	@Deprecated
	protected PluginClient(
		String reflectionInboundPortURI,
		int nbThreads,
		int nbSchedulableThreads) throws Exception
	{
		this(reflectionInboundPortURI, nbThreads, nbSchedulableThreads, null);
	}

	/**
	 * Preferred constructor (Phase C.3): also takes the
	 * {@code brokerReflectionURI} so the registration plugin knows
	 * which broker to contact.
	 */
	protected PluginClient(
		String reflectionInboundPortURI,
		int nbThreads,
		int nbSchedulableThreads,
		String brokerReflectionURI) throws Exception
	{
		super(reflectionInboundPortURI, nbThreads, nbSchedulableThreads);

		this.registrationPlugin = new ClientRegistrationPlugin(brokerReflectionURI);
		this.registrationPlugin.setPluginURI(reflectionInboundPortURI + "-registration-plugin");
		this.installPlugin(this.registrationPlugin);

		this.subscriptionPlugin = new ClientSubscriptionPlugin(
			this.registrationPlugin,
			this::onReceive);
		this.subscriptionPlugin.setPluginURI(reflectionInboundPortURI + "-subscription-plugin");
		this.installPlugin(this.subscriptionPlugin);

		this.publicationPlugin = new ClientPublicationPlugin(this.registrationPlugin);
		this.publicationPlugin.setPluginURI(reflectionInboundPortURI + "-publication-plugin");
		this.installPlugin(this.publicationPlugin);

		this.privilegedPlugin = new ClientPrivilegedPlugin(this.registrationPlugin);
		this.privilegedPlugin.setPluginURI(reflectionInboundPortURI + "-privileged-plugin");
		this.installPlugin(this.privilegedPlugin);
	}

	@Override
	public void execute() throws Exception
	{
		super.execute();
	}

	/** URI of this client inbound port offering ReceivingCI. */
	public String getReceptionPortURI()
	{
		try {
			return this.registrationPlugin.getReceptionPortURI();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// ---------------------------------------------------------------------
	// Registration API
	// ---------------------------------------------------------------------

	/** @return {@code true} ssi ce client est actuellement enregistré côté broker. */
	public boolean registered()
	{
		return this.registrationPlugin.registered();
	}

	/**
	 * Enregistre ce client auprès du broker dans la classe de service {@code rc}.
	 *
	 * @throws AlreadyRegisteredException si le client est déjà enregistré.
	 */
	public void register(RegistrationClass rc) throws AlreadyRegisteredException
	{
		this.registrationPlugin.register(rc);
	}

	/**
	 * Demande au broker un changement de classe de service (upgrade ou
	 * downgrade vers FREE).
	 *
	 * @throws UnknownClientException     si le client n'est pas enregistré.
	 * @throws AlreadyRegisteredException si la nouvelle classe est identique
	 *                                    à la classe actuelle.
	 */
	public void modifyServiceClass(RegistrationClass rc) throws UnknownClientException, AlreadyRegisteredException
	{
		this.registrationPlugin.modifyServiceClass(rc);
	}

	/**
	 * Désenregistre ce client (libère ses canaux privilégiés et ses
	 * souscriptions côté broker).
	 *
	 * @throws UnknownClientException si le client n'est pas enregistré.
	 */
	public void unregister() throws UnknownClientException
	{
		this.registrationPlugin.unregister();
	}

	// ---------------------------------------------------------------------
	// Subscription API
	// ---------------------------------------------------------------------

	/**
	 * Souscrit ce client au canal {@code channel} avec le filtre {@code filter}
	 * (qui peut être {@code null} pour « accepter tout »).
	 */
	public void subscribe(String channel, MessageFilterI filter)
	throws UnknownClientException, UnknownChannelException, UnauthorisedClientException
	{
		this.subscriptionPlugin.subscribe(channel, filter);
	}

	/** Désabonne ce client du canal {@code channel}. */
	public void unsubscribe(String channel)
	throws UnknownClientException, UnknownChannelException, UnauthorisedClientException, NotSubscribedChannelException
	{
		this.subscriptionPlugin.unsubscribe(channel);
	}

	/** Remplace le filtre actif sur {@code channel} par {@code filter}. */
	public void modifyFilter(String channel, MessageFilterI filter)
	throws UnknownClientException, UnknownChannelException, UnauthorisedClientException, NotSubscribedChannelException
	{
		this.subscriptionPlugin.modifyFilter(channel, filter);
	}

	// ---------------------------------------------------------------------
	// Publication API
	// ---------------------------------------------------------------------

	/** Publie {@code message} sur {@code channel} (livraison asynchrone). */
	public void publish(String channel, MessageI message)
	throws UnknownClientException, UnknownChannelException, UnauthorisedClientException
	{
		this.publicationPlugin.publish(channel, message);
	}

	// ---------------------------------------------------------------------
	// Privileged channel management
	// ---------------------------------------------------------------------

	/**
	 * Crée un canal privilégié appartenant à ce client.
	 *
	 * @param channel          nom du canal à créer.
	 * @param authorisedUsers  regex appliquée aux URIs de port de réception
	 *                         des clients autorisés (peut être vide pour
	 *                         « tout client enregistré »).
	 */
	public void createChannel(String channel, String authorisedUsers)
	throws UnknownClientException,
			fr.sorbonne_u.cps.pubsub.exceptions.AlreadyExistingChannelException,
			fr.sorbonne_u.cps.pubsub.exceptions.ChannelQuotaExceededException
	{
		this.privilegedPlugin.createChannel(channel, authorisedUsers);
	}

	/** @return {@code true} ssi ce client est propriétaire de {@code channel}. */
	public boolean hasCreatedChannel(String channel)
	throws UnknownClientException, fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException
	{
		return this.privilegedPlugin.hasCreatedChannel(channel);
	}

	/** @return {@code true} ssi ce client a atteint son quota de canaux privilégiés. */
	public boolean channelQuotaReached() throws UnknownClientException
	{
		return this.privilegedPlugin.channelQuotaReached();
	}

	/** Modifie la regex {@code authorisedUsers} d'un canal privilégié possédé. */
	public void modifyAuthorisedUsers(String channel, String authorisedUsers)
	throws UnknownClientException,
			fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException,
			UnauthorisedClientException
	{
		this.privilegedPlugin.modifyAuthorisedUsers(channel, authorisedUsers);
	}

	/**
	 * Détruit un canal privilégié possédé. Asynchrone : le retour ne garantit
	 * pas que la destruction soit visible immédiatement (cf.
	 * {@link #destroyChannelNow}).
	 */
	public void destroyChannel(String channel)
	throws UnknownClientException,
			fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException,
			UnauthorisedClientException
	{
		this.privilegedPlugin.destroyChannel(channel);
	}

	/** Variante synchrone de {@link #destroyChannel} : la destruction est
	 *  achevée au retour de la méthode. */
	public void destroyChannelNow(String channel)
	throws UnknownClientException,
			fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException,
			UnauthorisedClientException
	{
		this.privilegedPlugin.destroyChannelNow(channel);
	}

	// ---------------------------------------------------------------------
	// Reception hook
	// ---------------------------------------------------------------------

	/**
	 * Callback de réception câblé dans le {@link ClientSubscriptionPlugin}.
	 * Implémentation par défaut : trace + log. Les sous-classes peuvent
	 * remplacer ce comportement (file d'attente, filtrage applicatif, etc.).
	 */
	public void onReceive(String channel, MessageI message)
	{
		if (message == null) {
			this.traceMessage(
				"PluginClient " + this.getReflectionInboundPortURI() + " received empty batch on " + channel + "\n");
			return;
		}
		this.traceMessage(
			"PluginClient " + this.getReflectionInboundPortURI()
				+ " received on " + channel + " payload=" + message.getPayload()
				+ " timestamp=" + message.getTimeStamp() + "\n");
		this.logMessage(
			"[LOG] PluginClient " + this.getReflectionInboundPortURI()
				+ " received on " + channel + " payload=" + message.getPayload()
				+ " timestamp=" + message.getTimeStamp() + "\n");
	}

	// Rely on the BCM lifecycle (uninstallPlugin/shutdown) to cleanly disconnect
	// and destroy ports. Avoid custom shutdown actions as they can conflict with
	// the framework teardown order when assertions are enabled (-ea).
}
