# Corrections du tuteur vocal — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout phase-16-corrections-tuteur-vocal
```

---

## 1. Le besoin

L'utilisateur a testé le tuteur vocal (phase-9) en conditions réelles et signalé deux
problèmes : (1) après qu'une notion soit marquée maîtrisée, ajouter de nouvelles notions et
retenter le tutorat restait bloqué sur "toutes les notions sont déjà maîtrisées" ; (2) le
tuteur semblait "ne pas attendre" que l'utilisateur ait fini de parler avant de répondre.

## 2. Les décisions de conception

### 2.1 — Bug 1 : un piège UX, pas un bug de logique métier

Diagnostic par recherche dans le code (pas supposé) : `TuteurVocalService` recalcule
correctement `listerNotionsDeSeance` à chaque démarrage/transition de notion — la logique
métier fonctionne. Le vrai problème : ajouter une notion sur `MatiereDetailPage` ne la
rattache **jamais automatiquement** à une séance existante. Un geste manuel séparé sur
`SeanceDetailPage` (cocher la notion, cliquer "Enregistrer") est nécessaire **avant** de
démarrer le tutorat — facile à oublier, sans aucun avertissement. Correction : `gererDemarrage()`
sauvegarde désormais automatiquement l'état des cases cochées avant d'appeler
`demarrerTutorat`, éliminant la classe de bug entière sans toucher à `TuteurVocalService`.

### 2.2 — Découverte en cours de route : un vrai bug transactionnel backend

En vérifiant le correctif 2.1 en conditions réelles, un second bug (pré-existant depuis
phase-9, jamais remarqué avant) est apparu : `SeanceService.rattacherNotions` échouait avec
une violation de contrainte unique dès que le nouvel ensemble de notions recoupait l'ancien
(ex. garder une notion déjà cochée en en ajoutant une nouvelle — exactement le scénario que
le correctif 2.1 rend désormais systématique à chaque démarrage). Cause précise :
`deleteBySeanceId` n'est pas une requête bulk (`@Modifying @Query`) mais un "find puis
remove" différé — son DELETE reste en attente dans la file d'actions Hibernate. Hibernate
exécute systématiquement les INSERT en attente **avant** les DELETE en attente au sein d'un
même flush (ordre fixe de son `ActionQueue`), quel que soit l'ordre d'appel en Java :
réinsérer une notion déjà rattachée tentait donc l'INSERT avant que la suppression ne soit
réellement passée en base, violant la contrainte unique `(seance_id, notion_id)`. Correction :
un `seanceNotionRepository.flush()` explicite entre la suppression et la boucle de
réinsertion force l'exécution immédiate du DELETE. Sans cette découverte, le correctif 2.1
aurait transformé un bug rare (le rattachement change réellement) en bug systématique (le
rattachement se chevauche, ce qui est le cas courant).

### 2.3 — Bug 2 : modèle d'interaction fragile, pas un problème de timing pur

`useTutorRecorder.arreter()` attend correctement l'événement `onstop` avant de résoudre le
blob — le hook lui-même est correct. Le problème est en amont : `TuteurVocalPage` utilisait
un modèle "maintenir enfoncé" (`onMouseDown`/`onMouseUp`) sans filet de sécurité. Si la
souris dérive hors du bouton pendant que l'utilisateur parle (fréquent sur plusieurs
secondes), `onMouseUp` ne se déclenche jamais : l'enregistrement continue en arrière-plan,
orphelin, et un second clic démarre un second `MediaRecorder` qui écrase le premier (aucune
garde). Le clip court/accidentel qui en résulte part immédiatement, donnant l'impression que
le tuteur n'attend pas. Correction : bascule au clic (`onClick` unique, démarre/arrête selon
l'état) — plus robuste qu'un press-and-hold, et un pas concret vers la conversation continue
demandée pour la suite (pas la fonctionnalité complète, juste un modèle d'interaction plus
fiable dans le même esprit). `useTutorRecorder.demarrer()` gagne aussi une garde de défense
en profondeur (`if (recorderRef.current) return`), et `arreter()` remet enfin
`recorderRef.current` à `null` après l'arrêt — sans quoi cette garde aurait bloqué tout
second enregistrement pour toujours.

## 3. Les fichiers, un par un

### `SeanceDetailPage.tsx` (édité)
`gererDemarrage()` appelle `rattacherNotions` avant `demarrerTutorat`.

### `SeanceService.java` (édité)
`rattacherNotions` : ajout de `seanceNotionRepository.flush()` entre la suppression et la
réinsertion (déjà `@Transactional`, ce n'était pas le problème).

### `TuteurVocalPage.tsx` (édité)
Bouton d'enregistrement en bascule au clic (`onClick` unique), libellés ajustés ("Parler" /
"Clique pour envoyer..." / "Le tuteur réfléchit...").

### `useTutorRecorder.ts` (édité)
Garde anti-double-démarrage dans `demarrer()` ; `arreter()` remet `recorderRef.current` à
`null`.

### `SeanceServiceTest.java` (édité)
Nouveau test de régression vérifiant l'ordre exact des appels (`deleteBySeanceId` →
`flush()` → `save()`) via `Mockito.inOrder` — un test Mockito classique ne peut pas
vérifier le comportement réel de flush Hibernate, mais peut au moins garantir que le code
appelle bien `flush()` au bon endroit.

## 4. Les tests

248/248 tests backend (247 existants + 1 nouveau). `mvn -B verify` : `BUILD SUCCESS`, 0
finding SpotBugs/FindSecBugs, couverture maintenue. `npm run build` + `npm run lint` :
propres.

## 5. Comment on a vérifié en conditions réelles

Sur le backend réel (port 8080, code fusionné des 4 branches précédentes) : création d'un
couloir École, d'une matière, de deux notions (TCP, UDP), d'une séance ; rattachement de TCP
seul, puis marquage manuel de TCP comme "maîtrisée" en base pour simuler une séance déjà
terminée. Reproduction du bug avant correctif : `demarrerTutorat` répond bien "toutes les
notions sont déjà maîtrisées" (confirmé). Après correctif : cocher UDP sur la page de la
séance et cliquer directement "Démarrer le tutorat" (sans passer par "Enregistrer") démarre
correctement le tuteur sur UDP, avec TCP affiché "Maîtrisée" — capture d'écran à l'appui,
via Playwright piloté en conditions réelles (vrai appel Azure OpenAI pour générer l'ouverture
du tuteur). Le bug transactionnel (§2.2) a été isolé puis reproduit à l'identique par appels
HTTP directs avant d'être corrigé, avec confirmation du log d'erreur exact
(`duplicate key value violates unique constraint... Key (seance_id, notion_id)=(...) already
exists`) avant/après.

**Confirmé ensuite manuellement par l'utilisateur** dans son propre navigateur, sur
l'instance déjà tournante (backend port 8080, frontend port 5173) — reprise du compte de
test `tuteur-verif@test.local` et de la séance créée pendant la vérification automatisée,
démarrage du tutorat sur la notion UDP et bascule du bouton d'enregistrement au clic, sans
reproduire ni le blocage de progression ni le comportement "n'attend pas".

## 6. Limites connues, assumées, pas corrigées ici

- **Portée volontairement resserrée** aux deux bugs signalés — la refonte plus large
  (import en masse par un admin, inscription auto-assignée à un couloir par filière/promotion,
  contenu du tutorat basé sur des documents plutôt que saisi à la main, mode conversation
  libre façon Gemini Live) est cadrée séparément, pas traitée ici.
- **Le clic-pour-parler reste un point de départ**, pas la conversation continue complète
  demandée — un mode où le tuteur écoute en continu sans action explicite de l'utilisateur
  demanderait une détection d'activité vocale (VAD) côté client, hors de portée d'un
  correctif de bug.
- **Pas de test d'intégration réel** pour le comportement de flush Hibernate — seul un test
  Mockito vérifiant l'ordre des appels existe ; une vraie régression future sur ce point ne
  serait détectée qu'en conditions réelles (base de données réelle), pas par la suite de
  tests actuelle.

## 7. Pour reprendre seul

- Code de référence exact : `git checkout phase-16-corrections-tuteur-vocal`
- Si un futur `deleteBy...`/`removeBy...` (requête dérivée, pas `@Modifying @Query`) est
  suivi d'une réinsertion pouvant recouper les mêmes clés dans la même transaction, le même
  piège de flush Hibernate s'applique — `CouloirService.supprimerCouloir` documente déjà un
  problème voisin (transaction manquante), celui-ci est un problème d'ordre de flush au sein
  d'une transaction déjà présente, plus subtil.
- Prochaine direction possible : cadrage de la grosse refonte (import en masse, inscription
  auto-assignée, contenu piloté par documents, mode conversation libre).
