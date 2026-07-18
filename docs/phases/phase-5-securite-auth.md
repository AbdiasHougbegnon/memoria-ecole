# Phase 5 : authentification et verrouillage de l'API — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout phase-5-securite-auth
```
Ce tag pointe sur le commit `c462312`, vérifié end-to-end via curl contre un vrai backend + Postgres.

---

## 1. Le besoin

Un état des lieux détaillé des quatre chantiers restants de la Phase 4 (`couloirs/multi-tenant`, `isolation des données`, `sécurité`, `passage à l'échelle`) a été fait avant de commencer. Verdict sans appel sur la sécurité : **l'API était entièrement ouverte**. `pom.xml` ne contenait aucune dépendance de sécurité, `application.properties` aucune config d'auth, et `GET /api/v1/sessions` renvoyait toutes les sessions de tout le monde à n'importe qui connaissait l'URL — sans compte, sans token. `memoria-master-prompt.md` prévoyait pourtant Entra ID dès la Phase 1.

C'était le trou le plus critique des quatre : le multi-tenant et le passage à l'échelle peuvent attendre qu'il y ait de vrais utilisateurs à supporter, mais une API ouverte devient plus dure à corriger à mesure qu'on construit dessus. Choisie comme prochain chantier après validation avec l'utilisateur.

## 2. Les décisions de conception

### 2.1 — Entra ID tout de suite, ou une solution locale d'abord ?

Entra ID suppose un tenant Azure AD et une app registration déjà existants (client ID, secret, tenant ID, redirect URI) — rien de tout ça n'était disponible dans cette session. Plutôt que de bloquer sur une ressource externe non fournie, le choix a été de construire l'authentification avec **Spring Security + JWT et des comptes locaux (email + mot de passe)**, en respectant l'architecture propre déjà en place dans le projet : rien dans le domaine ne dépend du fournisseur d'identité choisi. Le jour où un tenant Entra ID existe, seule la couche `AuthController`/`AuthService` change — le reste de l'application (le mur de sécurité, l'extraction de l'utilisateur courant) reste identique.

### 2.2 — Quels endpoints restent ouverts ?

Le flux mobile QR code (Phase 2) permet de prendre une photo depuis un téléphone sans jamais se connecter — c'est une fonctionnalité "zéro setup" assumée dès sa conception, dont la sécurité repose sur la confidentialité de l'UUID de session, pas sur un compte utilisateur. Verrouiller ces 3 endpoints aurait cassé la fonctionnalité :

```
GET  /api/v1/sessions/{id}              (afficher le titre sur le telephone)
GET  /api/v1/sessions/{id}/documents    (lister les photos deja envoyees)
POST /api/v1/sessions/{id}/documents    (envoyer une nouvelle photo)
```

Tout le reste de `/api/v1/**` exige un token valide. `/api/v1/auth/**` reste ouvert, évidemment — impossible de se connecter derrière un mur d'authentification.

### 2.3 — Isolation par utilisateur ou juste un mur global ?

Pas de filtrage des sessions par créateur. Le modèle de déploiement de Memoria (`CLAUDE.md`) est **une instance dédiée par client**, pas un SaaS multi-tenant partagé — dans une même instance, tous les employés d'une même entreprise sont censés voir les mêmes réunions, comme un outil d'équipe classique. Ajouter un cloisonnement par utilisateur maintenant serait une fonctionnalité hypothétique non demandée ; le vrai besoin de cette phase était de fermer l'accès anonyme, pas de compartimenter entre collègues.

## 3. Les fichiers backend, un par un

### `Utilisateur` *(entité, nouveau package `core.auth`)*

```java
@Entity @Table(name = "utilisateurs", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class Utilisateur {
    private UUID id;
    private String email;
    private String motDePasseHash;  // jamais le mot de passe en clair
    private Instant dateCreation;
}
```

### `JwtService`

```java
public String genererToken(Utilisateur utilisateur) {
    return Jwts.builder()
            .subject(utilisateur.getId().toString())
            .claim("email", utilisateur.getEmail())
            .issuedAt(...).expiration(...)
            .signWith(cleSignature)
            .compact();
}

public Optional<UUID> validerEtExtraireUtilisateurId(String token) {
    try {
        Claims claims = Jwts.parser().verifyWith(cleSignature).build()
                .parseSignedClaims(token).getPayload();
        return Optional.of(UUID.fromString(claims.getSubject()));
    } catch (JwtException | IllegalArgumentException e) {
        return Optional.empty();  // token invalide, expire, ou signe avec un autre secret
    }
}
```

Secret HMAC configurable via `MEMORIA_JWT_SECRET` (défaut de dev inclus pour démarrer sans configuration), expiration 24h.

### `AuthService`

```java
public AuthResponse inscrire(String email, String motDePasse) {
    if (utilisateurRepository.existsByEmail(email)) {
        throw new EmailDejaUtiliseException(email);
    }
    Utilisateur utilisateur = new Utilisateur(email, passwordEncoder.encode(motDePasse));
    utilisateur = utilisateurRepository.save(utilisateur);
    return AuthResponse.depuis(utilisateur, jwtService.genererToken(utilisateur));
}

public AuthResponse connecter(String email, String motDePasse) {
    Utilisateur utilisateur = utilisateurRepository.findByEmail(email)
            .orElseThrow(IdentifiantsInvalidesException::new);
    if (!passwordEncoder.matches(motDePasse, utilisateur.getMotDePasseHash())) {
        throw new IdentifiantsInvalidesException();
    }
    return AuthResponse.depuis(utilisateur, jwtService.genererToken(utilisateur));
}
```

Mot de passe hashé avec BCrypt (`PasswordEncoder`), jamais comparé ni stocké en clair.

### `AuthController`

```
POST /api/v1/auth/inscription   -> 201, AuthResponse (token, utilisateurId, email)
POST /api/v1/auth/connexion     -> 200, AuthResponse
```

### `JwtAuthenticationFilter` et `SecurityConfig`

```java
protected void doFilterInternal(HttpServletRequest request, ...) {
    String enTete = request.getHeader("Authorization");
    if (enTete != null && enTete.startsWith("Bearer ")) {
        jwtService.validerEtExtraireUtilisateurId(enTete.substring(7))
                .ifPresent(id -> SecurityContextHolder.getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(id, null, List.of())));
    }
    filterChain.doFilter(request, response);
}
```

```java
.authorizeHttpRequests(authorize -> authorize
        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/v1/sessions/*").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/v1/sessions/*/documents").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/v1/sessions/*/documents").permitAll()
        .anyRequest().authenticated())
.addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
```

### `GestionnaireExceptionsApi` *(modifié)*

Deux entrées ajoutées : `EmailDejaUtiliseException` → 409, `IdentifiantsInvalidesException` → 401.

## 4. Un vrai bug trouvé et corrigé en cours de route

`JwtAuthenticationFilter` était d'abord annoté `@Component`. Deux conséquences imprévues :

1. **Double exécution par requête** — Spring Boot enregistre automatiquement tout bean `Filter` comme filtre servlet global, en plus de son ajout explicite dans la chaîne Spring Security via `addFilterBefore`. Le filtre aurait tourné deux fois par requête.
2. **`ResumeControllerTest` (un `@WebMvcTest`) cassé** — ce type de test ne charge que la tranche web, pas les `@Service` comme `JwtService`. Comme `@WebMvcTest` inclut automatiquement les beans `Filter`, `JwtAuthenticationFilter` était instancié sans que sa dépendance `JwtService` soit disponible → `NoSuchBeanDefinitionException`, contexte Spring en échec.

**Correctif** : `JwtAuthenticationFilter` n'est plus un bean Spring — une classe simple, instanciée à la main dans `SecurityConfig.filterChain()`. Toute la logique de câblage sécurité reste centralisée à un seul endroit, et le filtre ne peut plus être enregistré deux fois. `ResumeControllerTest` reçoit en complément `@AutoConfigureMockMvc(addFilters = false)` : ce test vérifie le comportement du contrôleur, pas l'application des règles de sécurité (déjà couvertes par les tests dédiés).

## 5. Le frontend

### `auth.ts` *(nouveau)*

Petit wrapper autour de `localStorage` : `enregistrerSession`, `obtenirToken`, `obtenirEmailConnecte`, `estConnecte`, `deconnecter`.

### `api.ts` *(modifié)*

Toutes les fonctions passent maintenant par un point d'entrée unique :

```typescript
async function appelApi(chemin: string, options: RequestInit = {}): Promise<Response> {
  const token = obtenirToken()
  const headers = new Headers(options.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const reponse = await fetch(chemin, { ...options, headers })
  if (reponse.status === 401) {
    deconnecter()
    window.location.href = '/connexion'
    throw new Error('Session expiree, reconnexion necessaire')
  }
  return reponse
}
```

Le token est ajouté seulement s'il existe — ce qui laisse les 3 endpoints du flux mobile fonctionner tels quels, avec ou sans connexion.

### `LoginPage.tsx` *(nouveau, route `/connexion`)*

Un seul formulaire, bascule connexion/inscription. Message d'erreur générique (email/mot de passe incorrect, ou email déjà utilisé) — jamais de détail qui permettrait de deviner si un compte existe.

### `RouteProtegee.tsx` *(nouveau)*

```typescript
export function RouteProtegee({ children }: { children: ReactNode }) {
  if (!estConnecte()) return <Navigate to="/connexion" replace />
  return children
}
```

### `App.tsx` *(modifié)*

Toutes les routes protégées sauf `/connexion` et `/mobile/sessions/:id` (flux QR code, volontairement sans garde).

### `SessionsListPage.tsx` *(modifié)*

Bouton "Déconnexion" ajouté à côté des liens existants.

## 6. Les tests

`AuthServiceTest` — 5 tests : inscription réussie (mot de passe hashé, token retourné), email déjà utilisé, connexion réussie, email inconnu, mot de passe incorrect.

`JwtServiceTest` — 4 tests : génération + extraction de l'id utilisateur, token invalide → vide, token signé avec un autre secret → vide, token expiré → vide.

`cd backend && mvn test` — **81/81 tests** passent au total (72 précédents + 9 nouveaux).

## 7. Comment on a vérifié en conditions réelles

Docker Desktop était arrêté, et le port 5433 (Postgres) est retombé dans une plage exclue par Hyper-V — le même problème environnemental récurrent déjà rencontré en Phase 1-4, sans lien avec ce code. Contournement habituel : conteneur Postgres temporaire sur un port libre, backend pointé dessus via `SPRING_DATASOURCE_URL`.

11 vérifications via curl contre le vrai backend :

| # | Vérification | Résultat |
|---|---|---|
| 1 | `GET /sessions` sans token | **403** |
| 2 | Inscription | 201, token JWT retourné |
| 3 | Connexion avec les mêmes identifiants | 200, token JWT retourné |
| 4 | Connexion avec mauvais mot de passe | **401** |
| 5 | Inscription avec un email déjà pris | **409** |
| 6 | `GET /sessions` avec token valide | 200, liste retournée |
| 7 | `POST /sessions` avec token valide | 201, session créée |
| 8 | `GET /sessions/{id}` sans token (flux mobile) | 200 — toujours ouvert |
| 9 | `GET /sessions/{id}/documents` sans token (flux mobile) | 200 — toujours ouvert |
| 10 | `POST /sessions/{id}/terminer` sans token | **403** — bloqué |
| 11 | `GET /sessions` avec un token invalide | **403** — rejeté |

Les 11 cas se comportent exactement comme prévu. `tsc --noEmit` propre côté frontend. Le serveur Vite sert `/connexion` (200 confirmé), mais **le formulaire de connexion n'a pas été cliqué dans un vrai navigateur** — aucun outil de navigateur disponible dans cet environnement pour cette vérification.

## 8. Limites connues, assumées, pas corrigées ici

- **Pas de RBAC** — un seul niveau d'accès (authentifié ou non), pas de rôles (admin/utilisateur) ni de permissions fines. Suffisant pour l'instant, pas assez pour un usage avec des rôles distincts (ex. qui peut confirmer un engagement vs qui peut juste consulter).
- **Pas d'Entra ID branché** — comptes locaux uniquement. La bascule est prévue par construction (seule la couche `AuthController`/`AuthService` change) mais pas faite, faute de tenant Azure AD disponible.
- **Pas d'isolation des données par utilisateur** — tout utilisateur authentifié voit toutes les sessions de l'instance. Choix assumé (modèle "outil d'équipe partagé"), pas un oubli.
- **Formulaire de connexion non testé dans un vrai navigateur** — seule la logique serveur (curl) et le typage (`tsc`) ont été vérifiés directement.
- **Le flux mobile QR code reste sans aucune authentification** — c'est son modèle de sécurité d'origine (UUID non devinable), pas une régression de cette phase.

## 9. Pour reprendre seul

- Code de référence exact : `git checkout phase-5-securite-auth`
- Pour brancher Entra ID plus tard : remplacer `AuthController`/`AuthService` par un flux OAuth2/OIDC, garder `JwtService` (ou son équivalent) pour émettre les tokens de session côté Memoria si besoin — le reste de l'application (le mur `SecurityConfig`, l'extraction de l'utilisateur courant dans les contrôleurs) n'a rien à changer.
- Pour ajouter des rôles/RBAC : `Utilisateur` gagnerait un champ `role`, `JwtAuthenticationFilter` mettrait les autorités correspondantes dans le `UsernamePasswordAuthenticationToken` au lieu de `List.of()`, et `SecurityConfig` utiliserait `.hasRole(...)` sur les routes concernées.
- Chemin de bout en bout : `LoginPage.tsx` → `POST /api/v1/auth/connexion` → `AuthController` → `AuthService` (vérifie via BCrypt) → `JwtService` (émet le token) → stocké en `localStorage` → `appelApi` l'attache à chaque requête suivante → `JwtAuthenticationFilter` le valide → `SecurityConfig` autorise ou bloque selon la route.
