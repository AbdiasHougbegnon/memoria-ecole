# Rôle admin — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout phase-20-role-admin
```

---

## 1. Le besoin

L'audit sécurité/architecture de juillet 2026 (`docs/audit-securite-architecture-2026-07.md`)
et `docs/gouvernance-donnees.md` §6 documentaient le même trou depuis la phase 13 : **aucun
rôle admin n'existe dans tout le projet**. Deux capacités RGPD manquaient concrètement :

1. Un administrateur d'instance ne pouvait pas déclencher l'effacement du compte d'un
   utilisateur qui en fait la demande par un autre canal (email, support) sans accès direct
   à la base — seul le self-service (`DELETE /api/v1/utilisateurs/moi`) existait.
2. Le journal RGPD (`journal_rgpd`) n'était consultable qu'en base directe — aucun endpoint.

Ce lot construit exactement ces deux capacités, pas plus (pas de gestion d'utilisateurs
générale, pas de rôle admin par module, pas de révocation en libre-service — direction
future si le besoin se confirme).

## 2. Les décisions de conception

### 2.1 — Comment un compte devient admin

Réutilise exactement la doctrine déjà en place pour `memoria.inscription.domaines-autorises`
(`AuthService`, phase 15) : **`memoria.admin.emails-autorises`**, liste d'emails complets
séparés par des virgules, vide par défaut = aucun admin. "Instance = tenant", pas de concept
de tenant à construire (même raisonnement que le budget Azure en phase-11 et la rétention
RGPD en phase-13).

Différence avec le précédent : appliquer la liste seulement à l'inscription laisserait un
trou si la personne s'est déjà inscrite avant que l'opérateur de l'instance ne configure la
liste. D'où un second mécanisme, `AdminBootstrapRunner` (`ApplicationRunner`), qui réconcilie
la même liste à **chaque démarrage** : pour chaque email listé qui correspond à un compte
existant, s'assure que `estAdmin = true`. Ça couvre le bootstrap initial ET la promotion d'un
compte déjà créé — redémarrer après avoir mis à jour la config suffit, jamais d'UI ni
d'endpoint de promotion. Retirer un email de la liste ne rétrograde personne automatiquement
(pas de révocation en libre-service, cohérent avec le reste de la doctrine RGPD du projet).

### 2.2 — Statut admin propagé via JWT, comme le module

Même mécanique que `ModuleMemoria` (`JwtService`/`JwtAuthenticationFilter`) : un claim
`admin` (boolean) dans le token, lu par le filtre pour ajouter l'autorité Spring
`ROLE_ADMIN` en plus de `MODULE_*`. Stateless, pas de relecture en base par requête — même
limite assumée que pour le module : un changement de statut admin n'est pris en compte qu'à
la réémission du token (reconnexion).

### 2.3 — Traçabilité : qui a déclenché un effacement

`JournalRgpd` gagne une colonne nullable `initiateurId` : `null` pour un effacement
self-service ou une purge de rétention, l'UUID de l'admin pour un effacement déclenché au nom
d'autrui. Typé plutôt que casé dans le champ texte libre `details` — cohérent avec la
doctrine de traçabilité du projet (toute donnée sensible reste traçable jusqu'à sa source).

### 2.4 — Résolution par email, pas par UUID

Les demandes RGPD arrivent par email (support, formulaire), jamais par UUID. Nouvelle
méthode `GouvernanceDonneesService.resoudreParEmail`, insensible à la casse
(`UtilisateurRepository.findByEmailIgnoreCase`, nouvelle méthode dérivée) — contrairement à
`findByEmail` (exact, comportement historique inchangé pour `connecter()`/`inscrire()`).

### 2.5 — Même séquence d'orchestration que le self-service

`GouvernanceAdminController.effacerCompte` reproduit exactement la séquence à deux appels de
`GouvernanceDonneesController.supprimerCompte` (`effacerCompte` puis `finaliserEffacement` —
`@Transactional` ne s'applique pas à un appel interne au sein d'une même instance Spring).
Seule différence : la cible est résolue par email et l'admin appelant est tracé comme
initiateur. `finaliserEffacement` gagne une troisième surcharge à 3 arguments
(`initiateurId`) ; la variante 2-arguments existante (self-service) délègue avec
`initiateurId = null` — aucun appelant existant à modifier.

## 3. Les fichiers, un par un

### `Utilisateur.java` (édité)
Ajoute `estAdmin` (boolean, `promouvoirAdmin()`). Colonne booléenne — voir §5 pour le piège
de migration rencontré malgré tout.

### `AuthService.java` (édité)
`@Value("${memoria.admin.emails-autorises:}")`. Promotion à l'inscription si l'email est
listé (insensible à la casse).

### `AdminBootstrapRunner.java` (nouveau, `core.auth`)
`ApplicationRunner` : réconcilie la liste à chaque démarrage pour les comptes déjà existants.

### `AuthResponse.java`, `JwtService.java`, `JwtAuthenticationFilter.java` (édités)
`AuthResponse.admin`. `JwtService` : claim `admin`, `UtilisateurAuthentifie.admin`.
`JwtAuthenticationFilter` : ajoute `ROLE_ADMIN` aux autorités si `admin` est vrai.

### `SecurityConfig.java` (édité)
`.requestMatchers("/api/v1/admin/**").hasAuthority("ROLE_ADMIN")`.

### `UtilisateurNotFoundException.java`, `UtilisateurRepository.java` (édités)
Constructeur `(String email)` ; `findByEmailIgnoreCase`.

### `JournalRgpd.java`, `JournalRgpdRepository.java` (édités)
`initiateurId` nullable + constructeur 4-arg (le 3-arg délègue avec `null`).
`findAllByOrderByDateActionDesc`.

### `GouvernanceDonneesService.java` (édité)
`resoudreParEmail(String)`, `finaliserEffacement(UUID, List<UUID>, UUID initiateurId)`.

### `GouvernanceAdminController.java`, `JournalRgpdResponse.java` (nouveaux, `core.gouvernance`)
`POST /api/v1/admin/utilisateurs/effacement` (body `{email}`), `GET /api/v1/admin/journal-rgpd`.

### Frontend — `types.ts`, `auth.ts`, `api.ts` (édités)
`AuthResponse.admin`, `JournalRgpdEntry`. `estAdminConnecte()` (localStorage, même pattern que
`obtenirModuleConnecte`). `effacerCompteAdmin`, `listerJournalRgpd`.

### Frontend — `RouteProtegee.tsx`, `Layout.tsx` (édités)
`adminRequis?: boolean` (garde de commodité UX, la vraie frontière reste `SecurityConfig`).
Section de nav "Administration" visible seulement si `estAdminConnecte()`.

### Frontend — `AdminPage.tsx` (nouveau)
Formulaire d'effacement par email (avec confirmation), table du journal RGPD
(type/cible/initiateur/date/détails, "self-service" affiché quand `initiateurId` est nul).

## 4. Les tests

317/317 tests backend (304 existants + 13 nouveaux) :
- `AuthServiceTest` — promotion admin à l'inscription (email listé, insensible à la casse,
  email absent de la liste → pas de promotion).
- `JwtServiceTest` — claim admin aller-retour (vrai/faux par défaut).
- `AdminBootstrapRunnerTest` — promotion d'un compte existant, idempotence (déjà admin),
  aucun effet si le compte n'existe pas encore ou si la liste est vide.
- `GouvernanceDonneesServiceTest` — `resoudreParEmail` (trouvé/absent),
  `finaliserEffacement` 3-arg trace l'initiateur, la variante 2-arg n'en trace aucun.

Pas de test dédié pour `GouvernanceAdminController` (aucun des deux contrôleurs de
gouvernance n'en a — logique testée au niveau service, comportement HTTP vérifié en
conditions réelles, cohérent avec le reste du projet).

`mvn -B clean verify` : `BUILD SUCCESS`, 0 finding SpotBugs/FindSecBugs.
`npm run build` + `npm run lint` : propres.

## 5. Comment on a vérifié

**Troisième occurrence du même piège de migration Hibernate** (déjà rencontré deux fois dans
ce projet, y compris pour une colonne booléenne cette fois — contrairement à ce qui avait été
noté comme "sûr" après la première occurrence) : `alter table utilisateurs add column
est_admin boolean not null` a échoué au démarrage (`column "est_admin" ... contains null
values` — la table avait déjà 68 lignes). Corrigé en trois temps manuels sur la base
Postgres locale : ajout de la colonne sans contrainte, backfill (`UPDATE ... SET est_admin =
false WHERE est_admin IS NULL`), puis ajout de la contrainte `NOT NULL`. Enseignement mis à
jour : **`ddl-auto=update` n'ajoute jamais de clause `DEFAULT` sur `ALTER TABLE ... ADD
COLUMN`, y compris pour un booléen** — la distinction qui compte n'est pas primitif vs
non-primitif mais table vide vs déjà peuplée, sans exception.

Vérification de bout en bout via l'API réelle (backend redémarré avec
`MEMORIA_ADMIN_EMAILS_AUTORISES` configuré) :
- Inscription d'un compte avec un email listé → `admin: true` dans la réponse immédiatement.
- Compte non-admin (JWT valide) sur `/api/v1/admin/journal-rgpd` → `403`.
- Admin déclenche l'effacement d'un second compte par email → `204`, le compte ne peut plus
  se connecter ensuite.
- Le journal RGPD affiche la nouvelle entrée en tête (tri décroissant), avec `initiateurId`
  = l'UUID de l'admin, à côté d'une entrée self-service antérieure avec `initiateurId: null`.
- `AdminBootstrapRunner` : compte inscrit sans être admin, ajouté à la liste, backend
  redémarré → promu au démarrage (log confirmé), `admin: true` à la connexion suivante.

Vérification visuelle via Playwright sur le frontend réel (`localhost:5173`) : connexion en
tant qu'admin, section "Administration" visible dans la nav, page `/admin` affichant le
formulaire d'effacement et le tableau du journal RGPD avec les deux entrées (dont la
distinction "self-service" vs UUID admin) — capture d'écran à l'appui.

## 6. Limites connues, assumées, pas corrigées ici

- **Pas de révocation en libre-service** — retirer un email de
  `memoria.admin.emails-autorises` ne rétrograde jamais un compte déjà promu ; nécessite un
  accès direct à la base, aussi rare que les autres opérations admin non construites.
- **Pas de gestion d'utilisateurs générale** — seulement effacement au nom d'autrui et
  consultation du journal ; pas de liste des utilisateurs, pas de modification de profil par
  un admin.
- **Statut admin propagé par JWT, pas relu en base** — un changement (nouvelle promotion,
  config modifiée) ne prend effet qu'à la reconnexion, même limite déjà assumée pour le
  module.
- **Pas de vérification d'identité intégrée** — la page admin suppose que la demande
  d'effacement a déjà été vérifiée par un autre canal avant d'être saisie ; rappelé
  explicitement dans l'UI, pas un contrôle applicatif.

## 7. Pour reprendre seul

- Code de référence exact : `git checkout phase-20-role-admin`.
- Pour activer un admin sur une instance existante : configurer
  `MEMORIA_ADMIN_EMAILS_AUTORISES` (une ou plusieurs adresses, séparées par des virgules) et
  redémarrer — couvre à la fois un compte à créer et un compte déjà existant.
- Direction possible suivante, non demandée pour l'instant : révocation en libre-service,
  gestion d'utilisateurs plus large, si le besoin se confirme.
