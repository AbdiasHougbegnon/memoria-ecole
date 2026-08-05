# Memoria École

Plateforme de capture et de révision intelligente de cours : enregistrement de séance,
transcription en direct, résumés de cours, tuteur vocal interactif qui ne lâche pas une
notion tant qu'elle n'est pas comprise, QCM et exercices générés à partir du contenu réel
de la matière, correction de travaux papier photographiés.

## Ce que ça fait

- **Capture audio en direct** pendant un cours, découpée en segments de 30 s (aucune perte
  de données même en cas de coupure réseau).
- **Résumés de cours** générés automatiquement en fin de séance, toujours reliés au passage
  exact de la transcription qui les justifie.
- **Tuteur vocal interactif** : dialogue en voix naturelle avec l'étudiant, s'appuie sur tout
  le contenu déjà disponible d'une matière (résumés de cours, documents, travaux papier
  déjà corrigés) — pas seulement une seule séance. Suit un niveau de maîtrise par notion et
  change d'approche (reformulation, analogie) tant que l'étudiant n'a pas compris.
- **QCM et exercices générés par IA** à partir du contenu réel de la matière, avec deux
  modalités : réponse libre notée qualitativement, ou question à choix multiple.
- **Travail papier photographié** : l'étudiant envoie une photo de l'énoncé et une photo de
  sa réponse, l'IA corrige exercice par exercice, décompose en points de correction
  repliables, et propose un mode de parcours progressif avec vérification de compréhension
  (question de contrôle après chaque correction, jamais bloquante pour la navigation).
- **Couloirs de classe** : espaces partagés par cohorte, avec import de matières en masse et
  inscription auto-assignée par domaine email d'établissement.
- **Recherche sémantique** et **fils de mémoire** (regroupement automatique des séances
  traitant du même sujet) sur l'ensemble du contenu de cours.

## Pourquoi c'est technique intéressant

- **Architecture hexagonale stricte** : le domaine métier ne dépend d'aucun détail
  d'infrastructure — Azure Speech, Azure OpenAI et Azure Document Intelligence sont des
  ports remplaçables, jamais couplés à la logique métier.
- **Doctrine de traçabilité IA non négociable** : aucune correction, aucun résumé, aucune
  réponse du tuteur n'est jamais présentée comme vérité sans lien vers la transcription ou
  le document source exact — condition de vente pour un établissement scolaire qui doit
  pouvoir justifier une évaluation.
- **Interaction pédagogique conçue, pas juste un chatbot** : le tuteur vocal maintient un
  score de maîtrise par notion, choisit consciemment de reformuler plutôt que de répéter, et
  la vérification de compréhension après une correction propose deux modalités de réponse
  (QCM déterministe côté serveur, ou réponse libre évaluée qualitativement par IA) — sans
  jamais bloquer la progression de l'étudiant.
- **Résilience pensée dès la conception** : panne Azure pendant l'enregistrement d'un cours →
  l'audio continue d'être capturé, la transcription rattrape son retard automatiquement ;
  redémarrage serveur → aucune séance active perdue.
- **Maîtrise des coûts Azure** : chaque appel IA (transcription, génération de QCM,
  correction, synthèse vocale) est chiffré et suivi par service, avec budget mensuel
  configurable.
- **Qualité imposée en CI** : couverture de tests (JaCoCo) et analyse statique de sécurité
  (SpotBugs + FindSecBugs, seuil "High") bloquent toute régression avant fusion.

## Stack

**Backend** — Java 21, Spring Boot 3.3, PostgreSQL, Spring Security (JWT), Maven.
**Frontend** — React 19, TypeScript, Vite, Tailwind CSS.
**IA / Cloud** — Azure Speech (transcription + synthèse vocale), Azure OpenAI (résumés, QCM,
exercices, tuteur vocal, embeddings), Azure Document Intelligence (extraction de PDF et
photos), Azure AI Search (recherche sémantique).
**Reconnaissance de locuteur** — service Python maison (FastAPI + SpeechBrain ECAPA-TDNN),
sans dépendance à un service cloud propriétaire.
**Observabilité** — Prometheus, Grafana, OpenTelemetry (traces).
**Infra** — Docker, Docker Compose, Terraform (déploiement mono-instance par établissement).

## Lancer le projet

Prérequis : Docker, Docker Compose. Des clés Azure (Speech, OpenAI, Document Intelligence,
AI Search) sont optionnelles pour explorer le code — les fonctionnalités IA se dégradent
proprement (log + message clair) si elles sont absentes, le reste de l'application
fonctionne normalement.

```bash
cp .env.example .env   # renseigner les clés Azure si disponibles, sinon laisser vide
docker compose up --build postgres backend frontend speaker-service
```

L'application est servie sur `http://localhost` (le backend n'est jamais exposé
directement, uniquement via le reverse proxy nginx du frontend).

Développement backend/frontend séparé (sans Docker) :

```bash
cd backend && mvn spring-boot:run       # port 8080
cd frontend && npm install && npm run dev   # port 5173
```

## Qualité

```bash
cd backend && mvn clean verify   # tests + couverture JaCoCo + SpotBugs/FindSecBugs
cd frontend && npm run build && npm run lint
```

## Documentation

`docs/phases/` contient une fiche technique par incrément livré (contexte, décisions,
alternatives écartées, vérification effectuée) — l'historique de conception du projet,
phase par phase.
