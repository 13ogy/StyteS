# GOSSIP — pilier 3 / 3

> Référence soutenance — Bogdan Styn, Setbel Mélissa.
> Code cité : `src/fr/sorbonne_u/cps/pubsub/base/components/Broker.java`,
> `src/fr/sorbonne_u/cps/pubsub/gossip/`,
> `src/fr/sorbonne_u/cps/pubsub/demo/Demo3JVMs.java`.

---

## 1. CDC §3.7 — vue d'ensemble de la fédération

Plusieurs brokers peuvent former une fédération **par graphe arbitraire**
(arbre, anneau, maillé). Le but : qu'un publish/subscribe local soit
visible des autres brokers, et que les méta-événements (création de
canal privilégié, modification d'autorisation, désinscription, ...) se
propagent à tous.

Le protocole tient en 3 règles :

1. **Voisinage statique** : chaque broker connaît une liste d'URIs de ports
   gossip de ses voisins, fournie au constructeur.
2. **Mémoire des messages déjà vus** pour éviter les boucles dans un
   maillage : chaque message gossip porte un URI unique, le broker
   mémorise les URIs déjà reçus et n'agit jamais deux fois.
3. **Re-diffusion vers les autres voisins** *sauf* celui qui vient de
   nous transmettre le message (skip-echo) : économie de bande et
   convergence plus rapide.

Le `Broker` implémente les deux interfaces de service
(`GossipReceiverCI` offerte, `GossipSenderCI` requise — voir
`Broker.java:67-72`) ainsi que l'interface fonctionnelle commune
`GossipImplementationI` (`Broker.java:73`).

---

## 2. Les 7 messages gossip

Tous résident dans `src/fr/sorbonne_u/cps/pubsub/gossip/messages/` et
implémentent **`EmitterAwareGossipMessageI`** (extension locale de
`GossipMessageI`) — voir §4 ci-dessous.

| Classe | Concern CDC | Données portées |
|---|---|---|
| `RegisterGossipMessage` | §3.5 — un client s'enregistre quelque part | `clientReceptionPortURI`, `RegistrationClass` |
| `UnregisterGossipMessage` | §3.5 — un client se désinscrit | `clientReceptionPortURI` |
| `ModifyServiceClassGossipMessage` | §3.5 — upgrade/downgrade FREE/STANDARD/PREMIUM | `clientReceptionPortURI`, `newRegistrationClass` |
| `CreateChannelGossipMessage` | §3.6 — canal privilégié créé | `channel`, `ownerReceptionPortURI`, `ownerClass`, `authorisedUsers` (regex) |
| `DestroyChannelGossipMessage` | §3.6 — canal détruit | `channel`, `ownerReceptionPortURI` |
| `ModifyAuthorisedUsersGossipMessage` | §3.6 — changement de policy | `channel`, `authorisedUsers` (regex) |
| `PublishGossipMessage` | §3.4 — un message a été publié | `channel`, `pubMessage`, `publisherReceptionPortURI` |

Tous portent en commun (hérité de `GossipMessageI`) :
`gossipMessageURI` (UUID unique) et `timestamp` (Instant).

---

## 3. Anti-loop : dédup atomique (Phase F.5 — `4c32aba`)

Avant la Phase F.5, le code faisait un *check-then-add* sous un
`gossipLock` séparé :

```java
// AVANT (racy si lock relâché entre les deux étapes) :
if (!processedGossipURIs.contains(uri)) {
    processedGossipURIs.add(uri);
    // ... traiter
}
```

Maintenant, le check + insert ne forment qu'une seule opération atomique
de `ConcurrentHashMap` (`Broker.java:1932-1934`) :

```java
if (this.processedGossipURIs.putIfAbsent(
        msg.gossipMessageURI(), msg.timestamp()) != null) {
    continue;   // déjà vu, on ignore
}
```

`putIfAbsent` retourne :
- `null` si la clé n'existait pas → on a *gagné* la course, on traite ;
- la valeur existante sinon → un autre thread (probablement venu d'un
  autre voisin) a déjà traité ce même message, on s'arrête.

Aucun verrou. Aucune fenêtre TOCTOU. Le `processedGossipURIs` est un
`ConcurrentHashMap<String,Instant>` (`Broker.java:258`) avec un nettoyage
périodique des entrées de plus de 2 minutes (`cleanupGossipMemory()`,
`Broker.java:2174-2179`, exécuté toutes les 2 min via
`scheduleTaskAtFixedRate` si le broker a un thread schedulable).

L'invariant post-dédup est documenté par un `assert` (`Broker.java:1938-1940`).

---

## 4. Skip-echo : ne jamais renvoyer à l'émetteur (Phase F.2 / commit `4c32aba`)

Dans un maillage, sans précaution, B1 envoie un gossip à B2, B2 le
re-diffuse à *tous* ses voisins (dont B1), B1 dédup et stoppe — mais on
a déjà fait un envoi RMI inutile. À 10 brokers ça devient ridicule.

### 4a. Le sender se déclare via `EmitterAwareGossipMessageI`

L'interface `GossipMessageI` (fichier figé fourni par le sujet) ne
déclare pas `getEmitterURI()`. On a introduit
`EmitterAwareGossipMessageI` dans
`src/fr/sorbonne_u/cps/pubsub/gossip/interfaces/EmitterAwareGossipMessageI.java`
qui *étend* `GossipMessageI` et déclare explicitement :

```java
// EmitterAwareGossipMessageI.java:22-31
public interface EmitterAwareGossipMessageI extends GossipMessageI {
    String getEmitterURI();
}
```

Tous nos 7 messages gossip implémentent `EmitterAwareGossipMessageI`
(grep `implements EmitterAwareGossipMessageI` -> 7 hits).

### 4b. Le broker tient un index inverse `emetteur -> outbound port`

```java
// Broker.java:243
private final Map<String, GossipSenderOutboundPort> sendersByNeighbour
        = new HashMap<>();
```

Rempli dans `start()` (`Broker.java:631-647`) au moment où le broker se
connecte à chacun de ses voisins. La clef est l'URI de réflexion du
voisin (le suffixe `-gossip-in` est strippé via `stripGossipSuffix()` —
`Broker.java:653-666`), pour pouvoir retrouver le sender à partir de
`getEmitterURI()`.

### 4c. La re-diffusion saute le sender immédiat

```java
// Broker.java:2123-2145
GossipMessageI forwarded = msg.copyWithNewEmitterURI(this.getReflectionInboundPortURI());
for (Map.Entry<String, GossipSenderOutboundPort> e
        : this.sendersByNeighbour.entrySet()) {
    if (immediateSenderReflectionURI != null
            && e.getKey().equals(immediateSenderReflectionURI)) {
        continue; // skip echo
    }
    final GossipSenderOutboundPort sender = e.getValue();
    this.runTask(this.esGossipIndex, o -> {
        try { sender.send(new GossipMessageI[]{forwarded}); }
        catch (Exception ex) { ((Broker) o).logMessage(...); }
    });
}
```

Trois nuances importantes :

- On itère **toujours** les autres voisins. On ne se contente pas de
  stopper si on a un voisin commun, parce que le graphe n'est pas
  forcément un arbre — la dédup F.5 protège déjà des cycles.
- L'émetteur est ré-écrit (`copyWithNewEmitterURI`) avant la re-diffusion.
  Ainsi le voisin suivant verra *nous* comme émetteur et nous excluera
  à son tour.
- L'envoi est lui-même un `runTask(esGossipIndex, ...)`, donc le
  `update()` ne bloque pas sur la latence réseau — c'est le pipeline
  gossip §5 ci-dessous.

Source soutenance §6.2 + §6.6 commenté inline dans le code
(`Broker.java:1929, 1942-1950, 2123-2145`).

---

## 5. Réception asynchrone — fix de stabilité

`GossipReceiverInboundPort.receive(...)` (la totalité du fichier,
`gossip/ports/GossipReceiverInboundPort.java:39-63`) :

```java
@Override
public void receive(GossipMessageI[] gossipMessages) throws Exception {
    try {
        ComponentI owner = this.getOwner();
        if (owner instanceof Broker) {
            final Broker broker = (Broker) owner;
            broker.runTask(broker.getGossipExecutorIndex(), o -> {
                try {
                    ((GossipImplementationI) o).receive(gossipMessages);
                } catch (Exception e) {
                    ((Broker) o).logMessage("[gossip receive async] " + e);
                }
            });
        } else {
            owner.runTask(o -> { ... });
        }
    } catch (Exception e) {
        throw new RemoteException(e.getMessage(), e);
    }
}
```

Pourquoi c'est critique : sous partie 4, le partenaire avait observé
des « 3 OK / 4 random failure » sur Demo3JVMs. Diagnostic : le
`receive()` synchrone de l'inbound port gardait la thread RMI bloquée
pendant tout le `update()` (qui prend des verrous écriture, fait du
fan-out outbound, etc.). Sur une fédération B1↔B2↔B3 où B2 reçoit
*simultanément* des publish de B1 et de B3, le pool RMI de B2 saturait
et certains gossips étaient perdus / réessayés.

Correction : la réception devient strictement asynchrone (le `runTask`
sur `esGossipIndex` est *non bloquant* côté RMI). La thread RMI
distante de l'envoyeur retourne immédiatement et peut traiter le
message gossip suivant. Combinée à la skip-echo (§4) et à la dédup
atomique (§3), Demo3JVMs est passée stablement à 5/5 vert (cf. §7).

---

## 6. Demo3JVMs — promenade

Fichier : `src/fr/sorbonne_u/cps/pubsub/demo/Demo3JVMs.java` (449 lignes).
Topologie : trois JVMs, chacun héberge un broker + un client.

### 6a. Topologie statique

```
       B1  <----gossip---->  B2  <----gossip---->  B3
       |                     |                     |
       C1 (FREE)             C2 (FREE)             C3 (FREE)
```

- B1 a pour voisin B2 (`Demo3JVMs.java:330-342`).
- B2 a deux voisins : B1 et B3 (`Demo3JVMs.java:365-377`).
- B3 a pour voisin B2 (`Demo3JVMs.java:387-398`).

Le graphe est une chaîne. Si on remplace par un cycle (B1↔B3 ajouté), la
dédup F.5 + skip-echo F.2 garantit toujours la convergence.

### 6b. Configuration de chaque broker

```java
// Demo3JVMs.java:336-342  (cas B1)
new Object[] {
    BROKER1_URI,           // reflectionInboundPortURI
    2, 1,                  // nbThreads, nbSchedulableThreads
    3, 2, 5,               // nbFreeChannels, standardQuota, premiumQuota
    2, 4, 8,               // nbReceptionThreads, nbPropagationThreads, nbDeliveryThreads
    broker1Neighbors       // List<String> URIs gossip des voisins
}
```

Chaque broker pré-crée donc 3 canaux libres (`channel0..channel2`),
autorise 2 canaux privilégiés par client STANDARD, 5 par client PREMIUM.

### 6c. Scénario joué

Construit dans `testScenario()` (cf. `Demo3JVMs.java` autour de la
ligne 200, scénario `TestScenario` BCM). Les étapes typiques (selon le
build courant) :

1. C1, C2, C3 s'enregistrent chacun chez son broker local en `FREE`.
2. C1 souscrit à `channel0` avec un filtre `AcceptAllTimeFilter`
   (`Demo3JVMs.java:298-305`).
3. C3 publie un message sur `channel0`.
4. Le `PublishGossipMessage` traverse B3 → B2 → B1, dédup et skip-echo
   font leur travail, et le message est livré à C1 via le snapshot local
   de B1.

Le canal étant un canal libre (`channel0` est pré-créé sur chaque
broker), la propagation gossip n'a pas besoin de re-créer le canal —
seul le `PublishGossipMessage` est utile.

### 6d. Lancement

```bash
cd deployment

# Terminal 1 : registre RMI global
./start-gregistry

# Terminal 2 : barrière cyclique pour la synchro inter-JVM
./start-cyclicbarrier

# Terminaux 3, 4, 5 : un par JVM
./start-dcvm 1 broker1-jvm
./start-dcvm 1 broker2-jvm
./start-dcvm 1 broker3-jvm
```

`start-dcvm` lance avec `-Djava.security.manager` et la policy
`config/dcvm.policy`. La JVM doit donc accepter le `SecurityManager`
(JDK 8 ; sur JDK 19 le launcher distribué nécessite un workaround
spécifique — d'où la préférence pour les démos centralisées sous JDK 19).

---

## 7. Stabilité observée

Sous partie 4 + les fixes Phase D.3 + F.2 + F.5, Demo3JVMs a été lancée
**5 fois consécutives** avec succès (5/5 vert). L'ordre conceptuel des
événements est respecté : l'instance C1 reçoit bien le message publié
par C3 trois sauts plus loin, sans message dupliqué (la dédup empêche
le retraitement même quand B2 voit le message arriver des deux côtés
si un cycle est introduit ; en chaîne pure il n'y a qu'un chemin).

Reproductibilité : le combo registre + barrière cyclique +
`start-dcvm <test#> <jvm-uri>` est l'approche officielle BCM4Java pour
les CVM distribués. Le fait qu'il soit scripté (4 fichiers shell dans
`deployment/`) garantit que la séquence de démarrage est toujours la
même.

---

## 8. Issues fermées

| Issue | Symptôme | Correction | Phase |
|---|---|---|---|
| `*GossipMessage` race-1 | Re-traitement double sous bursts | `putIfAbsent` atomique | F.5 / `4c32aba` |
| `*GossipMessage` race-2 (« quota-bounce ») | Quota privilégié incrémenté/décrémenté à mauvais escient sur re-diffusion | check `if (!channelExistLocked(...))` avant `merge(+1)` puis `merge(-1)` si déjà créé localement (`Broker.java:2001-2017`) | C.8 fix-2 |
| `*GossipMessage` race-3 (busy-poll destroy) | `destroyChannel` pollait sans relâche `inFlightPerChannel` | `Thread.sleep(10)` dans la boucle (`Broker.java:1786-1791`) — pragmatique. Note honnête : un `wait/notify` reste à coder pour devenir non-busy | C.8 fix-3 (partiel) |
| `*GossipMessage` race-4 (lock order) | `update(CreateChannelGossipMessage)` prenait `channelsLock` avant `registrationLock` | Réordonné sur l'ordre canonique (`Broker.java:1989-2023`) | C.8 fix-4 |
| Stability D3JVMs | « 3 OK / 4 random failure » — RMI thread pool saturé | `GossipReceiverInboundPort.receive` -> `runTask(esGossipIndex)` | partie 4 + Phase D.3 |
| Skip-echo | Bande gaspillée en maillage | `sendersByNeighbour` + `getEmitterURI()` filter dans `update()` | F.2 / `4c32aba` |

---

## 9. Schéma ASCII — chaîne 3 brokers

```
   +-----------+                    +-----------+                    +-----------+
   |  Broker 1 |                    |  Broker 2 |                    |  Broker 3 |
   |  (B1 RIP) |                    |  (B2 RIP) |                    |  (B3 RIP) |
   +-----------+                    +-----------+                    +-----------+
        |   ^                          |    ^                            |   ^
        |   |  EmitterAwareGossipMsg   |    |  copyWithNewEmitterURI     |   |
        |   |  with emitterURI=B1      |    |  (B2 -> B3, skip B1)       |   |
        |   |                          |    |                            |   |
        +---+--------------------------+----+----------------------------+---+
                       gossip-in <--> gossip-out (per neighbour)

  B1 publishes:                B2 receives:                  B3 receives:
   processedGossipURIs           processedGossipURIs           processedGossipURIs
     putIfAbsent(uri) -> null      putIfAbsent(uri) -> null      putIfAbsent(uri) -> null
     emitter = B1 (self)           emitter = B1                  emitter = B2
     for each n in senders:        immediateSender = B1          immediateSender = B2
       runTask(esGossip, send)     forward to (B3) only          forward to (none) only
                                     copyWithNewEmitterURI(B2)     (no other neighbour
                                                                    than the sender)

  If B1<->B3 also exists (mesh):
   B3 receives the same uri twice (once via B2, once direct from B1).
   Second putIfAbsent returns non-null -> continue. NO double processing,
   NO infinite ping-pong.
```

`copyWithNewEmitterURI` est appelé sur chaque hop, ce qui permet à
chaque broker d'identifier *l'émetteur immédiat* (et non l'émetteur
originel) pour le skip-echo.

---

## 10. Q/R anticipées

### Q1 — Que se passe-t-il si un URI gossip est rejoué ?
Le `processedGossipURIs.putIfAbsent` retourne la valeur déjà présente
(timestamp de la première réception), donc le `update()` `continue` —
zéro effet de bord, zéro re-diffusion. Le canal de mémoire est purgé
au-delà de 2 minutes (`cleanupGossipMemory`), donc un rejeu plus tardif
serait re-traité — c'est intentionnel : si on relance un broker après 5
minutes, il ré-émettra de bons gossips qui seront re-acceptés par les
autres.

### Q2 — Quelles garanties d'ordre entre brokers ?
Aucune garantie d'ordre **global** au-delà de la causalité : si B1 émet
A puis B sur la même chaîne RMI vers B2, ils arriveront dans l'ordre.
Mais entre A émis par B1 et A' émis par B3, la fédération ne les
sérialise pas. C'est cohérent avec un protocole gossip : *eventual
consistency*. Pour les opérations métier qui ont besoin d'ordre
(unregister vs publish), c'est la dédup et la sémantique idempotente
des opérations qui rendent l'ordre indifférent.

### Q3 — Comment scaler à 10 brokers ?
Trois facteurs :

1. **Topologie** : passer d'une chaîne à un graphe partiellement maillé
   (ex. anneau + diagonales) limite le diamètre à `O(log N)`. La
   skip-echo et la dédup tiennent toujours.
2. **Pool gossip** : `esGossipIndex` est dimensionné à 4 threads en dur
   (`Broker.java:342`). À 10 brokers / fan-out élevé, le passer en
   paramètre constructeur serait l'évolution naturelle.
3. **Mémoire de dédup** : `processedGossipURIs` croît linéairement avec
   le débit gossip ; le nettoyage des entrées >2 min suffit pour le
   débit de la démo. À l'échelle, on remplacerait par un cache borné
   (LRU) ou un TTL plus court.

### Q4 (bonus) — Pourquoi `EmitterAwareGossipMessageI` plutôt que d'ajouter `getEmitterURI` à `GossipMessageI` ?
Parce que `GossipMessageI` est une interface **figée** par le sujet —
on ne peut pas la modifier. L'extension locale ajoute la méthode tout
en restant compatible avec le reste de l'API. Le `instanceof` résiduel
dans `update()` est une sécurité défensive : si un test fournit un
`GossipMessageI` brut (non Emitter-aware), le code se contente de ne
pas faire de skip-echo (c'est-à-dire qu'il re-diffuse à *tous* les
voisins) — la dédup empêchera les boucles infinies.

### Q5 (bonus) — Pourquoi un *Visitor* pour `update()` ?

À la soutenance intermédiaire, l'examinateur a demandé de remplacer la
chaîne `if (msg instanceof X) { ... } else if (msg instanceof Y) { ... }`
par un dispatch typé. Réponse : pattern *Visitor* à double-dispatch.

```text
                ┌──────────────────────────────────┐
                │ AbstractGossipMessage            │
                │  abstract accept(Visitor v)      │   implements EmitterAwareGossipMessageI
                └──────────────────────────────────┘
                  ▲           ▲            ▲
                  │           │            │
        RegisterGossipMessage  PublishGossipMessage  …
        accept(v) { v.visit(this); }       accept(v) { v.visit(this); }

                ┌──────────────────────────────────┐
                │ GossipMessageVisitor             │
                │  visit(RegisterGossipMessage)    │
                │  visit(PublishGossipMessage)     │
                │  visit(...)                      │
                └──────────────────────────────────┘
                                ▲
                                │
                ┌──────────────────────────────────┐
                │ BrokerGossipHandler              │
                │  appliquer la mutation locale    │
                │  pour chaque type concret        │
                └──────────────────────────────────┘
```

Bénéfices, par rapport à la chaîne `instanceof` :

* **Substituabilité** — ajouter un nouveau type de gossip = ajouter
  une sous-classe de `AbstractGossipMessage` + une nouvelle méthode
  `visit(...)` au visiteur. Le compilateur signale les visiteurs
  oubliés (jamais d'oubli silencieux, contrairement à un `switch`
  sans `default` strict).
* **Pas de cast unsafe** dans `update()` — chaque `visit(...)` reçoit
  le type concret, donc tous les accesseurs (`getChannel`,
  `getOwnerReceptionPortURI`, etc.) sont typés.
* **Séparation des responsabilités** — `Broker.update()` reste
  responsable de la dédup atomique + du skip-echo + du fan-out ;
  `BrokerGossipHandler` est responsable de la mutation d'état locale.
  Les deux préoccupations se lisent indépendamment.

Le code reste à un seul `instanceof` défensif :
`if (msg instanceof AbstractGossipMessage) { ((AbstractGossipMessage) msg).accept(handler); }`.
Cette unique vérification est une garde côté sécurité (cf. Q4) qui ne
s'applique qu'aux messages tiers extérieurs à notre hiérarchie.
