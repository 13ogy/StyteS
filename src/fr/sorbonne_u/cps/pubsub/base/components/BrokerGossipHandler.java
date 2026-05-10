package fr.sorbonne_u.cps.pubsub.base.components;

import fr.sorbonne_u.cps.pubsub.base.ports.BrokerReceptionOutboundPort;
import fr.sorbonne_u.cps.pubsub.gossip.messages.*;
import fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI;
import fr.sorbonne_u.cps.pubsub.interfaces.RegistrationCI;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Concrete visitor that applies incoming gossip messages to a {@link Broker}'s
 * local state.
 * <p>
 * Each {@code visit} method mirrors one operation that may have been performed
 * on a remote broker — registration, channel creation, publication, etc. —
 * and replicates its effect locally, ensuring consistency across the gossip
 * network.
 * </p>
 * <p>
 * This class accesses the broker's internal state directly and must therefore
 * reside in the same package as {@link Broker}.
 * </p>
 *
 * @see GossipMessageVisitor
 * @see Broker
 *
 * @author Setbel Mélissa, Bogdan Styn
 */
public class BrokerGossipHandler implements GossipMessageVisitor {

 private final Broker broker;

 public BrokerGossipHandler(Broker broker) {
 this.broker = broker;
 }

 @Override
 public void visit(RegisterGossipMessage regMsg) {
 broker.logMessage("[Broker] gossip recv register from " + regMsg.getEmitterURI() + "\n");
 // Mémoriser localement — sans créer de port outbound
 // car ce client n'est PAS enregistré chez nous, juste connu
 broker.registrationLock.writeLock().lock();
 try {
 broker.registeredClients.putIfAbsent(
 regMsg.getClientReceptionPortURI(),
 regMsg.getRegistrationClass());
 broker.createdPrivilegedChannelsCount.putIfAbsent(regMsg.getClientReceptionPortURI(), 0);
 } finally {
 broker.registrationLock.writeLock().unlock();
 }
 }

 @Override
 public void visit(PublishGossipMessage pubMsg) {
 broker.logMessage("[Broker] gossip recv publish from " + pubMsg.getEmitterURI() + "\n");

 broker.beginInFlight(pubMsg.getChannel());
 broker.runTask(broker.esPropagationIndex, o -> {
 try {
 ((Broker) o).propagationStage(
 pubMsg.getChannel(),
 pubMsg.getPubMessage());
 } catch (Exception e) {
 broker.logMessage("[Broker] gossip propagation failed: " + e + "\n");
 ((Broker) o).finishInFlight(pubMsg.getChannel());
 }
 });
 }

 @Override
 public void visit(CreateChannelGossipMessage ccMsg) {
 broker.logMessage("[Broker] gossip recv channel creation from " + ccMsg.getEmitterURI() + "\n");

 Pattern p = null;
 if (ccMsg.getAuthorisedUsers() != null && !ccMsg.getAuthorisedUsers().isEmpty()) {
 p = Pattern.compile(ccMsg.getAuthorisedUsers());
 }
 final Pattern pattern = p;

 broker.registrationLock.writeLock().lock();
 try {
 // S'assurer que le propriétaire est connu localement
 broker.registeredClients.putIfAbsent(
 ccMsg.getOwnerReceptionPortURI(),
 ccMsg.getOwnerClass());
 broker.createdPrivilegedChannelsCount.merge(
 ccMsg.getOwnerReceptionPortURI(), 1, Integer::sum);

 broker.channelsLock.writeLock().lock();
 try {
 // Ne créer que si pas déjà présent
 if (!broker.channelExistLocked(ccMsg.getChannel())) {
 broker.subscriptionsLock.writeLock().lock();
 try {
 // Tout mettre à jour
 broker.channels.add(ccMsg.getChannel());
 broker.privilegedChannels.put(ccMsg.getChannel(),
 new Broker.PrivilegedChannelInfo(
 ccMsg.getOwnerReceptionPortURI(), pattern));
 broker.subscriptions.put(ccMsg.getChannel(), new HashMap<>());
 synchronized (broker.inFlightPerChannel) {
 broker.inFlightPerChannel.put(ccMsg.getChannel(), 0);
 }
 } finally {
 broker.subscriptionsLock.writeLock().unlock();
 }
 } else {
 broker.createdPrivilegedChannelsCount.merge(
 ccMsg.getOwnerReceptionPortURI(), -1, Integer::sum);
 }
 } finally {
 broker.channelsLock.writeLock().unlock();
 }
 } finally {
 broker.registrationLock.writeLock().unlock();
 }
 }

 @Override
 public void visit(DestroyChannelGossipMessage dcMsg) {
 broker.logMessage("[Broker] gossip recv destroy channel from " + dcMsg.getEmitterURI() + "\n");

 // Détruire la copie locale directement
 broker.registrationLock.writeLock().lock();
 try {
 broker.channelsLock.writeLock().lock();
 try {
 // Ne détruire que si le canal existe localement
 if (broker.channelExistLocked(dcMsg.getChannel())) {
 broker.subscriptionsLock.writeLock().lock();
 try {
 broker.subscriptions.remove(dcMsg.getChannel());
 } finally {
 broker.subscriptionsLock.writeLock().unlock();
 }
 broker.channels.remove(dcMsg.getChannel());
 broker.privilegedChannels.remove(dcMsg.getChannel());
 broker.inFlightPerChannel.remove(dcMsg.getChannel());
 // Décrémenter le quota du propriétaire
 broker.createdPrivilegedChannelsCount.merge(
 dcMsg.getOwnerReceptionPortURI(), -1, Integer::sum);
 }
 } finally {
 broker.channelsLock.writeLock().unlock();
 }
 } finally {
 broker.registrationLock.writeLock().unlock();
 }
 }

 @Override
 public void visit(ModifyServiceClassGossipMessage mscMsg) {
 broker.logMessage("[Broker] gossip recv modify service class from " + mscMsg.getEmitterURI() + "\n");

 broker.registrationLock.writeLock().lock();
 try {
 //Mettre à jour la classe de service localement
 broker.registeredClients.put(
 mscMsg.getClientReceptionPortURI(),
 mscMsg.getNewRegistrationClass());

 // Si downgrade vers FREE — supprimer le quota
 if (mscMsg.getNewRegistrationClass() == RegistrationCI.RegistrationClass.FREE) {
 broker.createdPrivilegedChannelsCount.remove(
 mscMsg.getClientReceptionPortURI());
 } else {
 // Si upgrade — initialiser le quota si pas encore présent
 broker.createdPrivilegedChannelsCount.putIfAbsent(
 mscMsg.getClientReceptionPortURI(), 0);
 }
 } finally {
 broker.registrationLock.writeLock().unlock();
 }
 }

 @Override
 public void visit(ModifyAuthorisedUsersGossipMessage mauMsg) {
 broker.logMessage("[Broker] gossip recv modify authorised users from " + mauMsg.getEmitterURI() + "\n");

 Pattern newPattern = Pattern.compile(mauMsg.getAuthorisedUsers());
 broker.channelsLock.writeLock().lock();
 try {
 Broker.PrivilegedChannelInfo info =
 broker.privilegedChannels.get(mauMsg.getChannel());
 if (info != null) {
 info.authorisedUsersPattern = newPattern;
 }
 } finally {
 broker.channelsLock.writeLock().unlock();
 }
 }

 @Override
 public void visit(UnregisterGossipMessage unregMsg) {
 broker.logMessage("[Broker] gossip recv unregister from " + unregMsg.getEmitterURI() + "\n");

 // retirer subscriptions
 broker.subscriptionsLock.writeLock().lock();
 try {
 for (Map<String, MessageFilterI> subs : broker.subscriptions.values()) {
 subs.remove(unregMsg.getClientReceptionPortURI());
 }
 } finally {
 broker.subscriptionsLock.writeLock().unlock();
 }

 // maintenant retirer le client
 BrokerReceptionOutboundPort out;
 broker.registrationLock.writeLock().lock();
 try {
 broker.registeredClients.remove(unregMsg.getClientReceptionPortURI());
 broker.createdPrivilegedChannelsCount.remove(unregMsg.getClientReceptionPortURI());
 } finally {
 broker.registrationLock.writeLock().unlock();
 }

 }
}
