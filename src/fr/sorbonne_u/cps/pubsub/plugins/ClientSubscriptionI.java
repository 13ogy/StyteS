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

import java.time.Duration;
import java.util.concurrent.Future;
import fr.sorbonne_u.components.PluginI;
import fr.sorbonne_u.cps.pubsub.exceptions.NotSubscribedChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnauthorisedClientException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownChannelException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI;

// -----------------------------------------------------------------------------
/**
 * The interface <code>ClientSubscriptionI</code> declares the signatures of
 * methods that a client subscription plug-in must implement.
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
public interface		ClientSubscriptionI
extends		PluginI
{
	// -------------------------------------------------------------------------
	// Signature and default methods
	// -------------------------------------------------------------------------

	// Signatures that are called by the owner component

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
	 * @param channel					name of an existing channel.
	 * @return							true if the use of {@code channel} is authorised for the component, otherwise false.
	 * @throws UnknownClientException	when the component is not registered on the publication/subscription system yet.
	 * @throws UnknownChannelException	when {@code channel} does not exist.
	 */
	public boolean		channelAuthorised(String channel)
	throws	UnknownClientException,
			UnknownChannelException;

	/**
	 * return true if this component already subscribed to {@code channel}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param channel					name of an existing channel.
	 * @return							true if this component already subscribed to {@code channel}.
	 * @throws UnknownClientException	when the component is not registered on the publication/subscription system yet.
	 * @throws UnknownChannelException	when {@code channel} does not exist.
	 */
	public boolean		subscribed(String channel)
	throws	UnknownClientException,
			UnknownChannelException;

	/**
	 * subscribe to {@code channel} with {@code filter}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * pre	{@code filter != null}
	 * post	{@code subscribed(channel)}
	 * </pre>
	 *
	 * @param channel						name of an existing channel.
	 * @param filter						filter to be applied to messages to decide if this component must receive them.
	 * @throws UnknownClientException		when the component is not registered on the publication/subscription system yet.
	 * @throws UnknownChannelException		when {@code channel} does not exist.
	 * @throws UnauthorisedClientException	when the client is not authorised to subscribe to {@code channel}.
	 */
	public void			subscribe(String channel, MessageFilterI filter)
	throws	UnknownClientException,
			UnknownChannelException,
			UnauthorisedClientException;

	/**
	 * unsubscribe to {@code channel}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * post	{@code !subscribed(channel)}
	 * </pre>
	 *
	 * @param channel						name of an existing channel.
	 * @throws UnknownClientException		when the component is not registered on the publication/subscription system yet.
	 * @throws UnknownChannelException		when {@code channel} does not exist.
	 * @throws UnauthorisedClientException	when the client is not authorised to use {@code channel}.
	 * @throws NotSubscribedChannelException	when the client has not subscribed to {@code channel}.
	 */
	public void			unsubscribe(String channel)
	throws	UnknownClientException,
			UnknownChannelException,
			UnauthorisedClientException,
			NotSubscribedChannelException;

	/**
	 * modify the filter associated to the subscription of this component to
	 * {@code channel}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param channel							name of an existing channel.
	 * @param filter							filter to be applied to messages to decide if this component must receive them.
	 * @throws UnknownClientException			when the component is not registered on the publication/subscription system yet.
	 * @throws UnknownChannelException			when {@code channel} does not exist.
	 * @throws UnauthorisedClientException		when the client is not authorised to use {@code channel}.
	 * @throws NotSubscribedChannelException	when the client has not subscribed to {@code channel}.
	 */
	public void			modifyFilter(
		String channel,
		MessageFilterI filter
		) throws	UnknownClientException,
					UnknownChannelException,
					UnauthorisedClientException,
					NotSubscribedChannelException;

	// Signatures that are called by the broker

	/**
	 * receive passively {@code message} from {@code channel}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param channel	name of an existing channel.
	 * @param message	message to be received from {@code channel}.
	 */
	public void			receive(String channel, MessageI message);

	/**
	 * receive {@code messages} from {@code channel}.
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
	 * @param channel		name of the channel from which {@code messsage} is received.
	 * @param messages		array of messages received by the component.
	 */
	public void			receive(String channel, MessageI[] messages);

	// Signatures that are called by the owner component and that offer it
	// more publish/subscribe operations but implemented locally

	/**
	 * wait for the next message received from {@code channel}, the caller
	 * thread being blocked until such a message is delivered from
	 * {@code channel}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * post	{@code return != null}
	 * </pre>
	 *
	 * @param channel							name of an existing channel.
	 * @return									the next message received from {@code channel}.
	 * @throws UnknownClientException			when the component is not registered on the publication/subscription system yet.
	 * @throws UnknownChannelException			when {@code channel} does not exist.
	 * @throws UnauthorisedClientException		when the client is not authorised to use {@code channel}.
	 * @throws NotSubscribedChannelException	when the client has not subscribed to {@code channel}.
	 */
	public MessageI		waitForNextMessage(String channel)
	throws	UnknownClientException,
			UnknownChannelException,
			UnauthorisedClientException,
			NotSubscribedChannelException;

	/**
	 * wait for the next message received from {@code channel}, the caller
	 * thread being blocked for a maximum duration {@code d}, returning
	 * {@code null} if no message has been received during the next {@code d}
	 * period of time.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * pre	{@code d != null}
	 * pre	{@code !d.isZero()}
	 * pre	{@code !d.isNegative()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param channel							name of an existing channel.
	 * @param d									maximum duration of the wait.
	 * @return									the next message received from {@code channel} or {@code null} if none.
	 * @throws UnknownClientException			when the component is not registered on the publication/subscription system yet.
	 * @throws UnknownChannelException			when {@code channel} does not exist.
	 * @throws UnauthorisedClientException		when the client is not authorised to use {@code channel}.
	 * @throws NotSubscribedChannelException	when the client has not subscribed to {@code channel}.
	 */
	public MessageI		waitForNextMessage(String channel, Duration d)
	throws	UnknownClientException,
			UnknownChannelException,
			UnauthorisedClientException,
			NotSubscribedChannelException;

	/**
	 * get a future on the next message received from {@code channel}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * post	{@code return != null}
	 * </pre>
	 *
	 * @param channel							name of an existing channel.
	 * @return									the next message received from {@code channel}.
	 * @throws UnknownClientException			when the component is not registered on the publication/subscription system yet.
	 * @throws UnknownChannelException			when {@code channel} does not exist.
	 * @throws UnauthorisedClientException		when the client is not authorised to use {@code channel}.
	 * @throws NotSubscribedChannelException	when the client has not subscribed to {@code channel}.
	 */
	public Future<MessageI>	getNextMessage(String channel)
	throws	UnknownClientException,
			UnknownChannelException,
			UnauthorisedClientException,
			NotSubscribedChannelException;
}
// -----------------------------------------------------------------------------
