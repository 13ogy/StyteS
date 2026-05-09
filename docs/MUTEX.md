# MUTEX — pilier 1 / 3

> Référence soutenance — Bogdan Styn, Setbel Mélissa.
> Code cité : `src/fr/sorbonne_u/cps/pubsub/base/components/Broker.java`
> (toutes les lignes ci-dessous sont à jour de la branche `final-iteration`).

---

## 1. Pourquoi la mutex est indispensable

Le `Broker` est partagé par toutes les threads RMI (clients distants), par les
4 executor services internes (`reception`, `propagation`, `delivery`, `gossip`)
et — en fédération — par les threads gossip des autres brokers. Chaque appel
public lit ou mute des collections non-thread-safe (`HashMap`, `HashSet`).

Contre-exemple à 2 lignes :

```text
Thread A (reception ES)  -- subscribe(rip-A, "channel0", f)
                            -> subscriptions.get("channel0").put("rip-A", f)
Thread B (reception ES)  -- publish(rip-B, "channel0", m)
                            -> snapshotTargets("channel0")
                                 -> subscriptions.get("channel0").entrySet()
```

Sans verrou, l'itération de B sur la même `HashMap` que A est en train de muter
peut lever `ConcurrentModificationException`, ou pire : laisser `subs` dans un
état corrompu (rebucket pendant le put). C'est exactement ce que les 3
`ReentrantReadWriteLock` empêchent.

---

## 2. Carte des verrous (Broker.java)

| État partagé | Type | Verrou / structure | Ligne déclaration |
|---|---|---|---|
| `registeredClients` (`Map<String,RegistrationClass>`) | `HashMap` | `registrationLock` (RRWL fair) | `Broker.java:187` |
| `receptionPortsOUT` (`Map<String,ReceivingOutboundPort>`) | `HashMap` | `registrationLock` | `Broker.java:189` |
| `channels` (`Set<String>`) | `HashSet` | `channelsLock` (RRWL fair) | `Broker.java:191` |
| `privilegedChannels` (`Map<String,PrivilegedChannelInfo>`) | `HashMap` | `channelsLock` | `Broker.java:207` |
| `subscriptions` (`Map<String,Map<String,MessageFilterI>>`) | `HashMap` imbriquée | `subscriptionsLock` (RRWL fair) | `Broker.java:220` |
| `sendersByNeighbour` (`Map<String,GossipSenderOutboundPort>`) | `HashMap` | écrit en `start()` puis figé ; lu sans verrou | `Broker.java:243` |
| `createdPrivilegedChannelsCount` | `ConcurrentHashMap<String,Integer>` | atomic `merge`/`putIfAbsent` | `Broker.java:211` |
| `inFlightPerChannel` | `ConcurrentHashMap<String,Integer>` | atomic `compute` | `Broker.java:225` |
| `processedGossipURIs` | `ConcurrentHashMap<String,Instant>` | atomic `putIfAbsent` | `Broker.java:258` |

Les trois RRWL sont déclarés en mode **fair** :

```java
// Broker.java:95-97
protected final ReentrantReadWriteLock registrationLock  = new ReentrantReadWriteLock(true);
protected final ReentrantReadWriteLock channelsLock      = new ReentrantReadWriteLock(true);
protected final ReentrantReadWriteLock subscriptionsLock = new ReentrantReadWriteLock(true);
```

---

## 3. Ordre canonique d'acquisition

L'ordre est strict, du plus « extérieur » au plus « intérieur » :

```
registrationLock -> channelsLock -> subscriptionsLock
```

Toutes les méthodes publiques le respectent. Exemples :

- `subscribe()` : `registrationLock.read` -> `channelsLock.read` -> `subscriptionsLock.write`
  (`Broker.java:1249-1280`).
- `unsubscribe()` : même ordre lecture / lecture / écriture.
- `destroyChannel()` : `registrationLock.read` -> `channelsLock.read`,
  puis libère, puis `channelsLock.write` pour cacher le canal
  (`Broker.java:1742-1769`).
- `destroyChannelCleanup()` : `registrationLock.write` -> `channelsLock.write`
  -> `subscriptionsLock.write` (`Broker.java:1805-1813`).
- `update()` (gossip) : pour `CreateChannelGossipMessage`,
  `registrationLock.write` -> `channelsLock.write` -> `subscriptionsLock.write`
  (`Broker.java:1989-2023`). C'est exactement la correction **C.8 fix-4** —
  l'ancien code prenait `channelsLock` avant `registrationLock`, créant un
  cycle potentiel avec les autres méthodes.

> Principe : un graphe de verrous totalement ordonné (ranked locks) ne peut
> pas former de cycle d'attente, donc pas de deadlock.

---

## 4. Schéma ASCII — topologie mutex

```
                +---------------------------------------------+
                |                  Broker                     |
                |                                             |
RMI thread  --->| RegistrationInboundPort (reg-in)            |
RMI thread  --->| PublishingInboundPort   (pub-in)            |
RMI thread  --->| PrivilegedClientInbound (priv-in)           |
RMI thread  --->| GossipReceiverInbound   (gossip-in)         |
                |          |                                  |
                |          v                                  |
                |     runTask(esReceptionIndex / esGossipIndex)|
                |          |                                  |
                |          v                                  |
                |   +----------------------------------+      |
                |   | guarded section: acquire locks   |      |
                |   |   1. registrationLock  (rd|wr)   |      |
                |   |   2. channelsLock      (rd|wr)   |      |
                |   |   3. subscriptionsLock (rd|wr)   |      |
                |   +----------------------------------+      |
                |          |                                  |
                |          v                                  |
                |  +---------+ +---------+ +----------------+ |
                |  | regis-  | | chan-   | | subscriptions  | |
                |  | tration | | nels    | | (per channel)  | |
                |  | (RRWL)  | | (RRWL)  | | (RRWL)         | |
                |  +---------+ +---------+ +----------------+ |
                |                                             |
                |  +--------------------------------------+   |
                |  | lock-free counters (ConcurrentHashMap)|   |
                |  | - inFlightPerChannel (compute)        |   |
                |  | - createdPrivilegedChannelsCount      |   |
                |  | - processedGossipURIs (putIfAbsent)   |   |
                |  +--------------------------------------+   |
                +---------------------------------------------+
```

Règle d'or visible sur ce schéma : **on ne fait jamais d'appel distant
(RMI) à l'intérieur d'un verrou**. Tous les `doPortConnection`,
`out.send(...)`, `out.receive(...)` se font après `unlock()`. Voir
`register()` (`Broker.java:870-891`) : la connexion outbound est faite
hors verrou puis le port est rangé sous verrou écriture.

---

## 5. Pourquoi `ReentrantReadWriteLock` plutôt que `synchronized`

Trois raisons :

1. **Concurrence des lecteurs.** `registered()`, `subscribed()`,
   `channelExist()`, `snapshotTargets()` sont massivement appelées par les
   chemins publish / propagation. En `synchronized` elles seraient
   sérialisées entre elles, alors qu'elles n'ont aucun effet de bord :
   un RRWL en mode lecture les laisse tourner en parallèle.
2. **Mode équitable (`new ReentrantReadWriteLock(true)`).** En charge réelle
   (publish multi-message), un flot continu de lecteurs peut affamer un
   `subscribe()` ou `destroyChannel()` qui demande l'écriture. Le mode fair
   garantit FIFO entre lecteurs et écrivains.
3. **Granularité.** Trois verrous indépendants permettent à `register()`
   et à `subscribe()` (sur des canaux disjoints) de tourner en parallèle.
   Un unique `synchronized(this)` les sérialiserait toutes.

Coût : il faut documenter l'ordre d'acquisition (cf. §3) — c'est fait dans
les commentaires `// Verrous d'écriture imbriqués dans l'ordre canonique...`
et dans les invariants assert.

---

## 6. `ConcurrentHashMap` : le cas des compteurs et de la dédup

Trois maps n'ont **aucun** verrou associé, parce que l'opération est
intrinsèquement atomique :

- **`processedGossipURIs.putIfAbsent(uri, ts)`** (`Broker.java:1932-1934`).
  Renvoie `null` si la clé n'existait pas (donc ce gossip est nouveau, on le
  traite) sinon la valeur existante (déjà vu, on `continue`). Avant la
  Phase F.5 (commit `4c32aba`), le code faisait
  `if (!set.contains(uri)) { ...; set.add(uri); }` sous un `gossipLock`
  séparé. Le double-check était racy si le verrou était lâché entre les
  deux. La phrase clef du commit C.6 (`bb1767f`) :
  > "ConcurrentHashMap.merge for in-flight + quota counters".
- **`inFlightPerChannel.compute(channel, (k,v) -> v == null ? 1 : v+1)`**
  (`Broker.java:504`). `compute` est atomique pour une clef donnée — le
  compteur ne peut pas être surécrit.
- **`createdPrivilegedChannelsCount.merge(rip, 1, Integer::sum)`**
  (`Broker.java:1608`, `:1817`, `:1995`, ...). Décrémente / incrémente le
  quota de canaux privilégiés du client.

Ces compteurs sont lus / écrits depuis tous les ES (reception, delivery,
gossip) — passer par un verrou les sérialiserait inutilement, alors qu'une
seule paire `compute`/`merge` règle la course.

---

## 7. Les `assert` comme documentation exécutable (Phase C.8 — `26032e3`)

Le commit C.8 a ajouté **37 invariants `assert`** dans `Broker.java`
(`grep -c 'assert ' Broker.java` → 37). Ils servent deux buts :

1. Documenter le contrat post-mutation directement à côté de la mutation
   (« si on est passé là, voici ce qui doit être vrai »).
2. Détecter immédiatement, sous `-ea`, toute régression silencieuse.

Trois exemples représentatifs :

```java
// Broker.java:1265-1270 — post-condition de subscribe()
assert subs.containsKey(receptionPortURI)
        : "subscriber must be present after subscribe: "
        + receptionPortURI + " on " + channel;
assert subs.get(receptionPortURI) == effective
        : "stored filter must be the effective (non-null) filter";
```

```java
// Broker.java:782-786 — pré-condition d'une méthode interne
private boolean registeredLocked(String receptionPortURI){
    assert this.registrationLock.getReadHoldCount() > 0
            || this.registrationLock.isWriteLockedByCurrentThread()
            : "registrationLock (read or write) must be held by current thread";
    return this.registeredClients.containsKey(receptionPortURI);
}
```

```java
// Broker.java:1771-1772 — post-condition de destroyChannel()
assert !this.channels.contains(channel)
        : "channel must be hidden immediately after destroyChannel: " + channel;
```

Le pattern « `*Locked` méthode » + assert sur le hold-count rend le
contrat de verrou *vérifiable à l'exécution* — un appel à
`registeredLocked()` sans verrou est immédiatement détecté.

---

## 8. Q/R anticipées

### Q1 — Et si on remplaçait tout par `synchronized` ?
On perd la concurrence des lecteurs (chaque `snapshotTargets` bloquerait
chaque `register`), donc le débit publish chute linéairement avec le
nombre de subscribers. On perd aussi la possibilité de tenir
`registrationLock.read` pendant qu'on prend `subscriptionsLock.write`
(impossible en `synchronized` sur le même moniteur). Enfin, on ne peut
pas asserter le hold-count, donc le contrat « cette méthode requiert le
verrou » devient purement documentaire.

### Q2 — Pourquoi le mode équitable ?
Sans `true` dans le constructeur RRWL, Java privilégie le débit. Sous
charge `publish` continue (qui ne prend que des read-locks), un
`destroyChannel` en attente d'écriture peut être indéfiniment doublé par
de nouveaux lecteurs. Le mode fair impose FIFO et garantit qu'un
écrivain finit toujours par passer, au prix d'un peu de débit.

### Q3 — Pourquoi `inFlightPerChannel` par canal au lieu d'un compteur global ?
Parce que `destroyChannel(channel)` doit attendre que **les messages de
CE canal** soient livrés, pas tous les messages du broker. Un compteur
global rendrait `destroyChannel` arbitrairement lent (il attendrait des
publish sur des canaux qui n'ont rien à voir). Côté implémentation, le
`ConcurrentHashMap.compute` reste local à la clé, donc les contentions
inter-canaux n'existent pas.

### Q4 (bonus) — Ordre des verrous dans le chemin gossip ?
Identique au chemin client (regis -> channels -> subs). C'était la
correction `C.8 fix-4` : `update()` pour `CreateChannelGossipMessage`
prenait à l'origine `channelsLock` avant `registrationLock`, ouvrant un
cycle potentiel avec `subscribe()`. Le code actuel
(`Broker.java:1989-2023`) respecte l'ordre canonique.
