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

import fr.sorbonne_u.components.interfaces.OfferedCI;
import fr.sorbonne_u.components.interfaces.RequiredCI;

// -----------------------------------------------------------------------------
/**
 * The interface <code>RegistrationCI</code> is offered to users to register on
 * the publication/subscription system and manage their subscriptions to
 * channels.
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
public interface		RegistrationCI
extends		OfferedCI,
			RequiredCI
{
	// -------------------------------------------------------------------------
	// Inner types and classes
	// -------------------------------------------------------------------------

	/**
	 * The interface <code>RegistrationClassI</code> must be implemented by all
	 * registration class enumerations.
	 *
	 * <p><strong>Description</strong></p>
	 * 
	 * <p><strong>Invariants</strong></p>
	 * 
	 * <pre>
	 * invariant	{@code true}	// no more invariant
	 * </pre>
	 * 
	 * <p>Created on : 2026-01-23</p>
	 * 
	 * @author	<a href="mailto:Jacques.Malenfant@lip6.fr">Jacques Malenfant</a>
	 */
	public static interface	RegistrationClassI
	{
		
	}

	/**
	 * The enumeration <code>RegistrationClass</code> defines the basic
	 * registration classes for the publish/subscribe system.
	 *
	 * <p><strong>Description</strong></p>
	 * 
	 * <p><strong>Implementation Invariants</strong></p>
	 * 
	 * <pre>
	 * invariant	{@code true}	// no more invariant
	 * </pre>
	 * 
	 * <p><strong>Invariants</strong></p>
	 * 
	 * <pre>
	 * invariant	{@code true}	// no more invariant
	 * </pre>
	 * 
	 * <p>Created on : 2026-01-23</p>
	 * 
	 * @author	<a href="mailto:Jacques.Malenfant@lip6.fr">Jacques Malenfant</a>
	 */
	public static enum RegistrationClass
	implements RegistrationClassI
	{
		/** free access to the system.										*/
		FREE,
		/** standard registration with a low fee.							*/
		STANDARD,
		/** high performance access registration with a higher fee.			*/
		PREMIUM
	}

	// -------------------------------------------------------------------------
	// Signature and default methods
	// -------------------------------------------------------------------------

	// Registration management

	/**
	 * return true if {@code receptionPortURI} has already been registered,
	 * otherwise false.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code receptionPortURI != null && !receptionPortURI.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param receptionPortURI	URI of the inbound port offering the component interface {@code ReceivingCI}.
	 * @return					true if {@code receptionPortURI} has already been registered, otherwise false.
	 * @throws Exception		<i>to do</i>.
	 */
	public boolean		registered(String receptionPortURI)
	throws Exception;

	/**
	 * return true if {@code receptionPortURI} has already been registered
	 * with service class {@code rc}, otherwise false.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code receptionPortURI != null && !receptionPortURI.isEmpty()}
	 * pre	{@code rc != null}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param receptionPortURI	URI of the inbound port offering the component interface {@code ReceivingCI}.
	 * @param rc				the registration class.
	 * @return					true if {@code receptionPortURI} has already been registered with service class {@code rc}, otherwise false.
	 * @throws Exception		<i>to do</i>.
	 */
	public boolean		registered(String receptionPortURI, RegistrationClass rc)
	throws Exception;

	/**
	 * register on the publication/subscription system passing the URI of the
	 * inbound port offering the component interface {@code ReceivingCI} on
	 * which the user component will receive its messages; the method returns
	 * an URI of an inbound port offering the component interface
	 * {@code PublishingCI} on which the user component will publish its
	 * messages.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code receptionPortURI != null && !receptionPortURI.isEmpty()}
	 * pre	{@code rc != null}
	 * post	{@code registered(receptionPortURI)}
	 * post	{@code registered(receptionPortURI, rc)}
	 * </pre>
	 *
	 * @param receptionPortURI	URI of the inbound port offering the component interface {@code ReceivingCI}.
	 * @param rc				the required registration class.
	 * @return					an URI of an inbound port offering the component interface {@code PublishingCI}.
	 * @throws Exception		<i>to do</i>.
	 */
	public String		register(String receptionPortURI, RegistrationClass rc)
	throws Exception;

	/**
	 * upgrade or degrade the registration to the class {@code rc}, returning
	 * a new URI of an inbound port offering the component interface
	 * {@code PublishingCI}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code receptionPortURI != null && !receptionPortURI.isEmpty()}
	 * pre	{@code rc != null}
	 * post	{@code registered(receptionPortURI, rc)}
	 * </pre>
	 *
	 * @param receptionPortURI	URI of the inbound port offering the component interface {@code ReceivingCI}.
	 * @param rc				the required registration class.
	 * @return					an URI of an inbound port offering the component interface {@code PublishingCI}.
	 * @throws Exception		<i>to do</i>.
	 */
	public String		modifyServiceClass(
		String receptionPortURI,
		RegistrationClass rc
		) throws Exception;

	/**
	 * unregister from the publication/subscription system.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code receptionPortURI != null && !receptionPortURI.isEmpty()}
	 * post	{@code !registered(receptionPortURI)}
	 * </pre>
	 *
	 * @param receptionPortURI	URI of the inbound port offering the component interface {@code ReceivingCI}.
	 * @throws Exception		<i>to do</i>.
	 */
	public void			unregister(String receptionPortURI)
	throws Exception;

	// Channel subscription management

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
	 * @param channel		name of a potential channel.
	 * @return				true if {@code channel} exists, otherwise false.
	 * @throws Exception	<i>to do</i>.
	 */
	public boolean		channelExist(String channel) throws Exception;

	/**
	 * return true if the use of {@code channel} is authorised for
	 * {@code receptionPortURI}, otherwise false.
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
	 * @param channel			name of a potential channel.
	 * @return					true if the use of {@code channel} is authorised for {@code receptionPortURI}, otherwise false.
	 * @throws Exception		<i>to do</i>.
	 */
	public boolean		channelAuthorised(
		String receptionPortURI,
		String channel
		) throws Exception;
	
	/**
	 * return true if {@code receptionPortURI} has subscribed to {@code channel},
	 * otherwise false.
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
	 * @param channel			name of an existing and authorised channel.
	 * @return					true if {@code receptionPortURI} has subscribed to {@code channel}, otherwise false.
	 * @throws Exception		<i>to do</i>.
	 */
	public boolean		subscribed(String receptionPortURI, String channel)
	throws Exception;

	/**
	 * subscribe to {@code channel} with filter {@code filter}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code receptionPortURI != null && !receptionPortURI.isEmpty()}
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * pre	{@code filter != null}
	 * post	{@code subscribed(receptionPortURI, channel)}
	 * </pre>
	 *
	 * @param receptionPortURI	URI of the inbound port offering the component interface {@code ReceivingCI}.
	 * @param channel			name of an existing channel.
	 * @param filter			filter that accepts the message the component wants to receive.
	 * @throws Exception		<i>to do</i>.
	 */
	public void			subscribe(
		String receptionPortURI,
		String channel,
		MessageFilterI filter
		) throws Exception;

	/**
	 * unsubscribe from {@code channel}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code receptionPortURI != null && !receptionPortURI.isEmpty()}
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * post	{@code !subscribed(receptionPortURI, channel)}
	 * </pre>
	 *
	 * @param receptionPortURI	URI of the inbound port offering the component interface {@code ReceivingCI}.
	 * @param channel			name of an existing channel.
	 * @throws Exception		<i>to do</i>.
	 */
	public void			unsubscribe(
		String receptionPortURI,
		String channel
		) throws Exception;

	/**
	 * modify the filter placed on {@code channel} to {@code filter}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code receptionPortURI != null && !receptionPortURI.isEmpty()}
	 * pre	{@code channel != null && !channel.isEmpty()}
	 * pre	{@code filter != null}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param receptionPortURI	URI of the inbound port offering the component interface {@code ReceivingCI}.
	 * @param channel			name of an existing channel.
	 * @param filter			filter that accepts the message the component wants to receive.
	 * @return					true if the modification has been performed, otherwise false.
	 * @throws Exception		<i>to do</i>.
	 */
	public boolean		modifyFilter(
		String receptionPortURI,
		String channel,
		MessageFilterI filter
		) throws Exception;
}
// -----------------------------------------------------------------------------
