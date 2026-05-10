# StyteS — projet de pub/sub réparti (M1 SAR)

**Auteurs :** Bogdan Styn, Setbel Mélissa
**Plateforme :** JDK 19 (corretto-19), BCM4Java-20032026, JUnit 4.13

---

## Contenu de l'archive

| Élément | Description |
|---|---|
| `src/` | Code source — racine du paquet `fr.sorbonne_u.cps.pubsub`. |
| `doc/` | Documentation Javadoc générée (livrée au même niveau que `src/`, conformément au CDC). |
| `deployment/` | Configuration XML + scripts shell pour les démos distribuées (voir §3). |
| `run-all-demos.sh` | Harnais de régression : compile, lance JUnit puis les 4 démos centralisées sous `-ea`. |
| `StyteS.iml`, `.classpath`, `.project` | Descripteurs de projet IntelliJ et Eclipse — l'archive s'ouvre directement dans l'un ou l'autre. |
| `README.md` | Ce fichier. |

> Les bibliothèques externes (BCM4Java, commons-math3, javassist, jing, JUnit, Hamcrest) **ne sont pas incluses** dans l'archive — voir §1 ci-dessous.

---

## 1. Bibliothèques requises

Le projet s'appuie sur les jars suivants :

| Jar | Usage |
|---|---|
| `BCM4Java-20032026.jar` | Framework de composants (obligatoire). |
| `commons-math3-3.6.1.jar` | Utilisée par BCM4Java. |
| `javassist.jar` | Utilisée par BCM4Java. |
| `jing.jar` | Utilisée par BCM4Java. |
| `junit-4.13.2.jar` | Tests unitaires (JUnit 4 — pas JUnit 5). |
| `hamcrest-core-1.3.jar` | Dépendance JUnit 4. |

**Pour compiler et exécuter, créer un dossier `libs/` à la racine et y déposer ces jars** (ou bien configurer le projet sous IntelliJ / Eclipse pour qu'il les trouve), puis :

```bash
find src -name '*.java' > sources.txt
javac -d out -cp "libs/*" @sources.txt
```

Pour les **démos distribuées** (§3), copier également ces mêmes jars dans `deployment/jars/` (les scripts `start-*` font `-cp 'jars/*'`).

---

## 2. Tests + démos centralisées (un seul script)

```bash
bash run-all-demos.sh
```

Sortie attendue :

```
JUnit:           rc=0  (102 + 2 assertions)
Central demos:   4/4 passed
GREEN
```

Les logs détaillés sont dans `/tmp/stytes-junit.log`, `/tmp/stytes-junit-mt.log` et `/tmp/stytes-demos/`.

### Lancer une démo centralisée individuellement

```bash
java -ea -cp "out:libs/*" fr.sorbonne_u.cps.pubsub.demo.<NomDeLaDemo>
```

| Démo | Ce qu'elle teste |
|---|---|
| `DemoSeparatedPlugin` | Pipeline pub/sub *isolé* (sans météo) avec les trois plugins clients distincts. |
| `DemoMidSemComplexTimedScenario` | Étape 3 complète : canaux FREE + privilégiés, regex `authorisedUsers`, dépassement de quota, **toutes** les familles de filtres (Property / Properties / ComparableValue / Equals / MultiValues / Before/After/BetweenTime). |
| `DemoMeteoTimedTestTool` | Application météo (CDC §5) : `WeatherStation` publie alertes et mesures-vent ; `WeatherOffice` souscrit aux alertes par niveau ; `WindTurbine` souscrit par région via `DistanceWindFilter`. |
| `DemoAudit2` | Scénario de l'**audit 2** (CDC §4.4) : 5 clients `FullClient` (A–E) qui publient et souscrivent **simultanément** (preuve de parallélisme), horloge en temps réel sur une fenêtre de 45 s. |

### Lancer un test JUnit individuel

```bash
java -ea -cp "out:libs/*" org.junit.runner.JUnitCore \
    fr.sorbonne_u.cps.pubsub.tests.MessageFilterTest
```

Les classes de test sont dans `src/fr/sorbonne_u/cps/pubsub/tests/` :

- **Messages et filtres** — `MessageTest`, `MessageFilterTest`, `PropertyFilterTest`, `ComparableValueFilterTest`
- **Application météo** — `Position2DTest`, `MeteoMessageFactoryTest`
- **Composant Broker** — `BrokerRegistrationTest`, `BrokerMultithreadingTest` (preuve de parallélisme par observation des thread-IDs)
- **Bavardage** — `GossipMessageTest`, `GossipMessageVisitorTest`
- **Stubs** — `StubReceiverComponent`, `StubReceivingInboundPort` (réutilisés par les tests Broker)

---

## 3. Démos distribuées

Trois démos étendent `AbstractDistributedCVM` et nécessitent `GlobalRegistry` + `DCVMCyclicBarrier` :

| Démo | Topologie | Fichier de configuration |
|---|---|---|
| `DemoTimedDistributed` | 2 brokers, gossip simple | `deployment/config/config2.xml` |
| `Demo3JVMs` | 3 brokers en chaîne `B1 ↔ B2 ↔ B3`, propagation gossip d'autorisation | `deployment/config/config3.xml` |
| `DemoFinal` | 4 brokers, scénario gossip exhaustif | `deployment/config/config2.xml` |

Lancement (exemple `Demo3JVMs` — chaque commande dans une fenêtre dédiée) :

```bash
cd deployment
bash start-gregistry              # registre RMI global
bash start-cyclicbarrier          # barrière de synchro inter-JVM
bash start-dcvm broker1-jvm
bash start-dcvm broker2-jvm
bash start-dcvm broker3-jvm
```

Ou en local sans les scripts (par exemple si vous ne voulez pas du `SecurityManager` déprécié sous JDK 19) :

```bash
java -cp "out:libs/*" fr.sorbonne_u.cps.pubsub.demo.Demo3JVMs broker1-jvm deployment/config/config3.xml
java -cp "out:libs/*" fr.sorbonne_u.cps.pubsub.demo.Demo3JVMs broker2-jvm deployment/config/config3.xml
java -cp "out:libs/*" fr.sorbonne_u.cps.pubsub.demo.Demo3JVMs broker3-jvm deployment/config/config3.xml
```

---

## 4. Régénérer la Javadoc (déjà incluse dans `doc/`)

```bash
rm -rf doc && mkdir doc
javadoc -quiet -d doc -cp "libs/*" -sourcepath src \
    -subpackages fr.sorbonne_u.cps.pubsub @sources.txt
```

---

## 5. Architecture en deux mots

- **Composants client par rôle** (`SubscriberClient`, `PublisherClient`, `PrivilegedClient`, `FullClient`) — chaque rôle est un assemblage de **plugins BCM4Java** (`ClientRegistrationPlugin`, `ClientSubscriptionPlugin`, `ClientPublicationPlugin`, `ClientPrivilegedPlugin`).
- **Broker** (`base/components/Broker.java`) — quatre `ExecutorService` internes :
  - `ES_RECEPTION` → réception des publications, détache l'appelant ;
  - `ES_PROPAGATION` → matching des filtres, élection des destinataires ;
  - `ES_DELIVERY` → push des messages aux abonnés ;
  - `ES_GOSSIP` → propagation des modifications d'état aux brokers fédérés.
  L'état (canaux, abonnements, regex `authorisedUsers`, quotas) est protégé par un `ReentrantReadWriteLock` ; le verrou n'est **jamais** tenu pendant un appel distant.
- **Gossip** (`gossip/` + `BrokerGossipHandler`) — visiteur typé sur `GossipMessageI` ; idempotence par `Map.put` ; anti-écho par `EmitterAwareGossipMessageI.getEmitterId()`.
- **Application météo** (`application/meteo/`) — `WeatherStation`, `WeatherOffice`, `WindTurbine` héritent du bon rôle et utilisent `MeteoAlertMessageFactory` / `WindMessageFactory` (constantes nommées, pas de littéraux disséminés). Encapsulation 2D : `Position2D::distanceTo`, `CircularRegion::contains`.
