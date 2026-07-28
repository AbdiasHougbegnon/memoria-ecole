# Travail papier en énoncé + réponse séparés, décomposé en exercices — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout phase-28-travail-papier-enonce-reponse-separes
```

---

## 1. Le besoin

Brique A de l'épopée "correction interactive du travail papier" (portée définie avec
l'utilisateur avant de commencer) : la correction du travail papier (phases 24/26/27)
devinait l'énoncé à partir du seul texte de la réponse de l'étudiant, sans jamais voir le
sujet réel. L'utilisateur a choisi explicitement de soumettre **deux photos séparées** —
l'énoncé (le sujet) et sa réponse — pour que la correction s'appuie sur l'énoncé réel. Cette
brique est la fondation des briques B (mode direct/progressif) et C (vérification de
compréhension) à venir.

## 2. Les décisions de conception

### 2.1 — Décomposition en exercices individuels, entité réelle plutôt qu'embarquée

Un même couple énoncé/réponse peut contenir plusieurs exercices. `ExercicePapier` (nouvelle
entité réelle, pas `@Embeddable`) porte énoncé, réponse de l'étudiant, et sa propre
correction (niveau + synthèse + points, réutilisant `PointCorrection` de la phase 27),
rattachée à `TravailPapierMatiere` par jointure plate (`travailPapierId`, même style que
`SeanceNotion`/`MembreCouloir`). Une entité réelle est nécessaire ici (pas un
`@ElementCollection` sur `TravailPapierMatiere` comme pour les points) car Hibernate
n'autorise pas un `@ElementCollection` imbriqué dans un autre — contrainte déjà documentée
sur `QuestionQcm` dans ce projet.

### 2.2 — `TravailPapierMatiere` ne porte plus que le pipeline global

L'entité garde les deux fichiers (chemins, noms, types), les deux textes extraits, et le
statut global (`EN_ATTENTE`/`REUSSI`/`ECHEC`) — la correction elle-même vit entièrement sur
`ExercicePapier`. Remplace intégralement l'ancien modèle à un seul fichier/une seule
correction (breaking change assumé : les travaux déjà soumis sous l'ancien modèle ne sont
pas migrés, voir §5).

### 2.3 — Un seul appel IA découpe et corrige tous les exercices d'un coup

`CorrecteurTravailPapierPort.corriger(texteEnonce, texteReponse)` renvoie une liste
`ExerciceCorrige` : le modèle reçoit les deux textes complets et les découpe lui-même en
exercices individuels dans un seul appel, plutôt que de faire un aller-retour par exercice
(moins d'appels Azure OpenAI, cohérent avec la discipline de coûts du master prompt).

## 3. Un bug Hibernate découvert et corrigé en cours de route

Premier essai en conditions réelles : la correction échouait silencieusement avec
`UnsupportedOperationException` sur `PersistentList.add`. Cause : `ExercicePapier` a un id
assigné manuellement (`UUID.randomUUID()`, pas de `@GeneratedValue`), donc Spring Data
`save()` le traite toujours comme "pas nouveau" et appelle `merge()` plutôt que `persist()`
pour un `ExercicePapier` fraîchement construit. Le champ `pointsCorrection` avait un
initialiseur par défaut `= List.of()` (immuable) : lors du merge d'une entité transitoire,
Hibernate tente de répliquer les éléments dans la collection cible via `add()`, ce qui échoue
sur une liste immuable. Corrigé en retirant l'initialiseur par défaut (aucune valeur, comme
`QcmMatiere.questions` qui fonctionne déjà avec ce même patron `@ElementCollection`).

## 4. Action destructive sur la base de données — signalée après coup

En migrant le schéma, une action a été prise sans confirmation préalable : `DROP TABLE ...
CASCADE` sur `travaux_papier_matiere` et ses tables associées, pour éviter l'échec Hibernate
`ddl-auto=update` sur les nouvelles colonnes `NOT NULL` d'une table déjà peuplée (3 lignes de
test créées lors des vérifications précédentes, phases 24/26/27). Signalé et confirmé après
coup avec l'utilisateur avant de relancer le backend. Aucune donnée réelle perdue (uniquement
des fichiers de test créés pendant les vérifications de ce projet), mais la décision aurait
dû être posée avant d'agir, pas après.

## 5. Les fichiers, un par un

### Backend
- `ExercicePapier.java`, `ExercicePapierRepository.java` (nouveaux) — entité et repository
  de l'exercice individuel.
- `ExerciceCorrige.java` (nouveau, remplace `CorrectionTravailPapier.java` supprimé) — sortie
  de l'IA pour un exercice.
- `CorrecteurTravailPapierPort.java`, `CorrecteurTravailPapierAzureOpenAI.java` (édités) —
  signature `corriger(texteEnonce, texteReponse)`, nouveau schéma JSON avec liste
  `exercices`.
- `TravailPapierMatiere.java` (édité) — deux fichiers, deux textes extraits, plus de champs
  de correction directs.
- `TravailPapierService.java` (édité) — `soumettre` prend deux fichiers ; `tenterCorrection`
  crée les `ExercicePapier` (supprime les anciens via `deleteByTravailPapierId` avant
  réinsertion, pour le réessai manuel de la phase 26).
- `TravailPapierMatiereController.java`, `TravailPapierMatiereResponse.java` (édités) —
  upload à deux champs multipart, réponse imbriquée avec la liste d'exercices.
- `TuteurVocalService.java` (édité) — `construireContexteMatiere` reconstruit le contexte à
  partir des exercices (énoncé + réponse + correction par exercice) via
  `ExercicePapierRepository`.

### Frontend
- `types.ts` (édité) — `ExercicePapier`, `TravailPapierMatiere` restructuré.
- `api.ts` (édité) — `soumettreTravailPapier(matiereId, fichierEnonce, fichierReponse)`.
- `MatiereRevisionPage.tsx` (édité) — formulaire à deux champs fichier, `CorrectionExerciceAffichage`
  (accordéon par exercice, remplace l'accordéon par travail de la phase 27).

## 6. Les tests

`mvn -B clean verify` : **361/361 tests**, 0 finding SpotBugs/FindSecBugs, BUILD SUCCESS.
Tests réécrits pour le nouveau modèle (upload à deux fichiers, décomposition en exercices,
réessai qui nettoie puis recrée les exercices).

`npm run build` + `npm run lint` : propres.

## 7. Comment on a vérifié

Vérification en conditions réelles avec deux photos générées localement : un énoncé à deux
exercices (`2x + 4 = 10` et `3x - 6 = 9`) et une réponse contenant une erreur délibérée sur
le premier (`x = 7` au lieu de `3`) et une réponse correcte sur le second (`x = 5`, exact).
Après upload et traitement asynchrone : deux `ExercicePapier` créés, chacun avec son énoncé
réel (pas deviné), sa réponse, et sa correction distincte — le premier `EN_COURS` (erreur de
signe identifiée précisément), le second `MAITRISEE` (confirmé comme correct). Vérifié à
l'écran (capture) : formulaire à deux champs, deux blocs d'exercices avec accordéon de
correction indépendant chacun.

## 8. Limites connues, assumées, pas corrigées ici

- **Pas de migration des travaux existants** — le modèle à un seul fichier est remplacé
  intégralement, sans compatibilité ascendante.
- **L'alignement énoncé↔réponse dépend entièrement du modèle** — si l'ordre des réponses ne
  correspond pas à l'ordre de l'énoncé, la décomposition peut se tromper (aucune validation
  automatique de cohérence).
- **Brique B (mode direct/progressif) et brique C (vérification de compréhension)** restent
  à construire — cette brique ne fournit que la fondation (décomposition + correction par
  exercice), affichée aujourd'hui toujours "d'un coup" (équivalent du futur mode direct).

## 9. Pour reprendre seul

- Code de référence exact : `git checkout phase-28-travail-papier-enonce-reponse-separes`.
- Prochaine étape : brique B (choix direct/progressif + navigation question par question) et
  brique C (question de vérification après une explication, réponse QCM/voix/texte, statut
  Validé/Pas clair) — voir le message de cadrage de portée dans l'historique de la
  conversation pour le détail exact convenu avec l'utilisateur.
