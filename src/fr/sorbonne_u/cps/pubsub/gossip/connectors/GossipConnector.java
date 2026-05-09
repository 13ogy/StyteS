package fr.sorbonne_u.cps.pubsub.gossip.connectors;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.connectors.AbstractConnector;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipReceiverCI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipSenderCI;

/**
 * Connector bridging a {@link GossipSenderCI} required by an emitting broker
 * to the {@link GossipReceiverCI} offered by a peer broker.
 *
 * <p>
 * Phase D.5: technical exceptions are wrapped in {@link RemoteException}.
 * </p>
 */
public class GossipConnector
        extends AbstractConnector
        implements GossipSenderCI
{

    @Override
    public void send(GossipMessageI[] gossipMessages) throws Exception {
        try {
            ((GossipReceiverCI) this.offering).receive(gossipMessages);
        } catch (Exception e) {
            throw new RemoteException(e.getMessage(), e);
        }
    }
}
