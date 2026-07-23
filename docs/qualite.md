# Qualité logicielle : gates avant toute PR

Le master prompt, section "Qualité logicielle (gates avant toute Pull Request)" : *"À partir
de la Phase 2, aucune PR n'est fusionnée sans : tests unitaires (obligatoires) et tests
d'intégration ; analyse statique (SonarQube ou équivalent) sans nouvelle dette critique ;
vérification OWASP sur les points sensibles ; scan des dépendances (vulnérabilités
connues) ; couverture de tests minimale définie et respectée."*

Ce lot ajoute ces gates à la CI (`.github/workflows/ci.yml`), sans dépendre d'un compte
externe (SonarCloud, etc. — voir §5).

## 1. Ce que chaque gate vérifie

| Outil | Ce qu'il vérifie | Où | Seuil |
|---|---|---|---|
| JaCoCo | Couverture de lignes du backend | `mvn verify` (job `backend`) | ≥ 50% (mesuré : 52,6% au moment de l'écriture) |
| SpotBugs + FindSecBugs | Analyse statique, dont règles orientées OWASP (injection, crypto faible, secrets en dur...) | `mvn verify` (job `backend`) | Bloque uniquement les findings `High` |
| OWASP Dependency-Check | CVE connus dans les dépendances Java (via la base NVD) | `mvn verify` (job `backend`) | Bloque si CVSS ≥ 8 (High/Critical) |
| `npm audit` | CVE connus dans les dépendances frontend | job `frontend` | Bloque si sévérité ≥ High |
| `pip-audit` | CVE connus dans `speaker-service/requirements.txt` | job `speaker-service` (nouveau) | Bloque si une vulnérabilité est trouvée |

## 2. Lire un rapport en local

```
cd backend && mvn -B verify
```

- Couverture : `backend/target/site/jacoco/index.html` (détail par package/classe).
- SpotBugs/FindSecBugs : la console Maven liste chaque finding avec classe + ligne + règle ;
  `mvn spotbugs:gui` ouvre l'interface graphique pour explorer un rapport existant.
- Dependency-Check : `backend/target/dependency-check-report.html` (rapport HTML complet,
  CVE par dépendance avec score CVSS et description).

## 3. Ajouter une suppression justifiée

**Jamais de suppression silencieuse.** Chaque suppression doit porter une justification
écrite, relue comme n'importe quel changement de code.

- **SpotBugs/FindSecBugs** : annotation `@SuppressFBWarnings(value = "...", justification =
  "...")` (dépendance `com.github.spotbugs:spotbugs-annotations`, scope `provided`),
  **posée au niveau classe si le code flagué vit dans un lambda** — SpotBugs compile un
  lambda en méthode synthétique séparée, qu'une annotation posée sur la méthode
  englobante ne couvre pas (piège rencontré sur `SecurityConfig.filterChain`, cf.
  `docs/phases/phase-8-gates-qualite.md`).
- **Dependency-Check** : créer `backend/dependency-check-suppressions.xml` (une entrée par
  faux-positif confirmé, avec justification) et le référencer via
  `<suppressionFiles>` dans la config du plugin — seulement si un vrai run le justifie,
  jamais créé à vide par précaution.

## 4. Secret optionnel : `NVD_API_KEY`

OWASP Dependency-Check interroge la base NVD (NIST) pour ses CVE. Sans clé API, ça
fonctionne mais c'est **très lent** en premier run (confirmé manuellement : plusieurs
heures pour une synchronisation complète, contre quelques minutes avec une clé) — voir
`docs/phases/phase-8-gates-qualite.md` pour le détail de cette vérification.

Recommandé (pas obligatoire, dégradation gracieuse) : demander une clé gratuite sur
[nvd.nist.gov/developers/request-an-api-key](https://nvd.nist.gov/developers/request-an-api-key),
puis l'ajouter comme secret GitHub (Settings → Secrets and variables → Actions →
`NVD_API_KEY`). Sans elle, la CI reste fonctionnelle mais le tout premier run (par jour,
avant que le cache `actions/cache` prenne le relais) peut être long.

## 5. Pourquoi pas un vrai SonarQube/SonarCloud

Le master prompt autorise explicitement *"SonarQube ou équivalent"*. SonarCloud
nécessiterait un compte externe + un token secret que je ne peux pas créer moi-même —
même raisonnement que `terraform apply`, jamais lancé faute d'identifiants Azure réels
(voir `docs/deploiement.md`). La combinaison JaCoCo + SpotBugs/FindSecBugs +
Dependency-Check est 100% auto-hébergée dans la CI GitHub Actions, sans compte externe,
et couvre les quatre exigences non-test du master prompt (couverture, analyse statique,
vérification OWASP, scan de dépendances). Rien n'empêche d'ajouter un vrai SonarCloud plus
tard — ce sont des outils complémentaires, pas mutuellement exclusifs.

## 6. Limites connues

- **Pas de vrai SonarQube/SonarCloud** — voir §5.
- **speaker-service n'a ni lint (ruff/mypy) ni tests ni couverture** — seul le scan de
  dépendances (`pip-audit`) est en place. Ajouter un vrai lint/test suite est un chantier
  séparé, plus lourd (le service n'a aujourd'hui aucun test).
- **`torch`/`torchaudio` non couverts par `pip-audit`** — installés via
  `--extra-index-url` avec un suffixe `+cpu`, non résolvables sur PyPI ; `pip-audit` les
  ignore avec un avertissement visible plutôt qu'un échec silencieux, mais ça reste un
  angle mort réel, pas une fausse sécurité cachée.
- **SpotBugs/FindSecBugs n'analyse que les classes `main`**, jamais les classes de test
  (comportement par défaut, volontairement gardé).
- **Le seuil de couverture (50%) documente la baseline actuelle**, pas un objectif visé —
  il empêche une régression, il ne pousse pas activement à écrire plus de tests. À faire
  monter progressivement à mesure que la couverture progresse naturellement.
