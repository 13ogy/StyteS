package fr.sorbonne_u.cps.pubsub.gossip.connectors;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.connectors.AbstractConnector;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipReceiverCI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipSenderCI;

/**
 * Connecteur reliant un {@link GossipSenderCI} requis par un courtier émetteur
 * au {@link GossipReceiverCI} offert par un courtier voisin.
 *
 * <p>
 * Ce connecteur sert d'unique pont sortant entre deux brokers fédérés : son
 * implémentation se contente de déléguer l'appel {@link #send(GossipMessageI[])}
 * vers le port de réception du voisin via {@code this.offering}.
 * </p>
 *
 * <p><strong>Politique d'exceptions</strong></p>
 * <ul>
 * <li>Toute exception technique remontée par l'appel offert est ré-empaquetée
 * en {@link RemoteException} pour respecter le contrat RMI exposé par
 * {@link GossipSenderCI#send(GossipMessageI[])}.</li>
 * <li>Aucune exception métier n'est définie sur cette interface : un envoi
 * gossip est best-effort, la dédup et la skip-echo (cf. {@code docs/GOSSIP.md}
 * §3 et §4) garantissent la convergence sans avoir à signaler d'erreur métier.</li>
 * </ul>
 *
 * <p>Voir {@code docs/GOSSIP.md} pour la vue d'ensemble du protocole gossip
 * inter-brokers.</p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class GossipConnector
 extends AbstractConnector
 implements GossipSenderCI
{

 /**
 * Délègue l'envoi gossip au {@link GossipReceiverCI} offert par le voisin.
 *
 * @param gossipMessages messages gossip à transmettre (jamais {@code null}).
 * @throws RemoteException toute exception technique rencontrée côté voisin.
 * @throws Exception déclarée par le contrat de
 * {@link GossipSenderCI#send(GossipMessageI[])}.
 */
 @Override
 public void send(GossipMessageI[] gossipMessages) throws Exception {
 try {
 ((GossipReceiverCI) this.offering).receive(gossipMessages);
 } catch (Exception e) {
 throw new RemoteException(e.getMessage(), e);
 }
 }
}
