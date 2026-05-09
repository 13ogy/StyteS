package fr.sorbonne_u.cps.pubsub.base.connectors;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.connectors.AbstractConnector;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;

/**
 * Connector used by the broker to push messages to clients through the
 * {@link ReceivingCI} interface.
 *
 * <p>
 * Phase D.5: technical exceptions are wrapped in {@link RemoteException}
 * (the CI declares {@code throws Exception} only).
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class ReceivingConnector extends AbstractConnector implements ReceivingCI {

	@Override
	public void receive(String channel, MessageI message) throws Exception {
		try {
			((ReceivingCI) this.offering).receive(channel, message);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void receive(String channel, MessageI[] messages) throws Exception {
		try {
			((ReceivingCI) this.offering).receive(channel, messages);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
