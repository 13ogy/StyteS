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
import fr.sorbonne_u.cps.pubsub.interfaces.MessageI.PropertyI;

// -----------------------------------------------------------------------------
/**
 * The interface <code>MessageFilterI</code> declares the signatures of methods
 * to be implemented by message filters in the publication/subscription system.
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
public interface		MessageFilterI
extends		Serializable
{
	// -------------------------------------------------------------------------
	// Inner types and classes
	// -------------------------------------------------------------------------

	/**
	 * The interface <code>ValueFilterI</code> declares the signatures of
	 * methods to be implemented by value filters <i>i.e.</i>, filters for
	 * values of properties put on messages.
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
	public interface	ValueFilterI
	extends		Serializable
	{
		/**
		 * return true if {@code value} matches the filter, otherwise false.
		 * 
		 * <p><strong>Contract</strong></p>
		 * 
		 * <pre>
		 * pre	{@code true}	// no precondition.
		 * post	{@code true}	// no postcondition.
		 * </pre>
		 *
		 * @param 	value	value to be tested to match against this value filter.
		 * @return	true if {@code value} matches the filter, otherwise false.
		 */
		public boolean	match(Serializable value);
	}

	/**
	 * The interface <code>PropertyFilterI</code> declares the signatures of
	 * methods to be implemented by property filters <i>i.e.</i>, filters for
	 * properties (name/value pairs) put on messages.
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
	public interface	PropertyFilterI
	extends		Serializable
	{
		/**
		 * return the property name that this property filter accepts.
		 * 
		 * <p><strong>Contract</strong></p>
		 * 
		 * <pre>
		 * pre	{@code true}	// no precondition.
		 * post	{@code return != null && !return.isEmpty()}
		 * </pre>
		 *
		 * @return	the property name that this property filter accepts.
		 */
		public String		getName();

		/**
		 * return the value filter that this property filter imposes on the
		 * filtered properties.
		 * 
		 * <p><strong>Contract</strong></p>
		 * 
		 * <pre>
		 * pre	{@code true}	// no precondition.
		 * post	{@code return != null}
		 * </pre>
		 *
		 * @return	the value filter that this property filter imposes on the filtered properties.
		 */
		public ValueFilterI	getValueFilter();

		/**
		 * return true if {@code property} is accepted by this property filter,
		 * otherwise false.
		 * 
		 * <p><strong>Contract</strong></p>
		 * 
		 * <pre>
		 * pre	{@code true}	// no precondition.
		 * post	{@code true}	// no postcondition.
		 * </pre>
		 *
		 * @param property	a property of a message to be tested.
		 * @return			true if {@code property} is accepted by this property filter, otherwise false.
		 */
		public boolean		match(PropertyI property);
	}

	/**
	 * The interface <code>MultiValuesFilterI</code> declares the signatures of
	 * methods to be implemented by filters cross-constraining several
	 * properties together <i>i.e.</i>, filters relating several property values.
	 *
	 * <p><strong>Description</strong></p>
	 * 
	 * <p><strong>Invariants</strong></p>
	 * 
	 * <pre>
	 * invariant	{@code true}	// TODO	// no more invariant
	 * </pre>
	 * 
	 * <p>Created on : 2026-01-23</p>
	 * 
	 * @author	<a href="mailto:Jacques.Malenfant@lip6.fr">Jacques Malenfant</a>
	 */
	public interface	MultiValuesFilterI
	extends		Serializable
	{
		/**
		 * return the array of property names which values are the target of
		 * the filter.
		 * 
		 * <p><strong>Contract</strong></p>
		 * 
		 * <pre>
		 * pre	{@code true}	// no precondition.
		 * post	{@code true}	// no postcondition.
		 * </pre>
		 *
		 * @return	the array of property names which values are the target of the filter.
		 */
		public String[]	getNames();

		/**
		 * return true if {@code values} are accepted by this filter, otherwise
		 * false.
		 * 
		 * <p><strong>Contract</strong></p>
		 * 
		 * <pre>
		 * pre	{@code values != null && values.length > 1}
		 * post	{@code true}	// no postcondition.
		 * </pre>
		 *
		 * @param values	values constrained by the filter.
		 * @return			true if {@code values} are accepted by this filter, otherwise false.
		 */
		public boolean	match(Serializable... values);
	}

	/**
	 * The interface <code>PropertiesFilterI</code> declares the signatures of
	 * methods to be implemented by properties filters <i>i.e.</i>, filters
	 * cross-constraining several properties (name/value pairs) put on messages
	 * at once.
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
	public interface	PropertiesFilterI
	extends		Serializable
	{
		/**
		 * return the multi-values filter associated to this properties filter.
		 * 
		 * <p><strong>Contract</strong></p>
		 * 
		 * <pre>
		 * pre	{@code true}	// no precondition.
		 * post	{@code true}	// no postcondition.
		 * </pre>
		 *
		 * @return	the multi-values filter associated to this properties filter.
		 */
		public MultiValuesFilterI	getMultiValuesFilter();

		/**
		 * return true if {@code properties} are accepted by this filter,
		 * otherwise false.
		 * 
		 * <p><strong>Contract</strong></p>
		 * 
		 * <pre>
		 * pre	{@code properties != null && properties.length > 1}
		 * pre	{@code Stream.of(getMultiValuesFilter().getNames()).allMatch(n -> Stream.of(properties).filter(p -> p.getName().equals(n)).findAny().isPresent())}
		 * post	{@code true}	// no postcondition.
		 * </pre>
		 *
		 * @param properties	properties of a message to be filtered.
		 * @return				true if {@code properties} are accepted by this filter, otherwise false.
		 */
		public boolean				match(PropertyI... properties);
	}

	/**
	 * The interface <code>TimeFilterI</code> declares the signatures of methods
	 * to be implemented by time filters <i>i.e.</i>, filters for the time stamp
	 * put on messages.
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
	public interface	TimeFilterI
	extends		Serializable
	{
		/**
		 * return true if {@code timestamp} is accepted by this time filter,
		 * otherwise false.
		 * 
		 * <p><strong>Contract</strong></p>
		 * 
		 * <pre>
		 * pre	{@code timestamp != null}
		 * post	{@code true}	// no postcondition.
		 * </pre>
		 *
		 * @param 	timestamp	the time stamp put on a message.
		 * @return	true if {@code timestamp} is accepted by this time filter, otherwise false.
		 */
		public boolean	match(Instant timestamp);
	}

	// -------------------------------------------------------------------------
	// Signature and default methods
	// -------------------------------------------------------------------------

	/**
	 * return the array of property filters that this filter imposes on messages
	 * to be accepted.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code true}	// no precondition.
	 * post	{@code return != null}
	 * </pre>
	 *
	 * @return	the array of property filters that this filter imposes on messages to be accepted.
	 */
	public PropertyFilterI[]	getPropertyFilters();

	/**
	 * return the array of properties filters that this filter imposes on
	 * messages to be accepted.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code true}	// no precondition.
	 * post	{@code return != null}
	 * </pre>
	 *
	 * @return	the array of properties filters that this filter imposes on messages to be accepted.
	 */
	public PropertiesFilterI[]	getPropertiesFilters();

	/**
	 * return the time filter that this filter imposes on messages to be
	 * accepted.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code true}	// no precondition.
	 * post	{@code return != null}
	 * </pre>
	 *
	 * @return	the time filter that this filter imposes on messages to be accepted.
	 */
	public TimeFilterI			getTimeFilter();

	/**
	 * return true if {@code message} is accepted by this filter, otherwise
	 * false.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code true}	// no precondition.
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param message	a message to be filtered.
	 * @return			true if {@code message} is accepted by this filter, otherwise false.
	 */
	public boolean				match(MessageI message);
}
// -----------------------------------------------------------------------------
