package fr.sorbonne_u.cps.pubsub.gossip.messages;

import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;

import java.time.Instant;

/**
 * Message de bavardage pour propager l'enregistrement d'un client
 * à tous les composants courtiers voisins.
 *
 * Quand un client s'enregistre auprès d'un courtier C1, C1 crée
 * ce message et le propage à ses voisins pour qu'ils mémorisent
 * localement que ce client existe avec cette classe de service.
 */
public class RegisterGossipMessage implements GossipMessageI {

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

    public RegisterGossipMessage(
            String gossipMessageURI,
            Instant timestamp,
            String emitterURI,
            String clientReceptionPortURI,
            RegistrationClass registrationClass)
    {
        this.gossipMessageURI      = gossipMessageURI;
        this.timestamp             = timestamp;
        this.emitterURI            = emitterURI;
        this.clientReceptionPortURI = clientReceptionPortURI;
        this.registrationClass     = registrationClass;
    }

    // -------------------------------------------------------------------------
    // GossipMessageI
    // -------------------------------------------------------------------------

    @Override
    public String gossipMessageURI() { return this.gossipMessageURI; }

    @Override
    public Instant timestamp() { return this.timestamp; }

    @Override
    public GossipMessageI copyWithNewEmitterURI(String newGossipEmitterURI) {
        return new RegisterGossipMessage(
                this.gossipMessageURI,       // on garde l'uri du messages
                this.timestamp,
                newGossipEmitterURI,         // on change l'émeteur pour le nouveau
                this.clientReceptionPortURI,
                this.registrationClass);
    }

    // -------------------------------------------------------------------------
    // Getters pour le courtier receveur
    // -------------------------------------------------------------------------

    public String getClientReceptionPortURI() {
        return this.clientReceptionPortURI;
    }

    public RegistrationClass getRegistrationClass() {
        return this.registrationClass;
    }

    public String getEmitterURI() {
        return this.emitterURI;
    }
}