package fr.sorbonne_u.cps.pubsub.base.ports;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractInboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;
import fr.sorbonne_u.cps.pubsub.base.components.Client;
import fr.sorbonne_u.cps.pubsub.base.components.PluginClient;
import fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin;

/**
 * Port inbound utilisé par le broker pour livrer un message à un client.
 * Compatible avec le {@link Client} historique et le {@link PluginClient}
 * basé sur les plugins.
 *
 * <p><strong>Propriétaire</strong> : composant client (cast vers
 * {@link Client} dans la version sans plugin, ou délégation au
 * {@link ClientRegistrationPlugin} dans la version avec plugin).</p>
 *
 * <p>
 * Phase D.3 : les callbacks de livraison sont dispatchés sur l'executor
 * par défaut du client via
 * {@link AbstractComponent#runTask(fr.sorbonne_u.components.ComponentI.ComponentTask) runTask}.
 * La thread RMI (et, transitivement, l'executor de livraison du broker
 * qui a ouvert l'appel) rend immédiatement la main, de sorte qu'un client
 * lent ne peut pas exercer de back-pressure sur le broker. Les exceptions
 * du callback côté client sont logguées sur son tracer.
 * </p>
 *
 * <p>
 * Phase D.5 : les exceptions techniques sont encapsulées dans
 * {@link RemoteException} (la CI ne déclare que {@code throws Exception}).
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class ClientInboundPort extends AbstractInboundPort implements ReceivingCI {

	/** Constructeur sans plugin (cas {@link Client}). */
	public ClientInboundPort(ComponentI owner) throws Exception {
		super(ReceivingCI.class, owner);
        this.pluginUri = null;
    }
	/**
	 * Constructeur utilisé en mode plugin : l'URI du port est aussi l'URI
	 * du {@link ClientRegistrationPlugin} qui en est propriétaire logique.
	 */
	public ClientInboundPort(ComponentI owner, String pluginURI) throws Exception {
		super(pluginURI, ReceivingCI.class, owner);
		this.pluginUri =pluginURI;
	}
	private final String pluginUri;

	/**
	 * Réception d'un message unitaire — dispatché en asynchrone sur le
	 * client (ou son plugin) pour éviter de bloquer la thread RMI / l'executor
	 * de livraison du broker.
	 *
	 * @see ReceivingCI#receive(String, MessageI)
	 */
	@Override
	public void receive(String channel, MessageI message) throws Exception {
		try {
			if (this.pluginUri != null) {
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
			} else {
				this.getOwner().runTask(o -> {
					try {
						((Client) o).receive(channel, message);
					} catch (Exception e) {
						((AbstractComponent) o).logMessage(
								"[ClientInboundPort client async] " + e);
					}
				});
			}
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
