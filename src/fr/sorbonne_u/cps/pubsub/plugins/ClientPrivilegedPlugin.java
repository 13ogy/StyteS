package fr.sorbonne_u.cps.pubsub.plugins;

import fr.sorbonne_u.components.AbstractPlugin;
import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.base.connectors.PrivilegedConnector;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientPrivilegedOutboundPort;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyExistingChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.ChannelQuotaExceededException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Plugin client implémentant les opérations de gestion de canaux privilégiés (CDC §3.5.2).
 *
 * <p><strong>Description</strong>
 *
 * <p>Ce greffon <em>possède son propre port outbound {@code PrivilegedClientCI}</em>. Il n'hérite
 * plus de {@link ClientPublicationPlugin} : la composition est désormais explicite côté composant
 * (les composants STANDARD/PREMIUM peuvent installer ce greffon seul ; comme {@code
 * PrivilegedClientCI extends PublishingCI}, ce port suffit pour publier sur les canaux privilégiés
 * ou libres).
 *
 * <p>L'identité du client transmise au broker reste l'URI du port {@code ReceivingCI} obtenue via
 * {@link ClientRegistrationPlugin#getReceptionPortURI()}.
 *
 * <p>Cycle de vie BCM :
 *
 * <ul>
 *   <li>{@link #installOn} — déclare {@code PrivilegedClientCI} comme requise (idempotent) ;
 *   <li>{@link #initialise} — publie le port et le connecte au port inbound {@code
 *       PrivilegedClientCI} du broker, dont l'URI est dérivée déterministement via {@link
 *       Broker#privilegedPortURIFor(String)} ;
 *   <li>{@link #finalise} — déconnecte le port s'il l'est encore ;
 *   <li>{@link #uninstall} — déconnecte (défensif), dépublie et détruit le port, retire l'interface
 *       requise.
 * </ul>
 *
 * <p>L'URI du plugin suit la convention {@code <reflectionURI>-privileged-plugin}.
 *
 * <p>Created on : 2026-02-04
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class ClientPrivilegedPlugin extends AbstractPlugin implements ClientPrivilegedI {
	private static final long serialVersionUID = 1L;

	/** Greffon de registration (identité du client). */
	protected final ClientRegistrationPlugin registrationPlugin;

	/**
	 * URI de réflexion du broker, utilisée pour dériver l'URI du port inbound {@code
	 * PrivilegedClientCI} via {@link Broker#privilegedPortURIFor(String)}.
	 */
	protected final String brokerReflectionURI;

	/** Port outbound {@code PrivilegedClientCI} possédé par ce greffon. */
	protected ClientPrivilegedOutboundPort privilegedPortOUT;

	/**
	 * {@code true} ssi {@link #installOn} a effectivement déclaré {@code PrivilegedClientCI} sur le
	 * composant ; sinon, l'interface était déjà déclarée (via une annotation
	 * {@code @RequiredInterfaces}) et le greffon ne doit pas la retirer dans {@link #uninstall}
	 * sous peine de violer la postcondition BCM.
	 */
	private boolean addedRequiredInterface;

	/**
	 * Crée un greffon privilégié adossé à un greffon de registration.
	 *
	 * @param registrationPlugin greffon de registration (identité + canal {@code RegistrationCI}) ;
	 *     non {@code null}.
	 * @param brokerReflectionURI URI de réflexion du broker auquel se connecter ; non {@code null}
	 *     et non vide.
	 */
	public ClientPrivilegedPlugin(
			ClientRegistrationPlugin registrationPlugin, String brokerReflectionURI) {
		super();
		this.registrationPlugin = Objects.requireNonNull(registrationPlugin, "registrationPlugin");
		this.brokerReflectionURI =
				Objects.requireNonNull(brokerReflectionURI, "brokerReflectionURI");
		if (brokerReflectionURI.isEmpty()) {
			throw new IllegalArgumentException("brokerReflectionURI must not be empty");
		}
	}

	/**
	 * Constructeur héritage : utilisé par les tests qui ne fournissent pas encore l'URI du broker.
	 * À éviter pour le code applicatif.
	 *
	 * @deprecated préférez {@link #ClientPrivilegedPlugin(ClientRegistrationPlugin, String)}.
	 */
	@Deprecated
	public ClientPrivilegedPlugin(ClientRegistrationPlugin registrationPlugin) {
		super();
		this.registrationPlugin = Objects.requireNonNull(registrationPlugin, "registrationPlugin");
		this.brokerReflectionURI = null;
	}

	/**
	 * Déclare {@code PrivilegedClientCI} sur le composant propriétaire (idempotent).
	 *
	 * @param owner composant sur lequel installer le greffon.
	 * @throws Exception si l'installation BCM échoue.
	 */
	@Override
	public void installOn(ComponentI owner) throws Exception {
		super.installOn(owner);
		if (!owner.isRequiredInterface(PrivilegedClientCI.class)) {
			this.addRequiredInterface(PrivilegedClientCI.class);
			this.addedRequiredInterface = true;
		}
	}

	/**
	 * Publie le port outbound {@code PrivilegedClientCI} et le connecte au port inbound
	 * correspondant du broker (URI dérivée via {@link Broker#privilegedPortURIFor(String)}).
	 *
	 * <p>La connexion ne donne aucune permission : un client FREE peut connecter ce port sans
	 * pouvoir créer de canal privilégié, le broker vérifiant la classe de service à chaque appel
	 * via {@code requireServiceClass(rip, STANDARD|PREMIUM)}.
	 *
	 * @throws Exception si la publication ou la connexion échoue, ou si l'URI du broker n'a pas été
	 *     fournie au constructeur.
	 */
	@Override
	public void initialise() throws Exception {
		super.initialise();
		if (this.brokerReflectionURI == null) {
			throw new IllegalStateException(
					"ClientPrivilegedPlugin requires a broker reflection inbound "
							+ "port URI; use the (registrationPlugin, brokerReflectionURI) "
							+ "constructor.");
		}
		this.privilegedPortOUT = new ClientPrivilegedOutboundPort(this.getOwner());
		this.privilegedPortOUT.publishPort();
		this.getOwner()
				.doPortConnection(
						this.privilegedPortOUT.getPortURI(),
						Broker.privilegedPortURIFor(this.brokerReflectionURI),
						PrivilegedConnector.class.getCanonicalName());
	}

	/**
	 * Déconnecte le port outbound s'il l'est encore.
	 *
	 * @throws Exception si la déconnexion BCM échoue.
	 */
	@Override
	public void finalise() throws Exception {
		if (this.privilegedPortOUT != null && this.privilegedPortOUT.connected()) {
			this.getOwner().doPortDisconnection(this.privilegedPortOUT.getPortURI());
		}
		super.finalise();
	}

	/**
	 * Démantèle le greffon : déconnecte (défensif), dépublie et détruit le port outbound, puis
	 * retire {@code PrivilegedClientCI} si elle a été déclarée par {@link #installOn}.
	 *
	 * @throws Exception si une opération BCM échoue.
	 */
	@Override
	public void uninstall() throws Exception {
		if (this.privilegedPortOUT != null && this.privilegedPortOUT.connected()) {
			this.getOwner().doPortDisconnection(this.privilegedPortOUT.getPortURI());
		}
		if (this.privilegedPortOUT != null && !this.privilegedPortOUT.isDestroyed()) {
			if (this.privilegedPortOUT.isPublished()) {
				this.privilegedPortOUT.unpublishPort();
			}
			this.privilegedPortOUT.destroyPort();
		}
		if (this.addedRequiredInterface
				&& this.getOwner().isRequiredInterface(PrivilegedClientCI.class)) {
			this.removeRequiredInterface(PrivilegedClientCI.class);
			this.addedRequiredInterface = false;
		}
		super.uninstall();
	}

	// ------------------------------------------------------------------
	// ClientPrivilegedI
	// ------------------------------------------------------------------

	/**
	 * @param channel nom du canal interrogé.
	 * @return {@code true} ssi le client a créé {@code channel}.
	 * @throws UnknownClientException si le client n'est pas enregistré.
	 * @throws UnknownChannelException si {@code channel} n'existe pas.
	 */
	@Override
	public boolean hasCreatedChannel(String channel)
			throws UnknownClientException, UnknownChannelException {
		try {
			return this.privilegedPortOUT.hasCreatedChannel(
					this.registrationPlugin.getReceptionPortURI(), channel);
		} catch (UnknownClientException | UnknownChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @return {@code true} ssi le quota de canaux privilégiés est atteint.
	 * @throws UnknownClientException si le client n'est pas enregistré.
	 */
	@Override
	public boolean channelQuotaReached() throws UnknownClientException {
		try {
			return this.privilegedPortOUT.channelQuotaReached(
					this.registrationPlugin.getReceptionPortURI());
		} catch (UnknownClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Crée un canal privilégié (cf. {@link ClientPrivilegedI#createChannel(String, String)}).
	 *
	 * @param channel nom du canal à créer.
	 * @param autorisedUsers motif regex sélectionnant les URIs {@code ReceivingCI} autorisées.
	 * @throws UnknownClientException si le client n'est pas enregistré.
	 * @throws AlreadyExistingChannelException si {@code channel} existe déjà.
	 * @throws ChannelQuotaExceededException si le quota du client est atteint.
	 */
	@Override
	public void createChannel(String channel, String autorisedUsers)
			throws UnknownClientException,
					AlreadyExistingChannelException,
					ChannelQuotaExceededException {
		try {
			this.privilegedPortOUT.createChannel(
					this.registrationPlugin.getReceptionPortURI(), channel, autorisedUsers);
		} catch (UnknownClientException
				| AlreadyExistingChannelException
				| ChannelQuotaExceededException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Modifie le motif regex des utilisateurs autorisés sur un canal dont ce client est le
	 * créateur.
	 *
	 * @param channel canal cible.
	 * @param autorisedUsers nouveau motif regex.
	 * @throws UnknownClientException si le client n'est pas enregistré.
	 * @throws UnknownChannelException si {@code channel} n'existe pas.
	 * @throws UnauthorisedClientException si le client n'est pas le créateur de {@code channel}.
	 */
	@Override
	public void modifyAuthorisedUsers(String channel, String autorisedUsers)
			throws UnknownClientException, UnknownChannelException, UnauthorisedClientException {
		try {
			this.privilegedPortOUT.modifyAuthorisedUsers(
					this.registrationPlugin.getReceptionPortURI(), channel, autorisedUsers);
		} catch (UnknownClientException | UnknownChannelException | UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Détruit un canal privilégié dont ce client est le créateur, en laissant le broker terminer
	 * proprement les livraisons en cours.
	 *
	 * @param channel canal à détruire.
	 * @throws UnknownClientException si le client n'est pas enregistré.
	 * @throws UnknownChannelException si {@code channel} n'existe pas.
	 * @throws UnauthorisedClientException si le client n'est pas le créateur de {@code channel}.
	 */
	@Override
	public void destroyChannel(String channel)
			throws UnknownClientException, UnknownChannelException, UnauthorisedClientException {
		try {
			this.privilegedPortOUT.destroyChannel(
					this.registrationPlugin.getReceptionPortURI(), channel);
		} catch (UnknownClientException | UnknownChannelException | UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Variante immédiate de {@link #destroyChannel(String)} : pas de drain des messages en cours.
	 *
	 * @param channel canal à détruire.
	 * @throws UnknownClientException si le client n'est pas enregistré.
	 * @throws UnknownChannelException si {@code channel} n'existe pas.
	 * @throws UnauthorisedClientException si le client n'est pas le créateur.
	 */
	@Override
	public void destroyChannelNow(String channel)
			throws UnknownClientException, UnknownChannelException, UnauthorisedClientException {
		try {
			this.privilegedPortOUT.destroyChannelNow(
					this.registrationPlugin.getReceptionPortURI(), channel);
		} catch (UnknownClientException | UnknownChannelException | UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// ------------------------------------------------------------------
	// Publication via le port privilégié (PrivilegedClientCI extends
	// PublishingCI). Permet à un composant qui n'installe que ce greffon
	// (typique pour PREMIUM/STANDARD) de publier sans installer en plus
	// le ClientPublicationPlugin.
	// ------------------------------------------------------------------

	/**
	 * Publie un message via le port privilégié (utile pour les classes de service qui n'installent
	 * pas le greffon de publication).
	 *
	 * @param channel canal de publication.
	 * @param message message à publier.
	 * @throws UnknownClientException si le client n'est pas enregistré.
	 * @throws UnknownChannelException si {@code channel} n'existe pas.
	 * @throws UnauthorisedClientException si le client n'est pas autorisé.
	 */
	public void publish(String channel, MessageI message)
			throws UnknownClientException, UnknownChannelException, UnauthorisedClientException {
		try {
			this.privilegedPortOUT.publish(
					this.registrationPlugin.getReceptionPortURI(), channel, message);
		} catch (UnknownClientException | UnknownChannelException | UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Variante batch de {@link #publish(String, MessageI)}, via le port privilégié.
	 *
	 * @param channel canal de publication.
	 * @param messages liste de messages à publier.
	 * @throws UnknownClientException si le client n'est pas enregistré.
	 * @throws UnknownChannelException si {@code channel} n'existe pas.
	 * @throws UnauthorisedClientException si le client n'est pas autorisé.
	 */
	public void publish(String channel, ArrayList<MessageI> messages)
			throws UnknownClientException, UnknownChannelException, UnauthorisedClientException {
		try {
			this.privilegedPortOUT.publish(
					this.registrationPlugin.getReceptionPortURI(), channel, messages);
		} catch (UnknownClientException | UnknownChannelException | UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
