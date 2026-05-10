# PIPELINE — pilier 2 / 3

> Référence interne — Bogdan Styn, Setbel Mélissa.
> Code cité : `src/fr/sorbonne_u/cps/pubsub/base/components/Broker.java`,
> `src/fr/sorbonne_u/cps/pubsub/base/ports/*InboundPort.java`,
> `src/fr/sorbonne_u/cps/pubsub/gossip/ports/GossipReceiverInboundPort.java`.

---

## 1. Ce qu'exige le CDC §4.2

Le cahier des charges impose une livraison **asynchrone** : la méthode
`publish` côté client ne doit pas attendre que tous les abonnés aient
reçu, ni même que le broker ait fini d'évaluer les filtres. Côté broker,
les phases « réception », « traitement / propagation » et « livraison »
doivent être **découplées** par des threads distincts, pour qu'une
livraison lente vers un abonné ne bloque pas les nouveaux publish, et
qu'une rafale de publish n'empêche pas les autres opérations
(`subscribe`, `register`, `destroyChannel`) de progresser.

Notre implémentation traduit ce découplage par un *pipeline* : 3
executor services internes au broker (+ 1 pour le gossip), enchaînés via
`runTask(esIndex, lambda)`. Chaque étape consomme la précédente et
produit pour la suivante.

---

## 2. Les 4 executor services du broker

Déclarés dans `Broker.java:79-88` et créés dans `init(...)`
(`Broker.java:339-342`).

| URI constante | Field index | Argument constructeur | Rôle |
|---|---|---|---|
| `ES_RECEPTION_URI = "broker-reception-es"` | `esReceptionIndex` | `nbReceptionThreads` | Désérialise toutes les requêtes entrantes (publish, register, subscribe, ...) hors de la thread RMI. |
| `ES_PROPAGATION_URI = "broker-propagation-es"` | `esPropagationIndex` | `nbPropagationThreads` | Construit le snapshot d'abonnés et soumet une tâche de delivery par cible. |
| `ES_DELIVERY_URI = "broker-delivery-es"` | `esDeliveryIndex` | `nbDeliveryThreads` | Évalue le filtre, appelle `out.receive(channel, message)` pour un seul abonné. |
| `ES_GOSSIP_URI = "broker-gossip-es"` | `esGossipIndex` | hard-codé à 4 | Reçoit les `update()` gossip et envoie les `send()` aux voisins. |

Création (`Broker.java:339-342`) :

```java
this.esReceptionIndex   = this.createNewExecutorService(ES_RECEPTION_URI,   nbReceptionThreads,   false);
this.esPropagationIndex = this.createNewExecutorService(ES_PROPAGATION_URI, nbPropagationThreads, false);
this.esDeliveryIndex    = this.createNewExecutorService(ES_DELIVERY_URI,    nbDeliveryThreads,    false);
this.esGossipIndex      = this.createNewExecutorService(ES_GOSSIP_URI,      4,                    false);
```

Les démos centralisées (`DemoMidTermDemoV2`, `DemoMidSemComplexTimedScenario`,
`DemoMeteoTimedTestTool`) construisent le broker avec
`new Object[] { BROKER_URI, 2, 1, 3, 2, 5, 2, 4, 8 }` :
2 reception threads, 4 propagation, 8 delivery (`DemoMidTermDemoV2.java:50`).

Indices exposés en lecture (`Broker.java:401-410`) :
`getReceptionExecutorIndex()`, `getPropagationExecutorIndex()`,
`getDeliveryExecutorIndex()`, `getGossipExecutorIndex()`. Les inbound
ports les utilisent pour ne jamais exécuter de logique métier sur la
thread RMI.

---

## 3. Cycle de vie d'un `publish` (1 message, N abonnés)

```
RMI thread (BCM4Java pool)                 # PublishingInboundPort:67-77
  |  publish(rip, "channel0", msg)
  |      -- call brokered through proxy
  |      -- enters PublishingInboundPort.publish()
  |      -- broker.runTask(esReceptionIndex, ...)
  |  return immediately to caller          # CDC §3.5: fire-and-forget
  v

ES_RECEPTION_URI (esReceptionIndex)        # Broker.java:432-439
  | submitPublish() lambda picks up the call
  |  -> receptionStage(rip, "channel0", msg, notifURI)
  |       beginInFlight(channel)           # Broker.java:454, lock-free CHM.compute
  |       ----- registrationLock.read      # Broker.java:456
  |       ----- channelsLock.read          # Broker.java:462
  |             requireClient OK
  |             channel exists OK
  |             channelAuthorisedLocked OK
  |       ----- channelsLock.unlock        # Broker.java:473
  |       ----- registrationLock.unlock    # Broker.java:477
  |       gossipToNeighbours(PublishGossip)
  |       runTask(esPropagationIndex, ...)
  v

ES_PROPAGATION_URI (esPropagationIndex)    # Broker.java:491-498
  |  -> propagationStage(channel, msg)
  |       List<DeliveryTarget> targets = snapshotTargets(channel)
  |             ----- registrationLock.read
  |             ----- subscriptionsLock.read
  |                   iterate subs[channel] -> build DeliveryTarget list
  |             ----- subscriptionsLock.unlock
  |             ----- registrationLock.unlock
  |       deliverToTargets(channel, msg, targets)
  |             AtomicInteger remaining = targets.size()
  |             for each target:
  v               runTask(esDeliveryIndex, ...)

ES_DELIVERY_URI (esDeliveryIndex)          # Broker.java:575-589
  |  per-target lambda, in parallel:
  |       if (filter.match(msg)) out.receive(channel, msg)   # remote RMI call
  |       finally { if (remaining.decrementAndGet() == 0)
  |                     broker.finishInFlight(channel); }
  v

  Subscriber's ReceivingInboundPort.receive(...)            # other JVM / component
```

Points clefs :

- **Aucun verrou n'est tenu pendant `out.receive(...)`** (`Broker.java:579`).
  La règle « no remote call under lock » est respectée : le snapshot a déjà
  copié le port outbound + le filtre dans une `DeliveryTarget` immutable.
- **`AtomicInteger remaining`** garantit que `finishInFlight` n'est appelé
  qu'une fois pour le message entier, même si N livraisons s'exécutent en
  parallèle (`Broker.java:573, 584-586`).
- **In-flight encadre tout le pipeline** : incrémenté juste à l'entrée de
  `receptionStage` (avant validation), décrémenté seulement quand la
  *dernière* delivery termine. C'est exactement ce que `destroyChannel`
  observe pour décider quand il peut nettoyer.

---

## 4. `runTask` plutôt que `handleRequest` (Phase D.3 — `214d240` + `6d767fc`)

Avant la Phase D.3, les inbound ports appelaient directement
`((Broker)getOwner()).publish(...)` depuis la thread RMI. Conséquence :
sur une fédération profonde de 3+ JVMs, chaque hop gossip / publish
gardait une thread RMI bloquée jusqu'au bout de la chaîne — et le pool
RMI par défaut de BCM4Java est petit (≈ 4 threads). Trois publish
concurrents en cascade suffisaient à figer le broker.

Correction : tous les inbound ports `void` exposent

```java
// PublishingInboundPort.java:65-77
@Override
public void publish(String rip, String channel, MessageI message) throws Exception {
    try {
        final Broker broker = (Broker) this.getOwner();
        broker.runTask(broker.getReceptionExecutorIndex(), o -> {
            try {
                ((Broker) o).publish(rip, channel, message);
            } catch (Exception e) {
                ((Broker) o).logMessage("[publish async] " + e);
            }
        });
    } catch (Exception e) {
        throw new RemoteException(e.getMessage(), e);
    }
}
```

La thread RMI :

1. fait un `runTask` (push dans une `BlockingQueue` du `ExecutorService`),
2. retourne tout de suite au caller.

Le travail réel est fait par le pool `esReception`, dont la taille est
choisie *indépendamment* du pool RMI. Le pool RMI redevient disponible
pour les hops suivants.

Idem dans `RegistrationInboundPort.java:106, :164, :182`,
`PrivilegedClientInboundPort.java:107, :125`, et
`GossipReceiverInboundPort.java:44-50`.

Note : les méthodes `synchrones` (qui doivent renvoyer une valeur — par
exemple `register()` qui renvoie un URI, ou `registered()` qui renvoie
un `boolean`) **ne** passent **pas** par `runTask`. Elles s'exécutent
directement sur la thread RMI parce qu'elles ont besoin du résultat
métier. La règle ne couvre donc bien que les methods `void`.

---

## 5. `bulkSubmit` — un seul snapshot par lot (Phase C.5 — `d2c3f14`)

Pour `publish(rip, channel, ArrayList<MessageI>)` et l'overload
`asyncPublishAndNotify` bulk, on ne paie le coût du snapshot d'abonnés
qu'**une fois** :

```java
// Broker.java:1442-1483 (extrait)
this.runTask(this.esReceptionIndex, o -> {
    Broker broker = (Broker) o;
    List<DeliveryTarget> targets;
    try {
        broker.validatePublisherAuthz(publisherReceptionPortURI, channel);
        targets = broker.snapshotTargets(channel);   // SINGLE snapshot
    } catch (Exception e) {
        self.logMessage("[Broker] bulk publish setup failed: " + e + "\n");
        return;
    }
    for (MessageI m : snapshotMessages) {
        if (m == null) continue;
        broker.beginInFlight(channel);
        try {
            broker.validatePublisherAuthz(publisherReceptionPortURI, channel);
        } catch (Exception e) {
            broker.finishInFlight(channel);
            self.logMessage("[Broker] bulk publish skipped after revocation");
            continue;
        }
        // gossip + per-message delivery to the same snapshot:
        broker.runTask(broker.esPropagationIndex, p -> {
            ((Broker) p).deliverToTargets(channel, msg, targets);
        });
    }
});
```

Deux décisions importantes :

- **Snapshot une seule fois** : itérer `subscriptions` sous lecture coûte
  `O(N_subscribers)`. Sans snapshot partagé, un lot de M messages
  paierait `O(M·N)` ; ici on est en `O(N + M)` côté snapshot + dispatch.
- **Re-validation par message** (`validatePublisherAuthz` redécoché à
  chaque tour de boucle, `Broker.java:1456`) : entre deux messages du
  lot, l'autorisation du publisher peut être révoquée
  (`modifyAuthorisedUsers`, `unregister`, `destroyChannel`). On honore
  la révocation au milieu du batch en sautant les messages restants.

C'est la frontière explicite du pipeline : reception
-> snapshot abonnés -> revalidation par message -> fan-out delivery.

---

## 6. Back-pressure par canal — `inFlightPerChannel`

`destroyChannel()` (`Broker.java:1736-1798`) suit ce protocole :

1. Vérifie sous read-lock que l'appelant est bien propriétaire du canal.
2. Cache le canal des `channels` (write-lock court) — plus aucun
   nouveau `publish` ne sera accepté.
3. Émet le gossip `DestroyChannelGossipMessage`.
4. Délègue le nettoyage à `esDeliveryIndex` qui attend que
   `inFlightPerChannel.get(channel) == 0`, puis appelle
   `destroyChannelCleanup` qui retire les abonnements / privilégiés et
   décrémente le quota propriétaire.

> Honnêteté : l'attente du in-flight reste un busy-poll
> `Thread.sleep(10)` (`Broker.java:1786-1791`). Ça suffit en pratique
> (les démos sont vertes), mais une amélioration future serait un
> `Object.wait()` réveillé par `finishInFlight` quand le compteur
> atteint 0 (le user nous a indiqué que la prose initiale parlait d'une
> notify/wait qui n'a pas encore été codée).

Avantage du compteur **par canal** : la destruction de `channel0` n'attend
pas la fin des publish sur `channel1`. C'est aussi pourquoi
`inFlightPerChannel` est un `ConcurrentHashMap` (`Broker.java:225`), pas
un compteur global.

---

## 7. Schéma ASCII — pipeline complet

```
   client side                              broker side                              subscriber side
 +------------+                          +-----------------------+                +---------------+
 | Publisher  |  PublishingCI            | Publishing  Inbound   |  esReception   | Receiver      |
 |  client    |--RMI publish(c,m)------->| Port (pub-in)         |                | InboundPort   |
 |  (publi-   |                          |   |                   |                | (per client)  |
 |  cation    |                          |   v   runTask         |                +---------------+
 |  plugin)   |                          | submitPublish ----> [ESreception]            ^
 +------------+                          |                                              |
                                         |     receptionStage                           |
                                         |       beginInFlight(c)                       |
                                         |       lock check (regis, chan)               |
                                         |       gossipToNeighbours(pub)----> [ESgossip]|
                                         |       runTask                                |
                                         |        |                                     |
                                         |        v                                     |
                                         |    [ESpropagation]                           |
                                         |     propagationStage                         |
                                         |       snapshotTargets(c)                     |
                                         |       deliverToTargets(c,m,T)                |
                                         |         for each t in T:                     |
                                         |           runTask                            |
                                         |             |                                |
                                         |             v                                |
                                         |          [ESdelivery] ----- out.receive(c,m)-+
                                         |               | finally:
                                         |               | remaining.decAndGet()
                                         |               |     == 0 -> finishInFlight
                                         +-----------------------+
```

Les 4 inbound ports (`reg-in`, `pub-in`, `priv-in`, `gossip-in`) sont
publiés sous des URIs déterministes dérivés du
`getReflectionInboundPortURI()` (`Broker.java:178-180, 246` +
`Broker.java:357-370`).

---

## 8. Q/R anticipées

### Q1 — Que se passe-t-il si `nbReceptionThreads = 0` ?
`createNewExecutorService(_, 0, false)` lève une exception à la
construction du broker (BCM exige `nbThreads >= 1`). En pratique on
utilise au minimum 2 threads de réception pour que `register` /
`subscribe` / `publish` ne s'auto-bloquent pas si une opération de
réception déclenche un sub-runTask sur le même pool.

### Q2 — Comment garantir l'ordre intra-canal ?
Sur un seul publish d'un *seul* message, `deliverToTargets` lance N
tâches concurrentes — les N abonnés reçoivent dans un ordre
indéterministe **entre eux**, mais chacun reçoit *bien* ce message.
Pour un même abonné, l'ordre est garanti par sa propre file
`LinkedBlockingQueue` côté `ClientSubscriptionPlugin` (`Phase E.4`,
commit `847724c`) : les `out.receive(...)` arrivent dans l'ordre où le
broker les a envoyés via le même `ReceivingOutboundPort` (RMI préserve
l'ordre par paire `outbound -> inbound`). Pour les rafales bulk via
`bulkSubmit`, le snapshot partagé garantit que tous les messages voient
le même set d'abonnés, et chaque message est dispatché avant d'attaquer
le suivant — donc ordre par abonné = ordre publish.

### Q3 — Pourquoi un executor gossip dédié ?
Pour deux raisons : (1) éviter qu'un fan-out gossip massif (ex.
`UnregisterGossipMessage` dans une fédération maillée) sature les pools
reception/propagation/delivery et fige les opérations clients ; (2)
permettre à `GossipReceiverInboundPort.receive()` de retourner
instantanément à la thread RMI distante (cf. `:44` :
`broker.runTask(broker.getGossipExecutorIndex(), ...)`). C'est la
correction qui a stabilisé Demo3JVMs (cf. GOSSIP.md §5).

### Q4 (bonus) — Et la livraison à un client lent ?
Le `runTask(esDeliveryIndex, ...)` met la livraison en file. Si l'abonné
est long à répondre, la tâche occupe **un** thread du pool delivery
pendant ce temps, mais les autres abonnés sont livrés en parallèle, et
les nouveaux publish (qui s'arrêtent à la réception / propagation)
continuent de s'enchaîner. La latence d'un consommateur lent reste
locale à ce consommateur — le pipeline isole correctement.
