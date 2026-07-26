# Contenu documentaire — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout feature/contenu-documentaire
```

---

## 1. Le besoin

Troisième sous-phase de l'épopée tuteur vocal (après 17a, import en masse des classes/matières,
et 17b, inscription auto-assignée). Jusqu'ici, c'est toujours l'enseignant ou l'étudiant qui
saisit les notions à la main sur une Matière. Objectif : l'enseignant (propriétaire du couloir)
téléverse une fiche (cours, exercices, épreuve — PDF ou photo) sur une Matière, le système en
extrait automatiquement des notions candidates (terme + définition) via Azure OpenAI, et
l'enseignant les valide ou les rejette avant qu'elles n'entrent réellement dans le suivi de
maîtrise des étudiants. Décision actée avec l'utilisateur : **pas d'injection directe en base**
— une extraction IA peut halluciner, et une notion fausse alimenterait directement le suivi
pédagogique réel des étudiants sans filtre. La validation humaine est donc une étape obligatoire,
pas une option.

## 2. Les décisions de conception

### 2.1 — Une entité `DocumentMatiere` séparée, pas une deuxième FK sur `Document`

`core/document/Document.java` (moteur générique) a une seule FK obligatoire, `sessionId`. Lui
ajouter un `matiereId` optionnel concurrent aurait été incohérent avec le style du projet
(Couloir/Matiere n'ont chacun qu'une seule FK obligatoire, jamais une FK "au choix" selon le
contexte). `ecole/document/DocumentMatiere.java` est donc un miroir exact de `Document`
(mêmes champs : type, nom de fichier, chemin de stockage, texte extrait, statut, date) mais
rattaché à une Matière (vocabulaire École) plutôt qu'à une Session. Le moteur ne voit jamais
passer de vocabulaire "matière" ou "fiche de cours" — `StockageDocumentPort` et
`ExtracteurDocumentPort` (core, réutilisés tels quels) sont agnostiques du type d'appelant : le
paramètre nommé `sessionId` dans leur signature n'est qu'une clé de regroupement opaque pour le
chemin de stockage, confirmé en relisant `StockageDocumentFichierLocal`.

### 2.2 — `NotionCandidate`, pas d'écriture directe dans `Notion`

Une `NotionCandidate` (statut `EN_ATTENTE` / `VALIDEE` / `REJETEE`) est une entité de premier
ordre distincte de `Notion`, jamais une `Notion` avec un flag "à valider". Elle porte le
`documentMatiereId` et le `matiereId` d'origine, et le terme/définition proposés par l'IA. Tant
qu'elle n'est pas validée, elle n'apparaît nulle part dans le suivi de maîtrise
(`MaitriseNotion`) : seule la validation crée une vraie `Notion` (via
`NotionService.creerNotionValidee`), potentiellement avec un terme/définition édités par
l'enseignant par rapport à la proposition IA brute.

### 2.3 — Traçabilité : `Notion.documentSourceId`, nullable, deuxième constructeur

Doctrine IA du projet : toute donnée business-critique produite par IA doit être traçable
jusqu'à sa source. `Notion` gagne un champ nullable `documentSourceId` (`UUID`) renseigné
uniquement pour une Notion issue de la validation d'une candidate — un enseignant peut ainsi
remonter de la notion vers la fiche source. Le constructeur historique à 4 arguments (utilisé
partout ailleurs pour la création manuelle) reste **intact** ; un second constructeur à 5
arguments délègue au premier puis renseigne `documentSourceId`. `NotionService.creerNotionValidee`
(nouvelle méthode, `creerNotion` existante inchangée) est le seul point d'entrée qui l'utilise.

### 2.4 — Panne de génération IA isolée de l'extraction du document

`GenerateurNotionsDepuisDocumentPort.genererNotionsCandidates` peut échouer (Azure OpenAI
indisponible ou en erreur) sans que l'extraction du texte du document, elle, ait échoué. Le
listener `DocumentMatiereService.surDocumentTeleverse` traite les deux étapes séparément : si
l'extraction réussit, le document est marqué `REUSSI` et sauvegardé immédiatement ; la
génération de candidats est ensuite tentée dans un bloc séparé, et son échec est seulement
loggué (`LOG.warn`) sans repasser le document en échec et sans nouvelle tentative automatique —
même logique que `ResumeCoursService`, qui ne relance pas seul un échec de génération IA.

### 2.5 — Vérification de propriété : déléguée quand possible, dupliquée sinon

`DocumentMatiereService.televerser` et `NotionCandidateService.rejeterCandidate` répliquent le
pattern déjà établi (`MatiereService`/`NotionService`) : résoudre le couloir via
`CouloirService.obtenirCouloir`, comparer `proprietaireId`, lever `PasProprietaireDuCouloirException`
sinon — il n'existe pas d'abstraction partagée pour ce contrôle dans le projet, donc pas de
nouvelle à inventer ici. En revanche, `NotionCandidateService.validerCandidate` ne duplique
**pas** ce contrôle : il délègue entièrement à `NotionService.creerNotionValidee`, qui le fait
déjà en interne. Si l'appelant n'est pas propriétaire, la création de la `Notion` échoue avant
toute mutation de la candidate — pas de risque qu'une candidate soit marquée `VALIDEE` sans
notion créée derrière.

## 3. Les fichiers, un par un

### `ecole/document/DocumentMatiere.java` + `DocumentMatiereRepository.java` (nouveaux)
Miroir de `core/document/Document`/`DocumentRepository`, rattaché à `matiereId`.
`findByMatiereIdOrderByDateCreationAsc`.

### `ecole/document/DocumentMatiereService.java` (nouveau)
`televerser(matiereId, nomFichier, typeContenu, contenu, utilisateurId)` : vérifie la propriété
du couloir, stocke via `StockageDocumentPort` (bean core), sauvegarde le document (`EN_ATTENTE`),
publie `DocumentMatiereTeleverseEvent`. `@Async @EventListener surDocumentTeleverse` : relit le
fichier (`Files.readAllBytes`), extrait le texte (`ExtracteurDocumentPort`, bean core), marque
réussi/échec ; si réussi, génère des candidats via `GenerateurNotionsDepuisDocumentPort` et les
sauvegarde en `NotionCandidate` (`EN_ATTENTE`) — panne de génération isolée, voir §2.4.
`listerDocuments(matiereId)`.

### `ecole/document/GenerateurNotionsDepuisDocumentPort.java` + `GenerateurNotionsDepuisDocumentAzureOpenAI.java` (nouveaux)
Port + implémentation Azure OpenAI, miroir exact de `GenerateurResumeCoursAzureOpenAI` /
`GenerateurTourTuteurAzureOpenAI` (même `HttpClient`, même ressource Azure OpenAI "Responses
API", même parsing JSON strict en sortie, même suivi de coût via
`CoutAzureService.enregistrerAppel(ServiceAzure.OPENAI_CHAT, ...)`). Prompt système : extraire
les notions clés (terme + définition concise) du texte fourni, réponse JSON stricte
`{"notions": [...]}`, liste vide plutôt qu'invention si aucune notion claire.

### `ecole/document/DocumentMatiereController.java` + `DocumentMatiereResponse.java` + `DocumentMatiereNotFoundException.java` (nouveaux)
`POST /api/v1/matieres/{matiereId}/documents` (multipart, champ `fichier`),
`GET /api/v1/matieres/{matiereId}/documents`. Exception 404 wirée dans
`GestionnaireExceptionsApi`.

### `ecole/notion/NotionCandidate.java` + `StatutNotionCandidate.java` + `NotionCandidateRepository.java` (nouveaux)
Entité de premier ordre distincte de `Notion` (voir §2.2). `findByMatiereIdOrderByDateCreationAsc`.

### `ecole/notion/NotionCandidateService.java` + `NotionCandidateController.java` + `NotionCandidateResponse.java` + `NotionCandidateNotFoundException.java` (nouveaux)
`listerCandidates(matiereId)`. `validerCandidate(candidateId, termeEdite, definitionEditee,
utilisateurId)` : ordre = taille de la liste de notions existantes de la matière, délègue la
création à `NotionService.creerNotionValidee`, marque la candidate `VALIDEE` seulement après
succès. `rejeterCandidate(candidateId, utilisateurId)` : vérifie la propriété elle-même (voir
§2.5), marque `REJETEE`, ne crée rien.
`GET /api/v1/matieres/{matiereId}/notions-candidates`,
`POST .../notions-candidates/{candidateId}/valider` (body `{terme, definition}`),
`POST .../notions-candidates/{candidateId}/rejeter`. Exception 404 wirée dans
`GestionnaireExceptionsApi`. `PasProprietaireDuCouloirException` existante réutilisée pour les
403 (déjà wirée).

### `ecole/notion/Notion.java` + `NotionService.java` (édités)
`documentSourceId` nullable + second constructeur (voir §2.3). `creerNotionValidee` (nouvelle
méthode, `creerNotion` existante intacte).

### `MatiereDetailPage.tsx` (édité)
Nouvelle section "Contenu documentaire", visible uniquement si `estProprietaire` : bouton de
téléversement (input file caché derrière un label stylé), liste des fiches avec leur statut,
liste des `NotionCandidate` `EN_ATTENTE` avec champs terme/définition pré-remplis et éditables +
boutons Valider/Rejeter. Après chaque action, `rafraichir()` recharge tout (notions, documents,
candidates) — le formulaire manuel de création de notion existant n'est pas touché.

## 4. Les tests

276/276 tests backend (258 existants + 18 nouveaux sur `DocumentMatiereServiceTest` (10) et
`NotionCandidateServiceTest` (8) : détection de type, vérification de propriété, extraction
réussie avec génération de candidats, extraction en échec sans génération, génération de
candidats en échec sans dégrader le statut du document, validation avec création de notion à la
suite des existantes, non-mutation de la candidate si la validation échoue côté propriété,
rejet). `mvn -B verify` : `BUILD SUCCESS`, couverture JaCoCo maintenue, 0 finding
SpotBugs/FindSecBugs. `npm run build` + `npm run lint` : propres.

## 5. Comment on a vérifié en conditions réelles

Fait par l'orchestrateur après fusion de `feature/contenu-documentaire` et
`feature/mode-conversation-libre` dans `master`. Un vrai PDF généré à la volée (fiche sur les
listes chaînées/piles/files) uploadé sur la matière "Algorithmique" via `curl` multipart :
extraction Azure Document Intelligence réussie (texte exact retrouvé dans `texteExtrait`),
génération Azure OpenAI de 6 candidats pertinents (liste chaînée, nœud, pile, LIFO, file, FIFO).
Validation d'un candidat avec terme/définition édités par l'enseignant : confirmé qu'une vraie
`Notion` apparaît dans `GET /matieres/{id}/notions` avec exactement le texte édité (pas la
proposition IA brute). Rejet d'un autre candidat : confirmé qu'aucune `Notion` n'est créée.
Vérification visuelle ensuite via Playwright sur `MatiereDetailPage` : upload, statut du
document, candidats affichés avec champs éditables et boutons Valider/Rejeter — capture d'écran
à l'appui.

**Un problème réel découvert à cette étape, invisible aux tests Mockito** : sans rapport avec
cette brique elle-même, la fusion avec la branche 19 a révélé que la colonne `notion_id` de
`tours_dialogue_tutorat` gardait sa contrainte `NOT NULL` héritée malgré le retrait de
`nullable = false` côté entité (`ddl-auto=update` ajoute des colonnes/contraintes mais n'en
retire jamais) — voir `docs/phases/phase-19-mode-conversation-libre.md` §5 pour le détail, ce
correctif concerne le mode LIBRE, pas le pipeline documentaire de cette fiche.

## 6. Limites connues, assumées, pas corrigées ici

- **Pas de nouvelle tentative automatique si la génération de candidats échoue** — le document
  reste marqué `REUSSI` (l'extraction a fonctionné) mais aucune candidate n'apparaît ; seul un
  nouveau téléversement de la même fiche relance le pipeline complet.
- **Pas de déduplication des candidates** — téléverser deux fois la même fiche, ou deux fiches
  qui se recoupent, génère deux jeux de candidates indépendants ; à l'enseignant de repérer les
  doublons lors de la validation.
- **Pas de lien retour visible côté étudiant** — `Notion.documentSourceId` est stocké (traçabilité
  exigée par la doctrine IA du projet) mais aucune UI ne l'exploite encore pour naviguer de la
  notion vers la fiche source ; à construire dans une sous-phase ultérieure si le besoin se
  confirme.

## 7. Pour reprendre seul

- Code de référence exact : `git checkout feature/contenu-documentaire`
- Sous-phase précédente : **17b — inscription auto-assignée** (développée en parallèle sur une
  autre copie du dépôt, pas encore fusionnée dans l'historique de cette branche au moment de ce
  travail).
- Sous-phase parallèle : **19 — mode conversation libre**, développée simultanément sur
  `feature/mode-conversation-libre` par une autre IA ; dépend de 18 (celle-ci) pour disposer
  d'un contexte documentaire réel à injecter dans le prompt du tuteur. Fusion des deux branches
  et vérification en conditions réelles à faire par l'orchestrateur.
