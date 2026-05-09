package fr.sorbonne_u.cps.pubsub.gossip.ports;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipImplementationI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipReceiverCI;

/**
 * Inbound port through which a remote broker delivers gossip messages.
 *
 * <p>
 * Phase D.3: the call is dispatched on the broker's dedicated gossip
 * executor (when the owner is a {@link Broker}) so the RMI thread of
 * the sender returns immediately even on deep federation chains. Falls
 * back to the default executor for any other {@link GossipImplementationI}
 * owner. Exceptions are logged on the owner tracer.
 * </p>
 */
public class GossipReceiverInboundPort
        extends AbstractInboundPort
        implements GossipReceiverCI
{

    public GossipReceiverInboundPort(String uri, ComponentI owner) throws Exception {
        super(uri, GossipReceiverCI.class, owner);
    }

    public GossipReceiverInboundPort(ComponentI owner) throws Exception {
        super(GossipReceiverCI.class, owner);
    }


    @Override
    public void receive(GossipMessageI[] gossipMessages) throws Exception {
        try {
            ComponentI owner = this.getOwner();
            if (owner instanceof Broker) {
                final Broker broker = (Broker) owner;
                broker.runTask(broker.getGossipExecutorIndex(), o -> {
                    try {
                        ((GossipImplementationI) o).receive(gossipMessages);
                    } catch (Exception e) {
                        ((Broker) o).logMessage("[gossip receive async] " + e);
                    }
                });
            } else {
                owner.runTask(o -> {
                    try {
                        ((GossipImplementationI) o).receive(gossipMessages);
                    } catch (Exception e) {
                        ((AbstractComponent) o).logMessage("[gossip receive async] " + e);
                    }
                });
            }
        } catch (Exception e) {
            throw new RemoteException(e.getMessage(), e);
        }
    }
}
