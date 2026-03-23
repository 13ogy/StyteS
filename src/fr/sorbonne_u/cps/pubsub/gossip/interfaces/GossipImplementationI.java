package fr.sorbonne_u.cps.pubsub.gossip.interfaces;

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
 * The interface <code>GossipImplementationI</code> declares the signatures of
 * the methods to be implemented by the components participating in the gossip
 * protocol.
 *
 * <p><strong>Description</strong></p>
 * 
 * <p>
 * A component participating in a gossip protocol requires the component
 * interface {@code GossipSenderCI} and its method {@code send} to propagate
 * gossip messages to its neighbours. It offers the component interface
 * {@code GossipReceiverCI} with a method {@code receive} through which it
 * receives the gossip messages coming from its neighbours.
 * </p>
 * <p>
 * To process incoming gossip messages, the component must implement a method
 * {@code receive} declared by the interface {@code GossipReceiverI}. The
 * traditional implementation of gossip protocols then use a method
 * {@code update} declared in this interface to both to integrate the incoming
 * messages in its state and to select the messages that it will propagate to
 * its own neighbours.
 * </p>
 * 
 * <p><strong>Invariants</strong></p>
 * 
 * <pre>
 * invariant	{@code true}	// no more invariant
 * </pre>
 * 
 * <p>Created on : 2026-02-12</p>
 * 
 * @author	<a href="mailto:Jacques.Malenfant@lip6.fr">Jacques Malenfant</a>
 */
public interface		GossipImplementationI
extends		GossipReceiverI
{
	// -------------------------------------------------------------------------
	// Signature and default methods
	// -------------------------------------------------------------------------

	/**
	 * compute an updated state from the state received from the sender and the
	 * state of the receiver to be returned to the sender in the gossip protocol.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code fromSender != null && fromSender.length > 0}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param fromSender	gossip messages received from the sender in the gossip protocol.
	 */
	public void			update(GossipMessageI[] fromSender);
}
// -----------------------------------------------------------------------------
