# Réessai manuel de la correction du travail papier — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout phase-26-reessai-correction-travail-papier
```

---

## 1. Le besoin

En vérifiant la correction automatique (phase 24), l'utilisateur a demandé pourquoi
`fiche_revision_risque_episens.pdf` affichait "Correction indisponible pour le moment".
Deux causes distinctes, toutes deux déjà documentées comme limite connue de la phase 24,
mais sans aucun moyen d'y remédier autrement qu'en re-téléversant le fichier :

1. Ce fichier avait été soumis **avant** le déploiement de la correction automatique — pas
   de correction rétroactive, comme prévu.
2. En rejouant la correction pour vérifier, un **deuxième** cas est apparu : la première
   tentative de correction échouait avec une erreur de parsing JSON (la sortie du modèle
   contenait une séquence d'échappement invalide, ex. `\-` dans un texte structuré en liste
   numérotée) — dégradation déjà correctement absorbée (le texte extrait n'est pas perdu,
   voir phase 24 §2.1), mais sans recours pour l'utilisateur autre que de tout re-soumettre.

## 2. Correction (au sens correctif) apportée en cours de route au diagnostic précédent

En creusant ce cas précis, un troisième travail papier de ce compte de test
(`733efa00-...tableau_test.png`) s'est révélé contenir le texte extrait suivant :
*"Reunion Memoria / Budget projet : 15000 euros Deadline : vendredi 12 juillet / Responsable
: Marie Dupont"*. C'est exactement le contenu que le tuteur avait présenté comme "la
dernière session enregistrée" dans l'incident de la phase 25.

Correction du diagnostic précédent : ce n'était **pas** une invention pure de zéro par le
modèle -- ce texte existait réellement dans les données fournies au tuteur (les travaux
papier de l'étudiant font partie du contexte, voir `construireContexteMatiere`). Le modèle a
donc bien halluciné en présentant ce contenu comme "une session enregistrée" (erreur de
labellisation de la source) et en prétendant avoir envoyé un email/généré une archive (ça,
resté une pure invention), mais les faits bruts (nom, montant, date) provenaient d'un vrai
travail papier déjà présent en base -- vraisemblablement un reliquat de test d'une
vérification antérieure (le nom de fichier `tableau_test.png` et son contenu à consonance
Entreprise plutôt qu'École suggèrent un fichier de test, pas un vrai travail d'étudiant).
Cela ne change rien au correctif de la phase 25 (les deux règles ajoutées couvrent aussi bien
l'invention pure que le mauvais étiquetage d'une source réelle), mais la communication
initiale à l'utilisateur ("ce n'est pas une fuite de données, pure invention") était
incomplète et a été corrigée dans la foulée.

## 3. Les décisions de conception

### 3.1 — Réessai manuel plutôt que retraitement automatique en masse

Plutôt que d'ajouter une tâche de fond qui retenterait automatiquement tous les travaux sans
correction (risque de coût Azure OpenAI non maîtrisé, cf. doctrine de discipline des coûts du
master prompt), un bouton "Corriger maintenant" apparaît uniquement là où la correction
manque, déclenché à la demande de l'étudiant concerné.

### 3.2 — Réutilise `tenterCorrection` telle quelle, pas de nouvelle logique de correction

`TravailPapierService.tenterCorrection` (déjà utilisée par le pipeline d'upload) est
extraite en méthode privée partagée et appelée aussi par la nouvelle méthode publique
`reessayerCorrection` -- même dégradation propre (le texte extrait n'est jamais perdu si la
correction échoue à nouveau), aucune duplication de logique.

### 3.3 — Contrôle d'accès identique aux autres endpoints "personnels"

`reessayerCorrection` vérifie que l'appelant est bien le propriétaire du travail papier
(`AccesTravailPapierRefuseException` sinon, même doctrine que
`AccesTutoratRefuseException`), et que le texte a bien été extrait au préalable
(`TexteExtraitIndisponibleException` sinon -- pas de sens à corriger un travail encore en
attente d'extraction ou en échec).

## 4. Les fichiers, un par un

- `TravailPapierMatiereNotFoundException.java`, `AccesTravailPapierRefuseException.java`,
  `TexteExtraitIndisponibleException.java` (nouveaux) -- mêmes conventions que les
  exceptions équivalentes de `tuteurvocal`/`exercice`.
- `TravailPapierService.java` (édité) -- `tenterCorrection` extraite en méthode privée
  partagée, nouvelle méthode publique `reessayerCorrection`.
- `TravailPapierMatiereController.java` (édité) -- `POST
  /api/v1/matieres/{matiereId}/travaux-papier/{travailId}/corriger`.
- `GestionnaireExceptionsApi.java` (édité) -- mappings des trois nouvelles exceptions
  (404/403/409).
- Frontend `api.ts`, `MatiereRevisionPage.tsx` (édités) -- bouton "Corriger maintenant"
  affiché uniquement quand `correctionTexte` est absent pour un travail `REUSSI`.

## 5. Les tests

`mvn -B clean verify` : **360/360 tests** (357 + 3 nouveaux), 0 finding SpotBugs/FindSecBugs.

Nouveaux tests : correction réussie sur un travail déjà extrait sans correction, refus si
l'appelant n'est pas le propriétaire, refus si aucun texte extrait n'est disponible.

`npm run build` + `npm run lint` : propres.

## 6. Comment on a vérifié

Réessai déclenché en conditions réelles sur `fiche_revision_risque_episens.pdf` (le fichier
exact cité par l'utilisateur) : première tentative échouée avec une erreur de parsing JSON
observée dans les logs (dégradation correcte, aucune perte de données), deuxième tentative
réussie avec une correction détaillée et pertinente (identification d'erreurs historiques
précises -- confusion d'auteurs, date de siècle mal orthographiée -- et conseils
d'amélioration). Vérifié à l'écran (capture) que les deux occurrences de ce fichier
affichent désormais leur correction.

## 7. Limites connues, assumées, pas corrigées ici

- **Pas de nouvelle tentative automatique** en cas d'échec de la correction -- l'étudiant
  doit cliquer "Corriger maintenant" à nouveau si la première tentative échoue (comportement
  jugé suffisant : les échecs de parsing JSON sont rares et l'action reste à un clic).
- **La fragilité du parsing JSON de sortie du modèle** (erreur d'échappement observée ici)
  est un problème latent partagé par tous les adaptateurs Azure OpenAI du projet
  (`GenerateurQcmAzureOpenAI`, `GenerateurExerciceSaisieLibreAzureOpenAI`, etc., qui utilisent
  tous le même `JSON.readTree(nettoyer(contenu))` naïf) -- non traité ici, hors du périmètre
  de cet incrément qui portait sur le réessai, pas sur la robustesse du parsing lui-même.

## 8. Pour reprendre seul

- Code de référence exact : `git checkout phase-26-reessai-correction-travail-papier`.
- Si des échecs de parsing JSON deviennent fréquents, envisager un réessai automatique
  immédiat (1 tentative supplémentaire) directement dans `tenterCorrection`, partagé par
  tous les adaptateurs concernés plutôt que traité au cas par cas.
