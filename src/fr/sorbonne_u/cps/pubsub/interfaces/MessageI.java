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

import java.io.Serializable;
import java.time.Instant;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownPropertyException;

// -----------------------------------------------------------------------------
/**
 * The interface <code>MessageI</code> declares the signatures of methods to be
 * implemented by messages transmitted through the  publication/subscription
 * system.
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
public interface		MessageI
extends		Serializable
{
	// -------------------------------------------------------------------------
	// Inner types and classes
	// -------------------------------------------------------------------------

	/**
	 * The interface <code>PropertyI</code> declares the signatures of methods
	 * to be implemented by properties objects put on messages.
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
	public interface	PropertyI
	extends		Serializable
	{
		/**
		 * return the property name.
		 * 
		 * <p><strong>Contract</strong></p>
		 * 
		 * <pre>
		 * pre	{@code true}	// no precondition.
		 * post	{@code return != null && !return.isEmpty()}
		 * </pre>
		 *
		 * @return	the property name.
		 */
		public String		getName();

		/**
		 * return the value of the property.
		 * 
		 * <p><strong>Contract</strong></p>
		 * 
		 * <pre>
		 * pre	{@code true}	// no precondition.
		 * post	{@code true}	// no postcondition.
		 * </pre>
		 *
		 * @return	the value of the property.
		 */
		public Serializable	getValue();
	}

	// -------------------------------------------------------------------------
	// Signature and default methods
	// -------------------------------------------------------------------------

	/**
	 * return true if {@code name} is the name of an existing property on the
	 * message.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code name != null && !name.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param name	a potential property name to be tested.
	 * @return		true if {@code name} is the name of an existing property on the message.
	 */
	public boolean		propertyExists(String name);

	/**
	 * add the property on this message.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code name != null && !name.isEmpty()}
	 * pre	{@code !propertyExists(name)}
	 * post	{@code propertyExists(name)}
	 * </pre>
	 *
	 * @param name	the name of the property to be added.
	 * @param value	the value of the property to be added.
	 */
	public void			putProperty(String name, Serializable value);

	/**
	 * remove the property {@code name} from the message.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code name != null && !name.isEmpty()}
	 * post	{@code !propertyExists(name)}
	 * </pre>
	 *
	 * @param name						the name of a property to be removed.
	 * @throws UnknownPropertyException	when {@code name} does not correspond to an existing property of this message.
	 */
	public void			removeProperty(String name)
	throws	UnknownPropertyException;

	/**
	 * return the value associated with {@code name} in this message.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code name != null && !name.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param name						the name of a property which value must be retrieved.
	 * @return							the value associated with {@code name} in this message.
	 * @throws UnknownPropertyException when {@code name} does not correspond to an existing property of this message.
	 */
	public Serializable	getPropertyValue(String name)
	throws	UnknownPropertyException;

	/**
	 * return the array of properties defined by this message.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code true}	// no precondition.
	 * post	{@code return != null}
	 * </pre>
	 *
	 * @return	the array of properties defined by this message.
	 */
	public PropertyI[]	getProperties();

	/**
	 * copy the message <i>i.e.</i>, make a deep copy of the message up to the
	 * properties values and the payload that is not copied but simply
	 * referenced by the new message.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code true}	// no precondition.
	 * post	{@code return != null}
	 * </pre>
	 *
	 * @return	a copy of this message, with the same properties and the same payload.
	 */
	public MessageI		copy();

	/**
	 * set the payload of this message.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code true}	// no precondition.
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param payload	payload to be set.
	 */
	public void			setPayload(Serializable payload);

	/**
	 * return the payload of this message.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code true}	// no precondition.
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @return	the payload of this message.
	 */
	public Serializable	getPayload();

	/**
	 * return the time stamp of this message.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code true}	// no precondition.
	 * post	{@code return != null}
	 * </pre>
	 *
	 * @return	the time stamp of this message.
	 */
	public Instant		getTimeStamp();
}
// -----------------------------------------------------------------------------
