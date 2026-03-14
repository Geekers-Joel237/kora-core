# ADR-001 — Ledger double-entrée immuable

**Date** : 2026-03-14
**Statut** : Accepted
**Auteurs** : Kora Core Engineering

---

## Contexte

Kora Core est un moteur de wallet production-grade pour une néobanque africaine. Chaque mouvement de fonds (cash-in, cash-out, transfert P2P) doit être auditable, reconstructible après incident, et conforme aux standards fintech.

La question centrale : **comment stocker les soldes des comptes ?**

Deux approches existent :
1. Stocker un solde courant dans la table `accounts` et le mettre à jour à chaque opération (`UPDATE balance = balance + X`).
2. Enregistrer chaque mouvement comme une entrée immuable dans un ledger en double-entrée, et dériver le solde depuis ces entrées.

---

## Décision

**Nous utilisons un ledger double-entrée immuable.** Aucune opération financière ne met à jour directement le champ `balance` via un `UPDATE` des entrées ledger. Le solde de référence est toujours la somme algébrique des `Operation` associées à un compte dans le ledger.

### Invariant fondamental

Pour tout ensemble cohérent d'opérations :

```
SUM(DEBIT operations) == SUM(CREDIT operations)
```

Toute transaction génère au minimum deux entrées miroir : un débit sur un compte et un crédit sur un autre. Un cashIn de 10 000 XOF génère :
- `CREDIT 10000 XOF` sur le compte client
- `DEBIT 10000 XOF` sur le float account système

### Cache dénormalisé sur `AccountEntity.balance_amount`

Pour éviter de recalculer le solde depuis les opérations à chaque lecture (coût O(n)), nous maintenons un champ dénormalisé `balance_amount` sur la table `accounts`. Ce champ est mis à jour en mémoire sur l'agrégat `Account` (via `credit()` / `debit()`) et persisté dans la même transaction que l'écriture ledger.

**Ce champ est une optimisation de lecture, jamais la source de vérité.**
En cas de divergence, les `Operation` du ledger ont toujours raison.

### Reverse compensatoire en cas d'échec provider

Si l'appel au provider externe échoue après que les entrées ledger ont été créées, nous **n'annulons pas** les écritures. Nous créons une transaction compensatoire (`reverse`) qui génère les 4 entrées miroir nécessaires pour ramener les soldes à leur état initial. La somme nette des 4 opérations est 0. L'invariant débit = crédit est préservé sur l'ensemble de la vie du ledger.

```
Transaction originale (FAILED) :  DEBIT float + CREDIT client       → somme nette = 0
Reverse compensatoire            :  DEBIT client + CREDIT float      → somme nette = 0
─────────────────────────────────────────────────────────────────────────────────────
Ledger global                    :  toutes opérations, somme nette = 0 ✓
```

### Float account — cas particulier

Le float account (`FLOAT_ACCOUNT`) représente la trésorerie du système. Il est unbounded : son solde ne fait pas l'objet d'un contrôle de provision. En conséquence, `Account.debit()` **ne met pas à jour le champ balance** pour un float account (et ne lève pas `InsufficientFundsException`).

L'audit du float account ne passe **pas** par `balance_amount` (toujours 0 en base). Il passe par la somme des `Operation` de type `DEBIT` / `CREDIT` associées au compte float dans le ledger.

---

## Conséquences

### Positives

- **Auditabilité complète** : chaque centime est tracé, chaque mouvement est attribué à une transaction.
- **Reconstructibilité** : après un incident (corruption du champ dénormalisé, rollback partiel), le solde réel se recalcule en lisant les `Operation`. Aucune donnée financière n'est perdue.
- **Correction garantie par l'invariant** : les tests `FinancialInvariantsDbTest` et `MoneyIntegrityE2ETest` valident `SUM(DEBIT) == SUM(CREDIT)` sur la DB réelle après chaque scénario.
- **Non-répudiation** : les entrées ledger sont immutables (pas de `UPDATE`, pas de `DELETE`). Le droit de modification n'existe pas au niveau applicatif.
- **Compatibilité régulateur** : répond aux exigences de traçabilité BCEAO/CEMAC pour les opérations de monnaie électronique.

### Négatives / trade-offs

- **Complexité accidentelle** : deux sources de vérité coexistent (ledger + cache dénormalisé). Un bug de mise à jour du cache est détecté uniquement à l'audit, pas à la lecture normale.
- **Charge d'écriture** : chaque transaction écrit N entrées (`Operation`) en plus de la transaction elle-même. Pour des volumes élevés (> 10k TPS), cela peut devenir un goulot.
- **Reconstruction coûteuse** : recalculer le solde depuis les opérations pour un compte actif depuis des années est O(n) sans index. Mitigé par le cache dénormalisé et futur snapshot périodique.

---

## Alternatives considérées

### A — UPDATE balance direct

```sql
UPDATE accounts SET balance_amount = balance_amount + :delta WHERE id = :id
```

**Rejeté** : pas d'historique, impossible d'auditer ou de reconstruire après incident. Risque de race condition sans `SELECT FOR UPDATE` explicite. Ne répond pas aux exigences réglementaires de traçabilité.

### B — Event sourcing pur

Stocker uniquement des événements (`MoneyDeposited`, `MoneyWithdrawn`) et reconstruire le solde à chaque lecture en rejouant les événements.

**Rejeté pour Step 0** : complexité opérationnelle (snapshots, projections, event store) disproportionnée pour la phase de démarrage. Prévu pour une extraction microservice du Ledger dans les étapes avancées du roadmap.

### C — Ledger + pas de cache dénormalisé

Supprimer `balance_amount` de `AccountEntity` et toujours calculer depuis les `Operation`.

**Rejeté** : chaque lecture de balance deviendrait une agrégation SQL sur toute l'historique du compte. Inacceptable pour la latence des APIs synchrones.

---

## Reconstruction après incident

Si `balance_amount` d'un compte est corrompu (divergence détectée par audit) :

```sql
-- Recalcul du solde depuis le ledger
SELECT
    SUM(CASE WHEN o.type = 'CREDIT' THEN o.amount ELSE -o.amount END) AS solde_reel
FROM operations o
JOIN transactions t ON o.transaction_id = t.id
WHERE o.account_id = :account_id
  AND o.deleted_at IS NULL;

-- Correction
UPDATE accounts SET balance_amount = :solde_reel WHERE id = :account_id;
```

Cette procédure est la seule circonstance où un `UPDATE` direct du solde est autorisé, et uniquement par un opérateur avec accès DB direct (hors application).