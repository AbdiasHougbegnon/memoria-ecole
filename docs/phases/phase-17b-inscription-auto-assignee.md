# Inscription auto-assignée — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout phase-17b-inscription-auto-assignee
```

---

## 1. Le besoin

Deuxième brique de l'épopée tuteur vocal (après 17a, import en masse). Un étudiant s'inscrit
avec juste son email d'école, choisit son année académique/filière/spécialité dans une liste,
et entre directement dans le couloir correspondant — pas d'étape séparée "rejoindre un
couloir". Dépend directement de 17a : la liste proposée est peuplée par les couloirs déjà
importés, et l'inscription est bloquée si la combinaison choisie n'existe pas encore (l'admin
doit l'avoir importée au préalable).

## 2. Les décisions de conception

### 2.1 — Un endpoint d'inscription Ecole séparé, pas des champs conditionnels sur l'existant

Plutôt que d'ajouter des champs `anneeAcademique`/`filiere`/`specialite` nullable sur
`InscriptionRequest`/`AuthController` (core), qui aurait fait remonter du vocabulaire École
dans le moteur générique, un nouvel endpoint `POST /api/v1/ecole/inscription` (package
`ecole/couloir`) orchestre : (1) résolution du couloir via
`ContexteScolaireCouloirRepository.findByAnneeAcademiqueAndFiliereAndSpecialite` (17a),
bloquant si absent ; (2) `AuthService.inscrire(...)` (core, réutilisé tel quel) ; (3)
`CouloirService.rejoindreCouloir(...)` (core, réutilisé tel quel). Le moteur ne voit jamais
passer une notion de "classe" — c'est la couche École qui orchestre deux services core
existants, exactement le sens de "controllers orchestrent, jamais de logique métier dans le
moteur".

### 2.2 — Résolution avant création, pas de compte orphelin

La résolution du couloir se fait **avant** l'appel à `authService.inscrire` : si la classe
n'existe pas, `ClasseIntrouvableException` (409) est levée sans qu'aucun compte ne soit créé
— vérifié directement en base après un essai volontairement raté (aucune ligne dans
`utilisateurs`). Cohérent avec la décision actée avec l'utilisateur : bloquer avec un message
clair plutôt que replier silencieusement vers le flux "rejoindre" existant.

### 2.3 — Endpoints publics, comme `/api/v1/auth/**`

`GET /api/v1/ecole/options-inscription` et `POST /api/v1/ecole/inscription` doivent rester
accessibles **avant** authentification — un futur étudiant n'a pas encore de compte, donc pas
de JWT. La règle générique déjà posée en 17a (`/api/v1/ecole/**` → `MODULE_ECOLE`) aurait
bloqué ces deux endpoints ; deux `permitAll()` spécifiques, déclarés avant cette règle plus
large dans `SecurityConfig`, les en exemptent explicitement — Spring Security retient la
première règle qui matche.

## 3. Les fichiers, un par un

### `ecole/couloir/InscriptionEcoleService.java` (nouveau)
Orchestration décrite en 2.1/2.2. `normaliser(specialite)` convertit une chaîne vide en
`null` avant la recherche (cohérent avec la sémantique `IS NULL` déjà établie en 17a).
`listerOptionsInscription()` retourne les triplets distincts pour peupler les selects.

### `ecole/couloir/InscriptionEcoleController.java` (nouveau)
`GET /ecole/options-inscription`, `POST /ecole/inscription` (`InscriptionEcoleRequest` :
email, motDePasse, anneeAcademique, filiere, specialite optionnelle).

### `ecole/couloir/ClasseIntrouvableException.java` + `OptionInscriptionResponse.java` (nouveaux)
Exception dédiée (409, wired dans `GestionnaireExceptionsApi`) ; DTO de réponse minimal (pas
de fuite de `couloirId` sur un endpoint public).

### `core/auth/SecurityConfig.java` (édité)
Deux `permitAll()` ciblés avant la règle `/api/v1/ecole/**` existante (voir §2.3).

### `LoginPage.tsx` (édité)
`FormulaireConnexion` : en mode inscription + module ECOLE, récupère les options
(`obtenirOptionsInscriptionEcole`, une seule fois, lazy) et affiche 3 selects en cascade
(année → filière → spécialité, chacun réinitialisant les suivants au changement). Le select
spécialité ne s'affiche que si au moins une spécialité existe pour la filière choisie
(certaines filières n'en ont pas, ex. Droit). Soumission via `inscrireEcole(...)` au lieu de
`inscrire(...)` uniquement dans ce cas précis — le flux Entreprise et la connexion classique
ne sont pas touchés.

## 4. Les tests

266/266 tests backend (262 existants + 4 sur `InscriptionEcoleService` : résolution +
rejoindre-couloir, normalisation spécialité vide, blocage si classe introuvable, liste des
options distinctes). `mvn -B verify` : `BUILD SUCCESS`, 0 finding SpotBugs/FindSecBugs.
`npm run build` + `npm run lint` : propres.

## 5. Comment on a vérifié en conditions réelles

Sur le backend réel (port 8080, code de cette brique) : `GET /ecole/options-inscription`
sans aucun header d'authentification renvoie bien les 4 classes importées en 17a (200, pas
401). `POST /ecole/inscription` avec un triplet valide renvoie 201 + un token, et le nouveau
compte apparaît bien comme membre (2/2) du couloir "Informatique - Génie Logiciel -
2026-2027" — confirmé via `GET /couloirs` avec son propre token. Tentative volontaire avec un
triplet inexistant : 409, et vérification directe en base (aucune ligne dans `utilisateurs`
pour cet email) confirmant qu'aucun compte orphelin n'est créé.

Vérification de l'UI ensuite via Playwright piloté en conditions réelles : navigation vers
`/connexion?module=ECOLE`, bascule sur "Inscription", remplissage email/mot de passe, choix
en cascade année → filière (Informatique) → spécialité (Réseaux), clic "Créer le compte" —
réponse `201` sur `/api/v1/ecole/inscription` confirmée, redirection vers `/`, et le compte
`nouvel-etudiant@test.local` retrouvé (via connexion + `GET /couloirs`) bien membre du couloir
"Informatique - Réseaux - 2026-2027" — exactement la spécialité choisie dans le formulaire.

## 6. Limites connues, assumées, pas corrigées ici

- **Pas de changement de filière en libre-service après inscription** — la page "Rejoindre un
  couloir" existante reste le seul recours pour corriger une erreur ou changer de filière en
  cours d'année.
- **Pas de validation enseignant de l'auto-inscription** — n'importe quel email autorisé
  (selon la liste blanche de domaines, phase-15) peut s'auto-assigner à n'importe quelle
  classe existante, sans confirmation humaine côté établissement.
- **Le select spécialité liste les valeurs telles qu'importées** — aucune normalisation
  (ex. "Génie Logiciel" vs "genie logiciel" créeraient deux classes distinctes) ; repose sur
  la cohérence du fichier CSV d'import (17a).

## 7. Pour reprendre seul

- Code de référence exact : `git checkout phase-17b-inscription-auto-assignee`
- Prochaine sous-phase : **18 — contenu du tutorat piloté par documents** (upload de fiches
  de cours/exercices sur une matière, extraction automatique de notions candidates avec
  validation enseignant avant confirmation).
- Puis 19 (mode conversation libre), qui dépend de 18 pour avoir un contexte réel à injecter
  dans le prompt du tuteur.
