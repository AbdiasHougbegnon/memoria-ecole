# Maîtrise des coûts Azure

Le master prompt, section *"Maîtrise des coûts Azure"* : *"Le coût des services IA peut
exploser sans discipline"* — mise en cache des embeddings, suppression des fichiers
temporaires, archivage à froid de l'audio ancien, regroupement des appels modèles,
réutilisation des résumés existants, quotas par tenant, monitoring des coûts par
service/tenant avec alertes.

## 1. État de chaque principe

| Principe | État |
|---|---|
| Réutilisation des résumés | Déjà fait (mise en cache par session, génération à la demande) |
| Regroupement des appels modèles | Déjà largement satisfait (`genererEmbeddings` accepte une liste) |
| Suppression des fichiers temporaires | Non applicable — aucun fichier temporaire n'est créé nulle part dans le projet |
| **Cache des embeddings** | **Corrigé dans cette brique** — voir §2 |
| **Quotas par tenant** | **Traité comme un budget global de l'instance** — voir §3 |
| **Monitoring des coûts, alertes** | **Nouveau dans cette brique** — voir §4 |
| Archivage à froid de l'audio | **Différé** — voir §5 |

## 2. Cache des embeddings

`GenerateurEmbeddingAzureOpenAI` maintient un cache LRU en mémoire (borné,
`memoria.cache.embeddings.taille-max=500`), clé = texte normalisé (`trim().toLowerCase()`).
Tout texte déjà embeddé (recherche répétée ou tout autre appelant du port) évite un appel
Azure. Pas de TTL : un embedding pour un texte donné est stable dans le temps sauf
changement de version de modèle côté Azure — risque faible, assumé.

## 3. "Quotas par tenant" = budget global de l'instance

Le modèle de déploiement du projet est une instance dédiée par client (pas de multi-tenant
interne, voir `docs/deploiement.md`) : **l'instance déployée EST le tenant**. Un "quota par
tenant" se traduit donc par un budget mensuel global, configurable :

```properties
memoria.cout.azure.budget-mensuel-euros=100   # ou MEMORIA_COUT_BUDGET_MENSUEL_EUROS
memoria.cout.azure.mode-strict=false          # ou MEMORIA_COUT_MODE_STRICT
```

Un dépassement est **toujours logué** (`WARN`), quel que soit le mode. En mode
`mode-strict=false` (défaut), rien d'autre ne se passe — cohérent avec la doctrine "outil
d'aide, pas de surveillance" déjà appliquée ailleurs dans ce projet (rappels d'engagements,
tableau de bord). En `mode-strict=true`, une `BudgetAzureDepasseException` est levée à
chaque nouvel appel Azure une fois le budget atteint — l'appelant dégrade gracieusement,
comme les autres cas d'indisponibilité Azure déjà gérés dans ce projet.

Le compteur mensuel est **en mémoire, remis à zéro au changement de mois calendaire et au
redémarrage de l'application** — pas persisté. Limite assumée pour ce premier increment.

## 4. Monitoring des coûts (Prometheus/Grafana)

Nouvelles métriques exposées via `/actuator/prometheus` (aucune nouvelle infra — réutilise
Prometheus/Grafana déjà provisionnés en phase-7) :

- `memoria_azure_appels_total{service=...}` — compteur cumulatif, nombre d'appels par service Azure.
- `memoria_azure_cout_euros_total{service=...}` — compteur cumulatif, coût estimé par service.
- `memoria_azure_cout_mensuel_euros` — jauge, coût estimé du mois calendaire en cours.
- `memoria_azure_budget_mensuel_euros` — jauge, budget configuré (pour comparaison visuelle).

3 nouveaux panels dans le dashboard Grafana "Memoria Backend" : appels par service, coût
estimé par service (€/heure, via `increase(...[1h])`), jauge coût mensuel vs budget.

### Comment le coût est estimé, service par service

| Service | Base de calcul | Configurable |
|---|---|---|
| `OPENAI_CHAT` / `OPENAI_EMBEDDING` | Tokens réels (`usage.total_tokens` de la réponse Azure) si présent, sinon forfait | `memoria.cout.azure.openai.euros-par-1k-tokens` |
| `SPEECH_STT` | Durée fixe du chunk (30s, alignée sur le moteur de capture) | `memoria.cout.azure.speech.euros-par-heure` |
| `SPEECH_TTS` | Nombre de caractères en entrée (unité de facturation réelle d'Azure Neural TTS) | `memoria.cout.azure.speech.euros-par-1k-caracteres` |
| `AI_SEARCH` | Forfait par appel | `memoria.cout.azure.forfait-par-appel-euros` |
| `DOCUMENT_INTELLIGENCE` | Nombre de pages analysées (extrait de la réponse) × forfait par page | `memoria.cout.azure.forfait-par-appel-euros` |

**Toutes les valeurs par défaut sont des approximations documentées, pas une facturation
contractuelle réelle** — les vrais tarifs Azure varient par région/contrat/niveau de
service et changent dans le temps. À ajuster via les propriétés ci-dessus selon le contrat
réel du client. `AI_SEARCH` en particulier est typiquement facturé à la capacité
provisionnée (réplicas/partitions), pas à l'appel — ce compteur suit un volume relatif
d'utilisation, pas une facturation marginale réelle.

## 5. Limites connues

- **Archivage à froid de l'audio ancien : différé, pas construit ici.** Le stockage actuel
  est un disque local permanent, sans notion de tier (documenté ailleurs comme
  "remplaçable... Azure Blob demain"). Un vrai archivage à froid suppose une intégration
  Azure Blob Storage d'abord. Supprimer l'audio ancien sur disque local casserait la
  doctrine de traçabilité du projet ("l'utilisateur peut toujours descendre du résumé à
  l'audio") — pas un compromis acceptable, donc explicitement pas fait.
- **Compteur mensuel non persisté** — remis à zéro au redémarrage de l'application.
- **Coûts `AI_SEARCH`/`DOCUMENT_INTELLIGENCE` approximatifs**, pas basés sur une facturation
  réelle mesurée.
- **`IdentificateurLocuteurAzureSpeech` non instrumenté** — code de référence désactivé
  (plus un bean Spring, Azure Speaker Recognition retiré par Microsoft), aucun appel réel
  à suivre.
