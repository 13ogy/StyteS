package fr.sorbonne_u.cps.pubsub.plugins;

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
import fr.sorbonne_u.cps.pubsub.interfaces.PrivilegedClientCI;
import fr.sorbonne_u.cps.pubsub.interfaces.PublishingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.ReceivingCI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;
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
		// Add interfaces
		this.addOfferedInterface(ReceivingCI.class);
		this.addRequiredInterface(RegistrationCI.class);
		this.addRequiredInterface(PublishingCI.class);
		this.addRequiredInterface(PrivilegedClientCI.class);
	}
	@Override
	public void initialise() throws Exception {
		super.initialise();
		// Publish ports
		this.receptionPortIN = new ClientInboundPort(this.getOwner(), this.getPluginURI());
		this.receptionPortIN.publishPort();

		this.registrationPortOUT = new ClientRegistrationOutboundPort(this.getOwner());
		this.registrationPortOUT.publishPort();

		this.publishingPortOUT = new ClientPublishingOutboundPort(this.getOwner());
		this.publishingPortOUT.publishPort();

		this.privilegedPortOUT = new ClientPrivilegedOutboundPort(this.getOwner());
		this.privilegedPortOUT.publishPort();

		// Connect ports
		this.getOwner().doPortConnection(
				this.registrationPortOUT.getPortURI(),
				Broker.registrationPortURI(),
				ClientBrokerRegistrationConnector.class.getCanonicalName());
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
		// Unpublish ports
		if (this.receptionPortIN != null && !this.receptionPortIN.isDestroyed()) {
			this.receptionPortIN.unpublishPort();
			this.receptionPortIN.destroyPort();
		}
		if (this.registrationPortOUT != null && !this.registrationPortOUT.isDestroyed()) {
			this.registrationPortOUT.unpublishPort();
			this.registrationPortOUT.destroyPort();
		}
		if (this.publishingPortOUT != null && !this.publishingPortOUT.isDestroyed()) {
			this.publishingPortOUT.unpublishPort();
			this.publishingPortOUT.destroyPort();
		}
		if (this.privilegedPortOUT != null && !this.privilegedPortOUT.isDestroyed()) {
			this.privilegedPortOUT.unpublishPort();
			this.privilegedPortOUT.destroyPort();
		}
		//Removing interfaces
		this.removeOfferedInterface(ReceivingCI.class);
		this.removeRequiredInterface(RegistrationCI.class);
		this.removeRequiredInterface(PublishingCI.class);
		this.removeRequiredInterface(PrivilegedClientCI.class);

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
		if (this.registered) throw new AlreadyRegisteredException();

		try {
			String brokerPortURI = this.registrationPortOUT.register(
					this.receptionPortIN.getPortURI(), rc);

			if (rc == RegistrationClass.FREE) {
				// FREE clients only get publishing
				this.getOwner().doPortConnection(
						this.publishingPortOUT.getPortURI(),
						brokerPortURI,
						ClientBrokerPublishingConnector.class.getCanonicalName());
			} else {
				// STANDARD/PREMIUM get privileged (which also covers publishing)
				this.getOwner().doPortConnection(
						this.privilegedPortOUT.getPortURI(),
						brokerPortURI,
						ClientBrokerPrivilegedConnector.class.getCanonicalName());
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
			}

			if (rc == RegistrationClass.FREE) {
				this.getOwner().doPortConnection(
						this.publishingPortOUT.getPortURI(),
						brokerPortURI,
						ClientBrokerPublishingConnector.class.getCanonicalName());
			} else {
				this.getOwner().doPortConnection(
						this.privilegedPortOUT.getPortURI(),
						brokerPortURI,
						ClientBrokerPrivilegedConnector.class.getCanonicalName());
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
}
