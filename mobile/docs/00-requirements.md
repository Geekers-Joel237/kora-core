# Kora Mobile — Exigences

Document autonome. Il définit **quoi** doit être construit et à **quel niveau de qualité**, sans prescrire le comment.
Le comment est dans `06-architecture.md`. L'apparence est dans `02-design-system.md`.

Statut : baseline V1.
Contrat backend de référence : `kora-core` @ `develop`, **étape 3 du `ROADMAP.md`**.

> **Le backend est en développement actif.** L'application a un double rôle : harnais de validation du comportement backend aujourd'hui, produit final à terme. Les exigences ci-dessous sont stables ; ce qui est mouvant, c'est la surface d'API qui les sert. Voir `09-api-evolution.md`.
>
> Les exigences marquées 🔄 dépendent d'une étape backend à venir et ne sont pas tenables en l'état.

---

## 1. Contexte et positionnement

Kora est un portefeuille électronique pour l'Afrique de l'Ouest, adossé au moteur `kora-core` : registre en partie double immuable, cycle de vie de paiement à machine à états stricte, orchestration multi-fournisseurs Mobile Money.

L'application mobile est le **seul point de contact client**. Elle n'est pas une vitrine de l'API : elle doit rendre lisible et rassurante une mécanique financière intrinsèquement asynchrone et faillible.

### Le pari produit

La plupart des wallets d'Afrique de l'Ouest traitent une transaction comme une boîte noire : on appuie, on attend, un message apparaît. Kora fait l'inverse — **la transparence du cycle de vie est la fonctionnalité**. Le backend expose déjà un historique d'états horodaté (`INITIALIZED → AUTHORIZED → CAPTURED → COMPLETED`). L'app l'affiche comme une frise temporelle vivante.

C'est le différenciateur, et c'est aussi ce qui impose la barre d'exécution : une frise d'états mal animée est pire que pas de frise du tout.

### Utilisateurs cibles

| Persona | Contexte | Ce qui compte pour eux |
|---|---|---|
| **Aminata**, commerçante, 34 ans | Android milieu de gamme, réseau 3G instable, 15–40 opérations/jour | Vitesse d'exécution, certitude qu'une opération est passée, historique filtrable |
| **Kwame**, salarié urbain, 27 ans | Android récent, 4G, 3–8 opérations/semaine | Transferts P2P instantanés, suivi du solde, esthétique |
| **Fatou**, étudiante, 21 ans | Android d'entrée de gamme, data comptée | Faible consommation data, aucune surprise sur les montants |

Conséquence directe : **le socle de test est un Android d'entrée de gamme sur réseau dégradé**, pas un iPhone Pro sur Wi-Fi.

---

## 2. Exigences fonctionnelles

Priorité : `P0` = bloquant pour la V1 · `P1` = V1 si le planning tient · `P2` = post-V1.

### 2.1 Identité et session

| ID | Exigence | Prio |
|---|---|---|
| FR-01 | L'utilisateur crée un compte avec nom complet, e-mail, indicatif + numéro de téléphone, et un PIN de 4 à 8 chiffres | P0 |
| FR-02 | Toute création de compte est confirmée par un code à 6 chiffres reçu **par e-mail**, valide 5 minutes | P0 |
| FR-03 | La connexion se fait par e-mail + PIN, puis second facteur OTP e-mail | P0 |
| FR-04 | La session survit à la fermeture de l'app tant que le jeton de rafraîchissement est valide (7 jours) | P0 |
| FR-05 | Le jeton d'accès (15 min) est renouvelé de façon transparente, sans jamais interrompre l'utilisateur au milieu d'une action | P0 |
| FR-06 | Après première connexion réussie, l'utilisateur peut activer le déverrouillage biométrique en lieu et place de la ressaisie du PIN à l'ouverture | P1 |
| FR-07 | L'utilisateur peut se déconnecter ; l'opération purge tout secret local | P0 |
| FR-08 | L'utilisateur peut demander un nouvel OTP après un délai anti-abus de 30 secondes | P0 |

### 2.2 Solde et vue d'ensemble

| ID | Exigence | Prio |
|---|---|---|
| FR-10 | L'écran d'accueil affiche le solde courant, sa devise, et le numéro de compte | P0 |
| FR-11 | Le solde peut être masqué d'un geste, et l'état masqué persiste entre les sessions | P0 |
| FR-12 | Le solde se rafraîchit au retour au premier plan, au tirer-pour-rafraîchir, et après toute opération réussie | P0 |
| FR-13 | L'accueil expose les trois actions monétaires en accès direct : déposer, retirer, envoyer | P0 |
| FR-14 | L'accueil liste les 5 dernières opérations avec accès à l'historique complet | P0 |

### 2.3 Opérations monétaires

| ID | Exigence | Prio |
|---|---|---|
| FR-20 | **Dépôt** : l'utilisateur alimente son portefeuille depuis un compte Mobile Money externe (montant, devise, opérateur, PIN) | P0 |
| FR-21 | **Retrait** : l'utilisateur transfère vers un compte Mobile Money externe (montant, devise, opérateur, PIN) | P0 |
| FR-22 | **Transfert P2P** : l'utilisateur envoie à un autre client Kora identifié par son numéro de téléphone (montant, devise, PIN) | P0 |
| FR-23 | Toute opération monétaire exige la saisie du PIN comme confirmation finale — c'est le point de non-retour | P0 |
| FR-24 | Un écran de récapitulatif précède la saisie du PIN : montant, destinataire ou opérateur, frais (0 en V1), total débité | P0 |
| FR-25 | Le montant se saisit sur un pavé numérique plein écran, jamais sur un `TextInput` clavier système | P0 |
| FR-26 | La saisie est bornée en temps réel par le solde disponible pour retrait et transfert ; le dépassement est signalé avant soumission | P0 |
| FR-27 | Le résultat d'une opération est un écran plein, sans ambiguïté, indiquant l'état réel renvoyé par le backend | P0 |
| FR-28 | Une opération dont l'état final est intermédiaire (`AUTHORIZED`, `CAPTURED`, `SETTLEMENT_PENDING`) est présentée comme **en cours**, jamais comme terminée | P0 |
| FR-29 | Un double appui, un re-render ou un retour arrière ne peuvent en aucun cas produire deux opérations | P0 |
| FR-29b 🔄 | Une opération dont l'issue est incertaine peut être rejouée **sans risque de double débit** — dépend de l'étape 4 (idempotency). En attendant, le rejeu reste une décision manuelle avertie | P0 |
| FR-30 | Les destinataires P2P récents sont proposés en accès rapide, stockés localement | P1 |

### 2.4 Historique

| ID | Exigence | Prio |
|---|---|---|
| FR-40 | Historique paginé, groupé par jour, avec en-têtes de section collants | P0 |
| FR-41 | Filtres combinables : type d'opération, état, sens (entrant/sortant), plage de dates | P0 |
| FR-42 | Pagination par défilement infini, avec indicateur de chargement en pied de liste | P0 |
| FR-43 | Le détail d'une opération affiche la **frise temporelle complète des transitions d'état**, horodatée | P0 |
| FR-44 | Le détail affiche le numéro d'opération, copiable d'un appui long | P0 |
| FR-45 | Les opérations en cours sont rafraîchies automatiquement tant qu'elles ne sont pas dans un état terminal | P1 |
| FR-45b 🔄 | L'app tolère un état de transaction inconnu sans plantage, et le signale en mode validation — les étapes 6 et 8 en introduiront de nouveaux | P0 |
| FR-46 | Recherche libre dans l'historique | P2 |
| FR-47 | Export d'un reçu au format image ou PDF | P2 |

### 2.5 Compte et réglages

| ID | Exigence | Prio |
|---|---|---|
| FR-50 | L'utilisateur consulte son profil : nom, e-mail, téléphone, numéro de compte | P0 |
| FR-51 | Réglages : biométrie, thème, langue, masquage du solde par défaut | P1 |
| FR-52 | Changement de PIN | P2 |
| FR-53 | Gestion des notifications | P2 |

---

## 3. Exigences non fonctionnelles

### 3.1 Qualité perçue — l'exigence centrale

> **NFR-01.** L'application doit être indiscernable, en fluidité et en soin du détail, d'une application produite par Revolut, N26 ou Wise.

Cette exigence n'est pas décorative : elle est la raison d'être du projet. Elle se décompose en critères vérifiables.

| ID | Exigence | Critère de vérification |
|---|---|---|
| NFR-02 | Aucune image fixe pendant une transition | Enregistrement d'écran à 240 fps : aucune frame dupliquée pendant une navigation |
| NFR-03 | Toute animation tourne sur le thread UI | Zéro animation pilotée par `setState` ou par le pont JS |
| NFR-04 | 60 fps soutenus sur l'appareil socle, 120 fps sur matériel compatible | Aucune frame perdue au défilement d'un historique de 200 lignes |
| NFR-05 | Le mouvement est régi par une physique de ressort, pas par des durées | Toute animation interruptible reprend depuis sa vélocité courante |
| NFR-06 | Chaque interaction significative produit un retour haptique de l'intensité correcte | Table de correspondance exhaustive dans `03-motion-and-feel.md` |
| NFR-07 | Aucun état de chargement ne s'affiche sous 200 ms | En dessous, la vue précédente est conservée |
| NFR-08 | Le chargement se matérialise par un squelette de la forme finale, jamais par un indicateur circulaire centré | Revue visuelle |
| NFR-09 | Les montants sont rendus en chiffres à chasse fixe | Aucun décalage horizontal lors de l'animation d'un solde |
| NFR-10 | Aucune valeur littérale de style hors du fichier de tokens | Règle de lint bloquante |
| NFR-11 | Toute liste de plus de 20 éléments est virtualisée | Revue de code |
| NFR-12 | La densité d'information tient sur un écran de 5,5" sans troncature d'un montant | Test sur écran 360×640 dp |

### 3.2 Performance

| ID | Exigence | Cible |
|---|---|---|
| NFR-20 | Démarrage à froid jusqu'au premier pixel utile | < 1,8 s sur l'appareil socle |
| NFR-21 | Solde affiché après démarrage à froid | < 2,5 s sur réseau 3G |
| NFR-22 | Réaction visuelle à un appui | < 100 ms, toujours |
| NFR-23 | Poids du bundle JS | < 4 Mo |
| NFR-24 | Empreinte mémoire au repos | < 180 Mo |
| NFR-25 | Consommation data par session de 5 opérations | < 120 Ko |

L'appareil socle est un Android à 8 Go de stockage, 3 Go de RAM, SoC d'entrée de gamme (classe Snapdragon 680), sur réseau bridé à 3G avec 400 ms de latence.

### 3.3 Résilience

| ID | Exigence |
|---|---|
| NFR-30 | La perte de réseau est signalée par un bandeau persistant non bloquant, jamais par une boîte de dialogue modale |
| NFR-31 | Toute lecture (solde, historique) sert d'abord le cache local, puis se révalide en arrière-plan |
| NFR-32 | Aucune écriture monétaire n'est rejouée automatiquement — le rejeu est toujours une décision explicite de l'utilisateur |
| NFR-33 | Un `503` du backend est présenté comme un incident temporaire avec une action de reprise, jamais comme un échec définitif |
| NFR-34 | Une session expirée en cours d'action ramène l'utilisateur exactement à l'écran quitté après reconnexion |
| NFR-35 | Toute erreur affichée indique à l'utilisateur ce qu'il peut faire, jamais uniquement ce qui s'est mal passé |

### 3.4 Sécurité

| ID | Exigence |
|---|---|
| NFR-40 | Les jetons résident exclusivement dans le trousseau matériel (Keychain iOS / Keystore Android) |
| NFR-41 | Le PIN n'existe qu'en mémoire volatile, le temps d'une requête, et est effacé immédiatement après |
| NFR-42 | Aucun secret, montant, jeton ou identifiant client dans les journaux d'une compilation de production |
| NFR-43 | Le contenu de l'application est occulté dans le sélecteur d'applications du système |
| NFR-44 | La capture d'écran est bloquée sur les écrans de saisie de PIN et d'OTP |
| NFR-45 | Épinglage de certificat sur les compilations de production |
| NFR-46 | L'application refuse de démarrer sur un appareil rooté ou jailbreaké en compilation de production *(P1)* |
| NFR-47 | Le PIN saisi n'apparaît jamais en clair à l'écran — uniquement des pastilles |

### 3.5 Accessibilité

| ID | Exigence |
|---|---|
| NFR-50 | Contraste minimum 4,5:1 pour le texte courant, 3:1 pour les éléments d'interface |
| NFR-51 | Toute cible tactile mesure au moins 44×44 dp |
| NFR-52 | Support de la mise à l'échelle des polices jusqu'à 200 % sans perte fonctionnelle |
| NFR-53 | Chaque élément interactif porte un libellé d'accessibilité explicite |
| NFR-54 | Les montants sont annoncés en toutes lettres par le lecteur d'écran, pas caractère par caractère |
| NFR-55 | Le respect de « réduire les animations » remplace les déplacements par des fondus, sans jamais supprimer le retour visuel |

### 3.6 Internationalisation

| ID | Exigence |
|---|---|
| NFR-60 | Français en langue par défaut, anglais en second, dès la V1 |
| NFR-61 | Aucune chaîne de caractères visible n'est écrite en dur dans un composant |
| NFR-62 | Le formatage monétaire respecte les conventions de la devise : **XAF s'affiche sans décimale** |
| NFR-63 | Les dates sont affichées dans le fuseau de l'appareil ; le backend émet exclusivement en UTC |

---

## 4. Hors périmètre V1

Explicitement exclu. Ne pas implémenter, ne pas prévoir d'emplacement dans l'interface.

- Cartes bancaires, virements internationaux, multi-devises actif
- Épargne, crédit, investissement, cryptoactifs
- Facturation marchand, QR code, paiement en point de vente
- Parrainage, cashback, programme de fidélité
- Chat support intégré
- Mode hors-ligne en écriture (file d'attente d'opérations)
- Notifications push — **le backend n'expose ni webhook ni canal temps réel**
- Fonctions d'administration (`/admin/**` est réservé au back-office, hors app cliente)

---

## 5. Contraintes imposées par le backend

Ces contraintes sont celles de l'**état actuel** du backend. Elles ne sont pas négociables côté app aujourd'hui, mais plusieurs disparaîtront. Détail et stratégie de contournement en `01-api-contract.md` §6 ; conditionnement par drapeau de capacité en `09-api-evolution.md` §4.

| Contrainte | Conséquence produit | Levée par |
|---|---|---|
| Le second facteur passe par **e-mail**, pas par SMS | L'écran OTP doit guider vers la boîte mail, pas attendre un SMS. Pas de remplissage automatique possible | — structurel |
| Le **PIN accompagne chaque opération monétaire** | Le pavé PIN est un composant central, pas un écran d'exception. Son ergonomie conditionne le ressenti global | — structurel |
| **Aucune clé d'idempotence** sur les endpoints de paiement | La protection contre le double débit est intégralement à la charge du client, par verrou local. Aucun rejeu automatique | **étape 4** |
| **Aucun endpoint de résolution de bénéficiaire** | Impossible d'afficher le nom du destinataire avant un transfert. Le récapitulatif compense par une confirmation renforcée sur le numéro | indéterminé |
| **Aucun endpoint de profil** | Le profil est reconstitué depuis les claims du jeton et les données saisies à l'inscription | indéterminé |
| **Aucun canal temps réel** | Le suivi d'une opération en cours se fait par sondage borné de l'historique | **étape 5+** |
| Le champ `paymentMethod` est une **chaîne libre** | La liste des opérateurs est définie et figée côté application | **étape 8** |
| **Aucun endpoint de déconnexion** | La déconnexion est purement locale : purge du trousseau et du cache | indéterminé |
| **Aucune limite de vélocité** | Aucun rejet pour plafond dépassé à gérer aujourd'hui — mais l'app doit être prête à en afficher | **étape 8** |

### Exigences liées au rôle de harnais

| ID | Exigence | Prio |
|---|---|---|
| VR-01 | L'app expose un mode validation, isolé sous `src/devtools/`, absent du bundle de production | P0 |
| VR-02 | Toute requête et toute réponse sont inspectables, `rawPin` masqué | P0 |
| VR-03 | Les 11 états et la frise complète sont consultables sans traduction ni regroupement | P0 |
| VR-04 | Un écart entre `/v3/api-docs` et les types locaux est détecté et signalé au démarrage | P1 |
| VR-05 | L'URL d'API est modifiable sans recompilation, avec purge des jetons au changement | P0 |
| VR-06 | Latence, coupure réseau et statut de réponse sont simulables côté client | P1 |
| VR-07 | Les observations de validation sont consignables et exportables en Markdown | P2 |

Détail complet en `10-validation-mode.md`.

---

## 6. Critères d'acceptation de la V1

La V1 est livrable lorsque, et seulement lorsque :

1. Les **34 exigences `P0`** — 30 fonctionnelles, 4 de validation — sont implémentées et vérifiées manuellement.
2. Tous les critères `NFR-02` à `NFR-12` sont vérifiés sur l'appareil socle.
3. Le parcours complet — inscription, OTP, dépôt, transfert, retrait, consultation du détail — s'exécute sans accroc sur réseau 3G bridé.
4. Les scénarios de défaillance sont tous couverts : réseau coupé en plein transfert, jeton expiré pendant la saisie du PIN, `503` du fournisseur, PIN erroné, solde insuffisant.
5. La checklist de `08-quality-bar.md` est intégralement cochée.