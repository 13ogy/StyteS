package fr.sorbonne_u.cps.pubsub.plugins;

import fr.sorbonne_u.components.AbstractPlugin;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import java.util.ArrayList;

/** Client-side plugin implementing publication operations (CDC §3.5). */
public class ClientPublicationPlugin extends AbstractPlugin implements ClientPublicationI
{
	private static final long serialVersionUID = 1L;

	protected final ClientRegistrationPlugin registrationPlugin;

	public ClientPublicationPlugin(ClientRegistrationPlugin registrationPlugin)
	{
		super();
		this.registrationPlugin = registrationPlugin;
	}

	@Override
	public boolean channelExist(String channel)
	{
		try {
			return this.registrationPlugin.getRegistrationPortOUT().channelExist(channel);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean channelAuthorised(String channel) throws UnknownClientException, UnknownChannelException
	{
		try {
			return this.registrationPlugin.getRegistrationPortOUT().channelAuthorised(
				this.registrationPlugin.getReceptionPortURI(),
				channel);
		} catch (UnknownClientException | UnknownChannelException e) {
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
			this.registrationPlugin.getPublishingPortOUT().publish(
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
			this.registrationPlugin.getPublishingPortOUT().publish(
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
