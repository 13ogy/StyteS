package fr.sorbonne_u.cps.pubsub.gossip.ports;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractOutboundPort;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipSenderCI;

/**
 * Outbound port used by a broker to push gossip messages to a peer.
 *
 * <p>
 * Phase D.5: technical exceptions are wrapped in {@link RemoteException}.
 * </p>
 */
public class GossipSenderOutboundPort extends AbstractOutboundPort implements GossipSenderCI {
    public GossipSenderOutboundPort(ComponentI owner) throws Exception {
        super(GossipSenderCI.class, owner);
    }

    @Override
    public void send(GossipMessageI[] gossipMessageIS) throws Exception {
        try {
            ((GossipSenderCI) this.getConnector()).send(gossipMessageIS);
        } catch (Exception e) {
            throw new RemoteException(e.getMessage(), e);
        }
    }
}
