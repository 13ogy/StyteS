package fr.sorbonne_u.cps.pubsub.base.ports;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;
import fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin;

/**
 * Port inbound utilisé par le broker pour livrer un message à un client.
 *
 * <p><strong>Propriétaire logique</strong> : le {@link ClientRegistrationPlugin}
 * installé sur le composant client. Le port est créé par le greffon avec
 * son URI, ce qui permet à BCM de retrouver le greffon comme
 * {@code TaskProviderReference} lors du dispatch asynchrone.</p>
 *
 * <p>
 * Les callbacks de livraison sont dispatchés sur l'executor par défaut du
 * client via
 * {@link AbstractComponent#runTask(fr.sorbonne_u.components.ComponentI.ComponentTask) runTask}
 * pour libérer immédiatement la thread RMI (et, transitivement, l'executor
 * de livraison du broker), de sorte qu'un client lent ne peut pas exercer
 * de back-pressure sur le broker.
 * </p>
 *
 * <p>
 * Les exceptions techniques sont encapsulées dans
 * {@link RemoteException} (la CI ne déclare que {@code throws Exception}) ;
 * les exceptions levées par le callback côté client sont logguées sur le
 * tracer du composant.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class ClientInboundPort extends AbstractInboundPort implements ReceivingCI {

	/** URI du greffon propriétaire (utilisé pour récupérer la référence). */
	private final String pluginUri;

	/**
	 * Construit le port avec l'URI du greffon
	 * {@link ClientRegistrationPlugin} comme URI de port BCM. Cet appariement
	 * URI-port / URI-greffon est exploité dans
	 * {@link #receive(String, MessageI)} via
	 * {@link AbstractComponent.AbstractTask#getTaskProviderReference()}.
	 */
	public ClientInboundPort(ComponentI owner, String pluginURI) throws Exception {
		super(pluginURI, ReceivingCI.class, owner);
		this.pluginUri = pluginURI;
	}

	/**
	 * Réception d'un message unitaire — dispatché en asynchrone sur le
	 * greffon de souscription pour ne pas bloquer la thread RMI ni
	 * l'executor de livraison du broker.
	 *
	 * @see ReceivingCI#receive(String, MessageI)
	 */
	@Override
	public void receive(String channel, MessageI message) throws Exception {
		try {
			this.getOwner().runTask(
					new AbstractComponent.AbstractTask(this.pluginUri) {
						@Override
						public void run() {
							try {
								((ClientRegistrationPlugin) this.getTaskProviderReference())
										.receive(channel, message);
							} catch (Exception e) {
								((AbstractComponent) this.getTaskOwner()).logMessage(
										"[ClientInboundPort plugin async] " + e);
							}
						}
					});
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/**
	 * Réception d'un lot de messages — itère et délègue à la version
	 * unitaire pour bénéficier du dispatch asynchrone par message.
	 *
	 * @see ReceivingCI#receive(String, MessageI[])
	 */
	@Override
	public void receive(String channel, MessageI[] messages) throws Exception
	{
		if (messages != null) {
			for (MessageI m : messages) {
				receive(channel, m);
			}
		}
	}

}
