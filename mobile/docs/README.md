# Kora Mobile — Documentation de conception

Spécification d'implémentation de l'application mobile **Kora**, cliente de `kora-core`.

Cible : **React Native + Expo**, TypeScript strict.
Barre de qualité UI/UX : **Revolut**. Pas « inspiré de », pas « à la manière de » — le même niveau d'exigence sur la densité d'information, la physique du mouvement, la latence perçue et le traitement des états d'échec.

---

## ⚠️ À lire avant tout le reste

**Le backend est en développement actif.** Il se situe aujourd'hui à l'étape 3 de `ROADMAP.md` ; les étapes 4 à 10 modifieront le contrat.

L'application a donc **deux rôles simultanés** :

1. **Harnais de validation** du comportement réel du backend, à chaque étape — c'est son rôle premier aujourd'hui.
2. **Produit final**, au niveau d'exécution défini dans `00-requirements.md`.

Trois conséquences directes sur la façon de travailler :

- Le contrat de `01-api-contract.md` est **daté**, pas définitif. Les sept écarts de son §6 sont des **contournements temporaires**, chacun conditionné à un drapeau de capacité — jamais câblés en dur. L'étape 4 (idempotency) en supprimera le principal.
- Le socle — design system, jetons de mouvement, composants, architecture — est **indépendant du backend**. Il s'écrit une fois, aux lots 2 et 3, et ne bouge plus. L'instabilité de l'API n'est jamais une raison de le différer.
- Ce qui est mouvant, c'est la couche de traduction, les états, les motifs de rejet. Ils sont isolés dans un seul point du code, par conception.

`09-api-evolution.md` définit le protocole de synchronisation. `10-validation-mode.md` définit la surface d'inspection qui fait de l'app un véritable harnais.

---

## Point d'entrée

**Un seul fichier à donner en premier : `mobile/docs/README.md` — celui-ci.** Il porte l'index, les règles d'exécution et le cadrage. Le reste se lit à la demande.

Selon la tâche :

| Tâche | Fichiers à charger |
|---|---|
| **Concevoir / implémenter l'UI** | `02-design-system.md` + `03-motion-and-feel.md` + `04-components.md` |
| Implémenter un écran | ceux du dessus + `05-screens.md` + `01-api-contract.md` |
| Brancher l'API | `01-api-contract.md` + `06-architecture.md` + `09-api-evolution.md` |
| Cadrer le périmètre | `00-requirements.md` |
| Démarrer le projet | `07-implementation-plan.md` |
| Valider le backend | `10-validation-mode.md` + `01-api-contract.md` |

---

## Comment lire cette doc

Les documents sont **ordonnés et cumulatifs**. Chacun suppose le précédent lu.

| # | Document | Ce qu'il verrouille |
|---|---|---|
| 00 | [`00-requirements.md`](./00-requirements.md) | **Document autonome.** Exigences fonctionnelles et non-fonctionnelles, périmètre V1, hors-scope explicite |
| 01 | [`01-api-contract.md`](./01-api-contract.md) | Le contrat exact du backend, extrait du code. **Source de vérité unique** |
| 02 | [`02-design-system.md`](./02-design-system.md) | **Document autonome.** Tokens : couleur, typo, espacement, rayon, élévation, iconographie |
| 03 | [`03-motion-and-feel.md`](./03-motion-and-feel.md) | Physique du mouvement, haptique, transitions. Le cœur du « niveau Revolut » |
| 04 | [`04-components.md`](./04-components.md) | Le catalogue de composants primitifs et leur comportement |
| 05 | [`05-screens.md`](./05-screens.md) | Spécification écran par écran, avec tous les états |
| 06 | [`06-architecture.md`](./06-architecture.md) | Stack, arborescence, state management, sécurité, offline |
| 07 | [`07-implementation-plan.md`](./07-implementation-plan.md) | Séquence d'implémentation en lots livrables |
| 08 | [`08-quality-bar.md`](./08-quality-bar.md) | Definition of Done, budgets perf, accessibilité |
| 09 | [`09-api-evolution.md`](./09-api-evolution.md) | Co-évolution avec un backend en développement : impact des étapes à venir, règles d'anti-fragilité, protocole de synchronisation |
| 10 | [`10-validation-mode.md`](./10-validation-mode.md) | Surface d'inspection faisant de l'app un harnais de validation du backend |

---

## Règles d'exécution pour l'agent d'implémentation

Ces règles priment sur toute habitude par défaut.

1. **Ne jamais inventer un endpoint.** Le document `01-api-contract.md` est exhaustif *à sa date*. Si un écran a besoin d'une donnée que l'API ne fournit pas, c'est un *écart* — ils sont listés en §6 du contrat, avec la stratégie de contournement imposée. Ne jamais supposer qu'un endpoint manquant existe « probablement ». En cas de doute : vérifier `/v3/api-docs`, puis mettre à jour le contrat **avant** d'écrire le code.

   Corollaire : tout contournement est étiqueté `CONTOURNEMENT(étape-N)` et conditionné à `API_CAPABILITIES` — voir `09-api-evolution.md` §4 (inventaire) et §5 (règles).

2. **Aucune bibliothèque de composants UI.** Pas de NativeBase, Tamagui, gluestack, RN Paper, RN Elements. Le design system est écrit à la main. C'est précisément ce qui sépare une app « propre » d'une app de niveau Revolut : le contrôle intégral du pixel et de la courbe d'animation.

3. **Aucune valeur en dur.** Toute couleur, espacement, rayon, durée, courbe vient d'un token. Un `#FFFFFF` ou un `marginTop: 12` littéral dans un écran est un défaut de revue.

4. **Le mouvement se fait par ressort, pas par durée.** Défaut = `withSpring` avec les configs de `03-motion-and-feel.md`. `withTiming` est réservé aux fondus d'opacité et aux changements de couleur.

5. **Tout écran a quatre états**, tous à spécifier et implémenter : `loading` (skeleton, jamais un spinner centré), `empty` (illustration + action), `error` (message actionnable + retry), `success`. L'oubli d'un état est un travail incomplet.

6. **L'argent ne bouge jamais deux fois.** Toute action monétaire est protégée par un verrou local qui survit au re-render. Voir `06-architecture.md` §5 — l'API n'expose pas de clé d'idempotence, la protection est entièrement côté client.

7. **Le PIN ne quitte jamais la mémoire volatile.** Jamais dans un state persisté, jamais dans un log, jamais dans SecureStore, jamais dans une URL.

---

## Environnement de développement

```
Backend      http://localhost:8081
Swagger UI   http://localhost:8081/swagger-ui.html
OpenAPI      http://localhost:8081/v3/api-docs
MailDev      http://localhost:1080     ← les OTP arrivent ici en local
Postgres     localhost:5432 / kora-db
```

Démarrage backend : voir [`../../CONTRIBUTING.md`](../../CONTRIBUTING.md).

> **À retenir dès maintenant** : les OTP sont envoyés **par e-mail**, pas par SMS. En développement ils sont interceptés par MailDev. Cela a une conséquence UX directe traitée en `05-screens.md` §3.