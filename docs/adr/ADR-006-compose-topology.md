# ADR-006 — Topologie Docker Compose et séparation socle / environnements

**Date**: 2026-08-26
**Status**: Accepted
**Authors**: Kora Core Engineering — Ivan Joël Tchatchoua Bayon
**Related**: ADR-005 — Calibration des tests de performance · ADR-007 — Hexagonal avant modulaire (numérotation faisant foi, voir D5) · `CONTRIBUTING.md` §3.2 · KC-02 — Séparation stricte de la configuration · KC-03 — Socle Compose minimal (amendé, voir D4)

---

## Contexte

Un seul `docker-compose.yml` décrivait à la fois l'architecture et son exploitation
locale. Trois conséquences mesurées :

- **Six conteneurs démarraient systématiquement**, dont InfluxDB et Grafana qui ne
  servent que pendant une campagne de charge — quelques heures par mois.
- **Redis tournait sans être utilisé.** `grep` sur `build.gradle` et `src/main` ne
  remonte qu'un commentaire javadoc : aucun starter, aucun client. Le fichier
  décrivait un système qui n'existait pas.
- **Postgres était publié sur toutes les interfaces** — `0.0.0.0:5432` et `[::]:5432`,
  vérifié par `docker ps` — avec un mot de passe de développement. Sur un réseau
  partagé, la base était joignable par n'importe qui.

Par ailleurs `restart: unless-stopped` sur tous les services faisait remonter la
stack à chaque démarrage de Docker Desktop, longtemps après la fin d'une session.

## Décisions

### D1 — L'application n'est pas conteneurisée

Elle tourne sur l'hôte via `./gradlew bootRun`, en développement comme en production.

Conteneuriser un runtime sert d'abord à figer une version et ses dépendances
natives. En Java, ce travail est déjà fait : le JAR exécutable produit par
`bootJar` embarque toutes ses dépendances, et la `toolchain` Gradle fixe le
langage à Java 21 quel que soit le JDK installé. Le même bytecode s'exécute
identiquement sur le JDK de la machine et dans un JRE conteneurisé.

Le gain de parité est donc faible, alors que le coût est immédiat : perte de
DevTools et du rechargement à chaud, et un rebuild d'image à chaque itération.
Les tests, eux, ne dépendent d'aucun de ces choix — ils démarrent leur propre
PostgreSQL via Testcontainers et ignorent Compose entièrement.

**Conséquence directe** : la production ne peut pas fermer complètement le port de
la base, l'application devant l'atteindre depuis l'hôte. Voir D3.

### D2 — Le socle décrit ce qu'un service *est*, l'override comment il est *exploité*

`docker-compose.yml` ne contient ni `ports`, ni `restart`, ni `container_name`, ni
montage de chemin hôte. Test décidable :

```bash
docker compose -f docker-compose.yml config | grep -E 'ports:|restart:'
```

doit ne rien afficher. Si quelque chose sort, une décision d'environnement a fuité
dans l'architecture et tous les environnements en héritent.

C'est ce critère — et non l'existence du fichier de production — qui garantit qu'un
nouvel environnement s'ajoute sans réécriture.

Le socle ne décrit pas non plus *tous* les services : seulement ceux que chaque
environnement exécute — postgres, et rien d'autre aujourd'hui. Un service qu'un
seul environnement exécute appartient au fichier de cet environnement (D4).

### D3 — Publication sur la boucle locale uniquement

`127.0.0.1:${DB_PORT}:5432` partout, développement compris, au lieu de
`${DB_PORT}:5432`.

Le préfixe ne coûte rien et supprime l'exposition réseau. En production, il est la
seule posture compatible avec D1 : l'application, sur l'hôte, atteint la base sur
la boucle locale ; personne d'autre ne le peut. L'accès opérateur passe par un
tunnel SSH sur ce même port.

### D4 — Un fichier par environnement, un profil par groupe optionnel

*Historique : profils Compose d'abord ; un fichier par pile
(`docker-compose.tooling.yml`, `docker-compose.observability.yml`) le
2026-08-26 ; retour aux profils le 2026-08-30, KC-03 amendé en conséquence.
Ce va-et-vient fait partie de la décision : la deuxième rédaction a suivi le nom
de fichier écrit dans le ticket au lieu de suivre le principe, et c'est
exactement l'erreur que cette version corrige.*

Deux axes orthogonaux, jamais mélangés :

| Axe | Primitive | Répond à | Valeurs |
|---|---|---|---|
| environnement | **fichier** | *où est-ce que je tourne ?* | `docker-compose.yml` (socle), `.override.yml` (dev), `.prod.yml` |
| groupe optionnel | **profil** | *qu'est-ce que je veux en plus ?* | *(aucun)*, `tooling`, `observability` |

| Profil | Services | Fréquence d'usage |
|---|---|---|
| *(aucun)* | postgres, maildev | tous les jours |
| `tooling` | pgadmin | quelques minutes par semaine |
| `observability` | influxdb, grafana | quelques heures par mois |

**Pourquoi le profil et non le fichier.** Trois raisons, par poids décroissant :

1. **Le `down`.** Un groupe optionnel en fichier oblige à répéter la liste `-f`
   identique à la descente ; en oublier un laisse ces conteneurs tourner sans
   rien signaler. Le profil, lui, est lu depuis `.env` : `up`, `ps`, `logs` et
   `down` s'accordent sur le même ensemble. La version en fichiers avait dû
   documenter ce piège dans `CONTRIBUTING.md` — un design dont l'usage quotidien
   exige un avertissement est le mauvais design.
2. **Le plan de fichiers mentait.** `docker-compose.tooling.yml` se lisait comme
   un pair de `docker-compose.prod.yml` alors qu'il ne l'est pas : `prod` répond
   « où », `tooling` répond « quoi en plus ». Cinq fichiers côte à côte
   suggéraient cinq options exclusives là où il y avait deux axes de deux.
3. **Le contenu démentait le nom.** `docker-compose.tooling.yml` portait
   `restart: "no"` et une publication sur `127.0.0.1` — des décisions de
   développement dans un fichier prétendument transverse. Il n'aurait pas été
   réutilisable tel quel sur un staging, ce qui était pourtant sa raison d'être.

**Ce que le profil coûte.** La sélection passe par une variable d'environnement
qu'il faut connaître pour comprendre pourquoi `docker compose up` démarre deux
conteneurs et pas cinq. C'était l'argument de la rédaction précédente et il reste
juste ; il est payé, pas nié :

- `COMPOSE_PROFILES` est déclarée en tête de `.env.example`, avec ses deux
  valeurs et leur effet ;
- `docker compose config --profiles` les énumère ;
- nommer un service active son profil pour cette commande seule —
  `docker compose up -d influxdb grafana` ne demande aucune variable, ce dont
  `perf/*-run.sh` se sert.

Le mot « profil » désigne désormais deux choses dans ce dépôt : les profils
Compose et les profils Spring. La collision est assumée et traitée explicitement
en `CONTRIBUTING.md` §3.2.2 ; l'alternative aurait été de renommer l'axe Compose,
ce que Docker ne permet pas.

**Conséquence : la règle « un service n'est déclaré que dans le socle » tombe.**
maildev, pgadmin, influxdb et grafana sont déclarés dans
`docker-compose.override.yml`, le fichier de l'environnement qui les exécute. La
production ne charge pas ce fichier : leur absence y est *structurelle*, il n'y a
aucune définition de service à activer, avec ou sans profil.

C'est un gain de sûreté, pas une concession. Avant, maildev vivait dans le socle
et la production devait l'éteindre avec `profiles: [ "never-in-production" ]` —
un profil dont le seul rôle était d'annuler le socle. Quand un fichier
d'environnement doit annuler le socle, la frontière du socle est mal placée. Le
socle ne contient plus que postgres, et le test de D2 reste vérifié.

**Mesure.** Garder les groupes optionnels éteints fait passer le démarrage de
développement de **115 s à 12 s** — `docker compose up -d --wait`, images déjà
présentes, attente que chaque service se déclare *healthy*, 5 conteneurs contre
2. La majeure partie de la centaine de secondes économisées vient de pgAdmin,
dont gunicorn met environ une minute à répondre.

**Les profils Compose et les profils Spring restent deux axes indépendants.** Les
premiers décident quels *conteneurs* tournent, les seconds quelle *configuration
applicative* s'applique ; aucun ne lit l'autre. Rien n'impose leur cohérence, et
une incohérence ne se voit pas au démarrage : le profil Spring `perf` sans le
profil Compose `observability` démarre normalement, puis échoue à pousser ses
métriques toutes les dix secondes pendant que le test de charge produit des
résultats d'apparence normale. La correspondance exigée est documentée dans
`CONTRIBUTING.md` §3.2.2 ; la faire respecter automatiquement serait un couplage
entre l'application et son orchestrateur, que nous refusons.

### D5 — Redis retiré

*Renvoi corrigé le 2026-09-06. La rédaction initiale disait « lorsque le `OtpStore`
en aura besoin (Step 2) ». Les deux moitiés ont vieilli : `OtpStore` n'existe plus,
et « Step 2 » désignait la numérotation que ADR-007 a supprimée — l'Étape 2 du
`ROADMAP.md` est faite et ne contenait pas Redis. La décision, elle, n'a pas bougé.*

Réintroduit lorsque `ExpiringStore<OtpCode>` en aura besoin — c'est-à-dire le jour
où l'application tourne sur plus d'une instance, les codes OTP vivant aujourd'hui
dans le tas d'une seule JVM. Aucune étape du `ROADMAP.md` ne le porte : c'est une
contrainte de déploiement, pas un jalon fonctionnel, et la dater d'avance
reviendrait à la même infrastructure spéculative que D4 refuse pour le staging.

Le port a été rendu générique entre-temps, ce qui réduit la réintroduction à une
ligne : `InMemoryExpiringStore` est déclaré dans `AuthUseCaseConfiguration`, et un
adaptateur Redis prend sa place sans qu'aucun appelant ne change. `put(key, value,
ttl)` est déjà la forme de `SET key value EX ttl`.

Déclarer une infrastructure inutilisée est la même classe d'erreur qu'une valeur de
repli sur un secret : le fichier ment sur le système. Vérification aujourd'hui —
`grep -i redis build.gradle src/main` ne remonte qu'un commentaire javadoc, celui
qui décrit précisément le remplacement d'une ligne ci-dessus.

### D6 — Healthchecks sur tout service dont un autre dépend

`depends_on: condition: service_healthy` est inapplicable sans healthcheck. Chaque
commande a été vérifiée dans l'image réelle, pas supposée :

| Service | Sonde | Note |
|---|---|---|
| postgres | `pg_isready` | `$$` échappe la substitution Compose pour que le shell du conteneur résolve |
| maildev | *aucune déclarée* | l'image embarque déjà son `HEALTHCHECK` sur `/healthz` |
| pgadmin | `wget /misc/ping` | `curl` absent de l'image ; `start_period: 90s`, gunicorn met ~60 s |
| influxdb | `wget /ping` | `curl` absent |
| grafana | `wget /api/health` | |

`depends_on` ne couvre que le démarrage : si Postgres redémarre à 3 h, l'application
ne le « ré-attend » pas, et Kubernetes ignore la directive. C'est un confort de
développement, pas une garantie de disponibilité.

### D7 — Spring gère le cycle de vie Compose, en développement seulement (révisée)

*Première rédaction : `spring.docker.compose.enabled: false` partout. Révisée le
2026-08-30 : activée dans le profil `dev` uniquement, sous trois conditions
vérifiées. Motif : pouvoir lancer l'application depuis l'IDE sans passer d'abord
par un terminal.*

Le refus initial reposait sur trois défauts réels. Aucun n'a disparu ; chacun est
neutralisé par une contre-mesure, et chaque contre-mesure a été vérifiée plutôt
que supposée.

**1. L'intégration ne résout qu'un seul fichier.** Elle invoque
`docker compose -f docker-compose.yml`, et nommer un fichier avec `-f` supprime le
chargement automatique de `docker-compose.override.yml`. Les ports y vivant,
l'application échouait au démarrage :

```
IllegalStateException: No host port mapping found for container port 5432
    at DefaultConnectionPorts.get
    at PostgresJdbcDockerComposeConnectionDetailsFactory
```

*Contre-mesure* : `spring.docker.compose.file` est déclarée
`java.util.List<java.io.File>` dans le `spring-configuration-metadata.json` de
la 4.0.3 — les deux fichiers sont nommés. Le log le confirme au pluriel :
« Using Docker Compose file**s** ..., ... », et les conteneurs portent alors leurs
vrais noms (`kora-postgres`) avec `5432/tcp -> 127.0.0.1:5432`.

**2. Ses `ConnectionDetails` priment sur `spring.datasource.*`.** Elles sont
construites à partir du bloc `environment:` du conteneur, c'est-à-dire du
superutilisateur.

*Contre-mesure* : le label `org.springframework.boot.ignore` sur le service
postgres dans `docker-compose.override.yml`. `DockerComposeLifecycleManager.isIgnored()`
teste `labels().containsKey(...)` — la présence de la clé suffit, la valeur n'est
pas lue (vérifié au bytecode de la 4.0.3). Le service est exclu du
`DockerComposeServicesReadyEvent`, donc aucune `ConnectionDetails` n'est produite,
pendant que `docker compose up` continue de le démarrer.

Vérification par falsification, avec
`--spring.datasource.url=jdbc:postgresql://localhost:59999/kora-db` :

| Label | Résultat observé | Conclusion |
|---|---|---|
| présent | `Connection to localhost:59999 refused` | la propriété gagne |
| retiré | `Started KoraCoreApplication`, health UP | l'URL est ignorée — le détournement est réel |

Le second cas est le plus instructif : sans le label, l'application démarre en
bonne santé **en ignorant silencieusement sa propre configuration de datasource**.
Le jour où KC-05 crée `kora_migration` et `kora_app`, elle se connecterait en
superutilisateur sans qu'aucune ligne de log ne le signale.

**3. Elle introduirait un second sélecteur de profils.** *Contre-mesure* : ne pas
renseigner `spring.docker.compose.profiles.active`. Le sous-processus
`docker compose` hérite du répertoire de travail et lit le même `.env` ; vérifié
en posant `COMPOSE_PROFILES=observability`, ce qui a fait démarrer influxdb et
grafana par le seul run IDE. `COMPOSE_PROFILES` reste la source unique (D4).

**Portée.** `enabled: false` dans le fichier de base, `true` dans
`application-dev.yaml` et nulle part ailleurs. Un serveur a déjà démarré sa stack ;
une campagne de charge la démarre depuis `perf/*-run.sh`. Faire monter des
conteneurs est une commodité de poste de développement, pas une politique
d'architecture — elle appartient donc à un profil, comme le monitoring.

**`lifecycle-management: start-only`**, et non le défaut `start-and-stop` :
arrêter l'application ne doit pas arrêter la base. C'est le pendant de
`restart: "no"` — la stack monte et descend parce qu'on l'a décidé, pas comme
effet de bord d'une configuration de run.

Le démarrage explicite en terminal, `docker compose up -d`, reste valable et
inchangé ; les deux chemins produisent les mêmes conteneurs.

### D8 — Images épinglées

`postgres:17.7-alpine`, `grafana/grafana:12.3.3`, `influxdb:1.8-alpine`. pgAdmin et
MailDev n'exposent pas de version exploitable dans leurs métadonnées et sont épinglés
par digest. Un `docker compose pull` ne doit pas pouvoir changer la stack sans
qu'une ligne du dépôt ne bouge.

## Conséquences

- Le développement quotidien démarre **deux** conteneurs au lieu de six, et passe
  de 115 s à 12 s avant que tout soit *healthy*.
- `restart: "no"` en développement : une base qui remonte seule masque qu'on ne l'a
  jamais démarrée délibérément. La production utilise `unless-stopped`, qui respecte
  un arrêt de maintenance tout en survivant à un redémarrage de la machine.
- Un `.env` incomplet devient visible : les défauts `:-1025` / `:-1080` sur les ports
  mail ont été retirés, dans la continuité de KC-02.
- `.gitignore` couvre désormais `.env*` avec exception `!.env.example`, avant que
  `.env.prod` et `.env.staging` n'existent.

## Trajectoire

**Jour de mise en production** : copier `.env.example` en `.env` sur le serveur,
renseigner les valeurs réelles, puis lancer

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

Aucun fichier du dépôt n'est à modifier.

**Staging** : `docker-compose.staging.yml` sera ajouté le jour où un serveur de
staging existera. Le créer d'avance produirait une infrastructure fictive que rien
ne vérifie.

**Si l'application est un jour conteneurisée** : `DB_HOST` passe de `localhost` à
`postgres` dans le `.env` du serveur, le service `app` rejoint le réseau `kora`, et
la publication du port de base peut alors disparaître entièrement. Aucun autre
changement de configuration Spring n'est requis — c'est précisément ce que
l'introduction de `DB_HOST` en KC-02 rendait possible.

## Alternatives considérées

| Option | Rejetée parce que |
|---|---|
| Conteneuriser l'application dès maintenant | Perte du rechargement à chaud pour un gain de parité faible en Java (D1) |
| Un seul fichier avec des commentaires par environnement | Illisible, et rien n'empêche une valeur de dev d'atteindre un serveur |
| Fichiers d'override pour sélectionner les services | Mauvais outil : influx/grafana ne sont pas *configurés* différemment, ils sont *absents* (D4) |
| Un fichier par pile optionnelle (`docker-compose.tooling.yml`) | Confond l'axe environnement et l'axe groupe optionnel, et oblige à répéter la liste `-f` au `down` (D4) |
| Créer `docker-compose.staging.yml` maintenant | Infrastructure spéculative, non vérifiable, qui se périme |
| Réseau `internal: true` pour la base en production | Incompatible avec D1 : la publication de port ne fonctionne pas sur un réseau interne, et l'application est sur l'hôte |
