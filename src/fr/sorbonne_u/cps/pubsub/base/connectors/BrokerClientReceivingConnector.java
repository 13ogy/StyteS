package fr.sorbonne_u.cps.pubsub.base.connectors;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.connectors.AbstractConnector;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;

/**
 * Connecteur reliant le port outbound {@link ReceivingCI} du broker au
 * port inbound {@link ReceivingCI} d'un client (sens broker → client,
 * inverse des trois autres connecteurs).
 *
 * <p><strong>Sens du raccordement</strong> : la référence {@link #offering}
 * pointe vers le port inbound du client qui offre {@link ReceivingCI}.</p>
 *
 * <p>
 * Phase D.5 : les exceptions techniques sont encapsulées dans
 * {@link RemoteException} (la CI ne déclare que {@code throws Exception}).
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class BrokerClientReceivingConnector extends AbstractConnector implements ReceivingCI {

	/** @see ReceivingCI#receive(String, MessageI) */
	@Override
	public void receive(String channel, MessageI message) throws Exception {
		try {
			((ReceivingCI) this.offering).receive(channel, message);
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see ReceivingCI#receive(String, MessageI[]) */
	@Override
	public void receive(String channel, MessageI[] messages) throws Exception {
		try {
			((ReceivingCI) this.offering).receive(channel, messages);
		} catch (RemoteException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
