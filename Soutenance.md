# StyteS — Notes pour la soutenance finale

> Auteurs : **Bogdan Styn**, **Setbel Mélissa**
> Branche soutenue : `final-iteration-plugin-roles`
> Plateforme : JDK 19 (corretto-19), BCM4Java-20032026, JUnit 4.13

---

## 1. Démarrage rapide

```bash
# Tout-en-un : compile + JUnit + 4 démos centralisées sous -ea
bash run-all-demos.sh
```

Sortie attendue : `JUnit rc=0`, `Central demos: 4/4 passed`, `GREEN`.

> Le script écrit les classes dans `/tmp/stytes-out` (Desktop est synchronisé
> iCloud ; les `.class` y sont parfois renommés "fichier 2.class" en cours
> d'exécution). Logs dans `/tmp/stytes-junit.log`, `/tmp/stytes-junit-mt.log`,
> `/tmp/stytes-demos/`.

### Compilation manuelle

```bash
find src -name '*.java' > sources.txt
javac -d out -cp "libs/*" @sources.txt
```

### Lancer une démo individuelle

```bash
java -ea -cp "out:libs/*" fr.sorbonne_u.cps.pubsub.demo.<NomDeLaDemo>
```

### Lancer un test JUnit individuel

```bash
java -ea -cp "out:libs/*" org.junit.runner.JUnitCore \
    fr.sorbonne_u.cps.pubsub.tests.MessageFilterTest
```

### Régénérer la Javadoc (déjà incluse dans `doc/`)

```bash
rm -rf doc && mkdir doc
javadoc -quiet -d doc -cp "libs/*" -sourcepath src \
    -subpackages fr.sorbonne_u.cps.pubsub @sources.txt
```

---

## 2. Plan de présentation (30 min)

| Temps  | Sujet                                                                  | Support                                              |
|--------|------------------------------------------------------------------------|------------------------------------------------------|
| 0–3    | Architecture en deux mots : pub/sub broker + clients par rôle, gossip | Schéma — `docs/PIPELINE.md` + `docs/GOSSIP.md`       |
| 3–8    | Pipeline du courtier (étape 3, le pilier)                              | `docs/PIPELINE.md`, `Broker.init()`                  |
| 8–13   | Protocole de bavardage (étape 4)                                       | `docs/GOSSIP.md`, `BrokerGossipHandler`              |
| 13–20  | Démos d'intégration (1 centralisée + Demo3JVMs en mention)             | Section 4 ci-dessous                                 |
| 20–25  | Tests unitaires JUnit                                                  | Section 5 ci-dessous                                 |
| 25–30  | Q/R — corrections vs soutenance mi-semestre                            | Section 7 ci-dessous                                 |

---

## 3. Architecture en une page

```
┌──────────────────────── Composants client (par rôle) ─────────────────────────┐
│  SubscriberClient   ────► reg + sub plugins                                   │
│  PublisherClient    ────► reg + pub plugins                                   │
│  PrivilegedClient   ────► reg + pub + priv plugins                            │
│  FullClient         ────► reg + sub + pub + priv plugins                      │
│  WeatherStation, WeatherOffice, WindTurbine — clients métier (héritent)       │
└────────────────────────────────────────────────────────────────────────────────┘
                            ▲                        ▲
        Inbound CI (broker) │                        │ Outbound CI (client)
     RegistrationCI         │                        │  ReceivingCI (callback)
     PublishingCI           │                        │
     PrivilegedClientCI     │                        │
                            │                        │
┌──────────────────────────────── Broker ─────────────────────────────────────┐
│  ┌──────────────┐  ┌──────────────────┐  ┌──────────────────┐  ┌─────────┐ │
│  │  ES_RECEPTION│─►│ ES_PROPAGATION   │─►│ ES_DELIVERY      │  │ES_GOSSIP│ │
│  │  (publish→   │  │ (filtres + match │  │ (callback push   │  │         │ │
│  │   tampons)   │  │  + gossip out)   │  │  vers ReceivingCI│  │         │ │
│  └──────────────┘  └──────────────────┘  └──────────────────┘  └─────────┘ │
│                                                                             │
│  État protégé par `ReentrantReadWriteLock stateLock`                        │
│   - jamais tenu pendant un appel distant (cf. commentaire `Broker.java`)    │
└─────────────────────────────────────────────────────────────────────────────┘
                            ▲ gossip CI (federation)
                            │
                       ┌────┴────────┐
                       │ Autres      │   ↔ ↔ ↔   protocole de bavardage
                       │ brokers     │           (CDC §3.7)
                       └─────────────┘
```

**Plugins BCM4Java (un par capacité client)** dans `src/.../plugins/` :

| Plugin                       | Port entrant     | Ports sortants            | Contrats |
|------------------------------|------------------|---------------------------|----------|
| `ClientRegistrationPlugin`   | `ReceivingCI`    | `RegistrationCI`          | register / unregister / modifyServiceClass |
| `ClientSubscriptionPlugin`   | (réutilise reg)  | (utilise reg pour sub)    | subscribe / unsubscribe / modifyFilter / `getNextMessage` (asynchrone, `CompletableFuture`) |
| `ClientPublicationPlugin`    | —                | `PublishingCI`            | publish / asyncPublishAndNotify |
| `ClientPrivilegedPlugin`     | —                | `PrivilegedClientCI`      | createChannel / destroyChannel / modifyAuthorisedUsers |

---

## 4. Démos centralisées (`run-all-demos.sh` — 4/4)

Chaque démo étend `AbstractCVM`, lance un `ClocksServer`, construit un
`TestScenario` (CDC Annexe B) et démarre via `startStandardLifeCycle(...)`.

| Démo                                | Ce qu'elle teste |
|-------------------------------------|------------------|
| **`DemoSeparatedPlugin`**           | Pipeline pub/sub *isolé* (sans météo) : 3 plugins clients distincts, scénario `subscribe → publish → reception`, vérifie que les 3 ES de `Broker` se relaient. |
| **`DemoMidSemComplexTimedScenario`**| Étape 3 complète : canaux FREE + privilégiés, regex `authorisedUsers`, dépassement de **quota** → `ChannelQuotaExceededException`, **toutes** les familles de filtres (Property / Properties / ComparableValue / Equals / MultiValues / Before/After/BetweenTime). |
| **`DemoMeteoTimedTestTool`**        | Application météo (CDC §5) : `WeatherStation` publie alertes/mesures-vent, `WeatherOffice` souscrit aux alertes par niveau, `WindTurbine` souscrit par région via `DistanceWindFilter` + `CircularRegion::in` (encapsulés dans `Position2D`). Vérifie `MeteoAlertMessageFactory` / `WindMessageFactory` (constantes — pas de littéraux disséminés). |
| **`DemoAudit2`**                    | Scénario d'**audit 2** (CDC §4.4) : 5 clients (A–E) `FullClient` qui publient et souscrivent **simultanément** (preuve de parallélisme), horloge en temps réel (`accélération = 1.0`, fenêtre 45 s). |

### Démos distribuées (non incluses dans la régression — sortie nominale)

Elles nécessitent `GlobalRegistry` + `DCVMCyclicBarrier` et historiquement
`SecurityManager` (déprécié sous JDK 19). À montrer dans 3/4 fenêtres terminal :

| Démo                  | Topologie                               | Config                |
|-----------------------|-----------------------------------------|-----------------------|
| `DemoTimedDistributed`| 2 JVM, 2 brokers, gossip simple         | `deployment/config/config2.xml` (4 JVM en réalité — voir `DemoFinal`) |
| **`Demo3JVMs`**       | 3 JVM, B1 ↔ B2 ↔ B3, démontre la propagation gossip d'`authorisedUsers` à travers la chaîne | `deployment/config/config3.xml` |
| `DemoFinal`           | 4 JVM, 4 brokers, scénario gossip exhaustif | `deployment/config/config2.xml` |

Lancement (ex. Demo3JVMs) — chaque commande dans une fenêtre dédiée :

```bash
cd deployment
bash start-gregistry              # registre RMI global
bash start-cyclicbarrier          # synchro inter-JVM
bash start-dcvm broker1-jvm
bash start-dcvm broker2-jvm
bash start-dcvm broker3-jvm
```

Ou en local sans les scripts :

```bash
java -cp "out:libs/*" fr.sorbonne_u.cps.pubsub.demo.Demo3JVMs broker1-jvm deployment/config/config3.xml
java -cp "out:libs/*" fr.sorbonne_u.cps.pubsub.demo.Demo3JVMs broker2-jvm deployment/config/config3.xml
java -cp "out:libs/*" fr.sorbonne_u.cps.pubsub.demo.Demo3JVMs broker3-jvm deployment/config/config3.xml
```

---

## 5. Tests unitaires JUnit (`src/.../tests/`)

| Test                                | Cible                                               |
|-------------------------------------|-----------------------------------------------------|
| `MessageTest`                       | `Message` — payload, propriétés, vue immuable, `equals` / `hashCode`. |
| `MessageFilterTest`                 | `MessageFilter` — combinaison `PropertyFilter` × `PropertiesFilter` × `TimeFilter`, court-circuit, validation des `null`. |
| `PropertyFilterTest`                | `PropertyFilter::match` — short-circuit booléen (correctif soutenance). |
| `ComparableValueFilterTest`         | `ComparableValueFilter` — chaque opérateur `EQ/NE/LT/LE/GT/GE` ; le `default` du switch lève `IllegalStateException` (correctif soutenance). |
| `Position2DTest`                    | `Position2D::distanceTo` + `CircularRegion::contains` — encapsulation rétablie (correctif soutenance). |
| `MeteoMessageFactoryTest`           | Vérifie que `WindMessageFactory` / `MeteoAlertMessageFactory` produisent les bons noms de propriétés (constantes, pas de chaînes disséminées). |
| `BrokerRegistrationTest`            | Composant `Broker` réel : `register / unregister / registered`, `UnknownClientException` levée correctement. |
| `BrokerMultithreadingTest`          | **Preuve de parallélisme** : 5 publications simultanées, observe que les 3 ES (`reception / propagation / delivery`) tournent sur des threads distincts (lancée dans une JVM isolée à cause d'une collision d'URI `DynamicComponentCreator`). |
| `GossipMessageTest`                 | Sérialisabilité + `serialVersionUID` des messages gossip. |
| `GossipMessageVisitorTest`          | Dispatch du visiteur (Register / Unregister / Publish / CreateChannel / DestroyChannel / ModifyAuth / ModifyServiceClass). |
| `StubReceiverComponent` / `StubReceivingInboundPort` | Stubs réutilisés par `BrokerRegistrationTest` et `BrokerMultithreadingTest`. |

Total : **102 + 2 assertions JUnit** (`102` dans la JVM principale, `2` dans la JVM
isolée pour `BrokerMultithreadingTest`).

---

## 6. Documents de conception (déjà rédigés)

- **`docs/PIPELINE.md`** — Pilier 2 : pourquoi 4 ES dans le broker, comment
  `runTask(esIndex, lambda)` enchaîne reception → propagation → delivery, pourquoi
  `stateLock` n'est jamais tenu pendant un appel distant.
- **`docs/GOSSIP.md`** — Pilier 3 : protocole de bavardage, double-pipeline
  (`gossipReceiver` + `gossipSender`), idempotence, propagation transitive,
  visiteur typé.
- **`docs/MUTEX.md`** — Justification du choix `ReentrantReadWriteLock` vs
  monitor classique : lectures fréquentes (`match` filtres), écritures rares
  (`subscribe` / `createChannel`).

---

## 7. Questions probables du jury — réponses préparées

### Q1 — *« Vous avez bien parallélisé le broker ? »*

Oui. Trois `ExecutorService` nommés (`ES_RECEPTION_URI`, `ES_PROPAGATION_URI`,
`ES_DELIVERY_URI`) + un quatrième pour le gossip (`ES_GOSSIP_URI`), créés via
`createNewExecutorService(uri, n, false)` dans `Broker.init(...)`. Chaque
`publish` côté CI lance immédiatement une `runTask(ES_RECEPTION, …)` qui finit
par poster sur `ES_PROPAGATION`, qui à son tour poste sur `ES_DELIVERY` pour
chaque abonné concerné. Le client appelant rend la main immédiatement
(asynchrone, conforme CDC §4.2). La preuve est dans `BrokerMultithreadingTest`
(observation directe des threads-id) et visuellement dans `DemoAudit2` (5
clients publient simultanément, traces entrelacées).

### Q2 — *« Pourquoi des plugins BCM4Java côté client ? »*

Pour découpler les **rôles** (sub / pub / priv / reg) du composant. Chaque
plugin :
1. **possède** ses propres ports (`ClientRegistrationPlugin` possède le port
   entrant `ReceivingCI`, partagé via `getReceptionPortURI()`) ;
2. déclare ses interfaces *programmatiquement* dans `installOn(...)` —
   `addOfferedInterface` / `addRequiredInterface` — ce qui rend les
   composants `SubscriberClient` etc. **sans annotations** `@OfferedInterfaces /
   @RequiredInterfaces`, exactement comme l'exemple canonique BCM
   `asynccall/example/Client`.
3. Permet aux clients métier (`WindTurbine`, `WeatherStation`,
   `WeatherOffice`) de **hériter** du bon rôle (resp. `FullClient`,
   `PublisherClient`, `SubscriberClient`) sans réinventer la mécanique.

### Q3 — *« Comment garantissez-vous la thread-safety du broker ? »*

Toute la table d'état (canaux, abonnements, regex `authorisedUsers`, quotas
consommés) est protégée par un unique `ReentrantReadWriteLock stateLock` :
- lectures (matching de filtres pour `propagate(...)`) prennent le
  *read lock* ;
- écritures (`subscribe`, `unsubscribe`, `createChannel`, `destroyChannel`,
  `register`) prennent le *write lock*.
- **Le verrou n'est jamais tenu pendant un appel sortant**
  (cf. commentaire en tête de `Broker.java`) — on copie l'état nécessaire,
  on libère, puis on déclenche la livraison via `runTask(ES_DELIVERY, …)`.
  Cela évite tout risque d'interblocage croisé broker↔client.

### Q4 — *« Et la correction des fautes du mid-term ? »*

| Reproche soutenance mi-semestre                                                        | Correction |
|----------------------------------------------------------------------------------------|------------|
| `MessageFilter::match` checkait `null` sur chaque élément des tableaux                | Validation au constructeur (rejet immédiat) → `match` n'a plus à tester `null`. |
| `acceptAll` complexe                                                                   | Remplacé par `AcceptAllMessageFilter` — `match` retourne juste `true`. |
| `match` réindexait avec `indexProperties`                                              | Utilise directement `Message::getProperties()` (vue immuable existante). |
| `PropertyFilter::match` avec `return` au milieu                                        | Rendu *single-exit* : `return name.equals(...) && valueFilter.match(...)`. |
| `ComparableValueFilter` switch `default → false`                                       | `default` lève `IllegalStateException("operator inattendu")`. |
| `DistanceWindFilter::distance` accédait aux coords via getters                         | Méthode déplacée dans `Position2D::distanceTo(other)`. |
| `CircularRegion::in` brisait l'encapsulation                                           | Devenu `CircularRegion::contains(Position2D)` qui appelle `Position2D::distanceTo`. |
| Pas de classes pour les messages de l'application météo                                | Créé `MeteoAlertMessageFactory` + `WindMessageFactory` + `MeteoProperties` (constantes nommées, plus aucune chaîne magique). |
| Quotas de canaux constants                                                             | Constructeur du `Broker` paramétré : `nbFreeChannels`, `standardQuota`, `premiumQuota`, plus les 3 tailles d'ES. |
| Getters publics `publishingPortURI` / `privilegedPortURI`                              | Supprimés. Le port `Publishing` est interne au broker (un seul). Le port `Privileged` n'est exposé **qu'aux** clients STANDARD/PREMIUM via la valeur de retour de `register(...)`. |
| `register` ne distinguait pas FREE des privilégiés                                     | `register` retourne `Optional<String>` : présent et = URI du port `PrivilegedClientCI` pour STANDARD/PREMIUM, vide pour FREE. |
| `registered` ne levait pas `UnknownClientException`                                    | Levée systématiquement quand le client n'apparaît dans aucune classe. |
| Ports/connecteurs duplications de `try {…} catch (Exception → RemoteException)`        | Conservé (exigé par BCM CI), mais en relevant d'abord chaque exception métier déclarée — pas de double-wrapping. |
| Code mort, `instanceof`, réflexion (`PortCleanupUtil`)                                 | Supprimés (cf. commits `fed1104`, `52c4e63`). |
| `serialVersionUID` manquant sur les messages gossip                                    | Ajouté = `1L` sur `AbstractGossipMessage` + `PublishGossipMessage`. |
| Indentation hétérogène                                                                 | Passe `google-java-format --aosp` + post-traitement → tabulations partout (commit `9568d9e`). |

### Q5 — *« Pourquoi un `Optional<String>` plutôt qu'une exception ? »*

Parce que ne pas être privilégié n'est **pas une erreur** — c'est un état
prévu et légitime du contrat. Les exceptions sont réservées aux conditions
exceptionnelles (déjà enregistré, classe inconnue…). `Optional` rend la
distinction visible dans le **type** sans surcharger le mécanisme
exceptionnel.

### Q6 — *« Pourquoi 4 surcharges de constructeur sur `Broker` ? »*

Symétrie : `(reflectionURI?) × (gossipNeighbors?)`. Les démos centralisées
n'ont besoin ni de l'URI de réflexion explicite ni de voisins gossip ; les
démos distribuées ont besoin des deux. Les 4 surcharges *appellent toutes*
le constructeur le plus complet (chaining), donc une seule méthode
`init(...)` réalise le câblage (ports + ES + gossip handler).

### Q7 — *« Comment fonctionne le bavardage (gossip) ? »*

Détaillé dans `docs/GOSSIP.md`. En résumé :
1. Toute opération **propageable** (register, unregister, createChannel,
   destroyChannel, modifyAuth, modifyServiceClass, publish) est encapsulée
   en `GossipMessageI` et postée sur `ES_GOSSIP`.
2. Le `BrokerGossipHandler` applique le visiteur, met à jour l'état local,
   puis renvoie aux voisins **différents de l'émetteur** (anti-écho via
   `EmitterAwareGossipMessageI.getEmitterId()`).
3. Idempotence : appliquer deux fois `register(C1)` ne fait rien — `Map.put`
   sur même clé. Pas besoin de TTL ni de table de doublons. Topologie :
   acyclique (chaîne ou arbre) — la garantie d'arrêt vient de l'absence de
   cycle dans `config3.xml`.

### Q8 — *« Comment teste-t-on l'application météo ? »*

`DemoMeteoTimedTestTool` : `WeatherStation` publie périodiquement
`WindData` (`{lat, lon, vitesse, timestamp}`) sur `wind-channel` et des
`MeteoAlert` (`{type, level}`) sur `alert-channel`. Le `WeatherOffice`
souscrit avec un `MultiValuesFilter("level", {ROUGE, ORANGE})`. Les
`WindTurbine` souscrivent avec un `DistanceWindFilter(myPosition, radius)`
qui réutilise `Position2D::distanceTo` — donc l'encapsulation 2D est
testée *à travers* le pipeline complet du broker.

### Q9 — *« Pourquoi `TestScenario` + `ClocksServer` plutôt que des `Thread.sleep` ? »*

Parce que c'est l'outil officiel BCM4Java (Annexe B du CDC) : un
`AcceleratedClock` partagé synchronise tous les composants ; chaque
`TestStep` est une lambda associée à un *participant* + un *instant*.
Avantages :
1. Reproductibilité — le même Instant déclenche le même step indépendamment
   de la vitesse machine.
2. Vérifiable — `TestScenario` valide pré-conditions (instants entre
   `start` et `end`, participants connus).
3. Remplaçable — `accélération = 1.0` pour debug, `> 1` pour CI.

### Q10 — *« Avez-vous gardé du code "legacy" ? »*

Non. Mid-cycle nous avions un `Client` "tout-en-un" sans plugins, un
`PortCleanupUtil` à base de réflexion (`Field.setAccessible(true)`), et des
`instanceof Broker / Message / WindData` ici et là. Tout a été supprimé
(cf. commits `fed1104` et `52c4e63`). Les ports BCM se nettoient
automatiquement via `AbstractComponent.shutdown()` qui désinstalle les
plugins, qui détruisent leurs propres ports.

### Q11 — *« Le `SecurityManager` est déprécié sous JDK 19, comment fait-on ? »*

Les démos centralisées (les 4 du `run-all-demos.sh`) n'en ont pas besoin —
on lance directement `startStandardLifeCycle(...)`. Les démos distribuées
historiques (`start-dcvm`) le réclament toujours via `dcvm.policy` ; on
laisse les scripts en l'état pour rétro-compatibilité, mais on peut aussi
lancer Demo3JVMs sans les scripts (cf. section 4) — la JVM affiche un
warning de dépréciation, sans erreur.

### Q12 — *« Où est l'asynchronisme côté client ? »*

`ClientSubscriptionPlugin` expose `getNextMessage(channel) →
CompletableFuture<MessageI>` et `waitForNextMessage(channel, Duration)`.
Cela permet à un client de `subscribe` puis `await` la prochaine
publication sans bloquer un thread du pool de réception. Le hook bas
niveau `MessageDeliveryHandler` reste disponible pour les clients qui
préfèrent un *push* immédiat (c'est ce qu'utilisent `WindTurbine` et
`WeatherOffice`).

---

## 8. Structure du livrable

```
StyteS/
├── src/                        ← code source, racine `fr.sorbonne_u.cps.pubsub`
├── doc/                        ← Javadoc générée (CDC : exigée au même niveau que src)
├── libs/                       ← BCM4Java + commons-math3 + javassist + jing + JUnit/Hamcrest
├── deployment/                 ← config XML + start-* scripts pour les démos distribuées
├── docs/                       ← notes de conception (PIPELINE / GOSSIP / MUTEX)
├── run-all-demos.sh            ← harnais de régression
├── Soutenance.md               ← ce document
├── cahier-des-charges.pdf      ← référence métier
├── StyteS.iml / .classpath / .project ← descripteurs IntelliJ + Eclipse (au cas où)
└── README.md (le cas échéant)
```

---

## 9. Checklist avant la soutenance

- [ ] `bash run-all-demos.sh` → `GREEN`, 4/4 démos passent, JUnit `rc=0`.
- [ ] `javadoc -quiet` → 0 erreur (déjà inclus dans `doc/`).
- [ ] Une fenêtre terminal ouverte sur `Broker.java` lignes 79–90 (déclaration des 4 ES).
- [ ] Une fenêtre sur `BrokerMultithreadingTest.java` (preuve par le test).
- [ ] Une fenêtre sur `docs/PIPELINE.md` (schéma du pipeline).
- [ ] Une fenêtre sur `docs/GOSSIP.md` (protocole de bavardage).
- [ ] Une démo centralisée prête à lancer en direct (`DemoAudit2` ou
      `DemoMidSemComplexTimedScenario`).
- [ ] La démo distribuée `Demo3JVMs` prête (3 fenêtres terminal +
      `start-gregistry` + `start-cyclicbarrier` lancés au préalable).
