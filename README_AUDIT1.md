# StyteS — Audit 1 (Étape 1 FREE) — Notes d’implémentation

Ce document résume **ce qui a été implémenté** pour l’**audit 1 / étape 1 FREE** (pub/sub minimal), **les fichiers concernés**, et **les aspects techniques** (ports, connecteurs, threads, etc.).  
Il est structuré pour répondre directement aux points typiques du **cahier des charges** (CDC).

> Scope choisi : **Étape 1 = FREE uniquement**. Toute la partie **privileged** (étape 2) est explicitement hors scope.

---

## 0) Comment compiler et exécuter les démos

### Compilation
```bash
javac -cp libs/BCM4Java-03022026.jar $(find src -name '*.java')
```

### Exécution (démo d’intégration pub/sub “base”)
```bash
java -cp libs/BCM4Java-03022026.jar:src fr.sorbonne_u.cps.pubsub.base.DemoAudit1
```

Démo :  
- 1 Broker + 2 Clients (subscriber + publisher)  
- subscribe sur `channel0` avec un filtre `type == "demo"`  
- publish d’un message qui porte la propriété `"type"="demo"`  
- livraison vers le subscriber (voir §6 pour la trace)

### Exécution (démo “application météo/éolienne” CDC §3.4)
```bash
java -cp libs/BCM4Java-03022026.jar:src fr.sorbonne_u.cps.pubsub.application.meteo.DemoMeteoAudit1
```

Démo :  
- 1 Broker + 1 WindTurbine + 2 WeatherStations + 1 WeatherOffice  
- souscription de l’éolienne à 2 canaux (vent + alertes) avec filtres (type=wind/type=alert)  
- publication d’un vent “proche” (accepté) et d’un vent “loin” (ignoré)  
- publication d’une alerte ORANGE (mise en sécurité) puis GREEN (retour normal)

---

## 1) Cahier des charges — Couverture par section (audit 1)
Ce tableau répond explicitement à la demande “3.1 implémenté / 3.2 non implémenté…”.

- **§3.1 Modèle de messages / propriétés** : ✅ implémenté (`Message.java`, propriétés `PropertyI`)
- **§3.2 Filtres** : ✅ implémenté (`MessageFilter.java` + `messages/filters/*`)
- **§3.3 Pub/Sub étape 1 (FREE)** : ✅ implémenté (broker + client, enregistrement, subscribe, publish, livraison filtrée)
- **§3.4 Application météo/éolienne** : ✅ implémenté (composants + scénario simple sans outil de test temporisé : `DemoMeteoAudit1`)
- **Étape 2 (Privileged, quotas, création/suppression de canaux, etc.)** : ❌ non implémenté (hors audit 1)

---

## 2) Audit 1 — Objectifs (CDC §4.2) : conformité
Rappel CDC §4.2 (audit 1) : première implantation centrée sur `RegistrationClass.FREE`, demandant :
1) **Implanter les composants courtiers et clients** + éléments BCM4Java (ports, connecteurs, …)  
   ✅ Réalisé dans `src/fr/sorbonne_u/cps/pubsub/base/*` (Broker/Client + ports + connecteurs).
2) **Créer et interconnecter** un broker, ≥1 éolienne, ≥2 stations, ≥1 bureau météo  
   ✅ Réalisé dans `DemoMeteoAudit1` (1 Broker + 1 WindTurbine + 2 WeatherStations + 1 WeatherOffice).
3) **Monter un scénario de test simple** (sans outil temporisé) avec ≥1 message **filtré** et reçu par l’éolienne  
   ✅ Réalisé dans `DemoMeteoAudit1` : filtres `type=wind` / `type=alert`, vent proche accepté, vent loin rejeté, alertes ORANGE/GREEN reçues.

---

## 3) CDC — Modèle “Message” et propriétés (implémenté ✅)

### Interfaces CDC
- `fr.sorbonne_u.cps.pubsub.interfaces.MessageI`
- `fr.sorbonne_u.cps.pubsub.interfaces.MessageI.PropertyI`

### Implémentation
- `src/fr/sorbonne_u/cps/pubsub/messages/Message.java`
  - payload sérialisable
  - timestamp
  - propriétés (tableau/collection de `PropertyI`)
  - opérations d’accès aux propriétés

**Statut** : ✅ implémenté (déjà réalisé avant la partie “base”).

---

## 4) CDC — Filtres (Value/Property/Time/MessageFilter) (implémenté ✅)

### Interfaces CDC
- `fr.sorbonne_u.cps.pubsub.interfaces.MessageFilterI`
  - `ValueFilterI`
  - `PropertyFilterI`
  - `PropertiesFilterI`
  - `TimeFilterI`
  - `MultiValuesFilterI`

### Implémentation
- `src/fr/sorbonne_u/cps/pubsub/messages/MessageFilter.java`
- `src/fr/sorbonne_u/cps/pubsub/messages/filters/*`
  - `AcceptAllValueFilter`, `EqualsValueFilter`, `ComparableValueFilter`, …
  - filtres temps : `AcceptAllTimeFilter`, `BetweenTimeFilter`, etc.
  - `PropertyFilter`, `PropertiesFilter`, `MultiValuesFilter`

**Statut** : ✅ implémenté (et utilisé par le broker via `filter.match(message)`).

---

## 5) CDC — Étape 1 FREE : Enregistrement + canaux FREE (implémenté ✅)

### 3.1 Création des canaux FREE
Dans le broker, à l’initialisation :
- création de `NB_FREE_CHANNELS` canaux : `channel0..channel{N-1}`

Fichier :
- `src/fr/sorbonne_u/cps/pubsub/base/components/Broker.java`
  - constante : `public static final int NB_FREE_CHANNELS = 3;`

**Statut** : ✅ implémenté.

### 3.2 Enregistrement d’un client FREE
Méthodes (CDC `RegistrationCI`) implémentées côté broker :
- `registered(receptionPortURI)`
- `registered(receptionPortURI, rc)`
- `register(receptionPortURI, rc)`
- `modifyServiceClass(receptionPortURI, rc)` *(implémentation minimale audit1)*
- `unregister(receptionPortURI)`

Fichier :
- `src/fr/sorbonne_u/cps/pubsub/base/components/Broker.java`

**Décision audit 1** :
- Le broker **refuse** `rc != FREE` en lançant `UnauthorisedClientException` (étape 2 hors scope).

**Statut** : ✅ implémenté.

---

## 6) CDC — Étape 1 FREE : Abonnements et gestion des filtres (implémenté ✅)

Méthodes (CDC `RegistrationCI`) implémentées côté broker :
- `channelExist(channel)`
- `channelAuthorised(receptionPortURI, channel)`
  - en FREE : autorisé si channel existe (tous les FREE peuvent utiliser les FREE channels)
- `subscribed(receptionPortURI, channel)`
- `subscribe(receptionPortURI, channel, filter)`
- `unsubscribe(receptionPortURI, channel)`
- `modifyFilter(receptionPortURI, channel, filter)`

Fichier :
- `src/fr/sorbonne_u/cps/pubsub/base/components/Broker.java`

**Modèle de données interne** :
- `Set<String> channels`
- `Map<String, Map<String, MessageFilterI>> subscriptions`
  - clé 1 : channel
  - clé 2 : `receptionPortURI` du client abonné
  - valeur : filtre

**Exceptions** :
- `UnknownClientException` si client non enregistré
- `UnknownChannelException` si channel inexistant
- `NotSubscribedChannelException` si unsubscribe/modifyFilter sur un channel non souscrit

**Statut** : ✅ implémenté.

---

## 7) CDC — Étape 1 FREE : Publication + livraison filtrée (implémenté ✅)

Méthodes (CDC `PublishingCI`) implémentées côté broker :
- `publish(receptionPortURI, channel, message)`
- `publish(receptionPortURI, channel, ArrayList<MessageI> messages)`

Fichier :
- `src/fr/sorbonne_u/cps/pubsub/base/components/Broker.java`

**Sémantique** :
1. vérifie client enregistré + channel exist
2. parcourt les abonnés du channel
3. pour chaque abonné : si `filter.match(message)` alors livraison

**Livraison** :
- Le broker possède **un outbound port par client** (exigence importante pour éviter une seule connexion partagée).

Données internes :
- `Map<String, BrokerReceptionOutboundPort> receptionPortsOUT`  
  (clé = `receptionPortURI` du client)

**Statut** : ✅ implémenté.

---

## 8) CDC — Ports et connecteurs BCM4Java (implémenté ✅)

L’objectif est que l’architecture “base” respecte le style BCM4Java :
- **Inbound port** : forward vers le composant owner
- **Outbound port** : appelle `getConnector()` au moment de l’appel
- **Connector** : forward via `this.offering` (pas de champs `courtier/client` null)

### 6.1 Inbound ports du broker
- `src/fr/sorbonne_u/cps/pubsub/base/ports/BrokerRegistrationInboundPort.java`
  - forward vers `((Broker)getOwner()).*`

- `src/fr/sorbonne_u/cps/pubsub/base/ports/BrokerPublishingInboundPort.java`
  - forward vers `((Broker)getOwner()).publish(...)`

**Statut** : ✅ implémenté.

### 6.2 Outbound ports client (robustes)
- `src/fr/sorbonne_u/cps/pubsub/base/ports/ClientRegistrationOutboundPort.java`
  - n’utilise plus de champ connector “figé”
  - wrap des `Exception` en `RemoteException`

- `src/fr/sorbonne_u/cps/pubsub/base/ports/ClientPublishingOutboundPort.java`
  - appelle `((PublishingCI)getConnector()).publish(...)`
  - wrap en `RemoteException`

**Statut** : ✅ implémenté.

### 6.3 Connecteurs (pas de champs null)
- `src/fr/sorbonne_u/cps/pubsub/base/connectors/ClientBrokerRegistrationConnector.java`
- `src/fr/sorbonne_u/cps/pubsub/base/connectors/ClientBrokerPublishingConnector.java`
- `src/fr/sorbonne_u/cps/pubsub/base/connectors/BrokerClientReceivingConnector.java`

Tous forward via :
- `((XxxCI)this.offering).method(...)`

**Statut** : ✅ implémenté.

### 6.4 Delivery broker → client
- `src/fr/sorbonne_u/cps/pubsub/base/ports/BrokerReceptionOutboundPort.java`
  - n’utilise plus de champ `connecteur` (qui était null à l’exécution)
  - appelle `((ReceivingCI)getConnector()).receive(...)`

**Statut** : ✅ implémenté.

---

## 9) CDC — Client FREE minimal (implémenté ✅)

Fichier :
- `src/fr/sorbonne_u/cps/pubsub/base/components/Client.java`

### 7.1 Ordre de connexion correct
Dans `register(rc)` :
1. `doPortConnection(registrationPortOUT -> Broker.registrationPortURI())`
2. `register(receptionPortIN.getPortURI(), rc)`
3. `doPortConnection(publishingPortOUT -> broker publishing inbound URI)`

**Statut** : ✅ implémenté.

### 7.2 API nécessaire pour la démo
Ajouté :
- `subscribe(String channel, MessageFilterI filter)`
- `publish(String channel, MessageI message)`

**Statut** : ✅ implémenté.

### 7.3 Réception
- `ClientInboundPort` forward vers `Client.receive(...)`
- `Client.receive(...)` trace le message (payload, properties, timestamp)

Fichiers :
- `src/fr/sorbonne_u/cps/pubsub/base/ports/ClientInboundPort.java`
- `src/fr/sorbonne_u/cps/pubsub/base/components/Client.java`

**Statut** : ✅ implémenté.

---

## 10) Démo d’intégration Audit 1 (implémentée ✅)

Fichier :
- `src/fr/sorbonne_u/cps/pubsub/base/DemoAudit1.java`

### Points techniques BCM4Java
- `AbstractComponent.createComponent(...)` retourne une **URI String** (pas un objet).
- Récupération de l’instance via `AbstractCVM.uri2component.get(uri)`.

### Scénario
- `Broker` : threads (2,0)
- `Client subscriber` : (1,0)
- `Client publisher` : (1,0)
- subscribe sur `channel0` avec filtre `type == demo`
- publish d’un `Message(\"Hello audit1\")` + property `type=demo`

**Statut** : ✅ compile + exécution sans exceptions.

> Remarque : si tu ne vois pas les traces de réception, c’est souvent car le tracing BCM4Java peut être désactivé par défaut.  
> Solution simple si besoin : remplacer `traceMessage(...)` par `System.out.println(...)` dans `Client.receive(...)`, ou activer le tracer/logging du composant.

---

## 11) Threads / exécution (CDC — aspects techniques)

### Broker
Constructeur BCM : `Broker(int nbThreads, int nbSchedulableThreads)`  
Dans la démo : `(2,0)`  
- 2 threads “request handling” (permet de servir register/subscribe/publish sans bloquer)

### Clients
Dans la démo : `(1,0)`  
- 1 thread suffit pour traiter les appels entrants ReceivingCI et faire les actions simples.

---

## 12) Ce qui n’est PAS implémenté (hors audit 1 / étape 2)

- Service **privileged** (`PrivilegedClientCI`) : création/destruction de channels privileged, quotas, regex authorisedUsers, etc.
- STANDARD/PREMIUM
- scénarios métier “météo/éoliennes/bureaux” complets (mais le socle pub/sub FREE est prêt)

Fichiers privileged supprimés car non nécessaires à l’audit 1 et non conformes à l’interface :
- `src/fr/sorbonne_u/cps/pubsub/base/connectors/ClientBrokerPriviledgedConnector.java`
- `src/fr/sorbonne_u/cps/pubsub/base/ports/BrokerPriviledgedInboundPort.java`
- `src/fr/sorbonne_u/cps/pubsub/base/ports/ClientPriviledgedOutboundPort.java`

---

## 13) Liste des fichiers “base” modifiés/ajoutés (récap)

### Modifiés
- `src/fr/sorbonne_u/cps/pubsub/base/components/Broker.java`
- `src/fr/sorbonne_u/cps/pubsub/base/components/Client.java`
- `src/fr/sorbonne_u/cps/pubsub/base/ports/BrokerRegistrationInboundPort.java`
- `src/fr/sorbonne_u/cps/pubsub/base/ports/BrokerPublishingInboundPort.java`
- `src/fr/sorbonne_u/cps/pubsub/base/ports/BrokerReceptionOutboundPort.java`
- `src/fr/sorbonne_u/cps/pubsub/base/ports/ClientInboundPort.java`
- `src/fr/sorbonne_u/cps/pubsub/base/ports/ClientRegistrationOutboundPort.java`
- `src/fr/sorbonne_u/cps/pubsub/base/ports/ClientPublishingOutboundPort.java`
- `src/fr/sorbonne_u/cps/pubsub/base/connectors/ClientBrokerRegistrationConnector.java`
- `src/fr/sorbonne_u/cps/pubsub/base/connectors/ClientBrokerPublishingConnector.java`
- `src/fr/sorbonne_u/cps/pubsub/base/connectors/BrokerClientReceivingConnector.java`

### Ajouté
- `src/fr/sorbonne_u/cps/pubsub/base/DemoAudit1.java`

---

## 14) TODO optionnel (tests unitaires JUnit)
Pas fait à ce stade (pas de JUnit en dépendance).  
Possible en ajoutant JUnit4 dans `libs/` + création de `src/test/...`.

---
