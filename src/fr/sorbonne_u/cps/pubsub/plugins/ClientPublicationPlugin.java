package fr.sorbonne_u.cps.pubsub.plugins;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.AbstractPlugin;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientPublishingOutboundPort;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.*;

import java.util.ArrayList;

/** Client-side plugin implementing publication operations (CDC §3.5). 
 *
 * @author Bogdan Styn
 */
public class ClientPublicationPlugin extends AbstractPlugin implements ClientPublicationI
{
	private static final long serialVersionUID = 1L;

	protected final ClientRegistrationPlugin registrationPlugin;

	public ClientPublicationPlugin(ClientRegistrationPlugin registrationPlugin)
	{
		super();
		this.registrationPlugin = registrationPlugin;
	}

	// All required ports and interfaces are in the registration pluging
	public void installOn(fr.sorbonne_u.components.ComponentI owner) throws Exception
	{
		super.installOn(owner);
	}

	public void initialise() throws Exception
	{
		super.initialise();
	}

	@Override
	public void finalise() throws Exception
	{
		super.finalise();
	}

	@Override
	public void uninstall() throws Exception
	{

		super.uninstall();
	}


	@Override
	public boolean channelExist(String channel)
	{
		try {
			return this.registrationPlugin
					.getRegistrationPortOUT().channelExist(channel);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean channelAuthorised(String channel) throws UnknownClientException, UnknownChannelException
	{
		try {
			return this.registrationPlugin
					.getRegistrationPortOUT().channelAuthorised(
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

	@Override
	public void asyncPublishAndNotify(String channel, MessageI message) {
		final ClientPublicationPlugin self = this;
		this.getOwner().runTask(o -> {
			try {
				self.publish(channel, message);
			} catch (Exception e) { e.printStackTrace(); }
		});
	}

	@Override
	public void asyncPublishAndNotify(String channel, ArrayList<MessageI> messages) {
		final ClientPublicationPlugin self = this;
		this.getOwner().runTask(o -> {
			try {
				self.publish(channel, messages);
			} catch (Exception e) { e.printStackTrace(); }
		});
	}
}
