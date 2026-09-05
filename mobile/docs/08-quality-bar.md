# Kora Mobile — Barre de qualité

Checklist de sortie. Une case non cochée bloque la livraison.

L'appareil de référence pour toute mesure est **l'appareil socle** défini en `00-requirements.md` §3.2 : Android d'entrée de gamme, 3 Go de RAM, réseau bridé à 3G avec 400 ms de latence.

---

## Relevé du lot 10, mis à jour au lot 1bis

Balayage complet effectué au lot 10 de `07-implementation-plan.md`, révisé après le lot 1bis. Trois états, et un seul compte comme « vérifié » :

| Marque | Sens |
|---|---|
| ✅ | Vérifié **automatiquement** — un test, une règle de lint ou un script d'audit échoue si la propriété se perd |
| ☑︎ | Vérifié **par revue de code** — exact à ce jour, sans garde-fou automatique |
| ⏳ | **Non vérifié** — exige l'appareil socle, un backend en fonctionnement, ou les deux |

**Ce qui bloque une livraison en production, à ce jour :**

1. L'épinglage de certificat est **en place mais inactif** — aucune empreinte configurée, le domaine de production n'existant pas encore. `npm run audit:pinning` le rappelle à chaque exécution.
2. Aucune mesure n'a été prise sur l'appareil socle. Les sept lignes du §4 restent vides.
3. Le parcours de validation manuelle du §10 n'a jamais été exécuté — **l'outillage qui le rend exploitable existe désormais** : inspecteur réseau, simulation d'échec, inspecteur de session et journal de scénarios exportable.

Aucun de ces trois points n'est un défaut de code : ce sont des vérifications qui exigent du matériel et un environnement que le développement n'a pas encore.

---

## 1. Ressenti

- ⏳ Toute réaction visuelle à un appui survient en moins de 100 ms, y compris pendant une requête réseau — *garanti par construction : la compression vit sur le thread UI (`Pressable` + gesture-handler), donc indépendante d'un blocage JS. Reste à mesurer.*
- ⏳ Aucune image fixe pendant une transition — vérifié sur enregistrement à 240 fps
- ✅ Toute animation tourne sur le thread UI, aucune ne repasse par le pont JS — *lint : `Animated` de React Native et `LayoutAnimation` sont bannis à l'import*
- ☑︎ Tout déplacement et toute mise à l'échelle utilisent `withSpring` ; `withTiming` est réservé à l'opacité, à la couleur et à la progression — *seule exception : le chemin « réduire les animations », où `withTiming` remplace les ressorts, comme le prescrit `03-motion-and-feel.md` §7.2*
- ✅ Aucun `TouchableOpacity` ni `Animated` de React Native dans la base de code — *lint*
- ✅ Aucun indicateur circulaire centré nulle part — *aucun `ActivityIndicator` dans l'arbre ; les états de chargement sont des squelettes ou les trois points de `Button`*
- ✅ Aucun squelette n'apparaît sur une réponse de moins de 200 ms — *`useDelayedLoading`, testé, désormais câblé sur l'accueil, l'activité et le détail (**il ne l'était pas avant le lot 10**)*
- ☑︎ Tout squelette reproduit la forme exacte du contenu final — *`SkeletonTransactionList` reprend `layout.rowHeight` et le gabarit de `TransactionRow` ; `SkeletonTimeline` reprend le rail et les nœuds de `StateTimeline`*
- ⏳ Le geste de retour par balayage suit le doigt et tient compte de la vélocité
- ✅ Le glissement d'une feuille modale oppose une résistance progressive au-delà du point haut — *`OVERDRAG_RESISTANCE` dans `Sheet`, couvert par `interaction.test.tsx`*
- ⏳ La compression du solde au défilement est fluide, sans à-coup ni saut
- ✅ Le compteur de solde part de la valeur précédente après un paiement, pas de zéro — *`AnimatedInteger` interpole depuis la valeur montée, testé*
- ✅ La secousse du `PinPad` a une amplitude décroissante — *`SHAKE_SEQUENCE`, testé*
- ✅ La chorégraphie de succès respecte les sept temps du §6.4 de `03-motion-and-feel.md` — *`SUCCESS_TIMELINE`, testé*
- ☑︎ Le nœud courant de `StateTimeline` pulse tant que l'opération n'est pas terminale — *conditionné par `isTerminalState`, lui-même testé*

## 2. Haptique

- ☑︎ Les 22 correspondances de la table du §3 de `03-motion-and-feel.md` sont implémentées — *écart corrigé au lot 10 : la copie de référence appelait `tap` là où la table impose `select`*
- ✅ `haptic.commit` se déclenche à la confirmation d'un paiement, avant la requête — *testé dans `payments.test.tsx`*
- ☑︎ Aucune impulsion n'est déclenchée par un événement non provoqué par l'utilisateur — *toutes les impulsions partent d'un `Pressable` ou d'un gestionnaire de saisie*
- ✅ Aucune impulsion dans les 50 ms suivant la précédente — *étranglement global, testé*
- ✅ L'haptique est intégralement désactivable dans les réglages — *ajouté au lot 10 ; l'API existait depuis le lot 3 mais n'était exposée nulle part*
- ✅ L'haptique subsiste lorsque « réduire les animations » est actif — *`useReduceMotion` ne touche qu'aux styles animés ; testé*

## 3. Fidélité visuelle

- ✅ Aucun littéral de style hors de `src/theme/` — *lint, règle `no-restricted-syntax`*
- ✅ Aucun `#000000` en fond, aucun `#FFFFFF` en texte sur fond sombre — *`tokens.test.ts`*
- ✅ Une seule couleur d'accent dans toute l'application — *`tokens.test.ts`*
- ☑︎ Aucun dégradé hors halo d'accent spécifié
- ☑︎ Aucune ombre portée en thème sombre hors `elevation.4`
- ✅ Tout montant utilise des chiffres tabulaires — *`Amount` impose `tabular`, testé*
- ✅ Tout montant XAF s'affiche sans décimale — *`money.test.ts`*
- ✅ Le séparateur de milliers est `U+202F`, le signe négatif `U+2212` — *vérifié caractère par caractère*
- ☑︎ Le rayon d'un élément imbriqué vaut `rayon du parent − padding`
- ✅ Aucun montant sortant n'est coloré en rouge — *`theme.flow.outbound` distinct de `status.failed`, testé*
- ✅ Toutes les icônes sont des SVG, à trait constant de 1,5 dp — *`icons.ts` est l'unique source, testé*
- ✅ Le thème clair est complet et ne casse aucun contraste — *`contrast.test.ts`, les deux thèmes*

## 4. Performance

| Mesure | Cible | Relevé |
|---|---|---|
| Démarrage à froid → premier pixel utile | < 1 800 ms | ⏳ |
| Démarrage à froid → solde affiché (3G) | < 2 500 ms | ⏳ |
| Réaction à un appui | < 100 ms | ⏳ |
| Défilement d'un historique de 200 lignes | 60 fps, 0 image perdue | ⏳ |
| Bundle JS | < 4 Mo | **4,67 Mo** — dépassement, voir l'arbitrage `NFR-23` ci-dessous |
| Mémoire au repos | < 180 Mo | ⏳ |
| Data pour 5 opérations | < 120 Ko | ⏳ |

- ✅ Toute liste de plus de 20 éléments passe par `FlashList` — *l'historique est la seule liste concernée ; l'accueil en affiche cinq*
- ☑︎ Aucune requête réseau redondante — *déduplication assurée par TanStack Query ; l'isolation solde/historique est testée*
- ✅ Le cache est réhydraté avant tout appel réseau au démarrage — *`restoreQueryCache` s'exécute au chargement du module racine, avant le premier rendu. **Absent avant le lot 10** : le cache était en mémoire seule*
- ⏳ Aucune fuite mémoire après 50 navigations aller-retour

### Arbitrage `NFR-23` — budget de bundle

Le budget de 4 Mo est **dépassé de 0,67 Mo**, et l'était déjà au lot 0 avec 3,7 Mo *sans une ligne de code applicatif*. Le budget visait manifestement le JavaScript d'application ; il est mesuré ici sur le bundle complet, runtime React Native, Reanimated, gesture-handler et navigation compris.

Trois faits pour trancher :

| Fait | Conséquence |
|---|---|
| Le socle non applicatif pèse 3,7 Mo à lui seul | Le budget de 4 Mo laisserait 0,3 Mo à l'application entière — irréaliste pour un produit à onze écrans |
| L'application complète ajoute ~0,97 Mo à ce socle | C'est **cette** valeur que le budget devrait encadrer |
| Le `.hbc` livré pèse 6,4 Mo | Le bytecode Hermes n'est pas comparable au JavaScript source ; le budget ne dit pas lequel il vise |

**Proposition** : requalifier `NFR-23` en « ≤ 1,5 Mo de JavaScript applicatif au-dessus du socle », mesuré par `npm run audit:bundle`. **Décision produit, pas technique** — laissée ouverte.

## 5. Correction financière

- ✅ Aucune arithmétique en virgule flottante sur un montant — *les montants sont des entiers d'unité mineure de bout en bout, testé*
- ✅ Un double appui sur `Confirmer` produit **une seule** requête — *deux `submit` concurrents dans le même `act`, testé*
- ✅ Le verrou anti-double-soumission est un `useRef`, pas un `useState`
- ✅ Le retour matériel et le geste de retour sont neutralisés pendant une soumission — *`useBlockBackWhileSubmitting`, testé*
- ✅ **Aucune reprise automatique sur `POST /payments/*`**, quelle qu'en soit la cause — *testé sur `503`, `500` et coupure réseau*
- ✅ Un `503` conduit à l'écran « issue incertaine » avec `Vérifier l'historique` en action principale
- ✅ Un `500` de `ProviderException` conduit au **même** écran, malgré son corps non conforme à RFC 7807
- ✅ Une expiration réseau conduit au même écran
- ✅ Un transfert vers un numéro inconnu est traité comme un **`404`**, avec son message dédié
- ✅ Les trois formats d'erreur sont normalisés ; un corps vide ou non-JSON ne fait pas planter la couche HTTP
- ✅ Un état `CAPTURED`, `AUTHORIZED` ou `SETTLEMENT_PENDING` est présenté comme **en cours**
- ✅ Le solde et l'historique sont invalidés après toute opération réussie — *et sur issue incertaine, jamais sur un rejet métier*
- ✅ Le montant est conservé lors d'un retour après un `422`

## 6. Sécurité

- ✅ Les jetons résident exclusivement dans SecureStore — *`session.test.ts`*
- ✅ Le PIN n'existe que dans un `useRef`, effacé dans le `finally`
- ✅ Le PIN n'apparaît dans aucun state persisté, aucun log, aucune URL — *`architecture.test.ts` interdit toute écriture d'un PIN dans un magasin*
- ✅ Le journal du mode validation ne contient ni PIN ni jeton — *masquage dans `lib/http`, vérifié sur une requête réelle traversant tout le client*
- ☑︎ La capture d'écran est bloquée sur les écrans de PIN et d'OTP — *`preventScreenCaptureAsync` dans `PinPad` et `verify-otp`*
- ☑︎ Le contenu est occulté dans le sélecteur d'applications — *`PrivacyShield` dès l'état `inactive`. **Garantie inégale** : Android est couvert par `FLAG_SECURE`, iOS peut prendre son instantané avant le rendu JavaScript. `CONTOURNEMENT(indéterminé)`*
- ✅ `console.*` est retiré en production — *`transform-remove-console` sous `NODE_ENV=production` ; le lint interdit déjà `console.log` hors devtools*
- ✅ Aucun jeton, montant ni identifiant client dans les journaux — *le masquage a lieu dans `lib/http`, avant l'entrée de journal ; vérifié sur une requête réelle*
- ✅ Un `401` de PIN ne déclenche jamais de rafraîchissement de jeton — *`client.test.ts`*
- ✅ Trois requêtes recevant `401` simultanément déclenchent **un seul** rafraîchissement — *`client.test.ts`*
- ✅ Un second `401` après rafraîchissement déclenche la fin de session — *sans éjection : voir §7*
- ✅ La déconnexion purge SecureStore, MMKV et le cache de requêtes — *y compris le cache persisté, ajouté au lot 10*
- ✅ Un e-mail inconnu et un PIN erroné produisent le même message — *`auth.test.ts`, dans les deux langues*
- ⏳ **L'épinglage de certificat est actif en production** — *mécanisme en place et testé (`plugins/with-certificate-pinning.js`), **inactif** faute d'empreintes : le domaine de production n'existe pas encore. `npm run audit:pinning` rapporte l'état réel. **Condition bloquante avant livraison.***

## 7. Résilience

- ☑︎ Chaque écran implémente ses quatre états
- ✅ Un échec sur une requête n'altère pas les sections servies par les autres — *`home-queries.test.tsx`, dans les deux sens*
- ☑︎ La perte de réseau produit un bandeau non bloquant, jamais une modale
- ✅ Le bandeau hors ligne n'apparaît qu'après 2 s de déconnexion — *`OFFLINE_GRACE_MS`*
- ✅ Les données de cache restent consultables hors ligne, avec l'ancienneté indiquée — *persistance MMKV + `formatRelativeAge` ; l'instant de mise à jour survit au redémarrage, testé*
- ☑︎ Les actions monétaires sont désactivées hors ligne, avec explication
- ✅ Une session expirée en cours de parcours ramène exactement à l'étape quittée — *`lifecycle.test.ts` : le statut reste `authenticated`, la pile n'est pas démontée*
- ☑︎ Toute erreur affichée indique une action possible — *`ErrorState` exige `onRetry`, `EmptyState` exige `actionLabel`*
- ☑︎ Aucun code d'erreur brut n'est visible par l'utilisateur — *le statut HTTP n'apparaît que dans le détail technique, replié par défaut*
- ✅ Tous les messages métier du §4.6 de `05-screens.md` sont traduits — *les 9 motifs, dans les deux langues*

## 8. Accessibilité

- ✅ Contraste ≥ 4,5:1 sur le texte, ≥ 3:1 sur les éléments d'interface, dans les deux thèmes — *`contrast.test.ts`*
- ☑︎ Toute cible tactile mesure au moins 44×44 dp — *`hitSlop` de `Pressable`, `layout.minTouchTarget` sur les lignes*
- ✅ La mise à l'échelle des polices jusqu'à 200 % ne tronque aucun montant — *plafonds `maxFontScale` testés ; les tailles d'affichage sont bornées à 130 %*
- ✅ Aucune hauteur fixe sur un conteneur de texte — *`architecture.test.ts`. **Quatre violations corrigées au lot 9** : `Button`, `TransactionRow`, les barres de navigation, les cellules d'OTP*
- ✅ Chaque élément interactif porte un libellé d'accessibilité — *`architecture.test.ts` vérifie chaque `<Pressable>` ; `IconButton` l'exige à la compilation*
- ✅ Les montants sont annoncés en toutes lettres par le lecteur d'écran — *`Amount` compose son `accessibilityLabel`, testé*
- ✅ « Réduire les animations » remplace les déplacements par des fondus — *testé sur `Pressable`, `Sheet`, `Segmented`, `Toggle`*
- ⏳ L'ordre de focus suit l'ordre visuel
- ☑︎ Aucune information n'est portée par la seule couleur — *`StatusChip` associe toujours pastille, icône et libellé*

## 9. Internationalisation

- ✅ Aucune chaîne visible n'est écrite en dur — *balayage exhaustif au lot 9 ; ne restent que les chemins SVG, les noms de marque et les langues affichées dans leur propre langue*
- ✅ `fr.json` et `en.json` sont complets et synchronisés — *`i18n.test.ts` compare les jeux de clés **et** les variables d'interpolation*
- ✅ Le basculement de langue ne laisse aucune clé non traduite — *y compris hors composants : libellés d'états, erreurs HTTP, en-têtes de date*
- ✅ Les dates s'affichent dans le fuseau de l'appareil — *aucune conversion manuelle ; `date-fns` opère en heure locale*
- ✅ Tout paramètre de date envoyé à l'API est en ISO-8601 UTC — *`toApiInstant`, testé*
- ⏳ Aucune troncature de texte en anglais comme en français

## 10. Parcours de validation manuelle

À exécuter intégralement sur l'appareil socle, réseau bridé, avant toute livraison.

**Statut : jamais exécuté.** Les 22 scénarios exigent l'appareil socle et un backend en fonctionnement. Le mode validation (`10-validation-mode.md`) est l'outillage prévu pour les consigner — son journal exporte en Markdown.

| # | Scénario | Attendu |
|---|---|---|
| 1 | Inscription → OTP MailDev → accueil | Aboutit sans accroc, solde à 0 |
| 2 | Dépôt de 50 000 XAF via Orange Money | Succès, solde animé de 0 à 50 000 |
| 3 | Transfert de 25 000 vers un second compte | Succès, case de vérification exigée |
| 4 | Retrait de 10 000 | Succès |
| 5 | Retrait supérieur au solde | Bloqué avant soumission, secousse |
| 6 | Transfert vers un numéro inexistant | `404` traduit lisiblement |
| 7 | Double appui rapide sur `Confirmer` | Une seule requête, un seul débit |
| 8 | Réseau coupé pendant un transfert | Écran « issue incertaine », aucun rejeu |
| 9 | Backend arrêté puis paiement | `503` → « issue incertaine » |
| 10 | Jeton d'accès expiré puis action | Rafraîchissement transparent |
| 11 | Jeton de rafraîchissement expiré | Feuille de reconnexion, retour à l'étape quittée |
| 12 | PIN erroné trois fois | Message unifié, aucune déconnexion |
| 13 | Historique de 200 opérations | Défilement à 60 fps, pagination fluide |
| 14 | Filtre type + sens + période | Résultats corrects, requête correcte |
| 15 | Détail d'une opération en cours | Frise pulsante, complétion en direct |
| 16 | Mode avion sur l'accueil | Bandeau, cache affiché, actions désactivées |
| 17 | Bascule de thème | Aucun contraste cassé |
| 18 | Bascule de langue | Aucune chaîne non traduite |
| 19 | Police système à 200 % | Aucune troncature de montant |
| 20 | « Réduire les animations » actif | Fondus, haptique conservée |
| 21 | Mise en arrière-plan sur l'écran de PIN | Contenu occulté |
| 22 | Déconnexion puis relance | Retour à l'écran de connexion, aucune donnée résiduelle |
| 23 | **Mode avion, application relancée à froid** | Solde et historique du cache persisté, ancienneté affichée |

> Le scénario 6 disait « `422` » ; le contrat §2 impose un **`404`**. Corrigé au lot 10.
> Le scénario 23 est ajouté au lot 10 : la persistance du cache n'existait pas quand la liste a été écrite.

---

## 11. Robustesse au contrat mouvant

Le backend étant en développement actif, ces points sont vérifiés à chaque livraison serveur.

- ✅ Un état de transaction inconnu se rend en famille `pending`, sans plantage, avec alerte en mode validation — *règle R2, testée ; le détecteur de dérive signale les nouvelles valeurs d'énumération*
- ✅ Un type de transaction inconnu affiche une icône générique et le libellé brut
- ✅ Un champ supplémentaire dans une réponse est ignoré sans erreur de validation — *`z.looseObject`, testé*
- ✅ Un champ optionnel absent produit un repli, jamais un plantage
- ✅ Un code d'erreur inconnu affiche le `detail` du `ProblemDetail`, pas un message générique
- ✅ Aucun type d'API ne sort de `features/*/api.ts` — *`architecture.test.ts` vérifie la liste des importeurs autorisés*
- ☑︎ Tout contournement est étiqueté `CONTOURNEMENT(étape-N)` et conditionné à `API_CAPABILITIES`
- ☑︎ Aucun contournement n'est câblé en dur
- ✅ `Idempotency-Key` est envoyé sur `/payments/*` même si le serveur l'ignore encore — *testé*
- ✅ Le détecteur de dérive signale tout écart avec `/v3/api-docs` au démarrage — *`useStartupDrift` + bannière rouge persistante sur écart bloquant. **L'analyse ne partait qu'à l'ouverture de l'onglet avant le lot 10**
- ⏳ `01-api-contract.md` est à jour par rapport au code serveur
- ⏳ Le journal de compatibilité de `09-api-evolution.md` §7 est renseigné
- ✅ **Le bundle de production ne contient aucun module de `src/devtools/`** — *`npm run audit:bundle`. **Les onze modules y étaient avant le lot 10** ; corrigé par une redirection de résolution dans `metro.config.js`*

### Scénarios de validation backend

Les 21 scénarios de `10-validation-mode.md` §11 sont exécutés et consignés. Les validations d'**absence** — double appui produisant un double débit, `paymentMethod` inconnu accepté, `Amount` sans contrôle d'échelle, jeton d'accès accepté par `/auth/refresh` — doivent être rejouées après les étapes 4 et 8 pour vérifier qu'elles **échouent désormais**.

**Statut : jamais exécutés.** Ils exigent un backend en fonctionnement.

---

## Outillage d'audit

| Commande | Ce qu'elle vérifie |
|---|---|
| `npm run verify` | typecheck + lint + 340 tests |
| `npm run audit:bundle` | Aucun module de `src/devtools/` dans le bundle ; taille face à `NFR-23` |
| `npm run audit:pinning` | État réel de l'épinglage de certificat |
| `npm run audit` | Les deux audits, dans l'ordre |

---

## Le test final

> Montrer l'application à quelqu'un qui utilise Revolut au quotidien, sans lui dire ce qu'elle est.
>
> Si la première réaction porte sur le produit — « ça fait quoi ? » — la barre est atteinte.
> Si elle porte sur l'exécution — « c'est un peu lent », « ça saute là » — elle ne l'est pas.

**Statut : jamais fait.** Il exige l'appareil, un backend, et quelqu'un d'autre que l'auteur.
