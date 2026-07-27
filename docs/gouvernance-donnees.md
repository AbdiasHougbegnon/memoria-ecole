# Gouvernance des données (RGPD)

Le master prompt qualifie cette section de *"condition d'entrée dans les secteurs régulés,
pas un raffinement tardif"* : durée de conservation paramétrable, droit à l'effacement réel
(y compris dérivés), export à la demande, audit des accès sur les données sensibles,
chiffrement au repos/transit, consentement explicite à l'enregistrement.

## 1. Périmètre de cette brique

| Principe | État |
|---|---|
| **Droit à l'effacement** | **Construit** — voir §2 |
| **Export des données** | **Construit** — voir §3 |
| **Durée de conservation paramétrable** | **Construite** — voir §4 |
| **Audit** | **Réduit et proportionné** — journal des opérations RGPD elles-mêmes, pas un audit général de tout accès. Voir §5 |
| Chiffrement au repos/transit | **Non traité ici** — infrastructure, pas du code applicatif. Voir §6 |
| Consentement explicite à l'enregistrement des participants | **Construit** — voir §7 |

## 2. Droit à l'effacement (`DELETE /api/v1/utilisateurs/moi`)

Self-service par défaut. Depuis la phase 20, un compte admin peut aussi déclencher
l'effacement d'un utilisateur qui en fait la demande par un autre canal (email, support) —
voir `docs/phases/phase-20-role-admin.md`. Les deux chemins partagent la même logique
d'effacement (`GouvernanceDonneesService.effacerCompte`), seule la résolution de la cible et
la traçabilité de l'initiateur diffèrent.

**Ce qui est supprimé définitivement (données personnelles exclusives)** : empreinte vocale
(réutilise `EmpreinteVocaleService.revoquer`), séances de tutorat et leurs tours de dialogue,
progression de maîtrise des notions, sessions personnelles (`couloirId == null`) et tout ce
qui en dérive (chunks audio sur disque, documents, transcriptions, résumés, compte-rendu,
documents Azure AI Search).

**Ce qui est anonymisé plutôt que supprimé (données partagées)** : une session rattachée à
un couloir garde son contenu (les autres membres en dépendent) mais `createurId` est mis à
`null` — l'état déjà supporté pour les sessions historiques, pas une nouvelle sémantique.
L'identification d'un locuteur (`SegmentLocuteur.utilisateurIdentifieId`) et l'assignation
d'un engagement (`Engagement.responsableUtilisateurId`) sont anonymisées de la même façon.

**Couloirs possédés** : transférés au membre le plus ancien s'il y en a un
(`CouloirService.transfererPropriete`, déjà existant), sinon supprimés
(`CouloirService.supprimerCouloir`, déjà existant, tolère déjà les sessions dont le couloir
a disparu).

**Séquencement** : une transaction Postgres (`GouvernanceDonneesService.effacerCompte`)
gère tout ce qui précède et collecte la liste des sessions personnelles à purger
complètement. Une seconde étape, hors transaction et best-effort
(`finaliserEffacement`) — orchestrée par le contrôleur, pas par un appel interne au même
bean (l'auto-invocation Spring ignorerait le `@Transactional`) — supprime les fichiers
disque et les documents Azure AI Search, puis journalise l'opération. Un échec de nettoyage
externe (disque indisponible, Azure Search en panne) ne bloque jamais l'effacement des
données personnelles côté Postgres, qui reste autoritaire.

## 3. Export des données (`GET /api/v1/utilisateurs/moi/export`)

Parcourt exactement le même périmètre que l'effacement, en lecture seule : profil, sessions
créées (avec transcription complète, résumés, compte-rendu), engagements dont l'utilisateur
est responsable, séances de tutorat et tours de dialogue, maîtrise par notion, couloirs.

**L'empreinte vocale n'expose que sa présence et sa date de consentement** — jamais la
référence au profil chez le fournisseur externe (Azure Speaker Recognition). Memoria ne
stocke jamais elle-même un vecteur biométrique brut (uniquement cette référence externe) ;
elle est exclue de l'export par précaution, pas parce qu'un vecteur brut existerait en base.

## 4. Durée de conservation + purge planifiée

"Instance = tenant" (même raisonnement que le budget Azure en phase-11) : une durée de
rétention **globale** pour l'instance, pas un concept de tenant à construire.

```properties
memoria.rgpd.retention-jours=-1   # -1 = desactive par defaut
memoria.rgpd.retention.cron=0 0 3 * * *
```

Désactivée par défaut (doctrine "sûr par défaut", déjà appliquée au budget Azure) : aucune
suppression tant que le client n'a pas configuré explicitement sa durée de conservation. Le
balayage quotidien réutilise `SessionPurgeService.purgerSessionCompletement` — **sans
distinction personnelle/partagée**, contrairement à l'effacement sur demande : une politique
de rétention s'applique à toutes les données de l'instance, pas seulement à celles d'un
utilisateur qui la demande explicitement.

## 5. Journal des opérations RGPD (`journal_rgpd`)

Une ligne par effacement de compte et par exécution du balayage de rétention (pas une ligne
par session purgée, pour ne pas noyer le journal) : type d'action, utilisateur cible
(nullable pour une purge de rétention), **initiateur** (nullable — l'UUID de l'admin pour un
effacement au nom d'autrui, `null` pour un self-service ou une purge de rétention, voir
phase 20), date, détails. **Ce n'est pas un audit général de tout accès à toute donnée
sensible** — un tel audit interdirait chaque lecture/écriture dans toute l'application, un
chantier d'observabilité à part entière, hors de proportion pour cette brique. Consultable
via `GET /api/v1/admin/journal-rgpd` (réservé `ROLE_ADMIN`, voir phase 20).

## 5bis. Note d'architecture (juillet 2026)

`GouvernanceDonneesService` et `SessionPurgeService` n'importent plus aucune classe
concrète `ecole.*`/`entreprise.*` — chaque produit apporte sa part via les ports
`EffaceurDonneesUtilisateurPort`/`PurgeurDonneesSessionPort`/
`ExportateurDonneesUtilisateurPort` (`core.gouvernance`), implémentés par
`EcoleGouvernanceContributor`/`EntrepriseGouvernanceContributor`. Comportement
fonctionnel inchangé (voir §2/§3) — seule l'architecture interne a bougé, pour respecter
la règle "le moteur ne dépend jamais d'un produit" (voir
`docs/audit-securite-architecture-2026-07.md` §2).

## 6. Ce qui reste à faire, explicitement

- **Chiffrement au repos et en transit** : ni implémenté dans le code, ni encore documenté
  dans `docs/deploiement.md` (vérifié : aucune mention de TLS/HTTPS/chiffrement). C'est un
  vrai trou de la couche infrastructure (reverse proxy TLS, chiffrement au repos du
  fournisseur Postgres managé), pas un renvoi de façade — à traiter dans une brique dédiée
  au déploiement/infrastructure, pas dans une brique de code applicatif. Bloqué faute
  d'accès à de vrais identifiants Azure/certificats, même raisonnement que l'absence de
  `terraform apply` documentée dans `docs/deploiement.md`.
- **Pas de révocation admin en libre-service** ni de gestion d'utilisateurs générale au-delà
  de l'effacement au nom d'autrui et de la consultation du journal — voir
  `docs/phases/phase-20-role-admin.md` §6.
- **`Matiere.createurId`** (`nullable = false`) n'est pas anonymisé à l'effacement —
  changerait un schéma pour un champ à faible sensibilité (créateur d'un intitulé de
  matière, pas un contenu personnel).
- **`FilMemoire.resumeCumulatif`** n'est pas retouché quand une session en est retirée — le
  texte déjà généré par IA peut encore faire écho au contenu supprimé ; le régénérer
  demanderait une vraie opération de résumé, hors de proportion pour une suppression de
  session.

## 7. Consentement explicite à l'enregistrement (phase 21)

`POST /api/v1/sessions` exige désormais `consentementEnregistrement: true` dans le corps de
la requête — sinon `ConsentementEnregistrementRequisException` (`400`), avant toute
construction ou sauvegarde de la session. Même doctrine que
`EmpreinteVocaleService.enregistrerConsentement` (consentement vérifié en premier, par le
service, pas par une contrainte de schéma) : le créateur de la session confirme avoir
informé les participants qu'elle sera enregistrée, horodaté sur
`Session.dateConsentementEnregistrement` (nullable — les sessions antérieures à cette
colonne n'ont pas ce champ, pas de reconstitution rétroactive possible).

Frontend (`Recorder.tsx`) : case à cocher obligatoire ("J'ai informé les participants que
cette session sera enregistrée"), le bouton "Démarrer" reste désactivé tant qu'elle n'est
pas cochée — garde de commodité UX, la vraie frontière reste le contrôleur backend. La
reprise d'une session interrompue (`reprendre()`) ne redemande pas confirmation : le
consentement a déjà été donné à la création de cette session précise.

**Limite assumée** : l'application ne peut pas vérifier qu'un participant physiquement
présent a réellement été informé — seule la déclaration du créateur est enregistrée,
horodatée et traçable. C'est le même niveau de garantie que pour n'importe quelle mention
légale déclarative (case à cocher), pas un contrôle technique de présence.
