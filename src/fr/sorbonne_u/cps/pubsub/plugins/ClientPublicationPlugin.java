package fr.sorbonne_u.cps.pubsub.plugins;

import fr.sorbonne_u.components.AbstractPlugin;
import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.base.connectors.ClientBrokerPublishingConnector;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientPublishingOutboundPort;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Plugin client implémentant les opérations de publication (CDC §3.5).
 *
 * <p><strong>Description</strong></p>
 *
 * <p>
 * Ce plugin <em>possède son propre port outbound {@code PublishingCI}</em>
 * (refactor soutenance §5.2). Le précédent design centralisait tous les
 * ports outbound dans le {@link ClientRegistrationPlugin}, ce qui forçait
 * un composant qui ne souscrit qu'à un seul rôle (par ex. un publieur pur)
 * à instancier malgré tout les ports privilégiés et de souscription qu'il
 * n'utilisera jamais. Conformément aux exemples
 * {@code SimpleRingOverlayNetwork-CM9} (un greffon = un port) et
 * {@code ProducerConsumer-13032026} (greffons producteur/consommateur
 * disjoints), chaque greffon possède désormais le port qui sert son rôle.
 * </p>
 *
 * <p>L'identité du client transmise au broker reste l'URI du port
 * {@code ReceivingCI} obtenue via
 * {@link ClientRegistrationPlugin#getReceptionPortURI()} : ce greffon ne
 * possède pas de port inbound mais a besoin du
 * {@link ClientRegistrationPlugin} pour récupérer cette identité ainsi
 * que le port outbound {@code RegistrationCI} utilisé par
 * {@link #channelExist(String)} et {@link #channelAuthorised(String)}.</p>
 *
 * <p>Cycle de vie BCM :</p>
 * <ul>
 *   <li>{@link #installOn} — déclare {@code PublishingCI} comme requise
 *       (idempotent) ;</li>
 *   <li>{@link #initialise} — publie le port et le connecte au port
 *       inbound {@code PublishingCI} du broker, dont l'URI est dérivée
 *       déterministement via {@link Broker#publishingPortURIFor(String)} ;</li>
 *   <li>{@link #finalise} — déconnecte le port s'il l'est encore ;</li>
 *   <li>{@link #uninstall} — déconnecte (défensif), dépublie et détruit
 *       le port, retire l'interface requise.</li>
 * </ul>
 *
 * <p>L'URI du plugin suit la convention
 * {@code <reflectionURI>-publication-plugin}.</p>
 *
 * <p>Created on : 2026-02-04</p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class		ClientPublicationPlugin
extends		AbstractPlugin
implements		ClientPublicationI
{
	private static final long serialVersionUID = 1L;

	/** Plugin de registration : fournit l'URI {@code ReceivingCI} (identité
	 *  du client) et le port outbound {@code RegistrationCI} pour
	 *  {@code channelExist} / {@code channelAuthorised}. */
	protected final ClientRegistrationPlugin registrationPlugin;
	/** URI de réflexion du broker, utilisée pour dériver l'URI du port
	 *  inbound {@code PublishingCI} via
	 *  {@link Broker#publishingPortURIFor(String)}. */
	protected final String brokerReflectionURI;

	/** Port outbound {@code PublishingCI} possédé par ce greffon. */
	protected ClientPublishingOutboundPort publishingPortOUT;
	/** {@code true} ssi {@link #installOn} a effectivement déclaré
	 *  {@code PublishingCI} sur le composant ; sinon, l'interface était
	 *  déjà déclarée (par exemple via une annotation {@code @RequiredInterfaces}
	 *  sur le composant) et le greffon ne doit pas la retirer dans
	 *  {@link #uninstall} sous peine de violer la postcondition BCM. */
	private boolean addedRequiredInterface;

	/**
	 * Crée un greffon de publication adossé à un greffon de registration.
	 *
	 * @param registrationPlugin	greffon de registration (identité +
	 *								canal {@code RegistrationCI}) ; non
	 *								{@code null}.
	 * @param brokerReflectionURI	URI de réflexion du broker auquel se
	 *								connecter ; non {@code null} et non
	 *								vide.
	 */
	public ClientPublicationPlugin(ClientRegistrationPlugin registrationPlugin,
								   String brokerReflectionURI)
	{
		super();
		this.registrationPlugin = Objects.requireNonNull(
			registrationPlugin, "registrationPlugin");
		this.brokerReflectionURI = Objects.requireNonNull(
			brokerReflectionURI, "brokerReflectionURI");
		if (brokerReflectionURI.isEmpty()) {
			throw new IllegalArgumentException(
				"brokerReflectionURI must not be empty");
		}
	}

	/**
	 * Constructeur héritage : utilisé par les tests qui ne fournissent pas
	 * encore l'URI du broker. À éviter pour le code applicatif.
	 *
	 * @deprecated préférez {@link #ClientPublicationPlugin(ClientRegistrationPlugin, String)}.
	 */
	@Deprecated
	public ClientPublicationPlugin(ClientRegistrationPlugin registrationPlugin)
	{
		super();
		this.registrationPlugin = Objects.requireNonNull(
			registrationPlugin, "registrationPlugin");
		this.brokerReflectionURI = null;
	}

	/**
	 * Déclare {@code PublishingCI} sur le composant propriétaire (idempotent).
	 *
	 * @param owner		composant sur lequel installer le greffon.
	 * @throws Exception	si l'installation BCM échoue.
	 */
	@Override
	public void installOn(ComponentI owner) throws Exception
	{
		super.installOn(owner);
		if (!owner.isRequiredInterface(PublishingCI.class)) {
			this.addRequiredInterface(PublishingCI.class);
			this.addedRequiredInterface = true;
		}
	}

	/**
	 * Publie le port outbound {@code PublishingCI} et le connecte au port
	 * inbound correspondant du broker (URI dérivée via
	 * {@link Broker#publishingPortURIFor(String)}).
	 *
	 * <p>Le greffon se connecte avant tout appel à
	 * {@link ClientRegistrationPlugin#register} : la connexion ne donne
	 * <em>aucune</em> permission ; les permissions sont vérifiées par le
	 * broker à chaque appel via {@code requireRegistered(rip)} et, si
	 * besoin, {@code requireServiceClass(rip, …)}.</p>
	 *
	 * @throws Exception	si la publication ou la connexion d'un port
	 *						échoue, ou si l'URI du broker n'a pas été
	 *						fournie au constructeur.
	 */
	@Override
	public void initialise() throws Exception
	{
		super.initialise();
		if (this.brokerReflectionURI == null) {
			throw new IllegalStateException(
				"ClientPublicationPlugin requires a broker reflection inbound "
				+ "port URI; use the (registrationPlugin, brokerReflectionURI) "
				+ "constructor.");
		}
		this.publishingPortOUT =
			new ClientPublishingOutboundPort(this.getOwner());
		this.publishingPortOUT.publishPort();
		this.getOwner().doPortConnection(
			this.publishingPortOUT.getPortURI(),
			Broker.publishingPortURIFor(this.brokerReflectionURI),
			ClientBrokerPublishingConnector.class.getCanonicalName());
	}

	/**
	 * Déconnecte le port outbound s'il l'est encore.
	 *
	 * @throws Exception	si la déconnexion BCM échoue.
	 */
	@Override
	public void finalise() throws Exception
	{
		if (this.publishingPortOUT != null
				&& this.publishingPortOUT.connected()) {
			this.getOwner().doPortDisconnection(
				this.publishingPortOUT.getPortURI());
		}
		super.finalise();
	}

	/**
	 * Démantèle le greffon : déconnecte (défensif), dépublie et détruit
	 * le port outbound, puis retire {@code PublishingCI} si elle a été
	 * déclarée par {@link #installOn}.
	 *
	 * @throws Exception	si une opération BCM échoue.
	 */
	@Override
	public void uninstall() throws Exception
	{
		if (this.publishingPortOUT != null
				&& this.publishingPortOUT.connected()) {
			this.getOwner().doPortDisconnection(
				this.publishingPortOUT.getPortURI());
		}
		if (this.publishingPortOUT != null
				&& !this.publishingPortOUT.isDestroyed()) {
			if (this.publishingPortOUT.isPublished()) {
				this.publishingPortOUT.unpublishPort();
			}
			this.publishingPortOUT.destroyPort();
		}
		if (this.addedRequiredInterface
				&& this.getOwner().isRequiredInterface(PublishingCI.class)) {
			this.removeRequiredInterface(PublishingCI.class);
			this.addedRequiredInterface = false;
		}
		super.uninstall();
	}

	// ------------------------------------------------------------------
	// ClientPublicationI
	// ------------------------------------------------------------------

	/**
	 * {@inheritDoc}
	 *
	 * <p>Délègue au port {@code RegistrationCI} du greffon de registration
	 * (le canal {@code channelExist} fait partie de {@code RegistrationCI}
	 * dans ce projet, voir l'interface frozen).</p>
	 *
	 * @param channel	nom du canal interrogé.
	 * @return			{@code true} ssi le canal existe côté broker.
	 */
	@Override
	public boolean channelExist(String channel)
	{
		try {
			return this.registrationPlugin
				.getRegistrationPortOUT().channelExist(channel);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * @param channel	nom du canal interrogé.
	 * @return			{@code true} ssi ce client est autorisé à publier
	 *					sur {@code channel}.
	 * @throws UnknownClientException	si le client n'est pas enregistré.
	 * @throws UnknownChannelException	si {@code channel} n'existe pas.
	 */
	@Override
	public boolean channelAuthorised(String channel)
	throws UnknownClientException, UnknownChannelException
	{
		try {
			return this.registrationPlugin
				.getRegistrationPortOUT().channelAuthorised(
					this.registrationPlugin.getReceptionPortURI(),
					channel);
		} catch (UnknownClientException | UnknownChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Publie {@code message} sur {@code channel} via le port outbound
	 * {@code PublishingCI} possédé par ce greffon.
	 *
	 * @param channel	canal de publication.
	 * @param message	message à publier.
	 * @throws UnknownClientException		si le client n'est pas enregistré.
	 * @throws UnknownChannelException		si {@code channel} n'existe pas.
	 * @throws UnauthorisedClientException	si le client n'est pas autorisé.
	 */
	@Override
	public void publish(String channel, MessageI message)
	throws UnknownClientException, UnknownChannelException, UnauthorisedClientException
	{
		try {
			this.publishingPortOUT.publish(
				this.registrationPlugin.getReceptionPortURI(),
				channel,
				message);
		} catch (UnknownClientException
				| UnknownChannelException
				| UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Variante batch de {@link #publish(String, MessageI)}.
	 *
	 * @param channel	canal de publication.
	 * @param messages	liste de messages à publier.
	 * @throws UnknownClientException		si le client n'est pas enregistré.
	 * @throws UnknownChannelException		si {@code channel} n'existe pas.
	 * @throws UnauthorisedClientException	si le client n'est pas autorisé.
	 */
	@Override
	public void publish(String channel, ArrayList<MessageI> messages)
	throws UnknownClientException, UnknownChannelException, UnauthorisedClientException
	{
		try {
			this.publishingPortOUT.publish(
				this.registrationPlugin.getReceptionPortURI(),
				channel,
				messages);
		} catch (UnknownClientException
				| UnknownChannelException
				| UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Soumet la publication via {@code runTask} sur l'un des executors du
	 * composant propriétaire pour ne pas bloquer le thread appelant.
	 *
	 * @param channel	canal de publication.
	 * @param message	message à publier.
	 */
	@Override
	public void asyncPublishAndNotify(String channel, MessageI message)
	{
		final ClientPublicationPlugin self = this;
		this.getOwner().runTask(o -> {
			try {
				self.publish(channel, message);
			} catch (Exception e) { e.printStackTrace(); }
		});
	}

	/**
	 * Variante batch de {@link #asyncPublishAndNotify(String, MessageI)}.
	 *
	 * @param channel	canal de publication.
	 * @param messages	liste de messages à publier.
	 */
	@Override
	public void asyncPublishAndNotify(String channel, ArrayList<MessageI> messages)
	{
		final ClientPublicationPlugin self = this;
		this.getOwner().runTask(o -> {
			try {
				self.publish(channel, messages);
			} catch (Exception e) { e.printStackTrace(); }
		});
	}
}
