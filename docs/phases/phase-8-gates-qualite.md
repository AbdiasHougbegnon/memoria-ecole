# Gates de qualité (JaCoCo + SpotBugs/FindSecBugs + GitHub Dependency Review) — comment on l'a construite

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
Choix final : JaCoCo (couverture) + SpotBugs/FindSecBugs (analyse statique + règles
orientées OWASP) + GitHub Dependency Review (CVE des dépendances) — voir §2.4 pour le
cheminement réel jusqu'à ce dernier choix, qui n'était pas le premier essayé.

### 2.2 — Rollout en une passe, seuils fixés sur des valeurs réellement mesurées

Chaque outil a été exécuté réellement pendant cette session, et chaque seuil vient d'une
mesure réelle, pas d'une valeur inventée à l'avance :
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

### 2.4 — Deux outils de scan de dépendances essayés et abandonnés avant GitHub Dependency Review

Le scan de dépendances Java a nécessité **trois tentatives réelles** avant d'aboutir — voir
§5.2 et §5.3 pour le détail complet de chaque échec :

1. **OWASP Dependency-Check** (base NVD locale) : impraticable sur un runner GitHub Actions
   éphémère — synchroniser 370 000+ enregistrements NVD depuis zéro prend plusieurs heures,
   confirmé en local ET en CI, **même avec une clé `NVD_API_KEY`**.
2. **Sonatype OSS Index** (`ossindex-maven-plugin`) : l'API anonyme renvoie `401
   Unauthorized` (compte requis depuis un changement de politique Sonatype récent) — et le
   plugin avale cette erreur en simple `WARNING`, rapportant `BUILD SUCCESS` sans avoir
   vérifié la moindre dépendance. Rejeté pour un vrai défaut de conception (faux sentiment
   de sécurité), pas seulement pour la lenteur.
3. **GitHub Dependency Review** (`actions/dependency-review-action`), retenu : natif à
   GitHub, zéro compte externe, utilise le graphe de dépendances déjà hébergé par GitHub
   pour ce repo. Se déclenche sur les PR (diffe le graphe base vs head), pas sur chaque
   push simple — mais c'est exactement la formulation du master prompt : *"aucune PR n'est
   fusionnée sans scan des dépendances"*, un gate au moment de la PR.

## 3. Les fichiers, un par un

### `backend/pom.xml`
Deux plugins actifs dans `<build><plugins>` : `jacoco-maven-plugin` 0.8.15,
`spotbugs-maven-plugin` 4.10.3.0 + `findsecbugs-plugin` 1.14.0 (`threshold=High`,
`effort=Max`). Versions confirmées via Maven Central, pas devinées —
`spotbugs-maven-plugin` doit être ≥ 4.8.0 (les versions ≤ 4.7.3.x plantent sur du bytecode
Java 21, issue spotbugs/spotbugs#2567). Dépendance `spotbugs-annotations` (scope
`provided`) ajoutée pour `@SuppressFBWarnings`. Un commentaire détaillé explique pourquoi
aucun plugin Maven ne fait le scan de dépendances (voir §2.4).

### `backend/src/main/java/com/memoria/core/auth/SecurityConfig.java`
`@SuppressFBWarnings` posée **au niveau classe**, pas sur la méthode `filterChain` — voir
§5.1, c'est un piège réel rencontré pendant la vérification, pas un choix arbitraire.

### `.github/workflows/ci.yml`
Job `backend` : `mvn -B test` devient `mvn -B verify` (couverture + analyse statique),
`timeout-minutes: 15` en garde-fou générique. Job `frontend` : ajoute
`npm audit --audit-level=high`. Nouveau job `speaker-service` : `pip-audit -r
requirements.txt --no-deps`. Nouveau job `dependency-review` : `actions/dependency-review-
action@v4`, `if: github.event_name == 'pull_request'` (a besoin d'une base de comparaison),
`fail-on-severity: high`.

### `docs/qualite.md` (nouveau)
Guide opérationnel miroir de `docs/deploiement.md` : ce que chaque gate vérifie, comment
lire les rapports, comment ajouter une suppression justifiée, pourquoi pas de plugin Maven
pour le scan de dépendances, limites connues.

## 4. Les tests

Aucun changement de code métier testable unitairement (config Spring Boot + plugins
Maven + workflow CI). `cd backend && mvn -B verify` — **164/164 tests** passent, inchangé.

## 5. Comment on a vérifié en conditions réelles

### 5.1 — `@SuppressFBWarnings` sur une méthode ne couvre pas un lambda

Première tentative : annotation posée sur `filterChain` (la méthode `@Bean`). Le build
échouait encore avec exactement le même finding. Cause : `.csrf(csrf -> csrf.disable())`
vit dans un lambda, compilé par `javac` en méthode synthétique séparée
(`lambda$filterChain$0`) — une annotation posée sur la méthode englobante ne s'applique
pas au bytecode de cette méthode générée, que SpotBugs analyse indépendamment. **Corrigé**
en déplaçant l'annotation au niveau de la classe `SecurityConfig`, qui couvre toutes ses
méthodes (générées ou non). Vérifié : `mvn verify` repasse avec `BugInstance size is 0`.

### 5.2 — OWASP Dependency-Check : impraticable même avec une clé NVD, confirmé en CI

Le premier run réel (sans `NVD_API_KEY`) a atteint **46% des 369 587 enregistrements NVD
après ~80 minutes**, avant d'être interrompu. L'interruption a laissé un verrou H2 corrompu
(`odc.update.lock`), qui a fait échouer la tentative suivante après 58 minutes. Un
troisième run, après nettoyage complet, s'est bloqué net dès le début — verrou Windows
résiduel persistant même sans processus Maven actif.

Après avoir obtenu une clé `NVD_API_KEY` et l'avoir ajoutée en secret GitHub, le run **en
CI réelle** a de nouveau échoué : `Error: The operation was canceled.` après avoir
téléchargé seulement 40 000/369 802 enregistrements (11%) — le job avait dépassé le
`timeout-minutes: 25` fixé initialement. Même avec authentification (rate limit NVD 10x
plus élevé), le volume de données et les conditions réseau du runner GitHub Actions ne
permettent pas de compléter ce premier sync dans un délai raisonnable. Ce n'est **pas** un
problème de configuration — la connexion et l'authentification fonctionnaient — c'est un
problème d'échelle structurel : un runner CI éphémère (aucun disque persistant entre runs)
doit retélécharger l'intégralité de la base à chaque fois que le cache est manquant, et ce
téléchargement complet ne rentre dans aucun budget de temps CI raisonnable.

**Décision** : abandonné pour la CI. Le plugin reste documentable comme option de scan
approfondi *local*, à la discrétion du développeur qui a le temps de laisser tourner un
premier sync (plusieurs heures sans clé, possiblement encore trop long même avec).

### 5.3 — Sonatype OSS Index : échoue silencieusement, rejeté pour un défaut de conception

Tentative de remplacement rapide : `ossindex-maven-plugin` (Sonatype OSS Index), qui
vérifie chaque dépendance individuellement via une API légère plutôt qu'un miroir complet.
Testé en local : `mvn verify` termine en 39 secondes avec `BUILD SUCCESS` — mais le log
contient `[WARNING] Failed to fetch component-reports` suivi d'une
`TransportException: Unexpected response; status: HTTP/1.1 401 Unauthorized`. L'API
anonyme de Sonatype OSS Index n'est plus accessible sans compte (changement de politique).
Le vrai problème n'est pas l'authentification manquante en soi, mais que **le plugin
avale cette erreur en simple avertissement et continue comme si tout allait bien** — un
`BUILD SUCCESS` qui ne veut rien dire, puisqu'aucune dépendance n'a réellement été vérifiée.
Un tel gate est pire qu'une absence de gate : il donne une fausse impression de sécurité.
**Rejeté**, remplacé par GitHub Dependency Review (§2.4, point 3).

### Séquence de vérification réelle

- `mvn -B verify` (backend, sans plugin de scan de dépendances) — 164/164 tests, couverture
  52,6% ≥ seuil 50%, SpotBugs/FindSecBugs 0 finding après correctif §5.1, **31,8 secondes**.
- `npm audit --audit-level=high` (frontend) — 0 vulnérabilité.
- `pip-audit -r requirements.txt --no-deps` (speaker-service) — 0 vulnérabilité connue ;
  `torch`/`torchaudio` skippés avec avertissement visible (suffixe `+cpu`, non résolvables
  sur PyPI) — limite documentée, pas un échec silencieux.
- OWASP Dependency-Check et Sonatype OSS Index — voir §5.2 et §5.3 (tous deux abandonnés).
- GitHub Dependency Review — se vérifie uniquement sur une vraie PR GitHub (pas testable en
  push simple ni en local), pas encore observé sur ce repo au moment de l'écriture.

## 6. Limites connues, assumées, pas corrigées ici

- **Pas de vrai SonarQube/SonarCloud** — voir §2.1.
- **Le scan de dépendances Java (GitHub Dependency Review) ne se déclenche que sur les
  PR**, pas sur un push simple — limite structurelle de l'action (diff base vs head).
- **speaker-service sans lint (ruff/mypy) ni tests ni couverture** — seul le scan de
  dépendances est en place ; ajouter un vrai lint/test suite est un chantier séparé, plus
  lourd (le service n'a aujourd'hui aucun test).
- **`torch`/`torchaudio` non couverts par `pip-audit`** — angle mort documenté, pas caché.
- **Le seuil de couverture (50%) documente la baseline actuelle**, ce n'est pas un objectif
  visé — il empêche une régression, il ne pousse pas activement à écrire plus de tests.

## 7. Pour reprendre seul

- Code de référence exact : `git checkout phase-8-gates-qualite`
- Le scan de dépendances Java se vérifie sur l'onglet "Checks" d'une pull request GitHub,
  pas en local ni sur un push simple.
- Lire un rapport JaCoCo/SpotBugs en local : voir `docs/qualite.md` §2.
- Prochaine direction possible : tuteur vocal École, ou faire monter progressivement le
  seuil de couverture à mesure que les tests s'accumulent naturellement.
