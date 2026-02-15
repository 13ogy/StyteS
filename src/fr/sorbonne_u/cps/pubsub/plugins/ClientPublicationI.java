package fr.sorbonne_u.cps.pubsub.plugins;

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

import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import java.util.ArrayList;
import fr.sorbonne_u.components.PluginI;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;

// -----------------------------------------------------------------------------
/**
 * The interface <code>ClientPublicationI</code> declares the signatures of
 * methods that a client publication plug-in must implement.
 *
 * <p><strong>Description</strong></p>
 * 
 * <p><strong>Invariants</strong></p>
 * 
 * <pre>
 * invariant	{@code true}	// no more invariant
 * </pre>
 * 
 * <p>Created on : 2026-02-04</p>
 * 
 * @author	<a href="mailto:Jacques.Malenfant@lip6.fr">Jacques Malenfant</a>
 */
public interface		ClientPublicationI
extends		PluginI
{
	// -------------------------------------------------------------------------
	// Signature and default methods
	// -------------------------------------------------------------------------

	/**
	 * return true if {@code channel} exists, otherwise false.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param channel	name of a potential channel.
	 * @return			true if {@code channel} exists, otherwise false.
	 */
	public boolean		channelExist(String channel);

	/**
	 * return true if the use of {@code channel} is authorised for the component,
	 * otherwise false.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param channel					name of a potential channel.
	 * @return							true if the use of {@code channel} is authorised for {@code receptionPortURI}, otherwise false.
	 * @throws UnknownClientException	when the component is not registered on the publication/subscription system yet.
	 * @throws UnknownChannelException	when {@code channel} does not exist.
	 */
	public boolean		channelAuthorised(String channel)
	throws	UnknownClientException,
			UnknownChannelException;

	/**
	 * publish {@code message} on {@code channel}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * pre	{@code message != null}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param channel						name of an existing channel.
	 * @param message						message to be published on {@code channel}.
	 * @throws UnknownClientException		when the component is not registered on the publication/subscription system yet.
	 * @throws UnknownChannelException		when {@code channel} does not exist.
	 * @throws UnauthorisedClientException	when the client is not authorised to publish on {@code channel}.
	 */
	public void			publish(String channel, MessageI message)
	throws	UnknownClientException,
			UnknownChannelException,
			UnauthorisedClientException;

	/**
	 * publish all of the {@code messages} on {@code channel}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * pre	{@code messages != null && messages.size() > 0}
	 * pre	{@code Stream.of(messages).allMatch(m -> m != null)}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param channel						name of the channel on which {@code message} must be published.
	 * @param messages						list of messages to be published on {@code channel}.
	 * @throws UnknownClientException		when the component is not registered on the publication/subscription system yet.
	 * @throws UnknownChannelException		when {@code channel} does not exist.
	 * @throws UnauthorisedClientException	when the client is not authorised to publish on {@code channel}.
	 */
	public void			publish(String channel, ArrayList<MessageI> messages)
	throws	UnknownClientException,
			UnknownChannelException,
			UnauthorisedClientException;
}
// -----------------------------------------------------------------------------
