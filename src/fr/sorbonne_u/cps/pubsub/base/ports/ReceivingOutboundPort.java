package fr.sorbonne_u.cps.pubsub.base.ports;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.components.ports.AbstractOutboundPort;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;

/**
 * Outbound port used by the broker to deliver messages to a given client.
 *
 * <p>
 * Phase D.5: technical exceptions are wrapped in {@link RemoteException}
 * (the CI declares {@code throws Exception} only).
 * </p>
 *
 * @author Bogdan Styn
 */
public class ReceivingOutboundPort extends AbstractOutboundPort implements ReceivingCI {

	public ReceivingOutboundPort(ComponentI owner) throws Exception {
		super(ReceivingCI.class, owner);
	}

	@Override
	public void receive(String channel, MessageI message) throws Exception {
		try {
			((ReceivingCI) this.getConnector()).receive(channel, message);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void receive(String channel, MessageI[] messages) throws Exception {
		try {
			((ReceivingCI) this.getConnector()).receive(channel, messages);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

}
