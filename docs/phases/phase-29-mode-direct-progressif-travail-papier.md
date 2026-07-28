# Mode direct / progressif pour parcourir les exercices corrigés — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout phase-29-mode-direct-progressif-travail-papier
```

---

## 1. Le besoin

Brique B du chantier "correction interactive du travail papier" (portée définie avec
l'utilisateur, voir phase 28 §1) : après la décomposition en exercices individuels (brique
A), l'étudiant doit pouvoir choisir entre voir toute la correction d'un coup ("correction
directe", comportement déjà existant) ou parcourir les exercices un par un avec navigation
("mode progressif").

## 2. La décision de conception

### 2.1 — Purement frontend, aucun état persisté

Cette brique ne fait que de la navigation dans des données déjà entièrement corrigées (par
la brique A) : aucune information nouvelle à stocker côté serveur, aucun appel réseau
supplémentaire. `ParcoursExercicesTravailPapier` gère le mode (`direct`/`progressif`) et
l'index courant en état local React, réutilise `CorrectionExerciceAffichage` (l'accordéon de
la phase 27) telle quelle pour afficher un exercice à la fois.

### 2.2 — Le toggle n'apparaît que s'il y a plus d'un exercice

Un travail papier à un seul exercice n'a pas de sens à "parcourir progressivement" -- le
sélecteur de mode et la barre de navigation restent masqués dans ce cas, seule la correction
directe s'affiche (comportement identique à avant cette brique).

### 2.3 — Navigation aux bornes désactivée, pas de boucle

"Précédent" est désactivé sur le premier exercice, "Suivant" sur le dernier -- pas de
retour au début en boucle, pour rester prévisible.

## 3. Les fichiers, un par un

- `MatiereRevisionPage.tsx` (édité) — nouveau composant `ParcoursExercicesTravailPapier`
  (toggle direct/progressif + stepper avec compteur "Exercice X/N" et navigation), remplace
  l'affichage à plat de tous les exercices dans la section Travail papier.

## 4. Comment on a vérifié

`npm run build` + `npm run lint` : propres (aucun changement backend, pas de nouvelle
exécution de `mvn verify` nécessaire).

Vérification en conditions réelles (Playwright) sur un travail à deux exercices (un faux, un
correct, réutilisé de la phase 28) : le toggle apparaît bien car il y a 2 exercices, bascule
correctement entre les deux modes, la navigation affiche "Exercice 1/2" puis "Exercice 2/2"
après un clic sur "Suivant", "Suivant" est bien désactivé sur le dernier exercice, "Précédent"
ramène correctement à l'exercice 1. Captures d'écran à l'appui pour les trois états.

## 5. Limites connues, assumées, pas corrigées ici

- **Pas de suivi de progression persistant** — revenir sur la page réinitialise toujours le
  mode à "direct" et l'index à 0 ; aucun "reprendre où j'en étais" pour l'instant.
- **Brique C (vérification de compréhension)** reste à construire — le mode progressif ne
  fait aujourd'hui que naviguer, sans encore poser de question de vérification après une
  réponse fausse ni suivre de statut Validé/Pas clair par exercice.

## 6. Pour reprendre seul

- Code de référence exact : `git checkout phase-29-mode-direct-progressif-travail-papier`.
- Prochaine étape : brique C — après une correction "En cours d'acquisition" ou "Non
  abordée" en mode progressif, poser une question de vérification (QCM/voix/texte), marquer
  l'exercice Validé ou Pas clair selon la réponse, sans jamais bloquer la navigation.
