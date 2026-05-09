package fr.sorbonne_u.cps.pubsub.gossip.messages;

import fr.sorbonne_u.cps.pubsub.gossip.interfaces.EmitterAwareGossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;

import java.time.Instant;
/**
 * Message gossip propageant la <strong>publication d'un message</strong>
 * sur un canal à tous les courtiers voisins de la fédération.
 *
 * <p><strong>Mutation broker répliquée (CDC §3.4)</strong></p>
 * <p>
 * Équivaut, côté broker récepteur, à un appel local
 * {@code publish(channel, pubMessage, publisherReceptionPortURI)} : le voisin
 * intègre le message dans son pipeline de livraison locale (matching de
 * filtres + push aux abonnés concernés).
 * </p>
 *
 * <p><strong>Garanties anti-loop / skip-echo</strong></p>
 * <ul>
 *   <li>Implémente {@link EmitterAwareGossipMessageI} : skip-echo via
 *   {@link #getEmitterURI()} (cf. {@code docs/GOSSIP.md} §4).</li>
 *   <li>{@link #gossipMessageURI()} unique et immuable, clef de dédup
 *   (cf. {@code docs/GOSSIP.md} §3) — garantit qu'un même message publié
 *   ne sera pas livré deux fois aux abonnés locaux d'un broker maillé.</li>
 *   <li>{@link #copyWithNewEmitterURI(String)} ne change que l'émetteur.</li>
 * </ul>
 *
 * <p>Voir {@code docs/GOSSIP.md} pour la vue d'ensemble du protocole.</p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class PublishGossipMessage implements EmitterAwareGossipMessageI {

    /** URI unique et immuable du message gossip (clef de dédup). */
    private final String gossipMessageURI;     // URI unique du message gossip
    /** Horodatage de création (utilisé par {@code cleanupGossipMemory}). */
    private final Instant timestamp;           // pour nettoyage mémoire
    /** URI de réflexion du broker qui vient d'émettre cette copie (skip-echo). */
    private final String emitterURI;           // courtier qui envoie
    /** Message de publication transporté (payload, propriétés, timestamp). */
    private final MessageI pubMessage;      // message publication embarqué
    /** Canal de publication ciblé. */
    private final String channel;             // canal de publication
    /** URI de réception du client publieur originel (sert au tracé / filtres). */
    private final String publisherReceptionPortURI; // URI du publieur

    /**
     * Construit un message gossip {@code Publish}.
     *
     * @param gossipURI    URI unique du message gossip.
     * @param ts           horodatage de création.
     * @param emitterURI   URI de réflexion du broker émetteur courant.
     * @param msg          message publié (payload + propriétés).
     * @param channel      canal cible.
     * @param publisherURI URI de réception du client publieur originel.
     */
    public PublishGossipMessage(String gossipURI, Instant ts,
                                String emitterURI, MessageI msg,
                                String channel, String publisherURI) {
        this.gossipMessageURI = gossipURI;
        this.timestamp = ts;
        this.emitterURI = emitterURI;
        this.pubMessage = msg;
        this.channel = channel;
        this.publisherReceptionPortURI = publisherURI;

    }

    // getters pour les courtiers
    public String getEmitterURI(){return this.emitterURI;}
    /** @return message de publication transporté (payload + propriétés). */
    public MessageI getPubMessage() { return this.pubMessage; }
    /** @return canal cible de la publication. */
    public String getChannel() { return this.channel; }
    /** @return URI de réception du client publieur originel. */
    public String getPublisherReceptionPortURI() { return this.publisherReceptionPortURI; }

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
        return new PublishGossipMessage(
                this.gossipMessageURI,        //  on garde l'uri du messages
                this.timestamp,
                newGossipEmitterURI,         // on change l'émeteur pour le nouveau
                this.pubMessage,
                this.channel,
                this.publisherReceptionPortURI);
    }
}
