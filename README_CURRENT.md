# StyteS — README_CURRENT

Ce README résume **l’état courant** du projet au regard du **cahier des charges CPS 2026** (*cahier-des-charges.pdf*), et indique :

1. ce qui est **implémenté** vs **à faire** vis-à-vis des exigences du CDC ;
2. ce qui est implémenté/à faire pour **Demo 1** et **Demo 2** (*3 parties au total* avec la partie CDC) ;
3. une **nouvelle démo** utilisant l’outil de scénario de test temporisé BCM4Java (CDC *Annexe B* : `TestScenario`).

---

## 0) Compilation

### Compilation “application” (sans les tests JUnit)

Les tests sous `src/fr/.../tests` utilisent JUnit, qui n’est pas dans `libs/`.

```bash
javac -cp libs/BCM4Java-03022026.jar $(find src -name '*.java' | grep -v '/tests/')
```

---

## 1) État d’implémentation vs Cahier des charges (CDC)

### ✅ Implémenté

#### CDC §3.1/§3.2 — Modèle de messages + filtres
- **Messages** (`MessageI`, propriétés, timestamp) : `src/fr/sorbonne_u/cps/pubsub/messages/Message.java`
- **Filtres** (`MessageFilterI`, filtres valeur/temps/propriétés) :
  - `src/fr/sorbonne_u/cps/pubsub/messages/MessageFilter.java`
  - `src/fr/sorbonne_u/cps/pubsub/messages/filters/*`

#### CDC §3.3 — Courtier (socle pub/sub)
- **Canaux FREE** créés à l’initialisation : `Broker.NB_FREE_CHANNELS` + `channel0..`.
- **Enregistrement** des clients (FREE/STANDARD/PREMIUM) : `Broker.registered/register/modifyServiceClass/unregister`.
- **Abonnements** + filtres par abonné : `Broker.subscribe/unsubscribe/modifyFilter`.
- **Publication** + livraison filtrée : `Broker.publish`.
- **Un outbound port de réception par client** (livraison broker → client) : `Broker.receptionPortsOUT`.

Fichier principal : `src/fr/sorbonne_u/cps/pubsub/base/components/Broker.java`.

#### CDC §3.4 — Application météo/éolienne (scénario simple)
- Composants applicatifs :
  - `src/fr/sorbonne_u/cps/pubsub/application/meteo/WindTurbine.java`
  - `src/fr/sorbonne_u/cps/pubsub/application/meteo/WeatherStation.java`
  - `src/fr/sorbonne_u/cps/pubsub/application/meteo/WeatherOffice.java`
- Vent proche accepté / vent lointain rejeté via filtre de souscription (`DistanceWindFilter`).
- Alertes ORANGE/GREEN reçues par l’éolienne → mode sécurité / retour normal.

#### CDC §3.2 — Clients privilégiés + canaux privilégiés (étape 2, partiel mais fonctionnel)
- **Création** de canaux privilégiés par STANDARD/PREMIUM, refusée pour FREE.
- **Quota** STANDARD/PREMIUM.
- **Regex authorisedUsers** (contrôle d’accès sur subscribe + publish).

Fichiers :
- `src/fr/sorbonne_u/cps/pubsub/base/components/Broker.java` (gestion des canaux privilégiés)
- `src/fr/sorbonne_u/cps/pubsub/base/components/Client.java` (API client + port sortant privileged)
- Ports/connecteurs privileged :
  - `src/fr/sorbonne_u/cps/pubsub/base/ports/BrokerPrivilegedInboundPort.java`
  - `src/fr/sorbonne_u/cps/pubsub/base/ports/ClientPrivilegedOutboundPort.java`
  - `src/fr/sorbonne_u/cps/pubsub/base/connectors/ClientBrokerPrivilegedConnector.java`

### ✅ Implémenté (outil de test temporisé, CDC Annexe B)

Une démo additionnelle (voir §4) montre l’utilisation des classes BCM4Java :
- `fr.sorbonne_u.utils.aclocks.ClocksServer`
- `fr.sorbonne_u.components.utils.tests.TestScenario` / `TestStep`

### ❌ Non implémenté / à implémenter ensuite

#### CDC §3.5 — Greffons
Les interfaces de greffons (Java) existent dans `src/fr/sorbonne_u/cps/pubsub/plugins/*`, mais **les greffons eux-mêmes ne sont pas implantés**.

#### CDC §3.5.3 — Réception avancée des messages
- `waitForNextMessage`, `getNextMessage`, discipline FIFO entre attentes, interaction avec `receive(...)` : **non implémenté**.

#### CDC §3.6 / Audit 2 (§4.4) — Asynchronisme + pools de threads + concurrence
- Publication/réception **asynchrones** via `runTask`.
- Pools distincts (réception/propagation/livraison).
- Sections critiques et gestion de concurrence.
- Scénarios temporisés d’intégration couvrant l’asynchronisme et la concurrence.

---

## 2) Demo 1 — CDC §3.4 / Audit 1 (§4.2)

### Objectif CDC
Audit 1 demande un **scénario simple sans outil temporisé** avec :
- 1 broker
- ≥1 éolienne
- ≥2 stations météo
- ≥1 bureau météo
- au moins un message filtré et reçu par l’éolienne

### Implémentation (✅)
Démo : `src/fr/sorbonne_u/cps/pubsub/demo/DemoMeteoAudit1.java`

Exécution :
```bash
java -cp libs/BCM4Java-03022026.jar:src fr.sorbonne_u.cps.pubsub.demo.DemoMeteoAudit1
```

Statut : ✅ fonctionne (vent proche reçu, vent loin ignoré, ORANGE/GREEN).

---

## 3) Demo 2 — Étape 2 (privileged) : canaux + contrôle d’accès + quotas

### Objectif
Illustrer :
- création d’un canal privilégié par un client STANDARD
- contrôle d’accès via regex `authorisedUsers`
- refus d’un client non autorisé
- dépassement de quota STANDARD

### Implémentation (✅)
Démo : `src/fr/sorbonne_u/cps/pubsub/demo/DemoAudit2Privileged.java`

Exécution :
```bash
java -cp libs/BCM4Java-03022026.jar:src fr.sorbonne_u.cps.pubsub.demo.DemoAudit2Privileged
```

Statut : ✅ fonctionne (subscribe refusé pour l’inautorisé, quota dépassé déclenche l’exception).

---

## 4) Démo additionnelle — Outil de test temporisé (CDC Annexe B)

### Objectif
Montrer l’usage de l’outil BCM4Java de scénario temporisé :
- création d’un `ClocksServer`
- création d’un `TestScenario` + `TestStep`
- exécution des étapes au bon instant via horloge accélérée

### Implémentation (✅)
Démo : `src/fr/sorbonne_u/cps/pubsub/demo/DemoMeteoTimedTestTool.java`

Elle rejoue le scénario météo, mais **sans `Thread.sleep(...)`** :
- souscription de l’éolienne à t+5s (virtuel)
- publication vent proche à t+15s
- publication vent loin à t+20s
- publication ORANGE à t+30s
- publication GREEN à t+40s

Exécution :
```bash
java -cp libs/BCM4Java-03022026.jar:src fr.sorbonne_u.cps.pubsub.demo.DemoMeteoTimedTestTool
```

### État actuel
Le code **compile** et la démo démarre correctement. Sur certaines exécutions, l’initialisation BCM4Java peut afficher uniquement `starting...` dans les premières secondes, car l’horloge est créée avec un délai de démarrage et les étapes sont planifiées ensuite.
De plus, le toggle tracing/logging via `AbstractCVM.toggleTracing` peut échouer (NPE interne BCM) selon les versions/configuration BCM4Java ; cela n’empêche pas l’exécution du scénario (un message est affiché sur stderr).

### Note technique
Pour que le composant exécute ses étapes, `WindTurbine` accepte désormais un paramètre optionnel `TestScenario` et exécute `executeTestScenario(...)` dans `execute()` (même pattern que l’exemple `data_store/clients/DataStoreClient.java`).

---

## 5) Notes sur les tests unitaires

Deux classes existent dans `src/fr/sorbonne_u/cps/pubsub/tests`:
- `MessageTest.java`
- `MessageFilterTest.java`

