# Correction du travail papier décomposée en points repliables — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout phase-27-correction-travail-papier-structuree
```

---

## 1. Le besoin

Retour direct sur l'affichage de la correction du travail papier (phase 24/26) : "un bloc de
texte, ce n'est pas du tout joli, et on ne peut pas réviser facilement". La correction était
un unique long paragraphe généré par l'IA, sans aucune structure exploitable côté frontend.

## 2. La décision de conception

### 2.1 — Structurer la sortie de l'IA elle-même, pas juste la mise en forme visuelle

Plutôt que de tenter de re-découper artificiellement un bloc de texte libre côté frontend
(fragile, dépend du style d'écriture du modèle), le schéma JSON demandé à Azure OpenAI change
directement : au lieu d'un champ `correction` unique, la réponse contient une `synthese_globale`
courte (1-2 phrases) et une liste `points` (1 à 8 selon la richesse réelle du travail), chacun
avec un `sujet` court (titre de 3-6 mots), un `constat` et une `correction_attendue` distincts.
Même doctrine de comptage variable déjà appliquée au QCM/exercices de matière (phase 23) :
jamais un nombre fixe de points.

### 2.2 — `@ElementCollection`/`@Embeddable`, le patron déjà établi pour les listes structurées

`QcmMatiere.questions` (liste de `QuestionQcm`) suit déjà ce patron pour stocker une liste de
sous-objets sur une entité JPA sans relation explicite. `TravailPapierMatiere.pointsCorrection`
(nouveau `@ElementCollection` de `PointCorrection`) le reproduit à l'identique -- nouvelle table
`travail_papier_points_correction`, aucune migration risquée (table neuve).

### 2.3 — Accordéon repliable/dépliable, pas un dépliage systématique

Chaque point démarre replié (juste son titre visible), cliquable individuellement pour
afficher le détail (constat + correction attendue), avec un bouton "Tout déplier/replier" --
répond explicitement à "on peut étirer ou fermer quand on clique, s'il y a le besoin".

## 3. Les fichiers, un par un

- `PointCorrection.java` (nouveau, `@Embeddable`) -- sujet, constat, correction attendue.
- `CorrectionTravailPapier.java` (édité) -- `(niveau, syntheseGlobale, points)` au lieu de
  `(niveau, correction)`.
- `CorrecteurTravailPapierAzureOpenAI.java` (édité) -- nouveau schéma JSON, parsing de la liste
  de points.
- `TravailPapierMatiere.java` (édité) -- `correctionSynthese` (texte) + `pointsCorrection`
  (`@ElementCollection`), remplace `correctionTexte`. `enregistrerCorrection` prend
  désormais 3 arguments.
- `TravailPapierService.java` (édité) -- `tenterCorrection` transmet la liste de points.
- `TravailPapierMatiereResponse.java` (édité) -- expose `correctionSynthese` +
  `pointsCorrection` (nouveau `PointCorrectionResponse`).
- `TuteurVocalService.java` (édité) -- `construireContexteMatiere` reconstruit un texte pour
  le tuteur à partir de la synthèse + des points (le tuteur continue de recevoir du texte,
  seule la persistance/l'API changent de forme).
- Frontend `types.ts`, `MatiereRevisionPage.tsx` (édités) -- nouveau composant
  `CorrectionTravailPapierAffichage` (accordéon avec état local d'ouverture par point).

## 4. Les tests

`mvn -B clean verify` : **360/360 tests** (inchangé en nombre, tests existants adaptés à la
nouvelle forme de `CorrectionTravailPapier`/`TravailPapierMatiere`), 0 finding
SpotBugs/FindSecBugs.

`npm run build` + `npm run lint` : propres.

## 5. Comment on a vérifié

Réessai réel (bouton "Corriger maintenant") sur `fiche_revision_risque_episens.pdf` : 8
points générés avec des titres courts et pertinents ("Origine des probabilités", "Théorie de
Douglas", "Knight et Stirling", etc.), chacun repliable/dépliable indépendamment, bouton
"Tout déplier" fonctionnel. Capture d'écran à l'appui dans les trois états (tout replié, un
point ouvert, tout déplié).

## 6. Limites connues, assumées, pas corrigées ici

- **Pas de capture séparée de l'énoncé** -- la correction reste basée uniquement sur le texte
  extrait de la photo du travail de l'étudiant, sans énoncé de référence fourni séparément.
  Discuté avec l'utilisateur comme évolution possible mais nécessitant une décision de
  conception (comment capturer l'énoncé : texte tapé, photo séparée, ou segmentation
  automatique d'une seule photo contenant les deux) -- pas encore tranché.
- **Pas de mode interactif question par question** ("apprentissage progressif avec simulation
  de partenaire de travail" demandé par l'utilisateur) -- resterait un chantier séparé,
  proche dans l'esprit du tuteur vocal (tour par tour) mais ancré sur le travail de
  l'étudiant plutôt que sur des notions générées.

## 7. Pour reprendre seul

- Code de référence exact : `git checkout phase-27-correction-travail-papier-structuree`.
- Prochaine étape naturelle si demandée : décider du mécanisme de capture de l'énoncé avant
  de construire le mode interactif question par question.
