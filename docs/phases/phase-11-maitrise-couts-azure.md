# Maîtrise des coûts Azure — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout phase-11-maitrise-couts-azure
```

---

## 1. Le besoin

Le master prompt consacre une section entière, jamais adressée dans aucune brique de cette
session, à *"Maîtrise des coûts Azure"* (*"les entreprises poseront la question très
vite"*) : mise en cache des embeddings, suppression des fichiers temporaires, archivage à
froid de l'audio ancien, regroupement des appels modèles, réutilisation des résumés
existants, quotas par tenant, monitoring des coûts par service/tenant avec alertes.
`docs/phases/phase-7-observabilite.md` l'avait explicitement classée hors périmètre, à
traiter séparément — c'est cette brique.

Un état réel de chaque principe a été vérifié par recherche dans le code, pas supposé :
réutilisation des résumés et regroupement des appels modèles étaient déjà satisfaits ;
suppression des fichiers temporaires était non applicable (aucun fichier temporaire n'existe
nulle part dans le projet) ; le cache des embeddings, les quotas et le monitoring des coûts
étaient de vrais trous.

## 2. Les décisions de conception

### 2.1 — Le vrai trou : aucun cache sur les embeddings

`RechercheService.rechercher()` ré-appelait Azure OpenAI pour l'embedding de la requête à
chaque recherche, y compris pour une requête identique répétée. `GenerateurEmbeddingAzureOpenAI`
maintient désormais un cache LRU en mémoire, borné (`LinkedHashMap` + `removeEldestEntry`,
`memoria.cache.embeddings.taille-max=500`, pas de nouvelle dépendance), clé = texte
normalisé (`trim().toLowerCase()`). Vérifié en conditions réelles (§5) : une requête répétée
n'incrémente plus le compteur `OPENAI_EMBEDDING`.

### 2.2 — "Quotas par tenant" = budget global de l'instance

Le modèle de déploiement du projet est une instance dédiée par client, pas de multi-tenant
interne (`docs/deploiement.md`) : l'instance déployée EST le tenant. Un "quota par tenant"
se traduit donc par un budget mensuel global (`memoria.cout.azure.budget-mensuel-euros`),
pas par une nouvelle entité `Tenant`. Dépassement toujours logué en `WARN` ; ne bloque rien
par défaut (`memoria.cout.azure.mode-strict=false`), cohérent avec la doctrine "outil
d'aide, pas de surveillance" déjà appliquée ailleurs dans ce projet (rappels d'engagements,
tableau de bord). En mode strict, `BudgetAzureDepasseException` — l'appelant dégrade
gracieusement, comme les autres cas d'indisponibilité Azure déjà gérés.

### 2.3 — Instrumentation centralisée, coût estimé par unité de facturation réelle

`CoutAzureService.enregistrerAppel(ServiceAzure, double)` — un point d'entrée unique,
injecté dans chaque adaptateur Azure. Le coût par appel est calculé selon l'unité de
facturation réelle du service, pas un forfait uniforme partout : tokens réels
(`usage.total_tokens`) pour les appels OpenAI (avec repli forfaitaire si le champ est
absent), durée fixe du chunk pour Speech STT, nombre de caractères pour Speech TTS, nombre
de pages réelles pour Document Intelligence, forfait documenté comme approximation pour
AI Search (typiquement facturé à la capacité, pas à la requête).

### 2.4 — Découverte : un adaptateur sur les 10 prévus est du code mort

`IdentificateurLocuteurAzureSpeech` n'a pas d'annotation `@Component` et porte un
commentaire de classe explicite : Azure Speaker Recognition a été retiré par Microsoft le
30 septembre 2025, la reconnaissance de locuteur en production passe par le service
auto-hébergé gratuit `speaker-service/`. Ce fichier n'est jamais instancié par Spring —
l'instrumenter aurait été du code mort. Skippé, et `SPEAKER_RECOGNITION` retiré de l'enum
`ServiceAzure` (n'aurait jamais été tagué en pratique). Le compte réel d'adaptateurs
instrumentés est donc **9, pas 10**.

### 2.5 — Compteurs cumulatifs, jauge mensuelle séparée

`memoria.azure.appels.total` et `memoria.azure.cout.euros.total` sont des `Counter`
Micrometer cumulatifs pour toujours (pattern Prometheus idiomatique — Grafana calcule la
fenêtre "ce mois-ci" via `increase(...[1h])`, pas l'application qui gère un reset). Le
budget mensuel, lui, a besoin d'un total remis à zéro chaque mois pour la décision de
blocage : `AtomicReference<YearMonth>` + `DoubleAdder`, exposé séparément via une `Gauge`
`memoria.azure.cout.mensuel.euros` — en mémoire, non persisté, remis à zéro au redémarrage
(limite assumée, §6).

## 3. Les fichiers, un par un

### `core/cout/ServiceAzure.java`, `CoutAzureService.java`, `BudgetAzureDepasseException.java` (nouveaux)
Le point d'entrée d'instrumentation, décrit en §2.3/§2.5.

### `CoutAzureServiceTest.java` (nouveau)
7 tests, `SimpleMeterRegistry` réel (pas de mock) : compteurs par service, jauge mensuelle
cumulée, non-blocage par défaut au-delà du budget, exception en mode strict, pas
d'exception avant le budget en mode strict, jauge de budget exposée, forfait configurable.

### `GenerateurEmbeddingAzureOpenAI.java` (édité)
Cache LRU décrit en §2.1, plus l'appel `CoutAzureService` pour les cache-misses uniquement.

### 9 adaptateurs Azure existants (édités, un appel `enregistrerAppel` ajouté chacun)
`GenerateurResumeAzureOpenAI`, `GenerateurCompteRenduAzureOpenAI`,
`GenerateurResumeCoursAzureOpenAI`, `GenerateurTourTuteurAzureOpenAI` (les 4 appels
OpenAI-chat, tokens réels), `TranscripteurAzureSpeech` (durée fixe du chunk, alignée sur
`Recorder.tsx`), `SynthetiseurVocalAzure` (caractères en entrée), `RechercheAzureAiSearch`
(forfait, deux sites : indexation + recherche), `ExtracteurDocumentAzureDocumentIntelligence`
(pages réelles extraites de la réponse).

### `application.properties` (édité)
Budget, mode strict, tarifs par service (tous documentés comme approximatifs, jamais une
facturation contractuelle réelle), taille du cache embeddings, durée du segment audio.

### `infra/observability/grafana/dashboards/memoria-backend.json` (édité)
3 nouveaux panels : appels par service (cumulé), coût estimé par service (€/heure via
`increase(...[1h])`), jauge coût mensuel vs budget avec seuils vert/orange(70%)/rouge(100%).

### `docs/couts-azure.md` (nouveau)
Guide opérationnel — miroir de `docs/qualite.md` — décrivant chaque métrique, comment
lire le budget, et les limites assumées.

## 4. Les tests

205/205 tests backend (198 existants + 7 nouveaux). `mvn -B verify` : `BUILD SUCCESS`, 0
finding SpotBugs/FindSecBugs, seuil de couverture maintenu. Une régression de compilation a
été rencontrée puis corrigée : `TranscripteurAzureSpeechTest` construisait l'ancien
constructeur à 4 arguments ; corrigé pour construire un vrai `CoutAzureService` (via
`SimpleMeterRegistry`) et l'injecter dans le nouveau constructeur à 7 arguments.

## 5. Comment on a vérifié en conditions réelles

Backend démarré sur un port dédié (`SERVER_PORT=8099`, pour ne pas interférer avec
l'instance déjà lancée par l'utilisateur sur 8080), inscription d'un utilisateur puis la
même recherche exécutée deux fois de suite via de vrais appels REST. Scrape direct de
`/actuator/prometheus` :
```
memoria_azure_appels_total{service="AI_SEARCH"} 2.0
memoria_azure_appels_total{service="OPENAI_EMBEDDING"} 1.0
memoria_azure_budget_mensuel_euros 100.0
memoria_azure_cout_euros_total{service="AI_SEARCH"} 0.02
memoria_azure_cout_euros_total{service="OPENAI_EMBEDDING"} 8.0E-6
memoria_azure_cout_mensuel_euros 0.020007999999999998
```
Preuve directe que le cache fonctionne (1 seul appel d'embedding malgré 2 recherches
identiques) et que `AI_SEARCH` n'est, à raison, pas mis en cache (2 appels — l'index est
interrogé à chaque recherche, seul l'embedding de la requête est réutilisable). Instance de
vérification arrêtée proprement après confirmation de son PID réel via `wmic` (jamais un
`taskkill` sur un PID non vérifié).

## 6. Limites connues, assumées, pas corrigées ici

- **Archivage à froid de l'audio ancien : différé.** Le stockage actuel est un disque local
  permanent sans notion de tier ; un vrai archivage à froid suppose une intégration Azure
  Blob Storage d'abord. Une approximation locale (supprimer l'audio ancien) casserait la
  doctrine de traçabilité du projet — pas fait.
- **Compteur mensuel non persisté** — remis à zéro au redémarrage de l'application, pas
  seulement au changement de mois.
- **Tarifs par défaut approximatifs**, pas la facturation contractuelle réelle du client —
  à ajuster via `application.properties` selon le contrat réel.
- **`AI_SEARCH` en forfait par appel** — la facturation réelle est typiquement à la
  capacité provisionnée, pas par requête ; ce compteur suit un volume relatif, pas un coût
  marginal réel.
- **`IdentificateurLocuteurAzureSpeech` non instrumenté** — code de référence désactivé
  (voir §2.4), jamais appelé en pratique.

## 7. Pour reprendre seul

- Code de référence exact : `git checkout phase-11-maitrise-couts-azure`
- Point d'entrée unique pour tout nouvel adaptateur Azure futur :
  `coutAzureService.enregistrerAppel(ServiceAzure.XXX, coutEstimeEuros)`.
- Prochaine direction possible : persister le compteur mensuel (si l'instance redémarre
  souvent en production et que la fenêtre "ce mois-ci" doit survivre aux redémarrages), ou
  un vrai archivage à froid une fois Azure Blob Storage intégré.
