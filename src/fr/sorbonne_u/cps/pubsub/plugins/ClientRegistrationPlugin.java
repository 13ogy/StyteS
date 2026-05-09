package fr.sorbonne_u.cps.pubsub.plugins;

import fr.sorbonne_u.components.AbstractPlugin;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.base.connectors.PrivilegedClientConnector;
import fr.sorbonne_u.cps.pubsub.base.connectors.PublishingConnector;
import fr.sorbonne_u.cps.pubsub.base.connectors.RegistrationConnector;
import fr.sorbonne_u.cps.pubsub.base.ports.ReceivingInboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.PrivilegedClientOutboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.PublishingOutboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.RegistrationOutboundPort;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.*;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;

/**
 * Client-side plugin implementing registration operations (CDC §3.5).
 * It owns the ports needed to register to the broker and connect the publication
 * and privileged management ports.
 
 *
 * @author Bogdan Styn
 */
public class ClientRegistrationPlugin extends AbstractPlugin implements ClientRegistrationI
{
	private static final long serialVersionUID = 1L;

	protected ReceivingInboundPort receptionPortIN;
	protected RegistrationOutboundPort registrationPortOUT;
	protected PublishingOutboundPort publishingPortOUT;
	protected PrivilegedClientOutboundPort privilegedPortOUT;

	private ClientSubscriptionPlugin subscriptionPlugin;

	protected RegistrationClass currentRC;
	protected boolean registered;

	/** Reflection inbound port URI of the broker this client wants to talk
	 *  to. {@code null} only when the deprecated no-arg constructor was
	 *  used; in that case {@link #initialise()} will fail with a clear
	 *  message. (Phase C.3) */
	private final String brokerReflectionURI;

	/**
	 * @deprecated use {@link #ClientRegistrationPlugin(String)} so that
	 *             this client knows which broker to connect to in a
	 *             multi-broker environment (Phase C.3).
	 */
	@Deprecated
	public ClientRegistrationPlugin()
	{
		this(null);
	}

	/**
	 * Preferred constructor (Phase C.3): the plugin knows which broker
	 * it must contact, identified by its reflection inbound port URI.
	 * The plugin uses {@link Broker#registrationPortURIFor(String)} to
	 * derive the broker's registration port URI deterministically.
	 */
	public ClientRegistrationPlugin(String brokerReflectionURI)
	{
		super();
		this.brokerReflectionURI = brokerReflectionURI;
		this.registered = false;
	}

	public void setSubscriptionPlugin(ClientSubscriptionPlugin plugin){
		this.subscriptionPlugin = plugin;
	}

	@Override
	public void installOn(fr.sorbonne_u.components.ComponentI owner) throws Exception
	{
		super.installOn(owner);
		// Add interfaces only if the owner does not already declare them
		// (e.g. via @OfferedInterfaces / @RequiredInterfaces annotations).
		// Re-adding triggers a precondition assertion under -ea.
		if (!owner.isOfferedInterface(ReceivingCI.class)) {
			this.addOfferedInterface(ReceivingCI.class);
		}
		if (!owner.isRequiredInterface(RegistrationCI.class)) {
			this.addRequiredInterface(RegistrationCI.class);
		}
		if (!owner.isRequiredInterface(PublishingCI.class)) {
			this.addRequiredInterface(PublishingCI.class);
		}
		if (!owner.isRequiredInterface(PrivilegedClientCI.class)) {
			this.addRequiredInterface(PrivilegedClientCI.class);
		}
	}
	@Override
	public void initialise() throws Exception {
		super.initialise();
		if (this.brokerReflectionURI == null || this.brokerReflectionURI.isEmpty()) {
			throw new IllegalStateException(
				"ClientRegistrationPlugin requires a broker reflection inbound "
				+ "port URI; use ClientRegistrationPlugin(String) instead of "
				+ "the deprecated no-arg variant (Phase C.3).");
		}
		// Publish ports
		this.receptionPortIN = new ReceivingInboundPort(this.getOwner(), this.getPluginURI());
		this.receptionPortIN.publishPort();

		this.registrationPortOUT = new RegistrationOutboundPort(this.getOwner());
		this.registrationPortOUT.publishPort();

		this.publishingPortOUT = new PublishingOutboundPort(this.getOwner());
		this.publishingPortOUT.publishPort();

		this.privilegedPortOUT = new PrivilegedClientOutboundPort(this.getOwner());
		this.privilegedPortOUT.publishPort();

		// Connect ports
		this.getOwner().doPortConnection(
				this.registrationPortOUT.getPortURI(),
				Broker.registrationPortURIFor(this.brokerReflectionURI),
				RegistrationConnector.class.getCanonicalName());
	}
	@Override
	public void finalise() throws Exception {
		if (this.registrationPortOUT != null && this.registrationPortOUT.connected()) {
			this.getOwner().doPortDisconnection(this.registrationPortOUT.getPortURI());
		}
		if (this.publishingPortOUT != null && this.publishingPortOUT.connected()) {
			this.getOwner().doPortDisconnection(this.publishingPortOUT.getPortURI());
		}
		if (this.privilegedPortOUT != null && this.privilegedPortOUT.connected()) {
			this.getOwner().doPortDisconnection(this.privilegedPortOUT.getPortURI());
		}
		super.finalise();
	}
	@Override
	public void uninstall() throws Exception
	{
		// Defensive disconnect-before-destroy: shutdown() may invoke uninstall()
		// before finalise() runs (or finalise() may have been bypassed entirely),
		// in which case destroyPort() would trip a !connected() precondition.
		if (this.registrationPortOUT != null && this.registrationPortOUT.connected()) {
			this.getOwner().doPortDisconnection(this.registrationPortOUT.getPortURI());
		}
		if (this.publishingPortOUT != null && this.publishingPortOUT.connected()) {
			this.getOwner().doPortDisconnection(this.publishingPortOUT.getPortURI());
		}
		if (this.privilegedPortOUT != null && this.privilegedPortOUT.connected()) {
			this.getOwner().doPortDisconnection(this.privilegedPortOUT.getPortURI());
		}

		// Unpublish ports (guarded with isPublished() — defensive shutdown
		// helpers may have already unpublished some ports).
		if (this.receptionPortIN != null && !this.receptionPortIN.isDestroyed()) {
			if (this.receptionPortIN.isPublished()) this.receptionPortIN.unpublishPort();
			this.receptionPortIN.destroyPort();
		}
		if (this.registrationPortOUT != null && !this.registrationPortOUT.isDestroyed()) {
			if (this.registrationPortOUT.isPublished()) this.registrationPortOUT.unpublishPort();
			this.registrationPortOUT.destroyPort();
		}
		if (this.publishingPortOUT != null && !this.publishingPortOUT.isDestroyed()) {
			if (this.publishingPortOUT.isPublished()) this.publishingPortOUT.unpublishPort();
			this.publishingPortOUT.destroyPort();
		}
		if (this.privilegedPortOUT != null && !this.privilegedPortOUT.isDestroyed()) {
			if (this.privilegedPortOUT.isPublished()) this.privilegedPortOUT.unpublishPort();
			this.privilegedPortOUT.destroyPort();
		}
		// Remove interfaces only if currently declared (mirrors the guarded
		// installOn(): when the owner declares them via annotations, the
		// plugin must not try to remove them).
		if (this.getOwner().isOfferedInterface(ReceivingCI.class)) {
			this.removeOfferedInterface(ReceivingCI.class);
		}
		if (this.getOwner().isRequiredInterface(RegistrationCI.class)) {
			this.removeRequiredInterface(RegistrationCI.class);
		}
		if (this.getOwner().isRequiredInterface(PrivilegedClientCI.class)) {
			this.removeRequiredInterface(PrivilegedClientCI.class);
		}
		if (this.getOwner().isRequiredInterface(PublishingCI.class)) {
			this.removeRequiredInterface(PublishingCI.class);
		}

		super.uninstall();
	}

	// ---------------------------------------------------------------------
	// Accessors
	// ---------------------------------------------------------------------

	public String getReceptionPortURI() throws Exception
	{
		return this.receptionPortIN.getPortURI();
	}

	public RegistrationOutboundPort getRegistrationPortOUT()
	{
		return this.registrationPortOUT;
	}

	public PublishingOutboundPort getPublishingPortOUT()
	{
		return this.publishingPortOUT;
	}

	public PrivilegedClientOutboundPort getPrivilegedPortOUT()
	{
		return this.privilegedPortOUT;
	}

	// ---------------------------------------------------------------------
	// ClientRegistrationI
	// ---------------------------------------------------------------------

	@Override
	public boolean registered()
	{
		return this.registered;
	}

	@Override
	public boolean registered(RegistrationClass rc) throws UnknownClientException
	{
		if (!this.registered) {
			throw new UnknownClientException("not registered");
		}
		return rc != null && rc == this.currentRC;
	}

	@Override
	public void register(RegistrationClass rc) throws AlreadyRegisteredException
	{
		if (this.registered) throw new AlreadyRegisteredException();

		try {
			String brokerPortURI = this.registrationPortOUT.register(
					this.receptionPortIN.getPortURI(), rc);

			if (rc == RegistrationClass.FREE) {
				// FREE clients only get publishing
				this.getOwner().doPortConnection(
						this.publishingPortOUT.getPortURI(),
						brokerPortURI,
						PublishingConnector.class.getCanonicalName());
			} else {
				// STANDARD/PREMIUM get privileged (which also covers publishing
				// because PrivilegedClientCI extends PublishingCI). Connect
				// BOTH outbound ports to the same broker privileged inbound
				// port so the publication plugin (which uses the publishing
				// outbound port) keeps working for these classes.
				this.getOwner().doPortConnection(
						this.privilegedPortOUT.getPortURI(),
						brokerPortURI,
						PrivilegedClientConnector.class.getCanonicalName());
				this.getOwner().doPortConnection(
						this.publishingPortOUT.getPortURI(),
						brokerPortURI,
						PublishingConnector.class.getCanonicalName());
			}
			this.currentRC = rc;
			this.registered=true;
		}catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	@Override
	public void modifyServiceClass(RegistrationClass rc) throws UnknownClientException, AlreadyRegisteredException
	{
		if (!this.registered) {
			throw new UnknownClientException("not registered");
		}
		if (rc == this.currentRC) {
			throw new AlreadyRegisteredException();
		}
		try {
			String brokerPortURI = this.registrationPortOUT.modifyServiceClass(this.receptionPortIN.getPortURI(), rc);

			if (this.currentRC == RegistrationClass.FREE) {
				this.getOwner().doPortDisconnection(this.publishingPortOUT.getPortURI());
			} else {
				this.getOwner().doPortDisconnection(this.privilegedPortOUT.getPortURI());
				if (this.publishingPortOUT.connected()) {
					this.getOwner().doPortDisconnection(this.publishingPortOUT.getPortURI());
				}
			}

			if (rc == RegistrationClass.FREE) {
				this.getOwner().doPortConnection(
						this.publishingPortOUT.getPortURI(),
						brokerPortURI,
						PublishingConnector.class.getCanonicalName());
			} else {
				this.getOwner().doPortConnection(
						this.privilegedPortOUT.getPortURI(),
						brokerPortURI,
						PrivilegedClientConnector.class.getCanonicalName());
				this.getOwner().doPortConnection(
						this.publishingPortOUT.getPortURI(),
						brokerPortURI,
						PublishingConnector.class.getCanonicalName());
			}

			this.currentRC = rc;
		} catch (UnknownClientException | AlreadyRegisteredException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void unregister() throws UnknownClientException
	{
		try {
			if (!this.registered) {
				throw new UnknownClientException("not registered");
			}
			this.registrationPortOUT.unregister(this.receptionPortIN.getPortURI());
			this.registered = false;
		} catch (UnknownClientException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}


	public void receive(String channel, MessageI message) {
		if (this.subscriptionPlugin != null) {
			this.subscriptionPlugin.receive(channel, message);
		}
	}
}
