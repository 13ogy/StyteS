package fr.sorbonne_u.cps.pubsub.plugins;

import fr.sorbonne_u.components.AbstractPlugin;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientPrivilegedOutboundPort;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyExistingChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.ChannelQuotaExceededException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientI;

import java.util.ArrayList;

/**
 * Client-side plugin implementing privileged channel-management operations
 * (CDC §3.5.2).
 *
 * <p>
 * This plugin delegates calls to the broker through the privileged outbound
 * port owned by {@link ClientRegistrationPlugin}. The client identity used by
 * the broker is the URI of the inbound port offering {@code ReceivingCI},
 * obtained from {@link ClientRegistrationPlugin#getReceptionPortURI()}.
 * </p>
 *
 * @author Bogdan Styn
 */
public class ClientPrivilegedPlugin extends ClientPublicationPlugin implements PrivilegedClientI
{
	private static final long serialVersionUID = 1L;


	public ClientPrivilegedPlugin(ClientRegistrationPlugin registrationPlugin) {
		super(registrationPlugin);
	}

	// Life cycle inherited from PublicationPlugin


	@Override
	public boolean hasCreatedChannel(String channel)
	throws UnknownClientException, UnknownChannelException
	{
		try {
			return this.registrationPlugin.getPrivilegedPortOUT().hasCreatedChannel(
				this.registrationPlugin.getReceptionPortURI(),
				channel);
		} catch (UnknownClientException | UnknownChannelException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean channelQuotaReached() throws UnknownClientException
	{
		try {
			return this.registrationPlugin.getPrivilegedPortOUT().channelQuotaReached(
				this.registrationPlugin.getReceptionPortURI());
		} catch (UnknownClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void createChannel(String channel, String autorisedUsers)
	throws UnknownClientException, AlreadyExistingChannelException, ChannelQuotaExceededException
	{
		try {
			this.registrationPlugin.getPrivilegedPortOUT().createChannel(
				this.registrationPlugin.getReceptionPortURI(),
				channel,
				autorisedUsers);
		} catch (UnknownClientException | AlreadyExistingChannelException | ChannelQuotaExceededException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void modifyAuthorisedUsers(String channel, String autorisedUsers)
	throws UnknownClientException, UnknownChannelException, UnauthorisedClientException
	{
		try {
			this.registrationPlugin.getPrivilegedPortOUT().modifyAuthorisedUsers(
				this.registrationPlugin.getReceptionPortURI(),
				channel,
				autorisedUsers);
		} catch (UnknownClientException | UnknownChannelException | UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void destroyChannel(String channel)
	throws UnknownClientException, UnknownChannelException, UnauthorisedClientException
	{
		try {
			this.registrationPlugin.getPrivilegedPortOUT().destroyChannel(
				this.registrationPlugin.getReceptionPortURI(),
				channel);
		} catch (UnknownClientException | UnknownChannelException | UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void destroyChannelNow(String channel)
	throws UnknownClientException, UnknownChannelException, UnauthorisedClientException
	{
		try {
			this.registrationPlugin.getPrivilegedPortOUT().destroyChannelNow(
				this.registrationPlugin.getReceptionPortURI(),
				channel);
		} catch (UnknownClientException | UnknownChannelException | UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void publish(String channel, MessageI message)
			throws UnknownClientException, UnknownChannelException, UnauthorisedClientException
	{
		try {
			this.registrationPlugin.getPrivilegedPortOUT().publish(
					this.registrationPlugin.getReceptionPortURI(),
					channel,
					message);
		} catch (UnknownClientException | UnknownChannelException | UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void publish(String channel, ArrayList<MessageI> messages)
			throws UnknownClientException, UnknownChannelException, UnauthorisedClientException
	{
		try {
			this.registrationPlugin.getPrivilegedPortOUT().publish(
					this.registrationPlugin.getReceptionPortURI(),
					channel,
					messages);
		} catch (UnknownClientException | UnknownChannelException | UnauthorisedClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
