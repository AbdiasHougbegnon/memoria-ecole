# Restriction d'inscription par domaine email — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout phase-15-restriction-domaine-email
```

---

## 1. Le besoin

Le master prompt (section "Accès utilisateurs et déploiement") précise l'inscription
sans friction du produit : *"connexion par email, usage immédiat, aucune validation
manuelle. Restriction par domaine automatique quand souhaité (ex. toute adresse
`@episen.fr` entre directement)."* Recherche confirmée dans le code avant de concevoir
cette brique : `AuthService.inscrire()` n'avait aucune notion de domaine — n'importe
quel email non déjà utilisé était accepté.

## 2. Les décisions de conception

### 2.1 — "Instance = tenant", même raisonnement qu'en phase-11 et phase-13

Pas de concept de tenant à construire, ni de table de configuration par client : une
seule clé `memoria.inscription.domaines-autorises` dans `application.properties`,
au même niveau que `memoria.cout.azure.budget-mensuel-euros` (phase-11) et
`memoria.rgpd.retention-jours` (phase-13). Vide par défaut = aucune restriction,
comportement historique inchangé — la même doctrine "sûr par défaut" que
`memoria.cout.azure.mode-strict`, mais inversée : ici le défaut *permissif* est le bon
défaut, la restriction est ce qu'on active explicitement.

### 2.2 — Liste de domaines, pas un domaine unique

Le master prompt donne un seul exemple (`@episen.fr`), mais un établissement a
généralement plusieurs domaines email distincts (profs, étudiants, personnel
administratif). `memoria.inscription.domaines-autorises` accepte donc une liste
séparée par des virgules (`episen.fr,etu.episen.fr`), parsée une seule fois à la
construction du service en `Set<String>` normalisé (trim + minuscules).

### 2.3 — Portée strictement limitée à l'inscription

La restriction ne s'applique qu'à `AuthService.inscrire()`. Volontairement écartés :
- `connecter()` : un compte déjà créé n'est jamais verrouillé rétroactivement par un
  changement de configuration ultérieur (vérifié en conditions réelles, §5).
- Les couloirs : se rejoignent par lien ou QR code (mécanisme déjà en place, phase-5),
  aucun rapport avec qui a le droit de créer un compte.

### 2.4 — Code HTTP 403, pas 400 ni 409

`DomaineEmailNonAutoriseException` est mappée sur `403 FORBIDDEN` dans
`GestionnaireExceptionsApi`, comme `PasMembreDuCouloirException` ou
`AccesTutoratRefuseException` : la requête est bien formée (email valide, mot de passe
correct), c'est un refus d'autorisation, pas une erreur de validation (`400`, déjà pris
par l'email invalide/mot de passe trop court) ni un conflit (`409`, déjà pris par
email-déjà-utilisé).

## 3. Les fichiers, un par un

- `core/auth/AuthService.java` (édité) — `Set<String> domainesAutorises` injecté par
  constructeur via `@Value("${memoria.inscription.domaines-autorises:}")`, même pattern
  que `RetentionService`/`CoutAzureService`. Vérification en tête de `inscrire()`, avant
  le contrôle d'unicité de l'email.
- `core/auth/DomaineEmailNonAutoriseException.java` (nouveau) — même style que
  `EmailDejaUtiliseException`.
- `core/web/GestionnaireExceptionsApi.java` (édité) — mapping `403 FORBIDDEN`.
- `application.properties` (édité) — nouvelle clé `memoria.inscription.domaines-autorises`,
  documentée en commentaire (pas dans `.env.example` : pas un secret, comme
  `retention-jours`/`budget-mensuel-euros`).
- `frontend/src/pages/LoginPage.tsx` (édité) — message d'erreur dédié sur `403` en mode
  inscription, sans révéler la liste exacte des domaines autorisés au client.

## 4. Les tests

231/231 tests backend (226 existants + 5 nouveaux dans `AuthServiceTest` : restriction
désactivée par défaut, domaine autorisé, domaine non autorisé, insensibilité à la
casse). `mvn -B test` : `BUILD SUCCESS`.

## 5. Comment on a vérifié en conditions réelles

Backend démarré localement (hors Docker, `mvn spring-boot:run`) sur le port 8081 contre
le Postgres dockerisé existant (port 5433 exposé) :

- `MEMORIA_INSCRIPTION_DOMAINES_AUTORISES=episen.fr`, `POST /api/v1/auth/inscription`
  avec `test@gmail.com` → `403`.
- Même configuration, `test@episen.fr` → `201`, compte créé.
- Même configuration, `autre@EPISEN.fr` (casse différente) → `201` (insensibilité à la
  casse confirmée).
- Compte `test@episen.fr` déjà créé : `POST /api/v1/auth/connexion` → toujours `200`
  (pas de verrouillage rétroactif — testé en connexion pendant que la restriction était
  encore active, ce qui est le cas le plus défavorable).
- Backend redémarré avec `MEMORIA_INSCRIPTION_DOMAINES_AUTORISES` vide (défaut) :
  `test2@gmail.com` de nouveau accepté (`201`) — non-régression confirmée.
- Comptes de test nettoyés de la base après vérification (`DELETE FROM utilisateurs
  WHERE email IN (...)`).

## 6. Limites connues, assumées, pas corrigées ici

- **Aucune UI d'administration** pour éditer `domaines-autorises` : c'est une variable
  d'environnement fixée au déploiement (cohérent avec le modèle "instance dédiée",
  changement rare — configuré une fois à l'installation chez le client).
- **Pas de distinction par module** (École/Entreprise) : une seule liste pour toute
  l'instance, cohérent avec "instance = tenant" — une instance sert un seul client, donc
  un seul jeu de domaines légitimes, indépendamment du module choisi à l'inscription.
- **Pas de vérification DNS/MX du domaine** : seule la partie après `@` est comparée à
  la liste configurée, aucune validation que le domaine existe réellement ou reçoit du
  courrier — hors sujet ici (l'inscription reste par ailleurs sans vérification email,
  cohérent avec la doctrine "sans friction" du master prompt).

## 7. Pour reprendre seul

- Code de référence exact : `git checkout phase-15-restriction-domaine-email`
- Réglage en production : `MEMORIA_INSCRIPTION_DOMAINES_AUTORISES=domaine1.fr,domaine2.fr`
  dans l'environnement du conteneur backend (voir `docker-compose.yml` /
  `docs/deploiement.md` pour le point d'injection des variables d'environnement).
- Prochaine direction possible : si un rôle admin d'instance apparaît (mentionné dans le
  master prompt mais non implémenté), en faire un réglage éditable depuis l'UI plutôt
  qu'une variable d'environnement figée au déploiement.
