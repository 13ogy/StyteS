# SOUTENANCE — feuille de route 30 minutes

> Bogdan Styn — Setbel Mélissa.
> Branche : `final-iteration` (24 commits ahead de `origin/partie4`).
> À lire en complément de `docs/MUTEX.md`, `docs/PIPELINE.md`, `docs/GOSSIP.md`.

---

## 1. Run sheet

| Tranche | Durée | Contenu | Support |
|---|---|---|---|
| **0 – 3 min** | 3 min | Pitch projet : pub/sub BCM4Java, 3 piliers (mutex / pipeline / gossip), mid-term + final | slide unique avec topologie broker + 4 ES + 4 inbound ports |
| **3 – 12 min** | 9 min | **Pilier 1 — Mutex.** Carte des verrous, ordre canonique, asserts. Démo : `DemoMidSemComplexTimedScenario` | `MUTEX.md` §2 §3 §4 |
| **12 – 21 min** | 9 min | **Pilier 2 — Pipeline.** 4 ES, runTask, bulkSubmit. Démo : `DemoMeteoTimedTestTool` | `PIPELINE.md` §2 §3 §5 |
| **21 – 27 min** | 6 min | **Pilier 3 — Gossip.** Skip-echo + dédup. Démo : `Demo3JVMs` (ou tour pré-enregistré). | `GOSSIP.md` §3 §4 §5 |
| **27 – 30 min** | 3 min | Q/R | §4 ci-dessous |

Marges :
- Si `Demo3JVMs` ne se lance pas en live (RMI registry indisponible),
  basculer sur capture d'écran + lecture commentée des logs des 3 JVMs.
- Si M. Malenfant pose des questions techniques très tôt, sacrifier le
  pilier 3 (le plus court) pour rester < 30 min.

---

## 2. Commandes exactes des 3 démos

> JDK : Amazon Corretto 19. Cf. `.idea/misc.xml` et le SDK IntelliJ.
> Toutes les commandes ci-dessous se lancent depuis la racine du repo.

### 2a. Compilation préalable (une seule fois par session)

```bash
find src -name '*.java' > sources.txt
javac -d out -cp "libs/*" @sources.txt
```

### 2b. Pilier 1 — `DemoMidSemComplexTimedScenario`

Scénario avec turbines, stations météo, intrus, et upgrades de classe ;
pousse fortement les chemins `register` / `subscribe` / `publish`
concurrents.

```bash
java -ea -cp "out:libs/*" \
    fr.sorbonne_u.cps.pubsub.demo.DemoMidSemComplexTimedScenario
```

Pourquoi celle-ci pour le pilier mutex : la concurrence client
(intruder + offices STANDARD/PREMIUM + plusieurs turbines) exerce
simultanément `registrationLock`, `channelsLock` et `subscriptionsLock`.
Les `assert` activés (`-ea`) prouvent à l'examinateur que les
post-conditions tiennent.

### 2c. Pilier 2 — `DemoMeteoTimedTestTool`

Démo météo time-driven : multi-canal + filtres + livraison observable.

```bash
java -ea -cp "out:libs/*" \
    fr.sorbonne_u.cps.pubsub.demo.DemoMeteoTimedTestTool
```

Pourquoi celle-ci pour le pilier pipeline : on voit clairement les 3
phases reception → propagation → delivery via les `logMessage(...)` des
3 ES, et le snapshot `bulkSubmit` est exercé par l'office qui consomme
des messages de plusieurs stations.

### 2d. Pilier 3 — `Demo3JVMs`

Démo distribuée 3 JVMs sur localhost. Préparation
(`deployment/jars/` doit contenir les jars compilés + `libs/*`) :

```bash
# Si pas encore fait : copier le bytecode sous deployment/jars/
mkdir -p deployment/jars
cp -r out/* deployment/jars/        # ou ajuster selon le packaging
cp libs/*.jar deployment/jars/

cd deployment

# Terminal A : registre RMI
./start-gregistry

# Terminal B : barrière cyclique
./start-cyclicbarrier

# Terminaux C, D, E : un par JVM (l'argument 1 est le test number)
./start-dcvm 1 broker1-jvm
./start-dcvm 1 broker2-jvm
./start-dcvm 1 broker3-jvm
```

> Note : `start-dcvm` utilise `-Djava.security.manager` + `dcvm.policy`.
> Sous JDK 19 cela peut nécessiter un JDK alternatif ou un workaround.
> En cas de souci à la soutenance, montrer une capture pré-enregistrée
> et expliquer que la démo centralisée + les JUnit prouvent la même
> logique gossip côté broker.

### 2e. Suite JUnit (preuve « tout passe »)

```bash
java -ea -cp "out:libs/*" org.junit.runner.JUnitCore \
    fr.sorbonne_u.cps.pubsub.tests.MessageTest \
    fr.sorbonne_u.cps.pubsub.tests.MessageFilterTest \
    fr.sorbonne_u.cps.pubsub.tests.PropertyFilterTest \
    fr.sorbonne_u.cps.pubsub.tests.ComparableValueFilterTest \
    fr.sorbonne_u.cps.pubsub.tests.MeteoMessageFactoryTest \
    fr.sorbonne_u.cps.pubsub.tests.Position2DTest \
    fr.sorbonne_u.cps.pubsub.tests.BrokerRegistrationTest
```

Soit 7 classes / **86 `@Test`** au total
(`grep -rn '@Test' src/fr/sorbonne_u/cps/pubsub/tests/ | wc -l` -> 86).
À mentionner si l'examinateur demande la couverture.

---

## 3. Cheat-sheet — 3 choses à mémoriser avant d'entrer

### 3.1 — Topologie mutex (3 RRWL fair + 3 CHM)

```
registrationLock (regis, receptionPortsOUT)
channelsLock     (channels set, privilegedChannels)
subscriptionsLock(subscriptions[ch] -> {sub -> filter})
+ CHM compute :   inFlightPerChannel, createdPrivilegedChannelsCount
+ CHM dedup :     processedGossipURIs (putIfAbsent)
ORDRE : registration -> channels -> subscriptions
JAMAIS d'appel RMI sous verrou.
```

### 3.2 — Pipeline 3+1 ES

```
RMI thread  ->runTask->  ESreception
                              |
                              v  receptionStage(checks + gossip)
                          ESpropagation
                              |
                              v  snapshotTargets + deliverToTargets
                          ESdelivery (per subscriber)
                              |
                              v  filter.match() + out.receive()
                          (finally finishInFlight)

ESgossip dédié pour reception/forward des messages gossip.
```

### 3.3 — Gossip = skip-echo + dedup

```
update(msg):
    if (processedGossipURIs.putIfAbsent(uri, ts) != null) continue;  // dedup
    immediateSender = (msg instanceof EmitterAwareGossipMessageI)
                       ? msg.getEmitterURI() : null;
    apply(msg) locally;
    forwarded = msg.copyWithNewEmitterURI(self);
    for n in sendersByNeighbour.entrySet():
        if (n.getKey() == immediateSender) continue;                 // skip-echo
        runTask(esGossip, () -> n.getValue().send(forwarded));
```

---

## 4. Q/R anticipées (10 paires couvrant les piliers + bonus)

### Q1. Pourquoi 3 verrous séparés et pas un seul ?
Pour permettre à `register()` (sur `registrationLock` write) et à un
`publish()` simultané (qui ne prend que `channelsLock.read +
subscriptionsLock.read` côté snapshot) de tourner en parallèle.
Granularité = débit. Cf. `MUTEX.md` §5.

### Q2. Et l'ordre d'acquisition, comment vous le garantissez ?
À l'inspection (revue de code) et par les `assert
registrationLock.getReadHoldCount() > 0` placés dans les helpers
`*Locked` (`Broker.java:782-786`). À l'exécution sous `-ea`, toute
violation de pré-condition est un `AssertionError` immédiat.

### Q3. Pourquoi le mode équitable des RRWL ?
Pour qu'un `subscribe()` ou `destroyChannel()` (write) ne soit pas
indéfiniment doublé par un flot de `publish` (read). FIFO sur les
attentes. Cf. `MUTEX.md` §5 point 2.

### Q4. Pourquoi 4 executor services et pas 2 ?
Les phases ont des coûts asymétriques : reception est rapide et
verrouillée (limiter le pool, 2 threads), propagation iter le snapshot
(4 threads), delivery fait du RMI vers N abonnés en parallèle (8
threads), gossip est un sous-pipeline distinct pour ne pas saturer la
livraison utilisateur. Cf. `PIPELINE.md` §2.

### Q5. Que se passe-t-il quand un abonné est déconnecté pendant la livraison ?
Le `out.receive(...)` lève une `RemoteException`. Le `try/catch` autour
(`Broker.java:578-582`) la log via `logMessage(...)` et continue. Le
`finally` décrémente `remaining` puis appelle `finishInFlight` quand
toutes les tentatives sont passées. La Phase E.5 (`af3757e`) ajoute un
hook `onBrokerDisconnect` côté plugin qui pourrait être étendu pour un
cleanup actif.

### Q6. Comment vous évitez les boucles dans le gossip ?
Dédup atomique via `ConcurrentHashMap.putIfAbsent` sur l'URI unique du
message (`Broker.java:1932-1934`). C'est *atomique*, donc deux threads
qui reçoivent le même URI en même temps ne le traiteront qu'une fois.
Cf. `GOSSIP.md` §3.

### Q7. Et le skip-echo, pourquoi pas juste se reposer sur la dédup ?
La dédup empêche le **traitement** double, mais pas l'**envoi**. Sur un
maillage à 10 brokers et un fan-out plein, l'overhead réseau /
sérialisation devient quadratique sans skip-echo. La paire `(emitterURI,
sendersByNeighbour)` permet un fan-out propre. Cf. `GOSSIP.md` §4.

### Q8. Pourquoi 4 plugins côté client au lieu d'un seul gros ?
Chaque plugin couvre une responsabilité CDC : registration, publication,
subscription, privileged. `ClientRegistrationPlugin` possède le port
`ReceivingCI` partagé (les autres plugins le réutilisent via
`getReceptionPortURI()`). C'est la base de la composition propre, et
`AbstractPluginComponent` (Phase E.1, `6496997`) la formalise via une
template method `final execute() { register(...); scenarioExecute(); }`.

### Q9. Le build : pourquoi pas Maven/Gradle ?
Projet pédagogique : le module IntelliJ + `libs/*.jar` est imposé par
l'enseignant, qui distribue `BCM4Java-20032026.jar` comme jar
pré-compilé. La compilation manuelle (`javac -d out -cp "libs/*" ...`)
reproduit exactement ce que fait IntelliJ.

### Q10. Pourquoi mettre tant d'`assert` plutôt que des tests JUnit ?
Les deux : 86 tests JUnit + 37 `assert` dans `Broker.java`. Les asserts
documentent les **invariants internes** (post-mutation, lock
hold-count) qu'un test JUnit ne peut pas observer sans casser
l'encapsulation. Sous `-ea`, ils transforment toute régression
silencieuse en `AssertionError` traçable, pendant les démos comme
pendant les tests. Cf. `MUTEX.md` §7.

---

## 5. Erreurs de la soutenance 1 — table de correction

Reprend les critiques de l'examinateur sur la mid-term + les phases
d'audit ; chaque ligne pointe la commit qui l'a réglée et résume la
résolution en une ligne.

| Critique mid-term | Commit | Résolution |
|---|---|---|
| Encapsulation : champs Broker `public`, ports statiques | `fdad9ba` (C.3) | `REGISTRATION_PORT_URI` static supprimé, URIs dérivés du reflection inbound port URI. Champs `private final` partout. |
| `RegistrationClass` confondu avec un simple `String` | `4c32aba` (C+F) | `registered(rip, rc)` ajouté à `RegistrationCI` + utilisé dans `Broker.registered()` (`Broker.java:826`). |
| `registered(...)` ne respectait pas son contrat d'exception | `4c32aba` (C+F) | `registered(rip)` lève `IllegalArgumentException` si `rip` null/empty (`Broker.java:798-816`) ; `Exception` propagée verbatim. |
| Quotas hard-codés à 2 / 5 dans `Broker` | `dd97b16` (G prep) | `standardQuota`, `premiumQuota` deviennent des arguments du constructeur (`Broker.java:215-216, 277-292`), passés par chaque démo (`new Object[] { ..., 2, 5, ... }`). |
| Publish synchrone bloquait la thread RMI | `214d240` + `6d767fc` (D.3) | Tous les void-inbounds `runTask(esReception, ...)` ; logique métier hors thread RMI. |
| Vérification `channel exists` faite après acquisition de lock écriture | `11a0c14` (C.2) | `requireChannel` + `channelExistLocked` factorisés ; pré-conditions vérifiées avant tout verrou. |
| Pipeline « inline » : tout dans `publish()` | `d2c3f14` (C.5) + ES création | Découpage en `submitPublish` -> `receptionStage` -> `propagationStage` -> `deliverToTargets`, plus l'introduction des 3 ES. |
| Gossip : `set.contains` puis `set.add` sous gossipLock | `4c32aba` (F.5) | `ConcurrentHashMap.putIfAbsent` atomique, `gossipLock` supprimé. |
| Gossip : ré-écho au sender immédiat | `4c32aba` (F.2) | `sendersByNeighbour` + `getEmitterURI()` filter dans `update()`. |
| Compteurs non-thread-safe (`int + lock séparé`) | `bb1767f` (C.6) | `ConcurrentHashMap.compute` / `merge` pour `inFlightPerChannel` et `createdPrivilegedChannelsCount`. |
| Naming des ports/connecteurs non aligné sur les CI | `8f31846` + `36b2662` + `7c14748` (D.1 1-3) | Renaming uniforme `<Side><Role>InboundPort/OutboundPort/Connector`. |
| Plugins : code dupliqué entre clients | `6496997` (E.1) | `AbstractPluginComponent` template-method `final execute()` -> `scenarioExecute()`. |
| File de réception non FIFO sous concurrence | `847724c` (E.4) | `ClientSubscriptionPlugin` passe à `LinkedBlockingQueue` (fair). |
| Pas d'invariants exécutables | `26032e3` (C.8) | 37 `assert <invariant>` ajoutés dans `Broker.java`. |
| Lifecycle plugin non idempotent | `e8f50af` (G prep) | `ClientRegistrationPlugin` install/uninstall idempotent + dual-connect publishing+privileged pour STANDARD/PREMIUM. |
| Inbound/outbound exception wrapping incohérent | `d727a55` (D.5) | Convention uniforme : exceptions métier propagées verbatim, autres `Exception` wrappées en `RemoteException`. |
| Réception gossip bloquait le pool RMI | partie 4 + `6d767fc` (D.3) | `GossipReceiverInboundPort.receive` -> `runTask(esGossip, ...)`. |
| Pas de hook quand le broker tombe | `af3757e` (E.5) | `onBrokerDisconnect` ajouté à `ClientRegistrationI`. |

---

## 6. Pour finir — anti-stress

- **Si tu hésites** : ouvre `Broker.java` et pointe directement la
  ligne. Le fichier fait 2181 lignes, tout y est.
- **Si tu te trompes** : reconnais, dis « la spec C.8 fix-3 mentionne un
  notify/wait, mais le code actuel reste un Thread.sleep(10) — c'est
  pragmatique, pas optimal, ça serait la prochaine itération ». M.
  Malenfant aime les étudiants lucides sur leur code.
- **Si une démo plante** : montrer les 86 tests JUnit verts (cf. §2e)
  et lire les `assert` du `Broker.java` pour prouver les invariants.

Bon courage.
