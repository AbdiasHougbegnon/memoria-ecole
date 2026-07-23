# Gates de qualité (JaCoCo + SpotBugs/FindSecBugs + OWASP Dependency-Check) — comment on l'a construite

**Pour revenir exactement à cet état du code :**
```
git checkout phase-8-gates-qualite
```

---

## 1. Le besoin

Le master prompt, section "Qualité logicielle (gates avant toute Pull Request)" : *"À partir
de la Phase 2, aucune PR n'est fusionnée sans : tests unitaires (obligatoires) et tests
d'intégration ; analyse statique (SonarQube ou équivalent) sans nouvelle dette critique ;
vérification OWASP sur les points sensibles ; scan des dépendances (vulnérabilités connues) ;
couverture de tests minimale définie et respectée."* Le projet est bien au-delà de la Phase 2.
La fiche technique de la brique observabilité (`docs/phases/phase-7-observabilite.md`) citait
explicitement ce chantier comme l'une des deux directions possibles suivantes.

État avant cette brique : `.github/workflows/ci.yml` avait 3 jobs (`backend` : `mvn -B test`
seul, `frontend` : lint + build, `docker` : garde-fou de build). Aucun plugin de
couverture/analyse statique/scan de dépendances dans `backend/pom.xml`. `speaker-service`
n'était dans aucun job CI. 164 tests backend, tous verts.

## 2. Les décisions de conception

### 2.1 — Équivalent auto-hébergé plutôt que SonarQube/SonarCloud

Le master prompt autorise explicitement *"SonarQube ou équivalent"*. SonarCloud nécessite un
compte externe + un token secret que je ne peux pas créer moi-même — même raisonnement que
`terraform apply`, jamais lancé faute d'identifiants Azure réels (`docs/deploiement.md`).
Choix : JaCoCo (couverture) + SpotBugs/FindSecBugs (analyse statique + règles orientées
OWASP) + OWASP Dependency-Check (CVE des dépendances), 100% auto-hébergé dans la CI GitHub
Actions, vérifiable sans compte externe. Rien n'empêche d'ajouter un vrai SonarCloud plus
tard — outils complémentaires, pas mutuellement exclusifs.

### 2.2 — Rollout en une passe, seuils fixés sur des valeurs réellement mesurées

Plutôt que d'atterrir en mode rapport puis activer l'échec plus tard, chaque outil a été
exécuté réellement pendant cette session, et chaque seuil vient d'une mesure réelle, pas
d'une valeur inventée à l'avance :
- Couverture de lignes mesurée : **52,6%** (1268/2412 lignes). Seuil JaCoCo fixé à **50%**
  (marge de sécurité modeste, documente la baseline honnêtement).
- SpotBugs/FindSecBugs : seuil `High` uniquement (évite le bruit des findings mineurs).

### 2.3 — Un seul vrai finding statique, suppimé avec justification écrite, pas ignoré

Le premier run réel a remonté **un seul** finding `High` :
`SPRING_CSRF_PROTECTION_DISABLED` sur `SecurityConfig.java` (le `.csrf(csrf ->
csrf.disable())` déjà présent depuis la brique sécurité/auth). Analyse : l'API est
`SessionCreationPolicy.STATELESS` avec authentification JWT Bearer, sans cookie de
session — CSRF exploite l'envoi automatique de cookies par le navigateur, non applicable
ici. C'est la pratique standard Spring Security pour les APIs non-navigateur, pas un
oubli. Suppimé via `@SuppressFBWarnings` avec justification écrite dans le code (voir §3 et
§5.1 pour le piège d'implémentation rencontré).

### 2.4 — dataDirectory hors `~/.m2`, clé NVD via variable d'environnement

`dependency-check-maven` pointe son `dataDirectory` sur `.owasp-dc-data` à la racine du
repo plutôt que dans `~/.m2` (déjà géré par le cache Maven de `actions/setup-java`, pour
éviter tout conflit de cache). La clé NVD (optionnelle) est lue via
`nvdApiKeyEnvironmentVariable`, jamais en configuration XML directe (bug connu du plugin,
et évite toute fuite en logs verbeux).

## 3. Les fichiers, un par un

### `backend/pom.xml`
Trois plugins ajoutés à `<build><plugins>` : `jacoco-maven-plugin` 0.8.15,
`spotbugs-maven-plugin` 4.10.3.0 + `findsecbugs-plugin` 1.14.0 (`threshold=High`,
`effort=Max`), `dependency-check-maven` 12.2.2 (`failBuildOnCVSS=8`). Versions confirmées
via Maven Central, pas devinées — `spotbugs-maven-plugin` doit être ≥ 4.8.0 (les versions
≤ 4.7.3.x plantent sur du bytecode Java 21, issue spotbugs/spotbugs#2567). Dépendance
`spotbugs-annotations` (scope `provided`) ajoutée pour `@SuppressFBWarnings`.

### `backend/src/main/java/com/memoria/core/auth/SecurityConfig.java`
`@SuppressFBWarnings` posée **au niveau classe**, pas sur la méthode `filterChain` — voir
§5.1, c'est un piège réel rencontré pendant la vérification, pas un choix arbitraire.

### `.github/workflows/ci.yml`
Job `backend` : `mvn -B test` devient `mvn -B verify`, avec un cache OWASP daté
(`owasp-dc-data-YYYY-MM-DD`, `restore-keys` en préfixe) et `timeout-minutes: 25` (garde-fou
découvert nécessaire, voir §5.2). Job `frontend` : ajoute `npm audit --audit-level=high`.
Nouveau job `speaker-service` : `pip-audit -r requirements.txt --no-deps`.

### `docs/qualite.md` (nouveau)
Guide opérationnel miroir de `docs/deploiement.md` : ce que chaque gate vérifie, comment
lire les rapports, comment ajouter une suppression justifiée, secret `NVD_API_KEY`
optionnel, limites connues.

## 4. Les tests

Aucun changement de code métier testable unitairement (config Spring Boot + plugins
Maven). `cd backend && mvn -B verify` — **164/164 tests** passent, inchangé.

## 5. Comment on a vérifié en conditions réelles

### 5.1 — `@SuppressFBWarnings` sur une méthode ne couvre pas un lambda

Première tentative : annotation posée sur `filterChain` (la méthode `@Bean`). Le build
échouait encore avec exactement le même finding. Cause : `.csrf(csrf -> csrf.disable())`
vit dans un lambda, compilé par `javac` en méthode synthétique séparée
(`lambda$filterChain$0`) — une annotation posée sur la méthode englobante ne s'applique
pas au bytecode de cette méthode générée, que SpotBugs analyse indépendamment. **Corrigé**
en déplaçant l'annotation au niveau de la classe `SecurityConfig`, qui couvre toutes ses
méthodes (générées ou non). Vérifié : `mvn verify` repasse avec `BugInstance size is 0`.

### 5.2 — OWASP Dependency-Check sans clé NVD : confirmé impraticable en une session

Le premier run réel (sans `NVD_API_KEY`) a atteint **46% des 369 587 enregistrements NVD
après ~80 minutes**, avant d'être interrompu. Extrapolé, une synchronisation complète
prendrait plusieurs heures — pas juste "un peu plus lent", un vrai obstacle pratique.
L'interruption a laissé un verrou H2 corrompu (`odc.update.lock`), qui a fait échouer la
tentative suivante avec `UpdateException: Unable to obtain an exclusive lock` après
58 minutes. Après nettoyage complet du dossier `.owasp-dc-data` et un nouveau run laissé
tourner, celui-ci s'est bloqué net dès l'étape `dependency-check:check`, sans reprendre le
téléchargement — très probablement un verrou Windows résiduel sur `odc.update.lock`
(fichier de 32 octets, "Device or resource busy" persistant même après confirmation que
plus aucun processus Maven ne le référençait). Le process a été arrêté ; **aucun run local
n'a pu être mené à complétion dans cette session.**

**Ce qui est réellement vérifié malgré cela** : le plugin est correctement configuré et
s'exécute sans erreur de configuration — il compile, se connecte à l'API NVD, s'authentifie,
télécharge des données réelles (confirmé par la croissance mesurée de `odc.mv.db`, jusqu'à
plus de 600 Mo). Ce qui n'a **pas** pu être vérifié localement : le résultat final du scan
(présence ou non de CVE ≥ CVSS 8 dans les dépendances actuelles). C'est une limite
opérationnelle confirmée par l'expérience directe, traitée comme `terraform apply` ailleurs
dans ce projet — jamais exécutée à complétion faute de moyen pratique, mais le code/la
config sont prêts et corrects.

**Conséquence directe sur la conception** : `timeout-minutes: 25` a été ajouté au job
`backend` de la CI comme garde-fou (mieux vaut un échec visible et rapide qu'un job qui
consomme des heures de minutes CI en silence — à noter : ce délai est probablement encore
trop court pour un tout premier run sans clé, à augmenter ou à accepter comme échec
attendu du tout premier push tant qu'aucune clé n'est fournie). `docs/qualite.md`
reclassifie `NVD_API_KEY` de "optionnel, un peu plus lent sans" à "recommandé fortement
avant de compter sur cette gate en pratique" — la dégradation gracieuse existe bien (le
plugin ne plante pas au démarrage faute de clé), mais elle n'est pas gratuite comme pour
les clés Azure ailleurs dans le projet.

### Séquence de vérification réelle

- `mvn -B verify` (backend) — 164/164 tests, couverture 52,6% ≥ seuil 50%, SpotBugs/
  FindSecBugs 0 finding après correctif §5.1.
- `npm audit --audit-level=high` (frontend) — 0 vulnérabilité.
- `pip-audit -r requirements.txt --no-deps` (speaker-service) — 0 vulnérabilité connue ;
  `torch`/`torchaudio` skippés avec avertissement visible (suffixe `+cpu`, non résolvables
  sur PyPI) — limite documentée, pas un échec silencieux.
- OWASP Dependency-Check — voir §5.2.

## 6. Limites connues, assumées, pas corrigées ici

- **Pas de vrai SonarQube/SonarCloud** — voir §2.1.
- **OWASP Dependency-Check impraticable sans `NVD_API_KEY`** — confirmé, pas théorique
  (voir §5.2). À traiter en obtenant une clé gratuite avant de compter réellement sur
  cette gate en CI.
- **speaker-service sans lint (ruff/mypy) ni tests ni couverture** — seul le scan de
  dépendances est en place ; ajouter un vrai lint/test suite est un chantier séparé, plus
  lourd (le service n'a aujourd'hui aucun test).
- **`torch`/`torchaudio` non couverts par `pip-audit`** — angle mort documenté, pas caché.
- **Le seuil de couverture (50%) documente la baseline actuelle**, ce n'est pas un objectif
  visé — il empêche une régression, il ne pousse pas activement à écrire plus de tests.

## 7. Pour reprendre seul

- Code de référence exact : `git checkout phase-8-gates-qualite`
- Obtenir une clé NVD gratuite : nvd.nist.gov/developers/request-an-api-key, puis
  l'ajouter comme secret GitHub `NVD_API_KEY` avant de compter sur la gate
  Dependency-Check en CI.
- Lire un rapport en local : voir `docs/qualite.md` §2.
- Prochaine direction possible : tuteur vocal École, ou faire monter progressivement le
  seuil de couverture à mesure que les tests s'accumulent naturellement.
