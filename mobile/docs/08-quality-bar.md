# Kora Mobile — Barre de qualité

Checklist de sortie. Une case non cochée bloque la livraison.

L'appareil de référence pour toute mesure est **l'appareil socle** défini en `00-requirements.md` §3.2 : Android d'entrée de gamme, 3 Go de RAM, réseau bridé à 3G avec 400 ms de latence.

---

## 1. Ressenti

- [ ] Toute réaction visuelle à un appui survient en moins de 100 ms, y compris pendant une requête réseau
- [ ] Aucune image fixe pendant une transition — vérifié sur enregistrement à 240 fps
- [ ] Toute animation tourne sur le thread UI, aucune ne repasse par le pont JS
- [ ] Tout déplacement et toute mise à l'échelle utilisent `withSpring` ; `withTiming` est réservé à l'opacité, à la couleur et à la progression
- [ ] Aucun `TouchableOpacity` ni `Animated` de React Native dans la base de code
- [ ] Aucun indicateur circulaire centré nulle part
- [ ] Aucun squelette n'apparaît sur une réponse de moins de 200 ms
- [ ] Tout squelette reproduit la forme exacte du contenu final
- [ ] Le geste de retour par balayage suit le doigt et tient compte de la vélocité
- [ ] Le glissement d'une feuille modale oppose une résistance progressive au-delà du point haut
- [ ] La compression du solde au défilement est fluide, sans à-coup ni saut
- [ ] Le compteur de solde part de la valeur précédente après un paiement, pas de zéro
- [ ] La secousse du `PinPad` a une amplitude décroissante
- [ ] La chorégraphie de succès respecte les sept temps du §6.4 de `03-motion-and-feel.md`
- [ ] Le nœud courant de `StateTimeline` pulse tant que l'opération n'est pas terminale

## 2. Haptique

- [ ] Les 23 correspondances de la table du §3 de `03-motion-and-feel.md` sont implémentées
- [ ] `haptic.commit` se déclenche à la confirmation d'un paiement, avant la requête
- [ ] Aucune impulsion n'est déclenchée par un événement non provoqué par l'utilisateur
- [ ] Aucune impulsion dans les 50 ms suivant la précédente
- [ ] L'haptique est intégralement désactivable dans les réglages
- [ ] L'haptique subsiste lorsque « réduire les animations » est actif

## 3. Fidélité visuelle

- [ ] Aucun littéral de style hors de `src/theme/` — vérifié par le lint
- [ ] Aucun `#000000` en fond, aucun `#FFFFFF` en texte sur fond sombre
- [ ] Une seule couleur d'accent dans toute l'application
- [ ] Aucun dégradé hors halo d'accent spécifié
- [ ] Aucune ombre portée en thème sombre hors `elevation.4`
- [ ] Tout montant utilise des chiffres tabulaires
- [ ] Tout montant XOF s'affiche sans décimale
- [ ] Le séparateur de milliers est `U+202F`, le signe négatif `U+2212`
- [ ] Le rayon d'un élément imbriqué vaut `rayon du parent − padding`
- [ ] Aucun montant sortant n'est coloré en rouge
- [ ] Toutes les icônes sont des SVG, à trait constant de 1,5 dp
- [ ] Le thème clair est complet et ne casse aucun contraste

## 4. Performance

| Mesure | Cible | Relevé |
|---|---|---|
| Démarrage à froid → premier pixel utile | < 1 800 ms | |
| Démarrage à froid → solde affiché (3G) | < 2 500 ms | |
| Réaction à un appui | < 100 ms | |
| Défilement d'un historique de 200 lignes | 60 fps, 0 image perdue | |
| Bundle JS | < 4 Mo | |
| Mémoire au repos | < 180 Mo | |
| Data pour 5 opérations | < 120 Ko | |

- [ ] Toute liste de plus de 20 éléments passe par `FlashList`
- [ ] Aucune requête réseau redondante — déduplication vérifiée
- [ ] Le cache est réhydraté avant tout appel réseau au démarrage
- [ ] Aucune fuite mémoire après 50 navigations aller-retour

## 5. Correction financière

- [ ] Aucune arithmétique en virgule flottante sur un montant
- [ ] Un double appui sur `Confirmer` produit **une seule** requête
- [ ] Le verrou anti-double-soumission est un `useRef`, pas un `useState`
- [ ] Le retour matériel et le geste de retour sont neutralisés pendant une soumission
- [ ] **Aucune reprise automatique sur `POST /payments/*`**, quelle qu'en soit la cause
- [ ] Un `503` conduit à l'écran « issue incertaine » avec `Vérifier l'historique` en action principale
- [ ] Un `500` de `ProviderException` conduit au **même** écran, malgré son corps non conforme à RFC 7807
- [ ] Une expiration réseau conduit au même écran
- [ ] Un transfert vers un numéro inconnu est traité comme un **`404`**, avec son message dédié — pas comme un `422` générique
- [ ] Les trois formats d'erreur sont normalisés ; un corps vide ou non-JSON ne fait pas planter la couche HTTP
- [ ] Un état `CAPTURED`, `AUTHORIZED` ou `SETTLEMENT_PENDING` est présenté comme **en cours**
- [ ] Le solde et l'historique sont invalidés après toute opération réussie
- [ ] Le montant est conservé lors d'un retour après un `422`

## 6. Sécurité

- [ ] Les jetons résident exclusivement dans SecureStore
- [ ] Le PIN n'existe que dans un `useRef`, effacé dans le `finally`
- [ ] Le PIN n'apparaît dans aucun state persisté, aucun log, aucune URL
- [ ] La capture d'écran est bloquée sur les écrans de PIN et d'OTP
- [ ] Le contenu est occulté dans le sélecteur d'applications
- [ ] `console.*` est retiré en production
- [ ] Aucun jeton, montant ni identifiant client dans les journaux
- [ ] Un `401` de PIN ne déclenche jamais de rafraîchissement de jeton
- [ ] Trois requêtes recevant `401` simultanément déclenchent **un seul** rafraîchissement
- [ ] Un second `401` après rafraîchissement déclenche la déconnexion
- [ ] La déconnexion purge SecureStore, MMKV et le cache de requêtes
- [ ] Un e-mail inconnu et un PIN erroné produisent le même message
- [ ] L'épinglage de certificat est actif en production

## 7. Résilience

- [ ] Chaque écran implémente ses quatre états
- [ ] Un échec sur une requête n'altère pas les sections servies par les autres
- [ ] La perte de réseau produit un bandeau non bloquant, jamais une modale
- [ ] Le bandeau hors ligne n'apparaît qu'après 2 s de déconnexion
- [ ] Les données de cache restent consultables hors ligne, avec l'ancienneté indiquée
- [ ] Les actions monétaires sont désactivées hors ligne, avec explication
- [ ] Une session expirée en cours de parcours ramène exactement à l'étape quittée
- [ ] Toute erreur affichée indique une action possible
- [ ] Aucun code d'erreur brut n'est visible par l'utilisateur
- [ ] Tous les messages métier du §4.6 de `05-screens.md` sont traduits

## 8. Accessibilité

- [ ] Contraste ≥ 4,5:1 sur le texte, ≥ 3:1 sur les éléments d'interface, dans les deux thèmes
- [ ] Toute cible tactile mesure au moins 44×44 dp
- [ ] La mise à l'échelle des polices jusqu'à 200 % ne tronque aucun montant
- [ ] Aucune hauteur fixe sur un conteneur de texte
- [ ] Chaque élément interactif porte un libellé d'accessibilité
- [ ] Les montants sont annoncés en toutes lettres par le lecteur d'écran
- [ ] « Réduire les animations » remplace les déplacements par des fondus
- [ ] L'ordre de focus suit l'ordre visuel
- [ ] Aucune information n'est portée par la seule couleur

## 9. Internationalisation

- [ ] Aucune chaîne visible n'est écrite en dur
- [ ] `fr.json` et `en.json` sont complets et synchronisés
- [ ] Le basculement de langue ne laisse aucune clé non traduite
- [ ] Les dates s'affichent dans le fuseau de l'appareil
- [ ] Tout paramètre de date envoyé à l'API est en ISO-8601 UTC
- [ ] Aucune troncature de texte en anglais comme en français

## 10. Parcours de validation manuelle

À exécuter intégralement sur l'appareil socle, réseau bridé, avant toute livraison.

| # | Scénario | Attendu |
|---|---|---|
| 1 | Inscription → OTP MailDev → accueil | Aboutit sans accroc, solde à 0 |
| 2 | Dépôt de 50 000 XOF via Orange Money | Succès, solde animé de 0 à 50 000 |
| 3 | Transfert de 25 000 vers un second compte | Succès, case de vérification exigée |
| 4 | Retrait de 10 000 | Succès |
| 5 | Retrait supérieur au solde | Bloqué avant soumission, secousse |
| 6 | Transfert vers un numéro inexistant | `422` traduit lisiblement |
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

---

## 11. Robustesse au contrat mouvant

Le backend étant en développement actif, ces points sont vérifiés à chaque livraison serveur.

- [ ] Un état de transaction inconnu se rend en famille `pending`, sans plantage, avec alerte en mode validation
- [ ] Un type de transaction inconnu affiche une icône générique et le libellé brut
- [ ] Un champ supplémentaire dans une réponse est ignoré sans erreur de validation
- [ ] Un champ optionnel absent produit un repli, jamais un plantage
- [ ] Un code d'erreur inconnu affiche le `detail` du `ProblemDetail`, pas un message générique
- [ ] Aucun type d'API ne sort de `features/*/api.ts` — couche de traduction respectée
- [ ] Tout contournement est étiqueté `CONTOURNEMENT(étape-N)` et conditionné à `API_CAPABILITIES`
- [ ] Aucun contournement n'est câblé en dur
- [ ] `Idempotency-Key` est envoyé sur `/payments/*` même si le serveur l'ignore encore
- [ ] Le détecteur de dérive signale tout écart avec `/v3/api-docs` au démarrage
- [ ] `01-api-contract.md` est à jour par rapport au code serveur
- [ ] Le journal de compatibilité de `09-api-evolution.md` §7 est renseigné
- [ ] Le bundle de production ne contient aucun module de `src/devtools/`

### Scénarios de validation backend

Les 15 scénarios de `10-validation-mode.md` §11 sont exécutés et consignés. Les deux scénarios de **validation d'absence** — double appui produisant un double débit, `paymentMethod` inconnu accepté — doivent être rejoués après l'étape 4 et l'étape 8 pour vérifier qu'ils **échouent désormais**.

---

## Le test final

> Montrer l'application à quelqu'un qui utilise Revolut au quotidien, sans lui dire ce qu'elle est.
>
> Si la première réaction porte sur le produit — « ça fait quoi ? » — la barre est atteinte.
> Si elle porte sur l'exécution — « c'est un peu lent », « ça saute là » — elle ne l'est pas.
