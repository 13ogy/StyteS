package fr.sorbonne_u.cps.pubsub.plugins;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.AbstractPlugin;
import fr.sorbonne_u.cps.pubsub.base.components.Broker;
import fr.sorbonne_u.cps.pubsub.base.connectors.ClientBrokerPrivilegedConnector;
import fr.sorbonne_u.cps.pubsub.base.connectors.ClientBrokerPublishingConnector;
import fr.sorbonne_u.cps.pubsub.base.connectors.ClientBrokerRegistrationConnector;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientInboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientPrivilegedOutboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientPublishingOutboundPort;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientRegistrationOutboundPort;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;

/**
 * Client-side plugin implementing registration operations (CDC §3.5).
 *
 * It owns the ports needed to register to the broker and connect the publication
 * and privileged management ports.
 
 *
 * @author Bogdan Styn
 */
public class ClientRegistrationPlugin extends AbstractPlugin implements ClientRegistrationI
{
	private static final long serialVersionUID = 1L;

	protected ClientInboundPort receptionPortIN;
	protected ClientRegistrationOutboundPort registrationPortOUT;
	protected ClientPublishingOutboundPort publishingPortOUT;
	protected ClientPrivilegedOutboundPort privilegedPortOUT;

	protected RegistrationClass currentRC;
	protected boolean registered;

	public ClientRegistrationPlugin()
	{
		super();
		this.registered = false;
	}

	@Override
	public void installOn(fr.sorbonne_u.components.ComponentI owner) throws Exception
	{
		super.installOn(owner);
		AbstractComponent ac = (AbstractComponent) owner;

		// Ports created on the owner component.
		this.receptionPortIN = new ClientInboundPort(ac);
		this.receptionPortIN.publishPort();

		this.registrationPortOUT = new ClientRegistrationOutboundPort(ac);
		this.registrationPortOUT.publishPort();

		this.publishingPortOUT = new ClientPublishingOutboundPort(ac);
		this.publishingPortOUT.publishPort();

		this.privilegedPortOUT = new ClientPrivilegedOutboundPort(ac);
		this.privilegedPortOUT.publishPort();
	}

	@Override
	public void uninstall() throws Exception
	{
		try {
			if (this.registered) {
				this.unregister();
			}
		}
        catch (Exception ignored) {
		}

		try {
			if (this.registrationPortOUT != null && this.registrationPortOUT.connected()) {
				this.getOwner().doPortDisconnection(this.registrationPortOUT.getPortURI());
			}
		} catch (Exception ignored) {
		}
		try {
			if (this.publishingPortOUT != null && this.publishingPortOUT.connected()) {
				this.getOwner().doPortDisconnection(this.publishingPortOUT.getPortURI());
			}
		} catch (Exception ignored) {
		}
		try {
			if (this.privilegedPortOUT != null && this.privilegedPortOUT.connected()) {
				this.getOwner().doPortDisconnection(this.privilegedPortOUT.getPortURI());
			}
		} catch (Exception ignored) {
		}

		try {
			if (this.privilegedPortOUT != null) this.privilegedPortOUT.unpublishPort();
		} catch (Exception ignored) {
		}
		try {
			if (this.publishingPortOUT != null) this.publishingPortOUT.unpublishPort();
		} catch (Exception ignored) {
		}
		try {
			if (this.registrationPortOUT != null) this.registrationPortOUT.unpublishPort();
		} catch (Exception ignored) {
		}
		try {
			if (this.receptionPortIN != null) this.receptionPortIN.unpublishPort();
		} catch (Exception ignored) {
		}
		try {
			if (this.privilegedPortOUT != null) this.privilegedPortOUT.destroyPort();
		} catch (Exception ignored) {
		}
		try {
			if (this.publishingPortOUT != null) this.publishingPortOUT.destroyPort();
		} catch (Exception ignored) {
		}
		try {
			if (this.registrationPortOUT != null) this.registrationPortOUT.destroyPort();
		} catch (Exception ignored) {
		}
		try {
			if (this.receptionPortIN != null) this.receptionPortIN.destroyPort();
		} catch (Exception ignored) {
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

	public ClientRegistrationOutboundPort getRegistrationPortOUT()
	{
		return this.registrationPortOUT;
	}

	public ClientPublishingOutboundPort getPublishingPortOUT()
	{
		return this.publishingPortOUT;
	}

	public ClientPrivilegedOutboundPort getPrivilegedPortOUT()
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
		try {
			if (this.registered) {
				throw new AlreadyRegisteredException();
			}
			this.currentRC = rc;

			// connect to broker registration port
			this.getOwner().doPortConnection(
				this.registrationPortOUT.getPortURI(),
				Broker.registrationPortURI(),
				ClientBrokerRegistrationConnector.class.getCanonicalName());

			// register -> receive broker publishing inbound port URI
			String brokerPublishingURI =
				this.registrationPortOUT.register(this.receptionPortIN.getPortURI(), rc);

			// connect publishing outbound port
			this.getOwner().doPortConnection(
				this.publishingPortOUT.getPortURI(),
				brokerPublishingURI,
				ClientBrokerPublishingConnector.class.getCanonicalName());

			// connect privileged outbound port
			this.getOwner().doPortConnection(
				this.privilegedPortOUT.getPortURI(),
				Broker.privilegedPortURI(),
				ClientBrokerPrivilegedConnector.class.getCanonicalName());

			this.registered = true;
		} catch (AlreadyRegisteredException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void modifyServiceClass(RegistrationClass rc) throws UnknownClientException, AlreadyRegisteredException
	{
		try {
			if (!this.registered) {
				throw new UnknownClientException("not registered");
			}
			if (rc == this.currentRC) {
				throw new AlreadyRegisteredException();
			}
			this.currentRC = rc;
			String brokerPublishingURI = this.registrationPortOUT.modifyServiceClass(this.receptionPortIN.getPortURI(), rc);

			// reconnecting publishing port
			this.getOwner().doPortDisconnection(this.publishingPortOUT.getPortURI());
			this.getOwner().doPortConnection(
				this.publishingPortOUT.getPortURI(),
				brokerPublishingURI,
				ClientBrokerPublishingConnector.class.getCanonicalName());
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
}
