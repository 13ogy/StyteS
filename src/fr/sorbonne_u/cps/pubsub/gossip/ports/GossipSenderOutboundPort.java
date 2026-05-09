package fr.sorbonne_u.cps.pubsub.gossip.ports;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractOutboundPort;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipSenderCI;

/**
 * Port sortant utilisé par un courtier pour pousser des messages gossip
 * vers un voisin de la fédération.
 *
 * <p>
 * Un broker maintient un index inverse {@code sendersByNeighbour}
 * (URI de réflexion du voisin -&gt; instance de ce port) afin de
 * sélectionner précisément les destinataires de la re-diffusion et
 * d'appliquer la skip-echo (cf. {@code docs/GOSSIP.md} §4).
 * </p>
 *
 * <p><strong>Politique d'exceptions (Phase D.5)</strong></p>
 * <ul>
 *   <li>Toute exception remontée par le connecteur est ré-empaquetée en
 *   {@link RemoteException} pour respecter le contrat RMI de
 *   {@link GossipSenderCI#send(GossipMessageI[])}.</li>
 *   <li>Aucune exception métier n'est exposée : un envoi gossip est
 *   best-effort, la dédup atomique côté voisin garantit la convergence.</li>
 * </ul>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class GossipSenderOutboundPort extends AbstractOutboundPort implements GossipSenderCI {

    /**
     * Crée un port sortant gossip avec un URI auto-généré.
     *
     * @param owner composant propriétaire (typiquement un broker fédéré).
     * @throws Exception en cas d'échec d'enregistrement BCM du port.
     */
    public GossipSenderOutboundPort(ComponentI owner) throws Exception {
        super(GossipSenderCI.class, owner);
    }

    /**
     * Pousse un lot de messages gossip vers le voisin connecté.
     *
     * @param gossipMessageIS messages à transmettre (jamais {@code null}).
     * @throws RemoteException toute exception technique rencontrée côté voisin.
     * @throws Exception       déclaré par le contrat
     *                         {@link GossipSenderCI#send(GossipMessageI[])}.
     */
    @Override
    public void send(GossipMessageI[] gossipMessageIS) throws Exception {
        try {
            ((GossipSenderCI) this.getConnector()).send(gossipMessageIS);
        } catch (Exception e) {
            throw new RemoteException(e.getMessage(), e);
        }
    }
}
