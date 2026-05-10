package fr.sorbonne_u.cps.pubsub.base.ports;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractOutboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;

import java.rmi.RemoteException;

/**
 * Port outbound utilisé par le broker pour livrer un message à un client abonné via l'interface
 * {@link ReceivingCI}.
 *
 * <p><strong>Propriétaire</strong> : {@link fr.sorbonne_u.cps.pubsub.base.components.Broker} (un
 * port outbound par client enregistré, indexé dans {@code Broker.receptionPortsOUT}).
 *
 * <p>les exceptions techniques sont encapsulées dans {@link RemoteException} (la CI ne déclare que
 * {@code throws Exception}).
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class BrokerReceptionOutboundPort extends AbstractOutboundPort implements ReceivingCI {

	/** Constructeur — owner = broker qui pousse les messages vers les clients. */
	public BrokerReceptionOutboundPort(ComponentI owner) throws Exception {
		super(ReceivingCI.class, owner);
	}

	/**
	 * @see ReceivingCI#receive(String, MessageI)
	 */
	@Override
	public void receive(String channel, MessageI message) throws Exception {
		try {
			((ReceivingCI) this.getConnector()).receive(channel, message);
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/**
	 * @see ReceivingCI#receive(String, MessageI[])
	 */
	@Override
	public void receive(String channel, MessageI[] messages) throws Exception {
		try {
			((ReceivingCI) this.getConnector()).receive(channel, messages);
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
