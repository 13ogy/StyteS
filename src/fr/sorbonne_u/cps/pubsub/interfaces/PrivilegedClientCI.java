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

// -----------------------------------------------------------------------------
/**
 * The component interface <code>PrivilegedClientCI</code> declares the methods
 * to be offered by a publication/subscription system to its privileged clients
 * to manage the channels.
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
public interface		PrivilegedClientCI
extends		PublishingCI
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
	 * pre	{@code receptionPortURI != null && !receptionPortURI.isEmpty()}
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param receptionPortURI	URI of the inbound port offering the component interface {@code ReceivingCI}.
	 * @param channel			channel to be tested.
	 * @return					true if the component has created {@code channel}.
	 * @throws Exception		<i>to do</i>.
	 */
	public boolean		hasCreatedChannel(
		String receptionPortURI,
		String channel
		) throws Exception;
			
	/**
	 * return true if the component registered under {@code receptionPortURI}
	 * has reached its channel quota.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code receptionPortURI != null && !receptionPortURI.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param receptionPortURI	URI of the inbound port offering the component interface {@code ReceivingCI}.
	 * @return					true if the component registered under {@code receptionPortURI} has reached its channel quota.
	 * @throws Exception		<i>to do</i>.
	 */
	public boolean		channelQuotaReached(String receptionPortURI)
	throws	Exception;

	/**
	 * create a new channel of the given name with a set of users authorised to
	 * use it.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code receptionPortURI != null && !receptionPortURI.isEmpty()}
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * pre	{@code autorisedUsers == null || !autorisedUsers.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param receptionPortURI	identifier of the requesting component <i>i.e.</i>, URI of its inbound port offering the component interface {@code ReceivingCI}.
	 * @param channel			name of the channel to be created.
	 * @param autorisedUsers	regular expression that matches the URIs of components that must be authorised to use the new channel.
	 * @throws Exception		<i>to do</i>.
	 */
	public void			createChannel(
		String receptionPortURI,
		String channel,
		String autorisedUsers
		) throws Exception;

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
	 * @param channel		name of an existing channel.
	 * @param uri			URI of component to be tested.
	 * @return				true if {@code uri} is authorised to use {@code channel}.
	 * @throws Exception	<i>to do</i>.
	 */
	public boolean		isAuthorisedUser(String channel, String uri)
	throws Exception;

	/**
	 * replace the users authorised to use {@code channel}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code receptionPortURI != null && !receptionPortURI.isEmpty()}
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * pre	{@code autorisedUsers != null && !autorisedUsers.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param receptionPortURI	identifier of the requesting component <i>i.e.</i>, URI of its inbound port offering the component interface {@code ReceivingCI}.
	 * @param channel			name of the channel.
	 * @param autorisedUsers	regular expression that matches the URIs of components that must be authorised to use the new channel.
	 * @throws Exception		<i>to do</i>.
	 */
	public void			modifyAuthorisedUsers(
		String receptionPortURI,
		String channel,
		String autorisedUsers
		) throws Exception;

	/**
	 * remove {@code uri} from the users authorised to use {@code channel}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code receptionPortURI != null && !receptionPortURI.isEmpty()}
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * pre	{@code regularExpression != null && !regularExpression.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param receptionPortURI	identifier of the requesting component <i>i.e.</i>, URI of its inbound port offering the component interface {@code ReceivingCI}.
	 * @param channel			name of the channel.
	 * @param regularExpression	regular expression that matches the URIs of components that must be authorised to use the new channel.
	 * @throws Exception		<i>to do</i>.
	 */
	public void			removeAuthorisedUsers(
		String receptionPortURI,
		String channel,
		String regularExpression
		) throws Exception;

	/**
	 * destroy the given channel when no more messages are currently waiting
	 * to be transmitted.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code receptionPortURI != null && !receptionPortURI.isEmpty()}
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param receptionPortURI				URI of the inbound port offering the component interface {@code ReceivingCI}.
	 * @param channel						name of the channel to be destroyed.
	 * @throws Exception					<i>to do</i>.
	 */
	public void			destroyChannel(String receptionPortURI, String channel)
	throws Exception;

	/**
	 * destroy the given channel immediately, even if the channel has still
	 * messages to be transmitted..
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code receptionPortURI != null && !receptionPortURI.isEmpty()}
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param receptionPortURI				URI of the inbound port offering the component interface {@code ReceivingCI}.
	 * @param channel						name of the channel to be destroyed.
	 * @throws Exception					<i>to do</i>.
	 */
	public void			destroyChannelNow(
		String receptionPortURI,
		String channel
		) throws Exception;
}
// -----------------------------------------------------------------------------
