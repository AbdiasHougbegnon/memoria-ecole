# Séparation École/Entreprise en deux modules — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout phase-6-modules-separes
```
Ce tag pointe sur le commit `718a097`, vérifié end-to-end avec de vrais comptes École et Entreprise (curl + navigateur).

---

## 1. Le besoin

École et Entreprise étaient mélangés dans les mêmes pages frontend : `SessionDetailPage.tsx` affichait côte à côte une section "Compte rendu complet (Entreprise)" et une section "Résumé de cours (Ecole)" ; la nav de `SessionsListPage.tsx` affichait "Engagements"/"Tableau de bord" (Entreprise) à côté de liens génériques, sans distinction visuelle. La connexion était un simple email/mot de passe, sans aucune notion de produit.

Demande explicite : deux modules réellement séparés (pas de mélange dans des pages partagées), regroupés par le moteur commun, avec une connexion qui dépend du module choisi. **Kafka et la séparation en microservices déployés séparément (bases de données séparées) restent explicitement hors périmètre** — reportés à un signal réel (deuxième développeur ou premier vrai déploiement client), conformément à la clause de pragmatisme du master prompt ("Phase 1, un développeur seul... les respecter en esprit suffit").

Trois décisions ont été tranchées avant conception :
1. Le module est **persisté sur le compte** (champ sur `Utilisateur`, choisi à l'inscription), pas un choix de routage éphémère.
2. **Écran de sélection dédié avant le login** ("Vous êtes... École / Entreprise"), pas un simple toggle sur le formulaire.
3. **Identité visuelle par module** pour les pages du moteur partagé (Recherche, Fils de mémoire, Couloirs, Paramètres) — même fonctionnalité, nav/couleurs différentes selon le module actif.

## 2. Les décisions de conception

### 2.1 — Suivre le chemin déjà anticipé pour un futur champ `role`

`docs/phases/phase-5-securite-auth.md` notait déjà, dans sa section "Pour reprendre seul" : *"Pour ajouter des rôles/RBAC : `Utilisateur` gagnerait un champ `role`, `JwtAuthenticationFilter` mettrait les autorités correspondantes dans le token au lieu de `List.of()`, et `SecurityConfig` utiliserait `.hasRole(...)`."* Ce chantier applique exactement ce chemin avec `module` à la place de `role` — aucun nouveau pattern d'autorisation inventé.

### 2.2 — `ModuleMemoria` dans `core.auth`, jamais dans un package produit

L'enum vit dans `com.memoria.core.auth`, pas dans `entreprise.*`/`ecole.*` : le mettre dans un package produit créerait une dépendance du moteur vers un produit, ce qui violerait la règle déjà respectée par `Couloir`/`MembreCouloir` ("concept de moteur générique, pas Ecole ni Entreprise, réutilisable par les deux produits").

### 2.3 — Le module est mis dans le JWT, pas relu en base à chaque requête

`JwtAuthenticationFilter` est volontairement stateless (déjà le cas pour l'email). Ajouter la claim `module` au token évite un appel DB par requête pour appliquer les restrictions d'autorisation. Limite assumée et documentée : un changement de module ne serait pris en compte qu'à la réémission d'un token (jusqu'à 24h) — sans conséquence ici, aucun endpoint ne permet de changer de module dans ce lot.

### 2.4 — Restriction par module dans `SecurityConfig`, pas dans les services

Un contrôle de module est un contrôle d'**autorisation**, pas une règle métier (`CLAUDE.md` : "Controllers orchestrate; they never contain business logic" — un service ne devrait pas non plus porter cette responsabilité transverse). Centraliser dans `SecurityConfig` via `hasAuthority("MODULE_ENTREPRISE")` / `hasAuthority("MODULE_ECOLE")` évite de dupliquer un `if (module != ...) throw` dans chaque service, et réutilise le mécanisme d'autorité Spring Security déjà en place pour le flux QR mobile.

### 2.5 — Cas "mauvais écran de login" : redirection silencieuse, pas un blocage

Un compte Entreprise qui se connecte depuis l'écran École est accepté normalement : `enregistrerSession` stocke toujours le `module` renvoyé par le **serveur** (jamais celui de l'URL cliquée), donc la nav qui suit affiche automatiquement le bon module sans logique spéciale. Refuser la connexion n'apporterait aucun bénéfice de sécurité (identifiants corrects), seulement de la friction — contraire au principe d'inscription sans friction du master prompt.

### 2.6 — Split de `SessionDetailPage` en sous-composants, pas en rendu conditionnel

Une fois la garde de module active côté backend, le `Promise.all` qui appelait *inconditionnellement* `obtenirCompteRendu`/`obtenirResumeCours`/`listerEngagementsSession` pour toute session aurait provoqué des 403 pour le mauvais module. Il fallait de toute façon rendre le fetch conditionnel au module, pas seulement l'affichage — donc extraction en `SessionDetailEntreprise.tsx` / `SessionDetailEcole.tsx`, chacun avec son propre effet de chargement, plutôt qu'un simple `if` autour du rendu dans le même fichier.

### 2.7 — Migration : correction manuelle en base, pas d'écran de choix forcé

`ddl-auto=update` ne peut pas ajouter une colonne `NOT NULL` sans défaut sur une table déjà peuplée (même problème déjà rencontré pour `rappel_retard_envoye`). Résolu manuellement : `ALTER TABLE ... ADD COLUMN module ... DEFAULT 'ENTREPRISE'` puis `DROP DEFAULT`. Pas de Flyway/Liquibase introduit pour une seule colonne (décision d'infra plus large, à envisager au même signal que Kafka), pas d'écran "choisis ton module" forcé au premier login — correction ponctuelle du développeur sur une base de dev qu'il contrôle intégralement (Phase 1, très peu de comptes, tous connus).

## 3. Les fichiers backend, un par un

### `ModuleMemoria.java` — nouveau, `com.memoria.core.auth`
```java
public enum ModuleMemoria { ECOLE, ENTREPRISE }
```

### `Utilisateur.java`
```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
private ModuleMemoria module;
```
Nouveau constructeur `Utilisateur(email, motDePasseHash, module)` ; l'ancien constructeur à 2 arguments est retiré (tous les appelants du code et des tests mis à jour).

### `InscriptionRequest.java` — module obligatoire
```java
public record InscriptionRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String motDePasse,
        @NotNull(message = "le module est obligatoire") ModuleMemoria module
) {}
```
`ConnexionRequest` ne change pas — le module n'est jamais fourni à la connexion, il est résolu depuis le compte existant.

### `AuthResponse.java` — expose le module réel du compte
```java
public record AuthResponse(String token, UUID utilisateurId, String email, ModuleMemoria module) {}
```

### `JwtService.java` — claim `module` + `validerEtExtraire`
```java
.claim("module", utilisateur.getModule().name())
// ...
public record UtilisateurAuthentifie(UUID utilisateurId, ModuleMemoria module) {}
public Optional<UtilisateurAuthentifie> validerEtExtraire(String token) { ... }
```
Renommage direct de `validerEtExtraireUtilisateurId` → `validerEtExtraire` (seuls `JwtAuthenticationFilter` et son test l'appelaient, pas de callers externes à préserver).

### `JwtAuthenticationFilter.java` — autorité par module
```java
var authorities = List.of(new SimpleGrantedAuthority("MODULE_" + u.module().name()));
var authentication = new UsernamePasswordAuthenticationToken(u.utilisateurId(), null, authorities);
```

### `SecurityConfig.java` — restriction des routes
```java
.requestMatchers("/api/v1/engagements/**").hasAuthority("MODULE_ENTREPRISE")
.requestMatchers("/api/v1/entreprise/**").hasAuthority("MODULE_ENTREPRISE")
.requestMatchers("/api/v1/sessions/*/engagements").hasAuthority("MODULE_ENTREPRISE")
.requestMatchers("/api/v1/sessions/*/compte-rendu").hasAuthority("MODULE_ENTREPRISE")
.requestMatchers("/api/v1/sessions/*/resume-cours").hasAuthority("MODULE_ECOLE")
```
Tout le reste (sessions, couloirs, recherche, fils-mémoire, transcriptions, résumés génériques, utilisateurs/moi, documents) reste `anyRequest().authenticated()` — accessible aux deux modules.

### `UtilisateurController.java` — `UtilisateurResponse` expose aussi le module
Cohérent avec l'exposition déjà existante de `email`/`nom` sur `/api/v1/utilisateurs/moi`.

## 4. Le frontend

### Nouveaux fichiers
- **`moduleIdentite.ts`** — table centralisée `IDENTITES_MODULE` (nom produit, couleurs) consommée par l'écran de choix, le login et la nav : évite toute incohérence de couleur entre ces trois surfaces.
- **`ChoixModulePage.tsx`** — route `/choix-module`, deux cartes École/Entreprise, chacune navigue vers `/connexion?module=ECOLE` ou `?module=ENTREPRISE`.
- **`components/Layout.tsx`** — remplace le bloc de header/nav codé en dur dans `SessionsListPage.tsx`. Lit `obtenirModuleConnecte()`, affiche nav et couleurs différentes par module (Entreprise : Fils de mémoire, Engagements, Tableau de bord, Couloirs, Rechercher, Paramètres ; École : Fils de mémoire, Couloirs, Rechercher, Paramètres).
- **`SessionDetailEntreprise.tsx`** / **`SessionDetailEcole.tsx`** — sections spécifiques extraites de `SessionDetailPage.tsx`, chacune avec son propre fetch (voir §2.6).

### Fichiers modifiés
- **`types.ts`** — `ModuleMemoria`, champ `module` sur `AuthResponse`.
- **`auth.ts`** — `CLE_MODULE` persisté par `enregistrerSession` (toujours la valeur serveur), lu par `obtenirModuleConnecte()`.
- **`LoginPage.tsx`** — lit `module` via `useSearchParams()` ; redirige vers `/choix-module` si absent/invalide. Couleurs/titre pilotés par `IDENTITES_MODULE`. Inscription envoie `module`, connexion ne l'envoie jamais.
- **`RouteProtegee.tsx`** — prop optionnelle `module?: ModuleMemoria` : redirige vers `/` si le module connecté ne correspond pas. Héberge désormais `Layout`, donc chaque page protégée en bénéficie sans changement individuel. Garde de **commodité UX**, pas une frontière de sécurité — la vraie frontière est `SecurityConfig` côté backend.
- **`App.tsx`** — route `/choix-module` ajoutée ; `/engagements` et `/tableau-de-bord` reçoivent `<RouteProtegee module="ENTREPRISE">`.
- **`SessionsListPage.tsx`** — perd tout son bloc de header/nav (géré par `Layout`), ne garde que le titre de section, `Recorder` et la liste des sessions.
- **`api.ts`** — `inscrire(email, motDePasse, module)` ; redirection sur 401 pointe vers `/choix-module` plutôt que `/connexion`.

## 5. Les tests

| Fichier | Changement |
|---|---|
| `AuthServiceTest` | Constructeurs `Utilisateur` mis à jour + `inscrire_stocke_le_module_choisi` (nouveau) |
| `JwtServiceTest` | Adapté à `validerEtExtraire` + `genererToken_propage_le_module_ecole` (nouveau) |
| `SessionServiceTest`, `TranscriptionServiceTest`, `CompteRenduServiceTest`, `EngagementServiceTest`, `RappelEngagementServiceTest` | Constructeurs `Utilisateur` mis à jour (module ajouté), aucune assertion nouvelle — ces tests ne portaient pas sur le module |

Pas de nouveau test `@WebMvcTest`/`@SpringBootTest` pour `SecurityConfig` — convention déjà explicite dans `ResumeControllerTest` (`@AutoConfigureMockMvc(addFilters = false)`, commentaire : règles Spring Security couvertes par les tests dédiés à `JwtService`/`AuthService` et par la vérification manuelle end-to-end).

`cd backend && mvn test` — **164/164 tests** passent (162 précédents + 2 nouveaux). `cd frontend && npx tsc -b --noEmit` — zéro nouvelle erreur (une erreur pré-existante et sans rapport sur `api.ts:7`, confirmée via `git stash` comme antérieure à ce chantier). `npm run lint` — clean.

## 6. Comment on a vérifié en conditions réelles

Backend redémarré avec le nouveau code (vérifié via son propre log de démarrage : `Started CoreApplication ... Tomcat started on port 8080`), migration SQL appliquée sur la base de dev réelle. Séquence vérifiée par curl avec deux vrais comptes :

```
POST /api/v1/auth/inscription {module: ECOLE}       -> 201, module: "ECOLE" dans le JWT
POST /api/v1/auth/inscription {module: ENTREPRISE}  -> 201, module: "ENTREPRISE" dans le JWT

GET /api/v1/engagements                  (token ECOLE)      -> 403
GET /api/v1/engagements                  (token ENTREPRISE) -> 200
GET /api/v1/entreprise/tableau-de-bord   (token ECOLE)      -> 403
GET /api/v1/entreprise/tableau-de-bord   (token ENTREPRISE) -> 200
GET /api/v1/sessions/{id}/resume-cours   (token ECOLE)      -> 200
GET /api/v1/sessions/{id}/resume-cours   (token ENTREPRISE) -> 403
GET /api/v1/utilisateurs/moi             (token ECOLE)      -> 200 (moteur commun)
GET /api/v1/couloirs                     (token ENTREPRISE) -> 200 (moteur commun)
```

Puis vérification visuelle par l'utilisateur dans le navigateur (`/choix-module` → connexion → nav/couleurs par module → détail de session affichant la bonne section) : confirmée fonctionnelle.

## 7. Limites connues, assumées, pas corrigées ici

- **Pas de changement de module possible après inscription** — aucun endpoint ne le permet ; cohérent avec le fait qu'un compte appartient durablement à un produit dans le modèle de déploiement cible (une instance dédiée par client).
- **Le module dans le JWT n'est jamais re-vérifié en base pendant sa durée de validité (24h)** — sans conséquence tant qu'aucun endpoint ne change le module d'un compte existant.
- **Migration manuelle, pas de script Flyway/Liquibase** — acceptable en Phase 1 (base de dev, développeur unique, comptes connus) ; à revoir si un vrai déploiement client l'exige.
- **Kafka et microservices séparés restent hors périmètre** — explicitement reporté à un signal réel (deuxième développeur ou premier vrai déploiement), pas construit ici par choix délibéré, pas par oubli.

## 8. Pour reprendre seul

- Code de référence exact : `git checkout phase-6-modules-separes`
- Chemin de bout en bout : `ChoixModulePage` → `LoginPage` (lit `?module=`) → `POST /api/v1/auth/inscription` ou `/connexion` → `AuthResponse.module` → `enregistrerSession` (persiste en localStorage) → `RouteProtegee` (garde UX) + `Layout` (nav/couleurs) → chaque requête API porte le JWT avec la claim `module` → `SecurityConfig` autorise ou bloque (403) selon la route.
- Prochaine direction retenue après cette brique : Docker + Terraform pour un déploiement mono-instance industrialisé (le master prompt le pose explicitement comme ce qui compte techniquement pour un vrai client, plutôt que le cloisonnement interne).
