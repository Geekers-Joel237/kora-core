# ADR-006 — Topologie Docker Compose et séparation socle / environnements

**Date**: 2026-08-26
**Status**: Accepted
**Authors**: Kora Core Engineering — Ivan Joël Tchatchoua Bayon
**Related**: ADR-005 — Calibration des tests de performance · `CONTRIBUTING.md` §3.2 · KC-02 — Séparation stricte de la configuration

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

### D3 — Publication sur la boucle locale uniquement

`127.0.0.1:${DB_PORT}:5432` partout, développement compris, au lieu de
`${DB_PORT}:5432`.

Le préfixe ne coûte rien et supprime l'exposition réseau. En production, il est la
seule posture compatible avec D1 : l'application, sur l'hôte, atteint la base sur
la boucle locale ; personne d'autre ne le peut. L'accès opérateur passe par un
tunnel SSH sur ce même port.

### D4 — Deux mécanismes distincts, pas un

- **Fichiers d'override** (`-f base -f prod`) : *comment* un service est configuré ici.
- **Profils Compose** (`profiles:`) : *si* un service tourne ici.

Les confondre produit la dérive qu'on veut éviter. Un service déclaré uniquement
dans un override rend l'architecture invisible dans le socle. Avec les profils,
tous les services restent déclarés une fois, et la sélection est une valeur
d'environnement — `COMPOSE_PROFILES` dans `.env` :

| Environnement | `COMPOSE_PROFILES` | Services |
|---|---|---|
| dev | `mail,tooling` | postgres, maildev, pgadmin |
| perf | `mail,observability` | postgres, maildev, influxdb, grafana |
| prod | *(vide)* | postgres |

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

Réintroduit lorsque le `OtpStore` en aura besoin (Step 2). Déclarer une
infrastructure inutilisée est la même classe d'erreur qu'une valeur de repli sur un
secret : le fichier ment sur le système.

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

### D7 — Spring ne gère pas la stack Compose

`spring.docker.compose.enabled: false` dans le fichier de base — donc pour tous les
profils, `perf` compris.

Le découpage D2 casse cette intégration, et c'est instructif : Spring résout **un
seul** fichier Compose (`docker-compose.yml`) puis invoque `docker compose -f <ce
fichier>`. Or nommer un fichier avec `-f` supprime le chargement automatique de
`docker-compose.override.yml` — le mécanisme même sur lequel repose D2. Les ports
vivant dans l'override, l'application échouait au démarrage :

```
IllegalStateException: No host port mapping found for container port 5432
    at DefaultConnectionPorts.get
    at PostgresJdbcDockerComposeConnectionDetailsFactory
```

Fournir les deux fichiers à `spring.docker.compose.file` (qui accepte bien une
`List<File>`) aurait corrigé le symptôme, pas la cause. Deux raisons plus lourdes
condamnaient l'intégration :

- ses `ConnectionDetails` **priment sur `spring.datasource.*`**. Elle se serait
  connectée avec le superutilisateur lu dans le bloc `environment:` du conteneur,
  court-circuitant en silence la séparation `kora_migration` / `kora_app` de D-KC02.
  Invisible aujourd'hui — les trois rôles valent `postgres` — et actif le jour où
  KC-05 crée les vrais rôles ;
- sa sélection de profils (`spring.docker.compose.profiles.active`) serait un
  second réglage pour la décision déjà portée par `COMPOSE_PROFILES` dans `.env`.

La stack se démarre donc explicitement, `docker compose up -d`, ce que
`CONTRIBUTING.md` §3.2 documentait déjà.

### D8 — Images épinglées

`postgres:17.7-alpine`, `grafana/grafana:12.3.3`, `influxdb:1.8-alpine`. pgAdmin et
MailDev n'exposent pas de version exploitable dans leurs métadonnées et sont épinglés
par digest. Un `docker compose pull` ne doit pas pouvoir changer la stack sans
qu'une ligne du dépôt ne bouge.

## Conséquences

- Le développement quotidien démarre **trois** conteneurs au lieu de six.
- `restart: "no"` en développement : une base qui remonte seule masque qu'on ne l'a
  jamais démarrée délibérément. La production utilise `unless-stopped`, qui respecte
  un arrêt de maintenance tout en survivant à un redémarrage de la machine.
- Un `.env` incomplet devient visible : les défauts `:-1025` / `:-1080` sur les ports
  mail ont été retirés, dans la continuité de KC-02.
- `.gitignore` couvre désormais `.env*` avec exception `!.env.example`, avant que
  `.env.prod` et `.env.staging` n'existent.

## Trajectoire

**Jour de mise en production** : copier `.env.example` en `.env` sur le serveur,
renseigner les valeurs réelles, laisser `COMPOSE_PROFILES` vide, lancer

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
| Créer `docker-compose.staging.yml` maintenant | Infrastructure spéculative, non vérifiable, qui se périme |
| Réseau `internal: true` pour la base en production | Incompatible avec D1 : la publication de port ne fonctionne pas sur un réseau interne, et l'application est sur l'hôte |
