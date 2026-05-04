package fr.sorbonne_u.cps.pubsub.gossip.messages;

import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;

import java.time.Instant;

/**
 * Message de bavardage pour propager la destruction d'un canal privilégié
 * à tous les composants courtiers voisins.
 *
 * Chaque courtier qui reçoit ce message détruit sa copie locale du canal.
 */
public class DestroyChannelGossipMessage implements GossipMessageI {

    private static final long serialVersionUID = 1L;

    private final String gossipMessageURI;
    private final Instant timestamp;
    private final String emitterURI;

    private final String channel;
    private final String ownerReceptionPortURI;

    public DestroyChannelGossipMessage(
            String gossipMessageURI,
            Instant timestamp,
            String emitterURI,
            String channel,
            String ownerReceptionPortURI)
    {
        this.gossipMessageURI      = gossipMessageURI;
        this.timestamp             = timestamp;
        this.emitterURI            = emitterURI;
        this.channel               = channel;
        this.ownerReceptionPortURI = ownerReceptionPortURI;
    }

    @Override
    public String gossipMessageURI() { return this.gossipMessageURI; }

    @Override
    public Instant timestamp() { return this.timestamp; }

    @Override
    public GossipMessageI copyWithNewEmitterURI(String newGossipEmitterURI) {
        return new DestroyChannelGossipMessage(
                this.gossipMessageURI,
                this.timestamp,
                newGossipEmitterURI,
                this.channel,
                this.ownerReceptionPortURI);
    }

    public String getChannel()               { return this.channel; }
    public String getOwnerReceptionPortURI() { return this.ownerReceptionPortURI; }
    public String getEmitterURI()            { return this.emitterURI; }
}