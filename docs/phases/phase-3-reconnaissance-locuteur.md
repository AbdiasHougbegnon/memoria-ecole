# Phase 3 : reconnaissance de voix récurrente — première brique (enrôlement + affichage) — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout phase-3-reconnaissance-locuteur
```
Ce tag pointe sur le commit `1142f6a`, vérifié end-to-end avec deux vrais comptes, une vraie API, et une implémentation factice du port d'identification (Azure Speaker Recognition s'étant révélé retiré en cours de route — voir §7).

---

## 1. Le besoin

Point du roadmap Phase 3 du master prompt, jamais construit. Le déclencheur concret : `Engagement.responsable` (et `ActionCompteRendu.responsable`) n'est qu'un label de diarization ("Intervenant 2"), jamais lié à un vrai compte `Utilisateur` — limite déjà notée dans le code (`Engagement.java`) et documentée deux fois lors du chantier engagements de cette session (rappels d'échéance, boucle fermée). Choisi comme direction suivante après la clôture de ce chantier.

## 2. Le blocage découvert avant de coder, et les décisions posées

Avant toute conception, l'exploration du pipeline de diarization (`TranscripteurAzureSpeech`) a révélé un fait structurant : **chaque chunk audio (30s) est transcrit par un appel HTTP indépendant à Azure Speech Fast Transcription**, et Azure **renumérote les locuteurs à chaque appel** — aucune continuité n'existe même *au sein d'une seule session*, encore moins entre sessions. Le problème "reconnaissance récurrente" est donc en réalité un problème de résolution d'identité par segment, pas de recollement d'index.

Deux décisions ont été posées explicitement à l'utilisateur avant de coder :

1. **Enrôlement** : flux dédié — dans les paramètres du compte, l'utilisateur enregistre ~20s de voix avec une case de consentement explicite (donnée biométrique RGPD, opt-in, révocable). Rejeté : confirmer depuis une session existante (plus naturel mais résout une ambiguïté de segment/locuteur qu'on préfère éviter pour une première brique).
2. **Portée** : identification **affichée seulement**. Un segment identifié avec confiance suffisante affiche le vrai nom ("Intervenant 2 (Jean Dupont)") dans la transcription. `Engagement.responsable` et le ciblage des rappels ne changent **pas** — chantier séparé, une fois ce mécanisme vérifié, même logique d'incréments que la série couloirs.

## 3. Les décisions de conception

### 3.1 — Le port vit dans le moteur, la logique de résolution suit le grain réel du pipeline

`com.memoria.core.locuteur` (pas `entreprise`/`ecole`) : la reconnaissance de locuteur est une capacité générique. `IdentificationLocuteurService` groupe explicitement par **`(numeroSequence, locuteur)`**, jamais par `locuteur` seul à l'échelle d'une session — refléter fidèlement la contrainte découverte en §2 plutôt que de supposer une continuité qui n'existe pas. Conséquence assumée : une personne parlant dans 10 chunks reçoit jusqu'à 10 appels d'identification indépendants ; pas de recollement inter-chunks dans cette brique.

### 3.2 — Identification événementielle et par lot, pas par chunk

`IdentificationLocuteurService` écoute `SessionTermineeEvent` (même pattern que `ResumeService`/`FilMemoireService`), pas `ChunkAudioEnregistreEvent` : une identification par chunk pendant l'enregistrement serait prématurée (moins de contexte, plus d'appels) et contraire à la discipline de coûts du projet. Un seul passage à la fin de la session, sur tous les chunks déjà transcrits avec succès.

### 3.3 — Ne jamais marquer un rappel/une identification "faite" si elle n'a pas eu lieu

Comme pour les rappels d'engagements (même classe de bug déjà rencontrée), si aucun profil vocal n'est prêt ou si l'extraction/l'appel échoue, le segment reste non identifié plutôt que d'être marqué à tort — il pourra être retenté à la prochaine occasion si une empreinte devient disponible entre-temps (note : dans cette brique, l'identification ne tourne qu'une fois, à la fin de session ; ce garde-fou protège surtout contre un échec partiel au sein du même passage).

### 3.4 — Idempotence via l'état du segment lui-même

Pas de table de suivi séparée : un segment déjà identifié (`utilisateurIdentifieId != null`) est simplement ignoré au prochain passage. `SegmentLocuteur` reste un `@Embeddable` immuable (pas de setter générique) — une nouvelle instance est reconstruite via `avecIdentification(...)`, cohérent avec le style du projet.

### 3.5 — Un profil vocal par utilisateur, le ré-enrôlement remplace

`EmpreinteVocale` porte une contrainte unique sur `utilisateur_id`. Ré-enrôler supprime l'ancien profil externe (best-effort, ne bloque jamais si la suppression distante échoue) avant de créer le nouveau — pas de profils orphelins accumulés.

### 3.6 — `Utilisateur.nom`, petit ajout nécessaire

Aucun champ nom affichable n'existait (seulement `email`). Sans lui, "Jean Dupont" n'existe nulle part dans le système. Ajouté en `nullable`, avec repli sur l'email (`nomAffichage()`) — mécanique, pas une extension de périmètre.

## 4. Les fichiers backend, un par un

### `com.memoria.core.locuteur` (nouveau package)

- **`EmpreinteVocale`** (entité) : `utilisateurId`, `profilExterneId` (nullable), `statut` (`EN_ATTENTE`/`PRETE`/`ECHEC`), `dateConsentement` (preuve RGPD), `dateCreation`.
- **`IdentificateurLocuteurPort`** : `enroller(byte[]): String`, `supprimerProfil(String)`, `identifier(byte[], List<String>): ResultatIdentification`. Détail d'infrastructure remplaçable, même principe que `TranscripteurPort`.
- **`IdentificateurLocuteurAzureSpeech`** : implémentation REST (`java.net.http.HttpClient`, pas de SDK, même réflexe que `TranscripteurAzureSpeech` pour la dégradation gracieuse) — **non fonctionnelle en l'état, voir §7**.
- **`ExtracteurAudioLocuteur`** : utilitaire statique pur, découpe un WAV de chunk en ne gardant que les tranches PCM correspondant à un locuteur donné, reconstruit un WAV autonome. Testable sans mock.
- **`EmpreinteVocaleService`** / **`EmpreinteVocaleController`** : `POST/GET/DELETE /api/v1/utilisateurs/moi/empreinte-vocale`.
- **`IdentificationLocuteurService`** : écoute `SessionTermineeEvent`, résout et écrit les identifications (voir §3.1–3.2).
- **`IdentificateurLocuteurFactice`** : implémentation de vérification (profil Spring `verification-locuteur`, jamais actif par défaut) — voir §6.

### `com.memoria.core.transcription` (modifiés)

`SegmentLocuteur` gagne `utilisateurIdentifieId`/`confianceIdentification` + `avecIdentification(...)`. `Transcription.identifierLocuteur(locuteur, utilisateurId, confiance)` réécrit les segments d'un locuteur local donné. `TranscriptionService.obtenirTranscriptionsAvecIdentification` résout les noms via `UtilisateurRepository.findAllById` (une seule requête, pas de N+1) ; `TranscriptionController` l'utilise désormais.

### `com.memoria.core.audio` (modifié)

`StockageAudioPort.lire(String): byte[]` ajouté (implémentation locale : `Files.readAllBytes`) — les chunks bruts étaient déjà persistés indéfiniment, juste jamais relus jusqu'ici.

### `com.memoria.core.auth` (modifiés/nouveaux)

`Utilisateur.nom` + `renseignerNom`/`nomAffichage`. Nouveau `UtilisateurController` : `GET/PUT /api/v1/utilisateurs/moi`.

## 5. Le frontend

`SessionDetailPage.tsx` : affiche `Intervenant {n} (Nom)` quand identifié — purement additif. Nouvelle page `/parametres` (`ParametresCompteePage.tsx`) : champ nom, enrôlement vocal (case de consentement obligatoire, `MediaRecorder` ~20s, conversion WAV, statut courant, révocation). `convertirBlobEnWav` extrait de `Recorder.tsx` vers `frontend/src/audioWav.ts`, partagé entre l'enregistrement de session et l'enrôlement vocal.

## 6. Les tests et la vérification en conditions réelles

### Tests unitaires (Mockito, style existant)

`ExtracteurAudioLocuteurTest` (WAV synthétique, découpe/concaténation/clamp), `EmpreinteVocaleServiceTest` (consentement, taille minimale, échec Azure → `ECHEC` sans exception, ré-enrôlement, révocation résiliente), `IdentificationLocuteurServiceTest` (aucun profil prêt → zéro appel, seuil de confiance, durée minimale, idempotence, résilience par locuteur, **deux chunks avec le même index local résolus vers des personnes différentes**), `IdentificateurLocuteurAzureSpeechTest`, `TranscriptionServiceTest` (résolution des noms, utilisateur supprimé toléré).

`cd backend && mvn test` — **149/149 tests** passent. `cd frontend && npx tsc --noEmit` — aucune erreur.

### Vérification réelle, sans vraies credentials Azure Speaker Recognition

Même principe que le faux serveur SMTP utilisé plus tôt dans le projet : `IdentificateurLocuteurFactice`, actif uniquement sous le profil Spring `verification-locuteur`, jamais par défaut. Ne prétend pas reconnaître une vraie voix — différencie déterministiquement par la longueur en octets de l'échantillon enrôlé face à celle du segment à identifier, un signal dérivé de l'audio réel plutôt qu'un mock aveugle.

Séquence réelle : deux comptes créés, chacun enrôlé via la vraie route API multipart avec un échantillon WAV réellement distinct (3s vs 8s) ; une session créée, deux chunks placés sur le disque réel (mêmes fichiers WAV) avec les lignes `AudioChunk`/`Transcription`/`SegmentLocuteur` correspondantes insérées en base, **les deux segments portant le même index local `locuteur=1`** ; `POST /sessions/{id}/terminer` déclenche réellement `SessionTermineeEvent`.

Résultat (`GET /sessions/{id}/transcriptions`) :

| Chunk | Index local | Attendu | Obtenu |
|---|---|---|---|
| #0 (3s, profil A) | locuteur 1 | résolu vers A | **"Alice Martin"**, confiance 0.99 |
| #1 (8s, profil B) | locuteur 1 (même index) | résolu vers B | **"Bob Bernard"**, confiance 0.99 |

Confirme que l'absence de continuité inter-chunks est bien respectée : le même index local désigne deux personnes différentes selon le chunk, et chacune est résolue correctement. Vérifié aussi visuellement (Playwright) dans la vraie page de session ("Intervenant 1 (Alice Martin)" / "Intervenant 1 (Bob Bernard)") et dans la page `/parametres` (statut "Active", date de consentement, nom pré-rempli). Révocation testée en réel : `DELETE` → 204, statut repasse à absent.

## 7. Découverte en cours de vérification : Azure Speaker Recognition est retiré

En vérifiant l'environnement pour préparer un test contre le vrai Azure, il s'est avéré que `AZURE_SPEECH_KEY`/`AZURE_SPEECH_REGION` sont bien configurées (la diarization réelle fonctionne probablement). Mais une recherche a confirmé que **Azure Speaker Recognition a été retiré par Microsoft le 30 septembre 2025** — pas une question d'accès restreint (Limited Access, ce qui était le risque anticipé avant de coder), mais un service qui n'existe plus du tout. Aucun remplacement direct n'est proposé par Microsoft dans Azure (leur seule suggestion, la diarization temps réel, ne résout pas la continuité inter-sessions). Alternatives citées : modèles auto-hébergés (pyannote, SpeechBrain, WeSpeaker) ou d'autres fournisseurs cloud (AWS Connect Voice ID, Google SpeakerID).

**`IdentificateurLocuteurAzureSpeech` est donc non fonctionnel en l'état** — documenté clairement comme tel dans le commentaire de classe, gardé comme référence de forme en attendant un choix de fournisseur de remplacement. Le port `IdentificateurLocuteurPort` isole cette décision : remplacer cette seule classe suffira, aucun autre fichier de cette brique n'a besoin de changer.

## 8. Limites connues, assumées, pas corrigées ici

- **Aucun fournisseur d'identification réellement fonctionnel** — voir §7. La brique est complète et vérifiée dans tout ce qui l'entoure, mais inerte en production tant qu'un remplaçant n'est pas choisi.
- **Pas de recollement inter-chunks** — assumé (§3.1), une personne parlant dans plusieurs chunks de la même session reçoit plusieurs identifications indépendantes.
- **Pas de ciblage de `Engagement.responsable`/rappels** — scope volontairement limité à l'affichage (§2).
- **Pas de ré-identification rétroactive** des sessions enregistrées avant cette brique.
- **Pas d'effacement rétroactif complet** des `utilisateurIdentifieId` déjà écrits à la révocation d'une empreinte — mirroir : supprimer un email ne le désenvoie pas.
- **Seuil de confiance (`0.70`) non calibré** contre un vrai modèle — un placeholder, à revoir quand un fournisseur réel sera choisi.

## 9. Pour reprendre seul

- Code de référence exact : `git checkout phase-3-reconnaissance-locuteur`
- Pour remplacer le fournisseur d'identification : implémenter `IdentificateurLocuteurPort` avec le nouveau choix (auto-hébergé ou autre cloud), retirer `@Profile("!verification-locuteur")` de l'ancienne classe Azure ou la supprimer, rien d'autre à toucher.
- Chemin de bout en bout : `ParametresCompteePage.tsx` (consentement + enregistrement) → `enregistrerEmpreinteVocale` (`api.ts`) → `EmpreinteVocaleController` → `EmpreinteVocaleService` → `IdentificateurLocuteurPort.enroller` → ... → `SessionTermineeEvent` → `IdentificationLocuteurService.surSessionTerminee` → `ExtracteurAudioLocuteur` → `IdentificateurLocuteurPort.identifier` → `Transcription.identifierLocuteur` → `TranscriptionService.obtenirTranscriptionsAvecIdentification` → `SessionDetailPage.tsx`.
- Pour la vérification factice : `SPRING_PROFILES_ACTIVE=verification-locuteur`, voir §6 pour le protocole complet.
