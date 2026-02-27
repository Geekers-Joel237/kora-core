# 🏦 KORA CORE


**Produit simulé** : Wallet néobanque (Mobile Money + Card-ready) :

P2P, cash-in/out, paiement marchand, multi-provider, settlement différé, réconciliation, risk, observabilité, cloud orchestration.

---

# 🧱 ÉTAPE 0 — Monolithe transactionnel conscient

**(Semaines 1–2)**

## 🎯 Contexte métier

On lance le noyau d’un wallet :

- création compte
- dépôt (cash-in)
- retrait (cash-out)
- transfert P2P

## ✅ Valeur pour une néobanque type Djamo

Sans fondations financières solides, tout le reste est fragile : support, litiges, audits, réputation.

Cette étape garantit : **cohérence financière** et **auditabilité** dès le début.

## 📊 Objectifs volumétriques

- **5 000 tx/jour**
- **5–10 req/sec** (peak)
- **P95 < 150 ms**
- **DB QPS ~ 30–60**

## 🔐 Contraintes sécurité (fortes, dès le début)

- Hashage mots de passe (Argon2/bcrypt) + politiques de mot de passe
- Auth JWT (rotation/expiry) + refresh tokens
- Validation stricte des entrées + protection injection
- Chiffrement secrets (pas de secrets en dur)
- Logs **sans PII** et sans secrets
- OWASP Top 10 baseline

## 🔧 Travaux techniques

- **Ledger double entrée** (écritures immuables)
- **Aucun “update balance” direct** : solde = somme ledger
- Index sur account_id, txn_id
- Tests invariants financiers (debits==credits)

## 🧠 Questions auxquelles tu dois répondre

- Pourquoi un solde ne doit jamais être “mis à jour” directement ?
- Comment reconstruire un solde après incident ?
- Quels index sont indispensables ?
- Quel est le plan si une écriture est dupliquée ?

## 🎯 Compétence acquise

**Financial integrity mindset** (base néobanque).

---

# 🧭 ÉTAPE 1 — Lifecycle Paiement réel

**(Semaines 3–4)**

## 🎯 Contexte métier

Un paiement réel n’est pas instantané.

Il passe par états :

INITIATED → AUTHORIZED → CAPTURED → SETTLED (+ FAILED/REVERSED).

En Afrique, le **settlement peut être différé (J+1)** selon provider.

## ✅ Valeur pour Djamo

- Réduire les litiges (“mon débit est passé mais…”)
- Rendre les états explicables au support
- Construire une base pour chargeback/reversal

## 📊 Objectifs volumétriques

- **15 000 tx/jour**
- **20–30 req/sec**
- **P95 < 200 ms**
- **DB QPS ~ 100–150**

## 🔐 Contraintes sécurité

- Gestion robuste des statuts (pas de transitions illégales)
- Anti-replay (nonce/correlation) sur les opérations sensibles
- Journalisation d’audit : *who/what/when* (sans fuite PII)

## 🔧 Travaux

- State machine stricte (transition validation)
- Persistence states + timestamps
- Handling des reversals

## 🧠 Questions

- Différence CAPTURED vs SETTLED ?
- Quand un paiement est “final” ?
- Comment modéliser reversal et chargeback ?

## 🎯 Compétence acquise

**Money lifecycle thinking** (au-delà du CRUD).

---

# 🧩 ÉTAPE 2 — Modular Monolith discipliné

**(Semaines 5–6)**

## 🎯 Contexte métier

Les fonctions s’accumulent : payment, ledger, provider, risk, reconciliation.

Sans découpage interne, la vitesse de delivery chute.

## ✅ Valeur pour Djamo

- Permettre des équipes multiples sans chaos
- Rendre le système évolutif **avant** microservices
- Accélérer la livraison sans casser le core

## 📊 Objectifs volumétriques

- **30 000 tx/jour**
- **50 req/sec** (peak)
- **P95 < 250 ms**
- **DB QPS ~ 250–350**

## 🔐 Contraintes sécurité

- Autorisations **par module** (ex: risk module jamais accessible directement)
- Séparation stricte des accès données (ownership)
- RBAC interne pour opérations admin

## 🔧 Travaux

- Modules internes : Ledger / Payments / Providers / Risk / Reconciliation
- Interfaces explicites entre modules
- Tests d’intégration module-to-module

## 🧠 Questions

- Où sont les boundaries métier ?
- Qui possède le ledger ?
- Quels modules sont critiques en latence ?

## 🎯 Compétence acquise

**Decoupage de domaine sous croissance**.

---

# 🧱 ÉTAPE 3 — Migration Hexagonale (Clean architecture pragmatique)

**(Semaines 7–9)**

## 🎯 Contexte métier

Multi-provider + tests métier + simulation provider.

L’infrastructure change, le domaine ne doit pas bouger.

## ✅ Valeur pour Djamo

- Changer un provider sans casser le système
- Tester le core sans dépendre de la DB
- Réduire les regressions sur le money-flow

## 📊 Objectifs volumétriques

- **50 000 tx/jour**
- **80–100 req/sec**
- **P95 < 300 ms**
- **DB QPS ~ 400–550**

## 🔐 Contraintes sécurité

- Zero trust inter-adapters (validation & signature)
- Secrets management propre (vault/env)
- Hardened configs (CORS, headers, rate limit)

## 🔧 Travaux

- Domain pur (sans Spring)
- Ports & adapters (DB, HTTP, providers)
- Tests métier exhaustifs + tests adaptateurs

## 🧠 Questions

- Pourquoi hexa maintenant ?
- Comment remplacer un provider sans changer le domaine ?
- Qu’est-ce qui doit rester stable dans le core ?

## 🎯 Compétence acquise

**Architecture orientée change & testabilité**.

---

# 🔁 ÉTAPE 4 — Idempotency & Network Reality (anti double-débit)

**(Semaines 10–11)**

## 🎯 Contexte métier

Réseau instable + retries.

La fintech ne pardonne pas : **double débit = crise**.

## ✅ Valeur pour Djamo

- Protection contre double débit
- Support et litiges réduits
- Robustesse en conditions africaines réelles

## 📊 Objectifs volumétriques

- **70 000 tx/jour**
- **150 req/sec** peak
- **P95 < 350 ms**
- **DB QPS ~ 700–900**
- **Retry rate simulé : 5–10%**

## 🔐 Contraintes sécurité

- Idempotency keys signées/associées user/session
- Rate limiting strict (anti abuse)
- Protection brute-force / credential stuffing
- Audit immuable sur opérations financières

## 🔧 Travaux

- idempotency_log table + index
- correlation IDs end-to-end
- retry w/ jitter + timeout policies
- simulation provider “late confirmation”

## 🧠 Questions

- Que faire si provider répond après timeout ?
- Exactly-once vs at-least-once ?
- Comment éviter replay côté client ?

## 🎯 Compétence acquise

**Failure-aware payment engineering**.

---

# 🔄 ÉTAPE 5 — Event-Driven interne + Outbox (sans overengineering)

**(Semaines 12–13)**

## 🎯 Contexte métier

Un paiement déclenche : notification, risk update, reporting.

Le synchrone crée couplage et latence.

## ✅ Valeur pour Djamo

- Découpler sans multiplier les services
- Réduire la latence sur le parcours paiement
- Préparer la scalabilité sans complexité ops prématurée

## 📊 Objectifs volumétriques

- **100 000 tx/jour**
- **250 req/sec** peak
- **~1 000 events/sec** (peak)
- **DB QPS ~ 1 200–1 600**
- **P95 < 400 ms**

## 🔐 Contraintes sécurité

- Event payload minimal (no PII)
- Signature/versioning des events
- Idempotence consumer + anti replay
- Least privilege pour publishers/consumers

## 🔧 Travaux

- outbox table transactionnelle
- publisher batché
- consumers idempotents + processed_event table
- DLQ + retry strategy

## 🧠 Questions

- Dual-write problem ?
- Comment gérer backlog/lag ?
- Comment versionner les events ?

## 🎯 Compétence acquise

**Distributed thinking sans explosion microservices**.

---

# 🔍 ÉTAPE 6 — Reconciliation Engine (institutionnel)

**(Semaines 14–15)**

## 🎯 Contexte métier

Ledger interne ≠ rapports provider (CSV).

Settlement J+1.

C’est le quotidien d’une néobanque.

## ✅ Valeur pour Djamo

- Détection d’écarts financiers
- Réduction des pertes & fraudes
- Base pour clôture comptable et audit

## 📊 Objectifs volumétriques

- **120 000 tx/jour**
- Batch nightly **120k lignes**
- Temps matching **< 10 min**
- DB QPS batch **2 000–5 000** (burst)

## 🔐 Contraintes sécurité

- Chiffrement/contrôle d’accès sur rapports providers
- Piste d’audit sur chaque action de résolution
- Permissions “manual review” strictes

## 🔧 Travaux

- Import provider report
- Matching (provider_ref, amount, timestamp windows)
- States : MATCHED / MISMATCH / MISSING_INTERNAL / MISSING_PROVIDER
- API report + queue manual review

## 🧠 Questions

- Quelles règles auto vs manuel ?
- Comment éviter faux positifs ?
- Comment escalader sans bloquer le produit ?

## 🎯 Compétence acquise

**Fintech operations maturity**.

---

# 🚀 ÉTAPE 7 — Extraction Microservice Reconciliation

**(Semaines 16–18)**

## 🎯 Contexte métier

Reconciliation = batch lourd, asynchrone, isolable.

C’est le bon candidat pour microservice.

## ✅ Valeur pour Djamo

- Isoler workload batch (ne pas impacter paiements temps réel)
- Indépendance d’évolution/scale
- Résilience opérationnelle

## 📊 Objectifs volumétriques

- **150 000 tx/jour**
- **300–400 req/sec** peak (core)
- Batch reconciliation isolé
- Latence inter-service contrôlée

## 🔐 Contraintes sécurité

- AuthN/AuthZ inter-services (mTLS / JWT service-to-service)
- Isolation réseau (policies)
- Event contract signing & versioning

## 🔧 Travaux

- DB séparée
- Event contract versionné
- Tracing distribué
- Monitoring lag + retries

## 🧠 Questions

- Comment éviter breaking changes ?
- Comment rejouer des events ?
- Comment corréler un incident cross-service ?

## 🎯 Compétence acquise

**Microservices extraction pragmatique**.

---

# 🌐 ÉTAPE 8 — Multi-provider orchestration + Risk/Velocity (sécurité métier)

**(Semaines 19–20)**

## 🎯 Contexte métier

Providers instables. Fallback requis.

Et la fraude augmente avec le volume.

## ✅ Valeur pour Djamo

- Continuité de service en cas de provider down
- Contrôle du risque (fraude, abus)
- Confiance et conformité

## 📊 Objectifs volumétriques

- **200 000 tx/jour**
- **500 req/sec** peak
- Provider latency 200ms → 2s
- P95 < 450ms (hors provider)
- DB QPS ~ 2 500–3 500

## 🔐 Contraintes sécurité (fortes)

- Velocity limits (daily/hourly)
- Anomaly flags (pattern suspicious)
- Circuit breaker + bulkhead
- Device/session binding (anti replay)
- Audit complet des décisions risk

## 🔧 Travaux

- Circuit breaker/fallback strategy
- Bulkhead isolation provider pools
- Risk engine : hard limit / soft limit / manual review

## 🧠 Questions

- Quand déclencher fallback ?
- Comment éviter double débit sur fallback ?
- Hard vs soft limits ?
- Comment réduire faux positifs fraude ?

## 🎯 Compétence acquise

**Resilience + risk-aware leadership**.

---

# ☁️ ÉTAPE 9 — Containerisation & Orchestration Cloud (K8s)

**(Semaines 21–23)**

## 🎯 Contexte métier

Croissance x5 → scaling horizontal + déploiements sûrs.

## ✅ Valeur pour Djamo

- Déploiement rapide et sûr
- Scaling automatique sur pics
- Réduction MTTR

## 📊 Objectifs volumétriques

- **300 000 tx/jour**
- **700–800 req/sec** peak
- P95 < 450ms
- DB QPS ~ 3 000–5 000

## 🔐 Contraintes sécurité

- Secrets K8s (sealed/managed)
- Network policies
- RBAC cluster minimal
- Image scanning (SCA)
- Non-root containers

## 🔧 Travaux

- Docker prod-grade
- K8s deploy + HPA
- Readiness/liveness
- CI/CD + rollback

## 🧠 Questions

- DB bottleneck : comment atténuer ?
- Read replicas / cache / partition ?
- Stratégie rollback ?

## 🎯 Compétence acquise

**Cloud operational readiness**.

---

# 📊 ÉTAPE 10 — Observabilité + KPI métier (Lead mindset)

**(Semaines 24–26)**

## 🎯 Contexte métier

La direction ne veut pas “CPU à 80%”.

Elle veut savoir : *l’argent circule-t-il correctement ?*

## ✅ Valeur pour Djamo

- Pilotage par risque métier
- Détection proactive incidents
- Amélioration continue (SLO/SLA)

## 📊 Objectifs volumétriques

- **500 000 tx/jour**
- **1000+ req/sec** peak
- **P95 < 500ms** (end-to-end hors provider extrême)
- **DB QPS ~ 5 000–8 000**

## 🔐 Contraintes sécurité

- Logs centralisés sans PII
- Tamper-proof audit trail
- Alerting sur patterns fraude/risque
- Access control sur dashboards

## 🔧 Travaux

- Metrics business : settlement delay, mismatch rate, retry rate, provider error rate
- SLO : success rate, latency budget
- Tracing distribué + correlation IDs
- Incident playbooks

## 🧠 Questions Engineering Lead

- Quel KPI technique reflète un risque financier ?
- Quel SLO pour “paiement réussi” ?
- Quand déclencher incident vs dégradation contrôlée ?
- Quel plan de capacity ?

## 🎯 Compétence acquise

**Engineering leadership + product/risk alignment**.

---

## 🏁 Résultat final (ce que Djamo verra)

Pas “un dev qui aime Kafka”.

Mais un futur lead qui sait :

- construire un core financier correct
- introduire modularité puis hexa quand justifié
- maîtriser réseau + idempotency (réalité africaine)
- gérer reconciliation (institutionnel)
- extraire microservices avec intelligence
- orchestrer cloud quand le volume l’impose
- sécuriser le système à chaque niveau
- piloter par KPI métier

---
