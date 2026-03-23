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

import java.io.Serializable;
import java.time.Instant;

// -----------------------------------------------------------------------------
/**
 * The interface <code>GossipMessageI</code> declares the signatures of
 * methods that must be implemented by messages exchanged among the participants
 * in the gossip protocol.
 *
 * <p><strong>Description</strong></p>
 * 
 * <p>
 * In a gossip protocol, participants exchange gossip messages to propagate
 * information through an overlay network. Such messages have an emitter, which
 * URI is stored in the message, a message URI to distinguish messages from each
 * others and a time stamp. Knowing the emitter, a receiver can avoid to send
 * it back a message just received from it. The message URI allows participants
 * to recognise messages that they have already processed and resent if
 * necessary, hence avoiding to flood the overlay network with messages that
 * have already circulated through it and its neighbours. Finally, the time
 * stamp is useful to manage the memory of already processed messages that can
 * be cleaned up of old messages information that cannot show up again despite
 * the unpredictable nature of the gossip message propagation algorithm.
 * </p>
 * <p>
 * The emitter URI is not meant to keep track of the initial participant that
 * introduced the message in the overlay network but rather to give a one-step
 * capability not to come back from a receiver to the immediate resender.
 * Therefore, whenever a message is resent by a participant, this participant
 * puts its own URI in the message before sending it to its neighbour. The
 * method {@code copyWithNewEmitterURI} is meant to help this process by
 * creating a shallow copy of the message changing only the URI of the emitter.
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
public interface		GossipMessageI
extends		Serializable
{
	// -------------------------------------------------------------------------
	// Signature and default methods
	// -------------------------------------------------------------------------

	/**
	 * return the URI of the gossip protocol message.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code true}	// no precondition.
	 * post	{@code return != null && !return.isEmpty()}
	 * </pre>
	 *
	 * @return	the URI of the gossip protocol message.
	 */
	public String		gossipMessageURI();

	/**
	 * return the time stamp marking the creation of the message.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code true}	// no precondition.
	 * post	{@code return != null}
	 * </pre>
	 *
	 * @return	the time stamp marking the creation of the message.
	 */
	public Instant		timestamp();

	/**
	 * shallow copy this gossip message with the new emitter URI
	 * {@code newGossipEmitterURI}.
	 * 
	 * <p><strong>Contract</strong></p>
	 * 
	 * <pre>
	 * pre	{@code newEmitterURI != null && !newEmitterURI.isEmpty()}
	 * post	{@code true}	// no postcondition.
	 * </pre>
	 *
	 * @param newGossipEmitterURI	new emitter URI for this gossip message.
	 * @return						a shallow copy of this message with a new emitter URI.
	 */
	public GossipMessageI	copyWithNewEmitterURI(String newGossipEmitterURI);
}
// -----------------------------------------------------------------------------
