package fr.sorbonne_u.cps.pubsub.gossip.ports;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipImplementationI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipMessageI;
import fr.sorbonne_u.cps.pubsub.gossip.interfaces.GossipReceiverCI;

/**
 * Port entrant par lequel un courtier voisin déclenche la réception d'un
 * lot de messages gossip.
 *
 * <p><strong>Dispatch executor-aware</strong></p>
 *
 * <p>
 * Le {@code receive(...)} ci-dessous est strictement <em>non bloquant</em>
 * du point de vue de la thread RMI distante : la tâche est postée sur un
 * executor service du composant via
 * {@link ComponentI#runTask(String, fr.sorbonne_u.components.ComponentI.FComponentTask)},
 * en utilisant l'URI d'executor passé au constructeur lorsqu'il est valide,
 * et l'executor par défaut sinon. Le port n'a donc plus à connaître la
 * classe concrète du composant propriétaire (pas de {@code instanceof}) :
 * c'est BCM qui résout l'executor par son URI.
 * </p>
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
 * <li>Toute exception au moment du dispatch (échec de {@code runTask},
 * {@code getOwner()} HS, ...) est ré-empaquetée en {@link RemoteException}
 * pour respecter le contrat RMI de
 * {@link GossipReceiverCI#receive(GossipMessageI[])}.</li>
 * <li>Les exceptions levées <em>à l'intérieur</em> de la tâche asynchrone
 * (i.e. par le {@code update()} du broker) ne peuvent plus remonter à
 * l'appelant ; elles sont loggées via {@code logMessage(...)} sur le tracer
 * du composant, préfixées par {@code [gossip receive async]}.</li>
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
	 * URI de l'executor service du composant propriétaire sur lequel les
	 * tâches de réception gossip doivent être postées. Peut être
	 * {@code null} ; dans ce cas le port retombe sur l'executor par défaut.
	 */
	private final String executorServiceURI;

	/**
	 * Crée un port entrant gossip avec un URI explicite et l'URI de
	 * l'executor service du composant propriétaire dédié au gossip.
	 *
	 * @param uri					URI du port (doit être unique dans la JVM).
	 * @param executorServiceURI	URI d'un executor service du composant
	 *								propriétaire sur lequel poster les tâches
	 *								(peut être {@code null}).
	 * @param owner					composant propriétaire ; tout
	 *								{@link GossipImplementationI} est accepté.
	 * @throws Exception en cas d'échec d'enregistrement BCM du port.
	 */
	public GossipReceiverInboundPort(
		String uri,
		String executorServiceURI,
		ComponentI owner
		) throws Exception
	{
		super(uri, GossipReceiverCI.class, owner);
		this.executorServiceURI = executorServiceURI;
	}

	/**
	 * Crée un port entrant gossip avec un URI explicite mais sans executor
	 * dédié — la tâche sera postée sur l'executor par défaut du composant.
	 */
	public GossipReceiverInboundPort(String uri, ComponentI owner) throws Exception {
		this(uri, null, owner);
	}

	/**
	 * Crée un port entrant gossip avec un URI auto-généré.
	 */
	public GossipReceiverInboundPort(ComponentI owner) throws Exception {
		super(GossipReceiverCI.class, owner);
		this.executorServiceURI = null;
	}


	/**
	 * Reçoit un lot de messages gossip et délègue leur traitement au
	 * propriétaire de manière strictement asynchrone (la thread RMI distante
	 * est libérée immédiatement après le {@code runTask}).
	 *
	 * <p>
	 * Le dispatch est executor-aware sans recourir à {@code instanceof} : si
	 * l'URI d'executor passé au constructeur est valide
	 * ({@link ComponentI#validExecutorServiceURI(String)}), la tâche y est
	 * postée ; sinon elle est postée sur l'executor par défaut du composant.
	 * </p>
	 *
	 * @param gossipMessages lot de messages gossip à traiter (jamais {@code null}).
	 * @throws RemoteException si l'amorçage du dispatch échoue ; les exceptions
	 *						   intra-tâche sont loggées et n'arrivent pas ici.
	 * @throws Exception déclaré par {@link GossipReceiverCI}.
	 */
	@Override
	public void receive(GossipMessageI[] gossipMessages) throws Exception {
		try {
			final ComponentI owner = this.getOwner();
			final ComponentI.FComponentTask task = o -> {
				try {
					((GossipImplementationI) o).receive(gossipMessages);
				} catch (Exception e) {
					((AbstractComponent) o).logMessage("[gossip receive async] " + e);
				}
			};
			if (this.executorServiceURI != null
				&& owner.validExecutorServiceURI(this.executorServiceURI)) {
				owner.runTask(this.executorServiceURI, task);
			} else {
				owner.runTask(task);
			}
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
