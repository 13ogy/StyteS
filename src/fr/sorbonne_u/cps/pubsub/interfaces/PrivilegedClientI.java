package fr.sorbonne_u.cps.pubsub.interfaces;

// Copyright Jacques Malenfant, Sorbonne Universite.
// Jacques.Malenfant@lip6.fr
//
// This software is a computer program whose purpose is to provide a
// basic component programming model to program with components
// distributed applications in the Java programming language.
//
// This software is governed by the CeCILL-C license under French law and
// abiding by the rules of distribution of free software.  You can use,
// modify and/ or redistribute the software under the terms of the
// CeCILL-C license as circulated by CEA, CNRS and INRIA at the following
// URL "http://www.cecill.info".
//
// As a counterpart to the access to the source code and  rights to copy,
// modify and redistribute granted by the license, users are provided only
// with a limited warranty  and the software's author,  the holder of the
// economic rights,  and the successive licensors  have only  limited
// liability. 
//
// In this respect, the user's attention is drawn to the risks associated
// with loading,  using,  modifying and/or developing or reproducing the
// software by the user in light of its specific status of free software,
// that may mean  that it is complicated to manipulate,  and  that  also
// therefore means  that it is reserved for developers  and  experienced
// professionals having in-depth computer knowledge. Users are therefore
// encouraged to load and test the software's suitability as regards their
// requirements in conditions enabling the security of their systems and/or 
// data to be ensured and,  more generally, to use and operate it in the 
// same conditions as regards security. 
//
// The fact that you are presently reading this means that you have had
// knowledge of the CeCILL-C license and that you accept its terms.

import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyExistingChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.ChannelQuotaExceededException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.components.PluginI;

// -----------------------------------------------------------------------------
/**
 * The interface <code>PrivilegedClientI</code> declares the method signatures
 * through which a publication/subscription system privileged user manages the
 * its channels.
 *
 * <p><strong>Description</strong></p>
 * 
 * <p><strong>Invariants</strong></p>
 * 
 * <pre>
 * invariant	{@code true}	// no more invariant
 * </pre>
 * 
 * <p>Created on : 2026-01-20</p>
 * 
 * @author	<a href="mailto:Jacques.Malenfant@lip6.fr">Jacques Malenfant</a>
 */
public interface		PrivilegedClientI
extends		PluginI
{
	// -------------------------------------------------------------------------
	// Signature and default methods
	// -------------------------------------------------------------------------

	/**
	 * return true if the component has created {@code channel}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param channel					channel to be tested.
	 * @return							true if the component has created {@code channel}.
	 * @throws UnknownClientException	when {@code receptionPortURI} does not correspond to a registered component.
	 * @throws UnknownChannelException	when the channel does not exist.
	 */
	public boolean		hasCreatedChannel(String channel)
	throws	UnknownClientException,
			UnknownChannelException;
			
	/**
	 * return true if the component has reached its channel quota.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code true}	// no precondition.
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @return							true if the component registered under {@code receptionPortURI} has reached its channel quota.
	 * @throws UnknownClientException	when {@code receptionPortURI} does not correspond to a registered component.
	 */
	public boolean		channelQuotaReached() throws UnknownClientException;

	/**
	 * create a new channel of the given name with a set of users authorised to
	 * use it.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * pre	{@code autorisedUsers == null || !autorisedUsers.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param channel							name of the channel to be created.
	 * @param autorisedUsers					regular expression that matches the URIs of components that must be authorised to use the new channel.
	 * @throws UnknownClientException			when {@code receptionPortURI} does not correspond to a registered component.
	 * @throws AlreadyExistingChannelException	when the channel to be created already exists.
	 * @throws ChannelQuotaExceededException	when, the user tries to exceed its channel quota.
	 */
	public void			createChannel(
		String channel,
		String autorisedUsers
		) throws	UnknownClientException,
					AlreadyExistingChannelException,
					ChannelQuotaExceededException;

	/**
	 * return true if {@code uri} is authorised to use {@code channel}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * pre	{@code uri != null && !uri.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param channel					name of an existing channel.
	 * @param uri						URI of component to be tested.
	 * @return							true if {@code uri} is authorised to use {@code channel}.
	 * @throws UnknownClientException	when {@code uri} does not correspond to a registered component.
	 * @throws UnknownChannelException	when the channel does not exist.
	 */
	public boolean		isAuthorisedUser(String channel, String uri)
	throws	UnknownClientException,
			UnknownChannelException;

	/**
	 * modify the users authorised to use {@code channel}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * pre	{@code autorisedUsers != null && !autorisedUsers.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param channel						name of the channel.
	 * @param autorisedUsers				regular expression that matches the URIs of components that must be authorised to use the new channel.
	 * @throws UnknownClientException		when {@code receptionPortURI} or {@code uri} does not correspond to a registered component.
	 * @throws UnknownChannelException		when the channel does not exist.
	 * @throws UnauthorisedClientException	when the user is not the one that created {@code channel}.
	 */
	public void			modifyAuthorisedUsers(
		String channel,
		String autorisedUsers
		) throws	UnknownClientException,
					UnknownChannelException,
					UnauthorisedClientException;

	/**
	 * destroy the given channel when no more messages are currently waiting
	 * to be transmitted.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param channel						name of the channel to be destroyed.
	 * @throws UnknownClientException		when {@code receptionPortURI} does not correspond to a registered component.
	 * @throws UnknownChannelException		when the channel to be destroyed does not exist.
	 * @throws UnauthorisedClientException	when the user is not the one that created {@code channel}.
	 */
	public void			destroyChannel(String channel)
	throws	UnknownClientException,
			UnknownChannelException,
			UnauthorisedClientException;

	/**
	 * destroy the given channel immediately, even if the channel has still
	 * messages to be transmitted..
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param channel						name of the channel to be destroyed.
	 * @throws UnknownClientException		when {@code receptionPortURI} does not correspond to a registered component.
	 * @throws UnknownChannelException		when the channel to be destroyed does not exist.
	 * @throws UnauthorisedClientException	when the user is not the one that created {@code channel}.
	 */
	public void			destroyChannelNow(String channel)
	throws	UnknownClientException,
			UnknownChannelException,
			UnauthorisedClientException;
}
// -----------------------------------------------------------------------------
