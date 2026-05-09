package fr.sorbonne_u.cps.pubsub.base.connectors;

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
 * @author Bogdan Styn
 */
public class PrivilegedClientConnector extends PublishingConnector
	implements PrivilegedClientCI {
	@Override
	public boolean hasCreatedChannel(String receptionPortURI, String channel)
			throws Exception {
		return ((PrivilegedClientCI) this.offering).hasCreatedChannel(receptionPortURI, channel);
	}

	@Override
	public boolean channelQuotaReached(String receptionPortURI) throws Exception {
		return ((PrivilegedClientCI) this.offering).channelQuotaReached(receptionPortURI);
	}

	@Override
	public void createChannel(String receptionPortURI, String channel, String autorisedUsers)
			throws Exception {
		((PrivilegedClientCI) this.offering).createChannel(receptionPortURI, channel, autorisedUsers);
	}

	@Override
	public void modifyAuthorisedUsers(String receptionPortURI, String channel, String autorisedUsers)
			throws Exception {
		((PrivilegedClientCI) this.offering).modifyAuthorisedUsers(receptionPortURI, channel, autorisedUsers);
	}

	@Override
	public void destroyChannel(String receptionPortURI, String channel) throws Exception {
		((PrivilegedClientCI) this.offering).destroyChannel(receptionPortURI, channel);
	}

	@Override
	public void destroyChannelNow(String receptionPortURI, String channel) throws Exception {
		((PrivilegedClientCI) this.offering).destroyChannelNow(receptionPortURI, channel);
	}
}
