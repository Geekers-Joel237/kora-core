# Kora Mobile — instructions agent

## Avant d'écrire du code

1. **Lire [`docs/README.md`](./docs/README.md)** — index, règles d'exécution, cadrage. C'est le point d'entrée obligatoire.
2. **Expo SDK 57.** L'API a changé entre versions majeures : consulter <https://docs.expo.dev/versions/v57.0.0/> plutôt que la mémoire.

## Documents selon la tâche

| Tâche | Fichiers |
|---|---|
| Concevoir / implémenter l'UI | `docs/02-design-system.md` + `docs/03-motion-and-feel.md` + `docs/04-components.md` |
| Implémenter un écran | ceux du dessus + `docs/05-screens.md` + `docs/01-api-contract.md` |
| Brancher l'API | `docs/01-api-contract.md` + `docs/06-architecture.md` + `docs/09-api-evolution.md` |
| Savoir quoi faire ensuite | `docs/07-implementation-plan.md` |

## Contraintes que le lint fait respecter

- Aucune valeur de style littérale hors de `src/theme/`
- Aucune bibliothèque de composants tierce
- Ni `TouchableOpacity`, ni `Animated` de React Native, ni `LayoutAnimation`
- `any` interdit ; `console.log` interdit hors `src/devtools/`

## Avant de rendre la main

```bash
npm run verify     # typecheck + lint + test
```

## Le backend bouge

`kora-core` est en développement actif (étape 3 du `ROADMAP.md`). Le contrat de `docs/01-api-contract.md` est **daté**. Ne jamais supposer qu'un endpoint absent existe « probablement » — vérifier `/v3/api-docs`, puis mettre à jour le contrat **avant** d'écrire le code. Voir `docs/09-api-evolution.md`.

Metro tourne sur le **port 8090** ; le 8081 est occupé par le backend.
