package fr.sorbonne_u.cps.pubsub.gossip.messages;

import fr.sorbonne_u.cps.pubsub.base.components.GossipMessageVisitor;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.EmitterAwareGossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;

import java.time.Instant;

/**
 * Message gossip propageant l'<strong>enregistrement d'un client</strong>
 * à tous les courtiers voisins de la fédération.
 *
 * <p><strong>Mutation broker répliquée (CDC §3.5)</strong></p>
 * <p>
 * Équivaut, côté broker récepteur, à un appel local
 * {@code register(clientReceptionPortURI, registrationClass)} : chaque voisin
 * mémorise localement l'existence du client et sa classe de service. Cela
 * permet aux brokers distants de prendre des décisions d'autorisation et de
 * quota cohérentes (cf. {@code Broker#update(...)} sur ce type).
 * </p>
 *
 * <p><strong>Garanties anti-loop / skip-echo</strong></p>
 * <ul>
 * <li>Implémente {@link EmitterAwareGossipMessageI} : skip-echo via
 * {@link #getEmitterURI()} (cf. {@code docs/GOSSIP.md} §4).</li>
 * <li>{@link #gossipMessageURI()} unique et immuable, clef de la dédup
 * atomique (cf. {@code docs/GOSSIP.md} §3).</li>
 * <li>{@link #copyWithNewEmitterURI(String)} ne modifie que l'émetteur,
 * l'URI gossip reste identique.</li>
 * </ul>
 *
 * <p>Voir {@code docs/GOSSIP.md} pour la vue d'ensemble du protocole.</p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class RegisterGossipMessage extends AbstractGossipMessage {

 private static final long serialVersionUID = 1L;

 // -------------------------------------------------------------------------
 // Champs requis par GossipMessageI
 // -------------------------------------------------------------------------

 /** URI unique de ce message gossip — pour éviter les boucles. */
 private final String gossipMessageURI;

 /** Instant de création — pour le nettoyage de la mémoire. */
 private final Instant timestamp;

 /** URI du courtier émetteur courant. */
 private final String emitterURI;

 // -------------------------------------------------------------------------
 // Payload — informations à propager
 // -------------------------------------------------------------------------

 /** URI du port de réception du client (son identité dans le système). */
 private final String clientReceptionPortURI;

 /** Classe de service du client. */
 private final RegistrationClass registrationClass;

 // -------------------------------------------------------------------------
 // Constructeur
 // -------------------------------------------------------------------------

 /**
 * Construit un message gossip {@code Register}.
 *
 * @param gossipMessageURI URI unique du message (immuable).
 * @param timestamp horodatage de création.
 * @param emitterURI URI de réflexion du broker émetteur courant.
 * @param clientReceptionPortURI URI de réception du client à enregistrer.
 * @param registrationClass classe de service du client.
 */
 public RegisterGossipMessage(
 String gossipMessageURI,
 Instant timestamp,
 String emitterURI,
 String clientReceptionPortURI,
 RegistrationClass registrationClass)
 {
 this.gossipMessageURI = gossipMessageURI;
 this.timestamp = timestamp;
 this.emitterURI = emitterURI;
 this.clientReceptionPortURI = clientReceptionPortURI;
 this.registrationClass = registrationClass;
 }

 // -------------------------------------------------------------------------
 // GossipMessageI
 // -------------------------------------------------------------------------

 /** {@inheritDoc} */
 @Override
 public String gossipMessageURI() { return this.gossipMessageURI; }

 /** {@inheritDoc} */
 @Override
 public Instant timestamp() { return this.timestamp; }

 /**
 * @param newGossipEmitterURI URI de réflexion du nouvel émetteur courant.
 * @return copie immuable avec {@code emitterURI} mis à jour ; URI gossip conservé.
 */
 @Override
 public GossipMessageI copyWithNewEmitterURI(String newGossipEmitterURI) {
 return new RegisterGossipMessage(
 this.gossipMessageURI, // on garde l'uri du messages
 this.timestamp,
 newGossipEmitterURI, // on change l'émeteur pour le nouveau
 this.clientReceptionPortURI,
 this.registrationClass);
 }

 // -------------------------------------------------------------------------
 // Getters pour le courtier receveur
 // -------------------------------------------------------------------------

 /** @return URI de réception du client à enregistrer. */
 public String getClientReceptionPortURI() {
 return this.clientReceptionPortURI;
 }

 /** @return classe de service du client. */
 public RegistrationClass getRegistrationClass() {
 return this.registrationClass;
 }

 public String getEmitterURI() {
 return this.emitterURI;
 }


 // Visitor pattern
 @Override
 public void accept(GossipMessageVisitor visitor) {
 visitor.visit(this);
 }
}