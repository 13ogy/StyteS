package fr.sorbonne_u.cps.pubsub.base.connectors;

import java.rmi.RemoteException;

import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyExistingChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.ChannelQuotaExceededException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;

/**
 * Connector from a client privileged outbound port to the broker privileged
 * inbound port.
 *
 * <p>
 * The connector forwards calls to the offering component through
 * {@link #offering}. Publishing operations are inherited from
 * {@link PublishingConnector} (Phase D.2): {@link PrivilegedClientCI} extends
 * {@link fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI}, so the privileged
 * connector is-a publishing connector.
 * </p>
 *
 * <p>
 * Phase D.5: business exceptions declared on the CI propagate verbatim;
 * every other technical {@link Exception} is wrapped in a
 * {@link RemoteException}.
 * </p>
 *
 * @author Bogdan Styn
 */
public class PrivilegedClientConnector extends PublishingConnector
	implements PrivilegedClientCI {
	@Override
	public boolean hasCreatedChannel(String receptionPortURI, String channel)
			throws Exception {
		try {
			return ((PrivilegedClientCI) this.offering).hasCreatedChannel(receptionPortURI, channel);
		} catch (UnknownClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public boolean channelQuotaReached(String receptionPortURI) throws Exception {
		try {
			return ((PrivilegedClientCI) this.offering).channelQuotaReached(receptionPortURI);
		} catch (UnknownClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void createChannel(String receptionPortURI, String channel, String autorisedUsers)
			throws Exception {
		try {
			((PrivilegedClientCI) this.offering).createChannel(receptionPortURI, channel, autorisedUsers);
		} catch (UnknownClientException | AlreadyExistingChannelException
				| ChannelQuotaExceededException | UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void modifyAuthorisedUsers(String receptionPortURI, String channel, String autorisedUsers)
			throws Exception {
		try {
			((PrivilegedClientCI) this.offering).modifyAuthorisedUsers(receptionPortURI, channel, autorisedUsers);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void destroyChannel(String receptionPortURI, String channel) throws Exception {
		try {
			((PrivilegedClientCI) this.offering).destroyChannel(receptionPortURI, channel);
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}

	@Override
	public void destroyChannelNow(String receptionPortURI, String channel) throws Exception {
		try {
			((PrivilegedClientCI) this.offering).destroyChannelNow(receptionPortURI, channel);
		} catch (UnknownClientException | UnknownChannelException
				| UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RemoteException(e.getMessage(), e);
		}
	}
}
