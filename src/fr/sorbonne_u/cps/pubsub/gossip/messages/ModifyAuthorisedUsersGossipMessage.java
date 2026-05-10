package fr.sorbonne_u.cps.pubsub.gossip.messages;

import fr.sorbonne_u.cps.pubsub.base.components.GossipMessageVisitor;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.EmitterAwareGossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;

import java.time.Instant;

/**
 * Message gossip propageant la <strong>modification de la liste des utilisateurs autorisés</strong>
 * sur un canal privilégié, à tous les courtiers voisins de la fédération.
 *
 * <p><strong>Mutation broker répliquée (CDC §3.6)</strong>
 *
 * <p>Équivaut, côté broker récepteur, à un appel local {@code modifyAuthorisedUsers(channel,
 * authorisedUsers)} : chaque voisin remplace la regex d'autorisation portée par sa réplique locale
 * du canal.
 *
 * <p><strong>Garanties anti-loop / skip-echo</strong>
 *
 * <ul>
 *   <li>Implémente {@link EmitterAwareGossipMessageI} : skip-echo via {@link #getEmitterURI()} (cf.
 *       {@code docs/GOSSIP.md} §4).
 *   <li>{@link #gossipMessageURI()} unique et immuable, clef de la dédup atomique (cf. {@code
 *       docs/GOSSIP.md} §3).
 * </ul>
 *
 * <p>Voir {@code docs/GOSSIP.md} pour la vue d'ensemble du protocole.
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class ModifyAuthorisedUsersGossipMessage extends AbstractGossipMessage {

	private static final long serialVersionUID = 1L;

	/** URI unique et immuable du message gossip (clef de dédup). */
	private final String gossipMessageURI;

	/** Horodatage de création. */
	private final Instant timestamp;

	/** URI de réflexion du broker qui vient d'émettre cette copie (skip-echo). */
	private final String emitterURI;

	// Payload — informations du canal à créer
	/** Nom du canal privilégié dont la policy change. */
	private final String channel;

	/** URI de réception du propriétaire du canal. */
	private final String ownerReceptionPortURI;

	/** Nouvelle regex d'autorisation à appliquer (peut être {@code null}). */
	private final String authorisedUsers; // regex, peut être null

	/** Classe de service du propriétaire (transport informatif). */
	private final RegistrationClass ownerClass;

	/**
	 * Construit un message gossip {@code ModifyAuthorisedUsers}.
	 *
	 * @param gossipMessageURI URI unique du message.
	 * @param timestamp horodatage de création.
	 * @param emitterURI URI de réflexion du broker émetteur courant.
	 * @param channel nom du canal privilégié.
	 * @param ownerReceptionPortURI URI de réception du propriétaire.
	 * @param authorisedUsers nouvelle regex (peut être {@code null}).
	 * @param ownerClass classe de service du propriétaire.
	 */
	public ModifyAuthorisedUsersGossipMessage(
			String gossipMessageURI,
			Instant timestamp,
			String emitterURI,
			String channel,
			String ownerReceptionPortURI,
			String authorisedUsers,
			RegistrationClass ownerClass) {
		this.gossipMessageURI = gossipMessageURI;
		this.timestamp = timestamp;
		this.emitterURI = emitterURI;
		this.channel = channel;
		this.ownerReceptionPortURI = ownerReceptionPortURI;
		this.authorisedUsers = authorisedUsers;
		this.ownerClass = ownerClass;
	}

	/** {@inheritDoc} */
	@Override
	public String gossipMessageURI() {
		return this.gossipMessageURI;
	}

	/** {@inheritDoc} */
	@Override
	public Instant timestamp() {
		return this.timestamp;
	}

	/**
	 * @param newGossipEmitterURI URI de réflexion du nouvel émetteur courant.
	 * @return copie immuable avec {@code emitterURI} mis à jour ; URI gossip conservé.
	 */
	@Override
	public GossipMessageI copyWithNewEmitterURI(String newGossipEmitterURI) {
		return new ModifyAuthorisedUsersGossipMessage(
				this.gossipMessageURI,
				this.timestamp,
				newGossipEmitterURI, // seul ce champ change
				this.channel,
				this.ownerReceptionPortURI,
				this.authorisedUsers,
				this.ownerClass);
	}

	/**
	 * @return nom du canal privilégié visé.
	 */
	public String getChannel() {
		return this.channel;
	}

	/**
	 * @return URI de réception du propriétaire du canal.
	 */
	public String getOwnerReceptionPortURI() {
		return this.ownerReceptionPortURI;
	}

	/**
	 * @return nouvelle regex d'autorisation ({@code null} = pas de restriction).
	 */
	public String getAuthorisedUsers() {
		return this.authorisedUsers;
	}

	public String getEmitterURI() {
		return this.emitterURI;
	}

	/**
	 * @return classe de service du propriétaire.
	 */
	public RegistrationClass getOwnerClass() {
		return this.ownerClass;
	}

	// Visitor pattern
	@Override
	public void accept(GossipMessageVisitor visitor) {
		visitor.visit(this);
	}
}
