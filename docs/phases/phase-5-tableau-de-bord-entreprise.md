# Phase 5 : tableau de bord Entreprise — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout phase-5-tableau-de-bord-entreprise
```
Ce tag pointe sur le commit `34e365b`, vérifié avec des données réelles insérées puis retirées, capture d'écran à l'appui.

---

## 1. Le besoin

Point du roadmap Phase 5 du master prompt : "suivi fin et tableaux de bord (entreprise)". Choisi comme suite naturelle du chantier engagements (suivi, rappels, boucle fermée), sans dépendance externe non résolue — contrairement à la reconnaissance de voix récurrente, bloquée sur un choix de fournisseur.

## 2. La décision de conception qui cadre tout le reste

Le master prompt pose une règle ferme, pas une suggestion : *"Outil d'aide, pas de surveillance. Les rappels vont d'abord à la personne concernée. **Le manager voit l'avancement global, pas le détail permanent des retards individuels.** L'employé garde le contrôle de ses engagements."*

Conséquence directe sur la conception : le tableau de bord n'expose **que des agrégats** — jamais un engagement précis, jamais un utilisateur précis. `TableauDeBordEntrepriseResponse` ne contient que des nombres (total, répartition par statut, taux, compteur de retard) ; aucun champ ne permet de remonter à une ligne `Engagement` ou à un `Utilisateur`. Ce n'est pas une omission à combler plus tard — c'est la contrainte structurante de la brique.

Note en passant : il n'existe aujourd'hui aucun rôle "manager" ni structure d'équipe/projet dans le modèle Entreprise (`Engagement` n'a pas de notion de propriétaire ou d'équipe, cohérent avec le modèle "outil d'équipe partagé" déjà en place depuis la Phase 5 sécurité). Le tableau de bord agrège donc sur **tous** les engagements du système, visible par tout utilisateur authentifié — pas de scope par équipe à inventer pour cette première brique.

## 3. Les décisions de conception

### 3.1 — Agrégation en mémoire, pas de requêtes SQL complexes

`TableauDeBordEntrepriseService.obtenirTableauDeBord()` appelle `engagementRepository.findAll()` puis calcule les statistiques via des streams Java, plutôt que d'écrire des requêtes JPQL `GROUP BY`/`COUNT`. Choix assumé de simplicité : le volume d'engagements de ce projet à ce stade ne justifie pas l'optimisation, cohérent avec le principe du projet de ne pas construire pour un besoin hypothétique futur.

### 3.2 — Le taux de complétion exclut les rejetés

Un engagement rejeté n'a jamais eu vocation à être terminé — l'inclure au dénominateur ferait mécaniquement baisser le taux de complétion sans que ce soit un signal utile. `tauxCompletion = termines / (total - rejetes)`, `0` si le dénominateur est nul.

### 3.3 — Couleurs : réutiliser l'identité visuelle existante, pas en inventer une

Le skill dataviz du projet a été invoqué avant d'écrire le graphique. Les quatre statuts (`EN_ATTENTE`/`CONFIRME`/`TERMINE`/`REJETE`) sont déjà des badges colorés sur `EngagementsPage` (ambre/bleu/vert/gris) — plutôt que de dériver une palette catégorielle neuve, ces teintes ont été reprises pour la cohérence visuelle dans toute l'application, avec des nuances `-500`/`-400` (plutôt que les `-400`/`-300` des badges, trop clairs) pour rester dans la bande de luminosité OKLCH validée par `scripts/validate_palette.js`. Le résultat reste sous le seuil de contraste 3:1 pour certaines teintes (attendu pour des couleurs de statut sur fond clair, selon le skill) — mitigé par une légende texte **toujours visible** (jamais seulement au survol), la légende étant la condition de secours exigée par le skill dans ce cas.

## 4. Les fichiers backend

### `com.memoria.entreprise.tableaudebord` (nouveau package)

```java
public record TableauDeBordEntrepriseResponse(
        long total,
        Map<StatutEngagement, Long> parStatut,
        double tauxCompletion,
        long enRetard
) {}
```

```java
@Service
public class TableauDeBordEntrepriseService {
    public TableauDeBordEntrepriseResponse obtenirTableauDeBord() {
        List<Engagement> engagements = engagementRepository.findAll();
        // compte par statut (EnumMap initialise a 0 pour chaque valeur)
        // tauxCompletion = termines / (total - rejetes), 0 si denominateur nul
        // enRetard = CONFIRME avec dateEcheance depassee
    }
}
```

`GET /api/v1/entreprise/tableau-de-bord` — pas de paramètre, pas de restriction d'accès particulière (tout utilisateur authentifié), cohérent avec `listerTous()` sur `EngagementController` qui a le même périmètre de visibilité.

## 5. Le frontend

`TableauDeBordPage.tsx` (route `/tableau-de-bord`, lien ajouté dans la nav à côté de "Engagements") :
- Trois tuiles statistiques (total, taux de complétion, en retard) — pas de sparkline/tendance dans cette brique (aucun historique temporel disponible, voir §6).
- Une barre empilée horizontale par statut (`flex` + `gap-[2px]` entre segments, largeurs proportionnelles), légende en dessous avec pastille de couleur + libellé + effectif — toujours visible, jamais seulement au survol.
- Une ligne de rappel explicite sous le graphique : *"Vue d'ensemble uniquement — pour le détail par personne, chacun gère ses propres engagements"* — rendre la contrainte du §2 visible à l'utilisateur, pas seulement respectée en silence côté API.

## 6. Les tests

`TableauDeBordEntrepriseServiceTest.java` — 5 tests : tout à zéro si aucun engagement, comptage correct par statut, exclusion des rejetés du taux de complétion, détection d'un engagement confirmé en retard, un engagement terminé avec échéance passée n'est **pas** compté en retard (le statut prime sur la date).

`cd backend && mvn test` — **154/154 tests** passent (149 précédents + 5 nouveaux). `cd frontend && npx tsc --noEmit` — aucune erreur.

## 7. Comment on a vérifié en conditions réelles

Quatre engagements de test insérés directement en base avec des statuts variés (`EN_ATTENTE`, `CONFIRME` à échéance future, `CONFIRME` en retard, `REJETE`), en plus de deux `TERMINE` déjà présents dans la base de développement (issus de vérifications précédentes). Requête réelle :

```
GET /api/v1/entreprise/tableau-de-bord
{"total":6,"parStatut":{"EN_ATTENTE":1,"CONFIRME":2,"REJETE":1,"TERMINE":2},"tauxCompletion":0.4,"enRetard":1}
```

Conforme : 2 terminés sur (6 − 1 rejeté) = 40 %, 1 seul des deux `CONFIRME` est réellement en retard. Vérifié aussi visuellement dans le vrai navigateur : trois tuiles (6 / 40 % / 1), barre empilée avec les quatre segments proportionnels et la légende correspondante. Données de test retirées après la capture.

## 8. Limites connues, assumées, pas corrigées ici

- **Pas de scope par équipe/manager** — aucune notion de ce type n'existe dans le modèle Entreprise actuel ; le tableau de bord agrège sur l'ensemble du système, visible par tout utilisateur authentifié.
- **Pas de tendance temporelle** — aucun historique des statuts n'est conservé (`Engagement` ne garde que son état courant et `dateDerniereMaj`), donc pas de graphique d'évolution dans le temps. Ajouter cela demanderait de journaliser les transitions de statut, hors périmètre ici.
- **Agrégation en mémoire** — voir §3.1, un choix de simplicité assumé, pas optimisé pour un grand volume.
- **Aucun filtre** (par période, par type de session, etc.) — une seule vue globale, pas de contrôles de filtrage dans cette première brique.

## 9. Pour reprendre seul

- Code de référence exact : `git checkout phase-5-tableau-de-bord-entreprise`
- Pour ajouter une tendance temporelle : il faudrait d'abord journaliser les transitions de statut d'un engagement (nouvelle table d'historique), puis agréger par période dans `TableauDeBordEntrepriseService`.
- Pour un scope par équipe : dépend d'abord de l'introduction d'une notion d'équipe/projet dans le modèle Entreprise (absente aujourd'hui) — pas la peine d'anticiper la forme que ça prendra avant que le besoin soit confirmé.
- Chemin de bout en bout : `TableauDeBordPage.tsx` → `obtenirTableauDeBordEntreprise` (`api.ts`) → `TableauDeBordEntrepriseController` → `TableauDeBordEntrepriseService` → `EngagementRepository.findAll()`.
