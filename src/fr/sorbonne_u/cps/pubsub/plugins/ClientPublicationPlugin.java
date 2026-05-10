package fr.sorbonne_u.cps.pubsub.plugins;

import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.AbstractPlugin;
import fr.sorbonne_u.cps.pubsub.base.ports.ClientPublishingOutboundPort;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.*;

import java.util.ArrayList;

/**
 * Plugin client implémentant les opérations de publication (CDC §3.5).
 *
 * <p><strong>Description</strong></p>
 *
 * <p>
 * Ce plugin <em>ne possède aucun port en propre</em> : il délègue toutes
 * ses opérations au port outbound {@code PublishingCI} possédé par le
 * {@link ClientRegistrationPlugin} associé. Il en va de même pour
 * {@code channelExist} / {@code channelAuthorised} qui passent par le
 * port {@code RegistrationCI} du plugin de registration.
 * </p>
 *
 * <p>
 * L'identité du client transmise au broker est l'URI du port
 * {@code ReceivingCI} obtenue via
 * {@link ClientRegistrationPlugin#getReceptionPortURI()}.
 * </p>
 *
 * <p>Cycle de vie BCM (cf. {@link AbstractPlugin}) :
 * {@link #installOn}, {@link #initialise}, {@link #finalise} et
 * {@link #uninstall} sont des appels {@code super} sans état
 * supplémentaire (toutes les ressources port sont gérées par le plugin
 * de registration).</p>
 *
 * <p>L'URI du plugin suit la convention
 * {@code <reflectionURI>-publication-plugin} fixée par
 * {@code PluginClient}.</p>
 *
 * <p>Created on : 2026-02-04</p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class ClientPublicationPlugin extends AbstractPlugin implements ClientPublicationI
{
	private static final long serialVersionUID = 1L;

	/** Plugin propriétaire des ports utilisés pour publier. */
	protected final ClientRegistrationPlugin registrationPlugin;

	/**
	 * Crée un plugin de publication adossé à un plugin de registration.
	 *
	 * @param registrationPlugin	plugin propriétaire des ports
	 *								{@code PublishingCI} et
	 *								{@code RegistrationCI} ; non {@code null}.
	 */
	public ClientPublicationPlugin(ClientRegistrationPlugin registrationPlugin)
	{
		super();
		this.registrationPlugin = registrationPlugin;
	}

	/**
	 * Délègue à {@link AbstractPlugin#installOn}. Aucune interface
	 * n'est ajoutée ici : elles sont déjà déclarées par le plugin de
	 * registration qui possède les ports correspondants.
	 *
	 * @param owner		composant propriétaire du plugin.
	 * @throws Exception	si l'installation BCM échoue.
	 */
	// All required ports and interfaces are in the registration pluging
	public void installOn(fr.sorbonne_u.components.ComponentI owner) throws Exception
	{
		super.installOn(owner);
	}

	/**
	 * Initialisation BCM par défaut ; aucune ressource locale à allouer.
	 *
	 * @throws Exception	si {@link AbstractPlugin#initialise()} échoue.
	 */
	public void initialise() throws Exception
	{
		super.initialise();
	}

	/**
	 * Finalisation BCM par défaut ; aucune ressource locale à libérer.
	 *
	 * @throws Exception	si {@link AbstractPlugin#finalise()} échoue.
	 */
	@Override
	public void finalise() throws Exception
	{
		super.finalise();
	}

	/**
	 * Désinstallation BCM par défaut ; aucune ressource locale à
	 * détruire.
	 *
	 * @throws Exception	si {@link AbstractPlugin#uninstall()} échoue.
	 */
	@Override
	public void uninstall() throws Exception
	{

		super.uninstall();
	}


	/**
	 * {@inheritDoc}
	 *
	 * <p>Délègue au port {@code RegistrationCI} du plugin de registration.</p>
	 *
	 * @param channel	nom du canal interrogé.
	 * @return			{@code true} ssi le canal existe côté broker.
	 */
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

	/**
	 * {@inheritDoc}
	 *
	 * @param channel	nom du canal interrogé.
	 * @return			{@code true} ssi ce client est autorisé à publier
	 *					sur {@code channel}.
	 * @throws UnknownClientException	si le client n'est pas enregistré.
	 * @throws UnknownChannelException	si {@code channel} n'existe pas.
	 */
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

	/**
	 * Publie {@code message} sur {@code channel} via le port
	 * {@code PublishingCI} du plugin de registration. L'appel est
	 * synchrone côté client mais le broker traite la publication de
	 * manière asynchrone (cf. pipeline réception → propagation →
	 * livraison).
	 *
	 * @param channel	canal de publication.
	 * @param message	message à publier.
	 * @throws UnknownClientException		si le client n'est pas enregistré.
	 * @throws UnknownChannelException		si {@code channel} n'existe pas.
	 * @throws UnauthorisedClientException	si le client n'est pas autorisé
	 *										à publier sur {@code channel}.
	 */
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

	/**
	 * Variante batch de {@link #publish(String, MessageI)} : publie une
	 * liste de messages sur un même canal.
	 *
	 * @param channel	canal de publication.
	 * @param messages	liste de messages non vides ne contenant aucun
	 *					élément {@code null}.
	 * @throws UnknownClientException		si le client n'est pas enregistré.
	 * @throws UnknownChannelException		si {@code channel} n'existe pas.
	 * @throws UnauthorisedClientException	si le client n'est pas autorisé
	 *										à publier sur {@code channel}.
	 */
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

	/**
	 * Soumet la publication via {@code runTask} sur l'un des executors du
	 * composant propriétaire pour ne pas bloquer le thread appelant.
	 *
	 * <p>Les exceptions levées par {@link #publish(String, MessageI)} sont
	 * imprimées sur la sortie d'erreur (le contrat n'autorise pas leur
	 * propagation par cette méthode asynchrone).</p>
	 *
	 * @param channel	canal de publication.
	 * @param message	message à publier.
	 */
	@Override
	public void asyncPublishAndNotify(String channel, MessageI message) {
		final ClientPublicationPlugin self = this;
		this.getOwner().runTask(o -> {
			try {
				self.publish(channel, message);
			} catch (Exception e) { e.printStackTrace(); }
		});
	}

	/**
	 * Variante batch de
	 * {@link #asyncPublishAndNotify(String, MessageI)}.
	 *
	 * @param channel	canal de publication.
	 * @param messages	liste de messages à publier.
	 */
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
