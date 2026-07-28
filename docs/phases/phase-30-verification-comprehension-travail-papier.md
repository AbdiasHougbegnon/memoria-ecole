# Vérification de compréhension du travail papier — comment on l'a construite

**Pour revenir exactement à cet état du code :**
```
git checkout phase-30-verification-comprehension-travail-papier
```

---

## 1. Le besoin

Brique C, dernière du chantier "correction interactive du travail papier" (portée définie
avec l'utilisateur, voir phase 28 §1). En mode progressif (brique B), après avoir lu la
correction d'un exercice, l'étudiant doit pouvoir vérifier qu'il a bien compris : une
question de contrôle est posée, répondable par cases à cocher ou en texte libre, et
l'exercice est marqué "Validé" ou "Pas clair" selon la réponse — sans jamais bloquer la
navigation Suivant/Précédent, quel que soit le résultat.

## 2. Les décisions de conception

### 2.1 — Deux chemins d'évaluation, un seul coûte un appel IA

Cocher des cases est une correction déterministe (comparer les indices cochés aux choix
marqués corrects côté serveur) : aucun appel Azure OpenAI n'est nécessaire, conforme à la
discipline de coût du projet (ne jamais payer un appel IA pour un calcul qu'un `equals`
suffit à faire). Répondre en texte libre est en revanche qualitatif — cela réutilise le
même classement `NiveauMaitrise` (`NON_ABORDEE`/`EN_COURS`/`MAITRISEE`) que le reste du
projet, via une nouvelle méthode `evaluerReponseLibre` sur `VerificateurComprehensionPort`.

### 2.2 — Un statut distinct de la correction initiale

`StatutVerification` (`NON_VERIFIE`/`VALIDE`/`PAS_CLAIR`) est un champ séparé de
`NiveauMaitrise` sur `ExercicePapier` : le premier est le jugement de l'IA sur la copie
initiale de l'étudiant, le second est le résultat de sa propre vérification de
compréhension a posteriori — un exercice "En cours d'acquisition" peut devenir "Validé"
après coup, sans que sa correction d'origine ne change.

### 2.3 — Ne bloque jamais la navigation

Conformément à la demande explicite de l'utilisateur ("il peut avancer en cliquant sur
suivant ou même revenir en arrière... tout ça c'est s'il a demandé le mode apprentissage
continu"), les boutons Suivant/Précédent de la brique B restent inchangés et jamais
désactivés par l'état de vérification — c'est un signal informatif pour l'étudiant, pas une
porte à franchir.

### 2.4 — Voix différée, mais le chemin texte est prêt à l'accueillir

La capture audio réelle (enregistrement + transcription) n'est pas construite dans cet
incrément — limitation assumée et documentée ci-dessous. En revanche, l'endpoint de réponse
libre prend un texte brut en entrée : le jour où la capture vocale existera, le texte
transcrit passera par exactement le même chemin, sans rien changer côté backend.

### 2.5 — Seulement en mode progressif

La vérification n'apparaît que dans `ParcoursExercicesTravailPapier` en mode `progressif`
(prop `verification` optionnelle sur `CorrectionExerciceAffichage`) ; le mode "correction
directe" reste une lecture complète sans interaction, comme demandé.

## 3. Les fichiers, un par un

### Backend

- `StatutVerification.java` (nouveau) — enum `NON_VERIFIE`/`VALIDE`/`PAS_CLAIR`.
- `ChoixVerification.java` (nouveau, `@Embeddable`) — `texte` + `correct`, pour éviter deux
  listes parallèles (textes / indices corrects) qui pourraient se désynchroniser.
- `QuestionVerificationGeneree.java` (nouveau, record) — `enonce` + `List<ChoixVerification>`,
  retour du port de génération.
- `VerificateurComprehensionPort.java` (nouveau) — `genererQuestion(enonce,
  correctionSynthese, points)` et `evaluerReponseLibre(questionVerification,
  reponseEtudiant)`.
- `VerificateurComprehensionAzureOpenAI.java` (nouveau) — implémentation Azure OpenAI,
  même patron HttpClient/Responses-API que tous les autres générateurs du package.
- `ExercicePapier.java` (édité) — trois nouveaux champs : `statutVerification` (défaut
  `NON_VERIFIE` à la construction), `questionVerificationEnonce`, `choixVerification`
  (`@ElementCollection`, **sans initialisateur par défaut** — même piège Hibernate déjà
  documenté en phase 28 pour `pointsCorrection`) ; deux nouvelles méthodes
  `enregistrerQuestionVerification` et `enregistrerResolutionVerification`.
- `ExercicePapierNotFoundException.java` (nouveau) — mappée en 404.
- `TravailPapierService.java` (édité) — `genererQuestionVerification`,
  `soumettreReponseChoix` (comparaison déterministe), `soumettreReponseLibre` (délègue au
  port IA), toutes protégées par un `chargerExercice` privé qui vérifie la propriété du
  travail (même doctrine que `reessayerCorrection`).
- `TravailPapierMatiereController.java` (édité) — trois endpoints sous
  `/api/v1/matieres/{matiereId}/travaux-papier/{travailId}/exercices/{exerciceId}/verification/...`
  (`/question`, `/reponse-choix`, `/reponse-libre`).
- `TravailPapierMatiereResponse.java` (édité) — `ExercicePapierResponse` expose
  `statutVerification`, `questionVerificationEnonce`, `choixVerification` (avec le flag
  `correct`, exposé au même titre que `QuestionQcmResponse.reponseCorrecte` ailleurs dans le
  projet).

### Frontend

- `types.ts` (édité) — `StatutVerification`, `ChoixVerification`, champs ajoutés à
  `ExercicePapier`.
- `api.ts` (édité) — `genererQuestionVerification`, `soumettreReponseChoixVerification`,
  `soumettreReponseLibreVerification`.
- `MatiereRevisionPage.tsx` (édité) — nouveau composant `VerificationComprehension` (bouton
  "Vérifier ma compréhension" → question + cases à cocher + champ texte libre → badge
  Validé/Pas clair) ; `CorrectionExerciceAffichage` accepte une prop `verification`
  optionnelle ; `ParcoursExercicesTravailPapier` et `SectionTravailPapier` la propagent
  uniquement pour l'exercice affiché en mode progressif.

## 4. Comment on a vérifié

`mvn -B clean verify` (backend, tests ajoutés pour les trois nouvelles méthodes de service
+ le cas d'accès refusé) et `npm run build && npm run lint` (frontend) : les deux propres.

Vérification en conditions réelles (pas de mocks) : soumission d'un vrai travail papier
(deux exercices, un faux "x=7" et un correct "x=5", mêmes fixtures que la phase 28) via
l'API réelle, extraction Azure Document Intelligence + correction Azure OpenAI confirmées
correctes comme en phase 28. Puis, en conditions réelles :
- génération d'une vraie question de vérification (Azure OpenAI) pour l'exercice faux —
  question et 4 choix cohérents obtenus,
- soumission des indices cochés correspondant exactement aux choix marqués corrects →
  `VALIDE`,
- soumission d'indices incluant un choix incorrect → `PAS_CLAIR`,
- génération d'une question sur l'exercice déjà correct, soumission d'une réponse libre
  hors-sujet → `PAS_CLAIR`, puis d'une réponse libre correcte → `VALIDE`.

Vérification dans le navigateur réel (Playwright) : bascule en mode progressif, clic sur
"Vérifier ma compréhension", question générée affichée avec ses cases à cocher, sélection
des bonnes réponses, clic sur "Valider mes choix" → badge vert "Vérification de
compréhension — Compris" affiché, sans que Suivant/Précédent ne soient jamais désactivés.
Un exercice déjà résolu via l'API affiche directement le badge au chargement de la page,
confirmant la cohérence de l'état persisté.

## 5. Limites connues, assumées, pas corrigées ici

- **Pas de capture audio réelle** — seul le chemin texte (tapé) est câblé ; la dictée vocale
  reste à construire (enregistrement + transcription), mais réutilisera l'endpoint
  `reponse-libre` sans changement backend.
- **Pas de nouvelle tentative après un statut "Pas clair"** — une fois la vérification
  résolue, il n'y a pas de bouton pour reposer une nouvelle question ; l'étudiant peut
  relire la correction mais pas redemander une vérification.
- **`choixVerification` expose le flag `correct` au client** — cohérent avec le patron déjà
  en place pour `QuestionQcmResponse` ailleurs dans le projet (pas de dissimulation
  particulière de la grille de correction pour ce type de QCM), mais à garder en tête si une
  doctrine plus stricte est adoptée plus tard.

## 6. Pour reprendre seul

- Code de référence exact : `git checkout phase-30-verification-comprehension-travail-papier`.
- Ce chantier clôt les trois briques prévues (A : décomposition énoncé/réponse, B :
  navigation directe/progressive, C : vérification de compréhension) du "tutorat progressif"
  appliqué au travail papier.
