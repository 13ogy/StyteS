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

import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI.RegistrationClass;
import fr.sorbonne_u.components.PluginI;
import fr.sorbonne_u.cps.pubsub.exceptions.AlreadyRegisteredException;
import fr.sorbonne_u.cps.pubsub.exceptions.UnknownClientException;

// -----------------------------------------------------------------------------
/**
 * The class <code>ClientRegistrationI</code>
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
public interface		ClientRegistrationI
extends		PluginI
{
	// -------------------------------------------------------------------------
	// Signature and default methods
	// -------------------------------------------------------------------------

	/**
	 * return true if the component has already been registered, otherwise false.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code true}	// no precondition.
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @return	true if the component has already been registered, otherwise false.
	 */
	public boolean		registered();

	/**
	 * return true if the component has already been registered with service
	 * class {@code rc}, otherwise false.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code rc != null}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param rc						the registration class.
	 * @return							true if {@code receptionPortURI} has already been registered with service class {@code rc}, otherwise false.
	 * @throws UnknownClientException	when the component is not registered yet.
	 */
	public boolean		registered(RegistrationClass rc)
	throws	UnknownClientException;

	/**
	 * register on the publication/subscription system with service
	 * class {@code rc}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code rc != null}
	 * post	{@code registered(rc)}
	 * </pre>
	 *
	 * @param rc							the required registration class.
	 * @throws AlreadyRegisteredException	when the component is already registered.
	 */
	public void			register(RegistrationClass rc)
	throws AlreadyRegisteredException;

	/**
	 * upgrade or degrade the registration to the class {@code rc}, returning
	 * a new URI of an inbound port offering the component interface
	 * {@code PublishingCI}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code rc != null}
	 * post	{@code registered(rc)}
	 * </pre>
	 *
	 * @param rc							the required registration class.
	 * @throws UnknownClientException		when the component is not registered yet.
	 * @throws AlreadyRegisteredException	when the component is already registered with the service class {@code rc}.
	 */
	public void			modifyServiceClass(RegistrationClass rc)
	throws	UnknownClientException,
			AlreadyRegisteredException;

	/**
	 * unregister from the publication/subscription system.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code true}	// no precondition.
	 * post	{@code !registered()}
	 * </pre>
	 *
	 * @throws UnknownClientException	when the component is not registered yet.
	 */
	public void			unregister() throws UnknownClientException;
}
// -----------------------------------------------------------------------------
