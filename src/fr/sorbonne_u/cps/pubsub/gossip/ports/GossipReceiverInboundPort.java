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
 * Port entrant par lequel un courtier voisin déclenche la réception d'un
 * lot de messages gossip.
 *
 * <p><strong>Dispatch executor-aware (Phase D.3)</strong></p>
 *
 * <p>
 * Le {@code receive(...)} ci-dessous est strictement <em>non bloquant</em>
 * du point de vue de la thread RMI distante :
 * </p>
 * <ul>
 *   <li>Si le composant propriétaire est un {@link Broker}, l'exécution est
 *   re-postée sur l'executor gossip dédié du broker
 *   ({@link Broker#getGossipExecutorIndex()}, alimenté par {@code esGossipIndex}
 *   — voir {@code docs/PIPELINE.md} §2 et {@code docs/GOSSIP.md} §5).</li>
 *   <li>Sinon (cas générique d'un {@link GossipImplementationI} arbitraire,
 *   utile pour des stubs de tests), on retombe sur l'executor par défaut
 *   du composant via {@link ComponentI#runTask}.</li>
 * </ul>
 *
 * <p>
 * Cette dissociation a été introduite après la régression « 3 OK / 4 random
 * failure » observée en exécution distribuée : un {@code receive()} synchrone
 * gardait la thread RMI immobilisée pendant tout le {@code update()} d'un
 * broker (verrous écriture, fan-out gossip sortant, livraison locale), ce
 * qui saturait le pool RMI sur les noeuds intermédiaires de la fédération.
 * </p>
 *
 * <p><strong>Politique d'exceptions</strong></p>
 * <ul>
 *   <li>Toute exception au moment du dispatch (échec de {@code runTask},
 *   {@code getOwner()} HS, ...) est ré-empaquetée en {@link RemoteException}
 *   pour respecter le contrat RMI de
 *   {@link GossipReceiverCI#receive(GossipMessageI[])}.</li>
 *   <li>Les exceptions levées <em>à l'intérieur</em> de la tâche asynchrone
 *   (i.e. par le {@code update()} du broker) ne peuvent plus remonter à
 *   l'appelant ; elles sont loggées via {@code logMessage(...)} sur le tracer
 *   du composant, préfixées par {@code [gossip receive async]}.</li>
 * </ul>
 *
 * <p>Voir {@code docs/GOSSIP.md} §5 pour la motivation détaillée.</p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class GossipReceiverInboundPort
        extends AbstractInboundPort
        implements GossipReceiverCI
{

    /**
     * Crée un port entrant gossip avec un URI explicite.
     *
     * @param uri    URI du port (doit être unique dans la JVM).
     * @param owner  composant propriétaire ; idéalement un {@link Broker}
     *               mais tout {@link GossipImplementationI} est accepté pour
     *               faciliter les tests.
     * @throws Exception en cas d'échec d'enregistrement BCM du port.
     */
    public GossipReceiverInboundPort(String uri, ComponentI owner) throws Exception {
        super(uri, GossipReceiverCI.class, owner);
    }

    /**
     * Crée un port entrant gossip avec un URI auto-généré.
     *
     * @param owner composant propriétaire.
     * @throws Exception en cas d'échec d'enregistrement BCM du port.
     */
    public GossipReceiverInboundPort(ComponentI owner) throws Exception {
        super(GossipReceiverCI.class, owner);
    }


    /**
     * Reçoit un lot de messages gossip et délègue leur traitement au
     * propriétaire de manière strictement asynchrone (la thread RMI distante
     * est libérée immédiatement après le {@code runTask}).
     *
     * <p>
     * Le dispatch est executor-aware : si le propriétaire est un {@link Broker},
     * la tâche est postée sur l'executor gossip dédié
     * ({@link Broker#getGossipExecutorIndex()}) ; sinon elle est postée sur
     * l'executor par défaut du composant.
     * </p>
     *
     * @param gossipMessages lot de messages gossip à traiter (jamais {@code null}).
     * @throws RemoteException si l'amorçage du dispatch échoue ; les exceptions
     *                         intra-tâche sont loggées et n'arrivent pas ici.
     * @throws Exception       déclaré par {@link GossipReceiverCI}.
     */
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
