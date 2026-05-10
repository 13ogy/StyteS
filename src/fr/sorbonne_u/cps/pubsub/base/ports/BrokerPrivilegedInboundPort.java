package fr.sorbonne_u.cps.pubsub.base.ports;

import java.rmi.RemoteException;

import fr.sorbonne_u.components.ComponentI;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyExistingChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.ChannelQuotaExceededException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;


/**
 * Port inbound exposant les opérations de gestion des canaux privilégiés
 * du broker via l'interface {@link PrivilegedClientCI}.
 *
 * <p><strong>Propriétaire</strong> : {@link Broker}.</p>
 *
 * <p>
 * Comme {@link PrivilegedClientCI} étend
 * {@link fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI}, ce port hérite
 * également des opérations {@code publish} / {@code asyncPublishAndNotify}
 * via {@link BrokerPublishingInboundPort} (Phase D.2 — la spécialisation des CIs
 * est miroitée par la hiérarchie OO des ports).
 * </p>
 *
 * <p>
 * Convention Phase D.5 : les exceptions métier déclarées sur la CI sont
 * propagées telles quelles ; toute autre {@link Exception} technique est
 * encapsulée dans une {@link RemoteException}. Phase D.3 : les opérations
 * void véritablement fire-and-forget ({@link #modifyAuthorisedUsers},
 * {@link #destroyChannel}) sont dispatchées sur l'executor de réception ;
 * leurs exceptions sont logguées (l'appelant ne peut pas les observer).
 * Les opérations dont la sémantique synchrone importe à l'appelant
 * ({@link #createChannel} : remontée de quota / unicité,
 * {@link #destroyChannelNow} : disparition garantie au retour) restent
 * basées sur {@link fr.sorbonne_u.components.AbstractComponent#handleRequest
 * handleRequest}.
 * </p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class BrokerPrivilegedInboundPort extends BrokerPublishingInboundPort implements PrivilegedClientCI
{
	/** Constructeur sans URI explicite — owner doit être un {@link Broker}. */
	public BrokerPrivilegedInboundPort(ComponentI owner) throws Exception
	{
		super(PrivilegedClientCI.class, owner);
	}

	/**
	 * Constructeur à URI explicite (Phase C.3) — le broker dérive cette URI
	 * depuis son URI de réflexion.
	 */
	public BrokerPrivilegedInboundPort(String uri, ComponentI owner) throws Exception
	{
		super(uri, PrivilegedClientCI.class, owner);
	}

	/** @see PrivilegedClientCI#hasCreatedChannel(String, String) */
	@Override
	public boolean hasCreatedChannel(String receptionPortURI, String channel) throws Exception
	{
		try {
			return this.getOwner().handleRequest(
					o -> ((Broker) o).hasCreatedChannel(receptionPortURI, channel)
			);
		} catch (UnknownClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/** @see PrivilegedClientCI#channelQuotaReached(String) */
	@Override
	public boolean channelQuotaReached(String receptionPortURI) throws Exception
	{
		try {
			return this.getOwner().handleRequest(
					o -> ((Broker) o).channelQuotaReached(receptionPortURI)
			);
		} catch (UnknownClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/**
	 * Création de canal privilégié — synchrone car l'appelant a besoin de la
	 * réponse métier (quota / unicité) immédiatement.
	 * @see PrivilegedClientCI#createChannel(String, String, String)
	 */
	@Override
	public void createChannel(String receptionPortURI, String channel, String autorisedUsers)
			throws Exception
	{
		try {
			this.getOwner().handleRequest(o -> {
				((Broker) o).createChannel(receptionPortURI, channel, autorisedUsers);
				return null;
			});
		} catch (UnknownClientException | AlreadyExistingChannelException
				| ChannelQuotaExceededException | UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/**
	 * Modification asynchrone (Phase D.3) de la regex {@code authorisedUsers}.
	 * @see PrivilegedClientCI#modifyAuthorisedUsers(String, String, String)
	 */
	@Override
	public void modifyAuthorisedUsers(String receptionPortURI, String channel, String autorisedUsers)
			throws Exception
	{
		try {
			final Broker broker = (Broker) this.getOwner();
			broker.runTask(broker.getReceptionExecutorIndex(), o -> {
				try {
					((Broker) o).modifyAuthorisedUsers(receptionPortURI, channel, autorisedUsers);
				} catch (Exception e) {
					((Broker) o).logMessage("[modifyAuthorisedUsers async] " + e);
				}
			});
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/**
	 * Destruction asynchrone (Phase D.3) — le canal devient invisible mais
	 * son nettoyage profond se fait après vidage du compteur in-flight.
	 * @see PrivilegedClientCI#destroyChannel(String, String)
	 */
	@Override
	public void destroyChannel(String receptionPortURI, String channel)
			throws Exception
	{
		try {
			final Broker broker = (Broker) this.getOwner();
			broker.runTask(broker.getReceptionExecutorIndex(), o -> {
				try {
					((Broker) o).destroyChannel(receptionPortURI, channel);
				} catch (Exception e) {
					((Broker) o).logMessage("[destroyChannel async] " + e);
				}
			});
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	/**
	 * Destruction synchrone — l'appelant attend la suppression effective
	 * de tout l'état du canal.
	 * @see PrivilegedClientCI#destroyChannelNow(String, String)
	 */
	@Override
	public void destroyChannelNow(String receptionPortURI, String channel)
			throws Exception
	{
		try {
			this.getOwner().handleRequest(o -> {
				((Broker) o).destroyChannelNow(receptionPortURI, channel);
				return null;
			});
		} catch (UnknownClientException | UnknownChannelException
				| UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
