package fr.sorbonne_u.cps.pubsub.base.components;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.annotations.OfferedInterfaces;
import fr.sorbonne_u.components.annotations.RequiredInterfaces;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.exceptions.NotSubscribedChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;
import fr.sorbonne_u.cps.pubsub.plugins.ClientPublicationPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientRegistrationPlugin;
import fr.sorbonne_u.cps.pubsub.plugins.ClientSubscriptionPlugin;
import fr.sorbonne_u.utils.aclocks.ClocksServer;

/**
 * A plugin-based client component implementing CDC §3.5 (excluding §3.5.3).
 *
 * This component coexists with the legacy {@link Client} component so that
 * previous demos remain unchanged.
 *
 * @author Bogdan Styn
 */
@OfferedInterfaces(offered = { ReceivingCI.class })
@RequiredInterfaces(required = {
	RegistrationCI.class,
	PublishingCI.class,
	PrivilegedClientCI.class
})
public class PluginClient extends AbstractComponent
{
	protected final ClientRegistrationPlugin registrationPlugin;
	protected final ClientSubscriptionPlugin subscriptionPlugin;
	protected final ClientPublicationPlugin publicationPlugin;
	protected PluginClient(
		String reflectionInboundPortURI,
		int nbThreads,
		int nbSchedulableThreads) throws Exception
	{
		super(reflectionInboundPortURI, nbThreads, nbSchedulableThreads);

		this.registrationPlugin = new ClientRegistrationPlugin();
		this.registrationPlugin.setPluginURI(reflectionInboundPortURI + "-registration-plugin");
		this.installPlugin(this.registrationPlugin);

		this.subscriptionPlugin = new ClientSubscriptionPlugin(
			this.registrationPlugin,
			this::onReceive);
		this.subscriptionPlugin.setPluginURI(reflectionInboundPortURI + "-subscription-plugin");
		this.installPlugin(this.subscriptionPlugin);

		this.publicationPlugin = new ClientPublicationPlugin(this.registrationPlugin);
		this.publicationPlugin.setPluginURI(reflectionInboundPortURI + "-publication-plugin");
		this.installPlugin(this.publicationPlugin);
	}

	@Override
	public void execute() throws Exception
	{
		super.execute();
	}

	/** URI of this client inbound port offering ReceivingCI. */
	public String getReceptionPortURI()
	{
		try {
			return this.registrationPlugin.getReceptionPortURI();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// ---------------------------------------------------------------------
	// Registration API (delegation)
	// ---------------------------------------------------------------------

	public boolean registered()
	{
		return this.registrationPlugin.registered();
	}

	public void register(RegistrationClass rc) throws AlreadyRegisteredException
	{
		this.registrationPlugin.register(rc);
	}

	public void modifyServiceClass(RegistrationClass rc) throws UnknownClientException, AlreadyRegisteredException
	{
		this.registrationPlugin.modifyServiceClass(rc);
	}

	public void unregister() throws UnknownClientException
	{
		this.registrationPlugin.unregister();
	}

	// ---------------------------------------------------------------------
	// Subscription API (delegation)
	// ---------------------------------------------------------------------

	public void subscribe(String channel, MessageFilterI filter)
	throws UnknownClientException, UnknownChannelException, UnauthorisedClientException
	{
		this.subscriptionPlugin.subscribe(channel, filter);
	}

	public void unsubscribe(String channel)
	throws UnknownClientException, UnknownChannelException, UnauthorisedClientException, NotSubscribedChannelException
	{
		this.subscriptionPlugin.unsubscribe(channel);
	}

	public void modifyFilter(String channel, MessageFilterI filter)
	throws UnknownClientException, UnknownChannelException, UnauthorisedClientException, NotSubscribedChannelException
	{
		this.subscriptionPlugin.modifyFilter(channel, filter);
	}

	// ---------------------------------------------------------------------
	// Publication API
	// ---------------------------------------------------------------------

	public void publish(String channel, MessageI message)
	throws UnknownClientException, UnknownChannelException, UnauthorisedClientException
	{
		this.publicationPlugin.publish(channel, message);
	}

	// ---------------------------------------------------------------------
	// Privileged channel management
	// ---------------------------------------------------------------------

	public void createChannel(String channel, String authorisedUsers)
	throws UnknownClientException,
			fr.sorbonne_u.cps.pubsub.exceptions.AlreadyExistingChannelException,
			fr.sorbonne_u.cps.pubsub.exceptions.ChannelQuotaExceededException
	{
		try {
			this.registrationPlugin.getPrivilegedPortOUT().createChannel(
				this.getReceptionPortURI(),
				channel,
				authorisedUsers);
		} catch (Exception e) {
			// Preserve declared exceptions when possible (no Java preview features).
			if (e instanceof UnknownClientException) {
				throw (UnknownClientException) e;
			}
			if (e instanceof fr.sorbonne_u.cps.pubsub.exceptions.AlreadyExistingChannelException) {
				throw (fr.sorbonne_u.cps.pubsub.exceptions.AlreadyExistingChannelException) e;
			}
			if (e instanceof fr.sorbonne_u.cps.pubsub.exceptions.ChannelQuotaExceededException) {
				throw (fr.sorbonne_u.cps.pubsub.exceptions.ChannelQuotaExceededException) e;
			}
			throw new RuntimeException(e);
		}
	}

	// ---------------------------------------------------------------------
	// Reception hook
	// ---------------------------------------------------------------------

	public void onReceive(String channel, MessageI message)
	{
		if (message == null) {
			this.traceMessage(
				"PluginClient " + this.getReflectionInboundPortURI() + " received empty batch on " + channel + "\n");
			return;
		}
		this.traceMessage(
			"PluginClient " + this.getReflectionInboundPortURI()
				+ " received on " + channel + " payload=" + message.getPayload()
				+ " timestamp=" + message.getTimeStamp() + "\n");
		this.logMessage(
			"[LOG] PluginClient " + this.getReflectionInboundPortURI()
				+ " received on " + channel + " payload=" + message.getPayload()
				+ " timestamp=" + message.getTimeStamp() + "\n");
	}
}
