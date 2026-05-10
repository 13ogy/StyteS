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
 * Plugin client implémentant les opérations de gestion de canaux
 * privilégiés (CDC §3.5.2).
 *
 * <p>
 * Ce plugin délègue ses appels au broker via le port outbound
 * {@code PrivilegedClientCI} possédé par {@link ClientRegistrationPlugin}.
 * L'identité du client utilisée par le broker est l'URI du port
 * {@code ReceivingCI}, obtenue via
 * {@link ClientRegistrationPlugin#getReceptionPortURI()}.
 * </p>
 *
 * <p>
 * <strong>Héritage :</strong> hérite de {@link ClientPublicationPlugin}
 * pour réutiliser le câblage et la sémantique de {@code publish} ;
 * cependant les méthodes {@link #publish(String, MessageI)} et
 * {@link #publish(String, java.util.ArrayList)} sont surchargées pour
 * passer par le port <em>privilégié</em> (qui hérite de
 * {@code PublishingCI}) plutôt que par le port {@code PublishingCI}
 * standard. Cycle de vie BCM hérité du parent ; aucun port en propre.
 * </p>
 *
 * <p>L'URI du plugin suit la convention
 * {@code <reflectionURI>-privileged-plugin}.</p>
 *
 * <p>Created on : 2026-02-04</p>
 *
 * @author Bogdan Styn, Setbel Mélissa
 */
public class ClientPrivilegedPlugin extends ClientPublicationPlugin implements PrivilegedClientI
{
	private static final long serialVersionUID = 1L;


	/**
	 * Crée un plugin privilégié adossé à un plugin de registration.
	 *
	 * @param registrationPlugin	plugin propriétaire des ports
	 *								{@code PrivilegedClientCI} et
	 *								{@code RegistrationCI} ; non {@code null}.
	 */
	public ClientPrivilegedPlugin(ClientRegistrationPlugin registrationPlugin) {
		super(registrationPlugin);
	}

	// Life cycle inherited from PublicationPlugin


	/**
	 * Indique si ce client est le créateur de {@code channel}.
	 *
	 * @param channel	nom du canal interrogé.
	 * @return			{@code true} ssi le client a créé {@code channel}.
	 * @throws UnknownClientException	si le client n'est pas enregistré.
	 * @throws UnknownChannelException	si {@code channel} n'existe pas.
	 */
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

	/**
	 * Indique si ce client a atteint le quota de canaux privilégiés
	 * autorisé par sa classe de service ({@code STANDARD} : 2,
	 * {@code PREMIUM} : 5).
	 *
	 * @return	{@code true} ssi le quota est atteint.
	 * @throws UnknownClientException	si le client n'est pas enregistré.
	 */
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

	/**
	 * Crée un canal privilégié dont l'accès est restreint au motif
	 * regex {@code autorisedUsers} (appliqué aux URIs des ports
	 * {@code ReceivingCI} des clients candidats).
	 *
	 * @param channel			nom du canal à créer.
	 * @param autorisedUsers	motif regex (Java) sélectionnant les URIs
	 *							de réception autorisées.
	 * @throws UnknownClientException			si le client n'est pas enregistré.
	 * @throws AlreadyExistingChannelException	si {@code channel} existe déjà.
	 * @throws ChannelQuotaExceededException	si le quota du client est atteint.
	 */
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

	/**
	 * Modifie le motif regex des utilisateurs autorisés sur un canal
	 * privilégié dont ce client est le créateur.
	 *
	 * @param channel			canal cible.
	 * @param autorisedUsers	nouveau motif regex.
	 * @throws UnknownClientException		si le client n'est pas enregistré.
	 * @throws UnknownChannelException		si {@code channel} n'existe pas.
	 * @throws UnauthorisedClientException	si ce client n'est pas le
	 *										créateur de {@code channel}.
	 */
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

	/**
	 * Détruit un canal privilégié dont ce client est le créateur, en
	 * laissant le broker terminer proprement les livraisons en cours
	 * (drain). Pour une destruction immédiate, voir
	 * {@link #destroyChannelNow(String)}.
	 *
	 * @param channel	canal à détruire.
	 * @throws UnknownClientException		si le client n'est pas enregistré.
	 * @throws UnknownChannelException		si {@code channel} n'existe pas.
	 * @throws UnauthorisedClientException	si ce client n'est pas le
	 *										créateur de {@code channel}.
	 */
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

	/**
	 * Variante immédiate de {@link #destroyChannel(String)} : le broker
	 * détruit le canal sans drainer les messages en cours.
	 *
	 * @param channel	canal à détruire.
	 * @throws UnknownClientException		si le client n'est pas enregistré.
	 * @throws UnknownChannelException		si {@code channel} n'existe pas.
	 * @throws UnauthorisedClientException	si ce client n'est pas le
	 *										créateur de {@code channel}.
	 */
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

	/**
	 * Surcharge {@link ClientPublicationPlugin#publish(String, MessageI)}
	 * pour faire passer la publication par le port {@code PrivilegedClientCI}
	 * (qui hérite de {@code PublishingCI}). Réservée aux canaux privilégiés.
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

	/**
	 * Variante batch de {@link #publish(String, MessageI)}, passant elle
	 * aussi par le port {@code PrivilegedClientCI}.
	 *
	 * @param channel	canal de publication.
	 * @param messages	liste de messages à publier.
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
