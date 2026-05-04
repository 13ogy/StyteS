package fr.sorbonne_u.cps.pubsub.gossip.messages;

import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;

import java.time.Instant;

/**
 * Message de bavardage pour propager la modification de classe de service
 * d'un client à tous les composants courtiers voisins.
 */
public class ModifyServiceClassGossipMessage implements GossipMessageI {

    private static final long serialVersionUID = 1L;

    private final String gossipMessageURI;
    private final Instant timestamp;
    private final String emitterURI;

    private final String clientReceptionPortURI;
    private final RegistrationClass newRegistrationClass;

    public ModifyServiceClassGossipMessage(
            String gossipMessageURI,
            Instant timestamp,
            String emitterURI,
            String clientReceptionPortURI,
            RegistrationClass newRegistrationClass)
    {
        this.gossipMessageURI       = gossipMessageURI;
        this.timestamp              = timestamp;
        this.emitterURI             = emitterURI;
        this.clientReceptionPortURI = clientReceptionPortURI;
        this.newRegistrationClass   = newRegistrationClass;
    }

    @Override
    public String gossipMessageURI() { return this.gossipMessageURI; }

    @Override
    public Instant timestamp() { return this.timestamp; }

    @Override
    public GossipMessageI copyWithNewEmitterURI(String newGossipEmitterURI) {
        return new ModifyServiceClassGossipMessage(
                this.gossipMessageURI,
                this.timestamp,
                newGossipEmitterURI,
                this.clientReceptionPortURI,
                this.newRegistrationClass);
    }

    public String getClientReceptionPortURI() { return this.clientReceptionPortURI; }
    public RegistrationClass getNewRegistrationClass() { return this.newRegistrationClass; }
    public String getEmitterURI() { return this.emitterURI; }
}