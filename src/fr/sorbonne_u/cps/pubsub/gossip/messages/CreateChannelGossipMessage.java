package fr.sorbonne_u.cps.pubsub.gossip.messages;

import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;

import java.time.Instant;

/**
 * Message de bavardage pour propager la création d'un canal privilégié
 * à tous les composants courtiers voisins.
 *
 * Quand un client privilégié crée un canal auprès de C1, C1 crée
 * ce message et le propage pour que tous les courtiers dupliquent
 * ce canal localement.
 * @author Melissa Setbel
 */
public class CreateChannelGossipMessage implements GossipMessageI {

    private static final long serialVersionUID = 1L;

    private final String gossipMessageURI;
    private final Instant timestamp;
    private final String emitterURI;

    // Payload — informations du canal à créer
    private final String channel;
    private final String ownerReceptionPortURI;
    private final String authorisedUsers; // regex, peut être null
    private final RegistrationClass ownerClass;

    public CreateChannelGossipMessage(
            String gossipMessageURI,
            Instant timestamp,
            String emitterURI,
            String channel,
            String ownerReceptionPortURI,
            String authorisedUsers,
            RegistrationClass ownerClass)
    {
        this.gossipMessageURI      = gossipMessageURI;
        this.timestamp             = timestamp;
        this.emitterURI            = emitterURI;
        this.channel               = channel;
        this.ownerReceptionPortURI = ownerReceptionPortURI;
        this.authorisedUsers       = authorisedUsers;
        this.ownerClass            = ownerClass;
    }

    @Override
    public String gossipMessageURI() { return this.gossipMessageURI; }

    @Override
    public Instant timestamp() { return this.timestamp; }

    @Override
    public GossipMessageI copyWithNewEmitterURI(String newGossipEmitterURI) {
        return new CreateChannelGossipMessage(
                this.gossipMessageURI,
                this.timestamp,
                newGossipEmitterURI,  // seul ce champ change
                this.channel,
                this.ownerReceptionPortURI,
                this.authorisedUsers,
                this.ownerClass);
    }

    public String getChannel()               { return this.channel; }
    public String getOwnerReceptionPortURI() { return this.ownerReceptionPortURI; }
    public String getAuthorisedUsers()       { return this.authorisedUsers; }
    public String getEmitterURI()            { return this.emitterURI; }
    public RegistrationClass getOwnerClass() { return this.ownerClass; }
}