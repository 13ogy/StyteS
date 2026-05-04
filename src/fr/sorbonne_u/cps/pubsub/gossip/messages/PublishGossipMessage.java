package fr.sorbonne_u.cps.pubsub.gossip.messages;

import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;

import java.time.Instant;
/**
 * Message de bavardage pour propager la publication d'un message d'un client
 * à tous les composants courtiers voisins.
 *
 * Quand un client publie un message, le courtier C1 auprès duquel il est enregistré
 * crée ce message et le propage à ses voisins pour qu'ils le publient aux clients concernés.
 */
public class PublishGossipMessage implements GossipMessageI {

    private final String gossipMessageURI;     // URI unique du message gossip
    private final Instant timestamp;           // pour nettoyage mémoire
    private final String emitterURI;           // courtier qui envoie
    private final MessageI pubMessage;      // message publication embarqué
    private final String channel;             // canal de publication
    private final String publisherReceptionPortURI; // URI du publieur

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
    public MessageI getPubMessage() { return this.pubMessage; }
    public String getChannel() { return this.channel; }
    public String getPublisherReceptionPortURI() { return this.publisherReceptionPortURI; }

    @Override
    public String gossipMessageURI() {
        return this.gossipMessageURI;
    }

    @Override
    public Instant timestamp() {
        return this.timestamp;
    }

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
