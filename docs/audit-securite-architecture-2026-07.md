# Audit sécurité/architecture — juillet 2026

Revue transversale de tout le code produit depuis le début du projet (pas seulement
l'épopée tuteur vocal Ecole), demandée pour repérer les failles d'autorisation et les
incohérences d'architecture avant qu'elles ne s'accumulent. Menée via trois audits
indépendants (autorisation, architecture/dérive de schéma, cohérence frontend), chaque
finding revérifié manuellement (lecture du code réel) avant correction. Onze findings
confirmés ou plausibles, tous corrigés dans ce lot.

## 1. Autorisation manquante sur des endpoints mutants

**Constat** : plusieurs endpoints qui écrivent ou déclenchent un appel Azure payant
n'exigeaient qu'un JWT valide, sans vérifier que l'appelant a un lien avec la session
visée — n'importe quel utilisateur authentifié pouvait terminer/manipuler la session
d'un autre, ou déclencher la génération payante d'un résumé sur une session qui n'est
pas la sienne.

**Corrigé** : `AudioChunkController.enregistrerChunk`, `SessionController.terminerSession`,
`EngagementController` (confirmer/rejeter/terminer/planifier échéance), et les quatre
contrôleurs `genererResume`-style (`ResumeController`, `ResumeCoursController`,
`QcmController`, `CompteRenduController`) exigent désormais `@AuthenticationPrincipal` et
appellent une vérification d'accès centralisée.

**Nouvelle primitive** : `SessionService.verifierAcces(sessionId, utilisateurId)` — lève
`AccesSessionRefuseException` (mappée en `403`) sauf si l'appelant est le créateur de la
session, un membre de son couloir, ou si la session est antérieure à l'introduction du
champ `createurId` (`createurId == null`, même doctrine que
`SessionService.listerSessionsVisibles` : les sessions historiques restent visibles/
actionnables par tous, pas de régression rétroactive sur des données existantes).

**Callers internes préservés** : certains de ces services sont aussi appelés par du code
interne de confiance (événements, orchestration inter-service), pas seulement par HTTP —
par exemple `FilMemoireService` appelle `ResumeService.obtenirOuGenererResume` sans
utilisateur identifié. Plutôt que de casser ces appels, la méthode a été **surchargée** :
une variante 2-arguments (interne, sans vérification) reste disponible à côté de la
nouvelle variante 3-arguments (HTTP, vérifie l'accès puis délègue). Les autres services
(`ResumeCoursService`, `QcmService`, `CompteRenduService`, `EngagementService`) n'avaient
aucun appelant interne (vérifié par recherche exhaustive) : leur signature a été changée
directement, sans surcharge.

## 2. Violation de la doctrine Clean Architecture (le moteur importait Ecole/Entreprise)

**Constat** : `GouvernanceDonneesService` et `SessionPurgeService` (package
`core.gouvernance`) importaient directement des classes concrètes `ecole.*` et
`entreprise.*` (`SeanceTutorat`, `Engagement`, `ResumeCours`, leurs repositories...) —
violation directe de la règle "le moteur n'a jamais de dépendance vers un produit".

**Corrigé** : trois ports définis dans `core.gouvernance` —
`EffaceurDonneesUtilisateurPort`, `PurgeurDonneesSessionPort`,
`ExportateurDonneesUtilisateurPort` (ce dernier à méthodes par défaut vides, chaque
produit ne redéfinissant que celles qui le concernent). Chaque produit implémente les
trois dans un unique `@Component` : `ecole.gouvernance.EcoleGouvernanceContributor`,
`entreprise.gouvernance.EntrepriseGouvernanceContributor`. Spring collecte
automatiquement les implémentations via injection de `List<PortType>` dans le
constructeur des deux services core — la flèche de dépendance est inversée (produit →
moteur), plus jamais moteur → produit. Voir `docs/gouvernance-donnees.md` pour le
comportement fonctionnel, inchangé par ce refactor (uniquement l'architecture interne a
bougé).

**Note de conception** : le nom des méthodes des ports (`exporterSeancesTutorat`,
`exporterEngagements`...) continue de décrire des concepts produit — ce n'est pas ce que
la doctrine interdit. Ce qu'elle interdit, c'est l'import d'une **classe concrète**
Ecole/Entreprise dans le moteur ; les DTOs d'export (`ExportDonneesUtilisateur.*`) sont
déjà des types core, légitimement transverses puisque l'export RGPD décrit
l'intégralité d'un utilisateur.

## 3. Gestion d'erreur silencieuse côté frontend

**Constat** : `CouloirDetailPage`, `EngagementsPage`, `RecherchePage` avaient des
handlers sans `try/catch` — un 403 (cohérent avec le point 1) ou un 409 échouait sans
aucun retour visible pour l'utilisateur, contrairement aux 14 autres pages du projet qui
suivent déjà le pattern `erreur: string | null` + message affiché.

**Corrigé** : les trois pages suivent maintenant ce même pattern (état `erreur`, remis à
`null` au début de chaque action, message affiché en cas d'échec).

## 4. Champs de traçabilité non exposés par l'API

**Constat** : `Notion.documentSourceId` (lien vers le document source, phase 18) existait
en base mais `NotionResponse` ne l'exposait pas — impossible pour le frontend de remonter
à la fiche source. `DocumentMatiere.texteExtrait` existait côté backend
(`DocumentMatiereResponse`) sans équivalent dans le type frontend `DocumentMatiere`.

**Corrigé** : `NotionResponse` inclut désormais `documentSourceId` ; le type frontend
`DocumentMatiere` inclut `texteExtrait` et `Notion` inclut `documentSourceId`.

## 5. Absence de polling après upload de fiche

**Constat** : l'extraction (Azure Document Intelligence) puis la génération des notions
candidates (Azure OpenAI) sont asynchrones côté serveur (~9s observées en conditions
réelles), mais `MatiereDetailPage` ne rafraîchissait qu'une fois, à la fin de l'upload —
l'utilisateur ne voyait ni le document passer à `REUSSI`, ni apparaître les candidats,
sans recharger la page à la main.

**Corrigé** : polling toutes les 4s (même intervalle que `Recorder.tsx`) tant qu'un
document reste `EN_ATTENTE`, sans geler l'écran (pas de `chargement` global pendant le
polling, seuls documents/candidats sont rafraîchis).

## 6. Duplication de `verifierProprietaireDuCouloir`

**Constat** : cette vérification était dupliquée verbatim dans cinq services
(`MatiereService`, `NotionService`, `SeanceService`, `NotionCandidateService`,
`DocumentMatiereService`).

**Corrigé** : méthode publique unique `CouloirService.verifierProprietaireDuCouloir`,
appelée par les cinq services au lieu de leur copie locale.

## 7. Risque de migration Hibernate sur `Resume.type`

**Constat** : `Resume.type` porte `nullable = false` sur une table potentiellement déjà
peuplée — le même piège que deux bugs réels déjà rencontrés et corrigés dans ce projet
(`ddl-auto=update` ignore silencieusement un `ALTER COLUMN ... SET NOT NULL` sur une
colonne non-primitive si la table contient des lignes). **Non corrigé dans ce lot** :
la table était vide au moment de l'introduction de la colonne, donc rien à migrer
rétroactivement aujourd'hui — le risque est documenté ici pour toute future colonne
`NOT NULL` non-booléenne ajoutée à une table déjà peuplée (voir le pattern de migration
manuelle en 3 étapes dans `docs/phases/phase-19-mode-conversation-libre.md`).

## Vérification

- Backend : `mvn -B clean verify` — 304/304 tests, 0 finding SpotBugs/FindSecBugs.
- Frontend : `npm run build` (typecheck + build) et `npm run lint` (oxlint) — les deux
  passent sans erreur ni avertissement.
