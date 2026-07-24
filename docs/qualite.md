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
| GitHub Dependency Review | CVE connus dans les dépendances (Maven/npm/pip), sur les PR | job `dependency-review` (PR uniquement) | Bloque si sévérité ≥ High |
| `npm audit` | CVE connus dans les dépendances frontend | job `frontend` | Bloque si sévérité ≥ High |
| `pip-audit` | CVE connus dans `speaker-service/requirements.txt` | job `speaker-service` | Bloque si une vulnérabilité est trouvée |

## 2. Lire un rapport en local

```
cd backend && mvn -B verify
```

- Couverture : `backend/target/site/jacoco/index.html` (détail par package/classe).
- SpotBugs/FindSecBugs : la console Maven liste chaque finding avec classe + ligne + règle ;
  `mvn spotbugs:gui` ouvre l'interface graphique pour explorer un rapport existant.
- Dependency Review : uniquement visible sur une PR GitHub (onglet "Checks" de la PR) —
  pas d'équivalent local, c'est une action GitHub native basée sur son graphe de
  dépendances hébergé.

## 3. Ajouter une suppression justifiée

**Jamais de suppression silencieuse.** Chaque suppression doit porter une justification
écrite, relue comme n'importe quel changement de code.

**SpotBugs/FindSecBugs** : annotation `@SuppressFBWarnings(value = "...", justification =
"...")` (dépendance `com.github.spotbugs:spotbugs-annotations`, scope `provided`),
**posée au niveau classe si le code flagué vit dans un lambda** — SpotBugs compile un
lambda en méthode synthétique séparée, qu'une annotation posée sur la méthode englobante
ne couvre pas (piège rencontré sur `SecurityConfig.filterChain`, cf.
`docs/phases/phase-8-gates-qualite.md`).

## 4. Pourquoi pas un plugin Maven pour le scan de dépendances Java

Deux outils ont été essayés et abandonnés en conditions réelles — voir
`docs/phases/phase-8-gates-qualite.md` §5 pour le détail complet :

1. **OWASP Dependency-Check** (base NVD locale) : synchroniser les 370 000+
   enregistrements NVD depuis zéro dépasse largement ce qui est raisonnable sur un
   runner GitHub Actions éphémère (aucun état persistant entre runs) — confirmé à
   plusieurs reprises, en local ET en CI, **même avec une clé `NVD_API_KEY`**.
2. **Sonatype OSS Index** (`ossindex-maven-plugin`) : l'API anonyme renvoie désormais
   `401 Unauthorized` (compte requis depuis un changement de politique Sonatype) — et
   pire, le plugin avale cette erreur en simple `WARNING` et rapporte `BUILD SUCCESS`
   sans avoir vérifié la moindre dépendance. Un faux sentiment de sécurité, pas
   juste un outil plus lent : rejeté, pas seulement "pas encore configuré".

**Solution retenue** : **GitHub Dependency Review** (`actions/dependency-review-action`),
natif à GitHub, zéro compte externe, se déclenche sur les PR (diffe le graphe de
dépendances base vs head) et bloque si une dépendance introduite a une vulnérabilité
connue. Couvre Maven, npm et pip en une seule action, via le graphe de dépendances déjà
maintenu par GitHub pour ce repo. Correspond très précisément à la formulation du master
prompt : *"aucune PR n'est fusionnée sans... scan des dépendances"* — un gate au moment de
la PR, pas nécessairement à chaque push.

## 5. Pourquoi pas un vrai SonarQube/SonarCloud

Le master prompt autorise explicitement *"SonarQube ou équivalent"*. SonarCloud
nécessiterait un compte externe + un token secret que je ne peux pas créer moi-même —
même raisonnement que `terraform apply`, jamais lancé faute d'identifiants Azure réels
(voir `docs/deploiement.md`). La combinaison JaCoCo + SpotBugs/FindSecBugs + GitHub
Dependency Review est 100% auto-hébergée (Maven + GitHub natif), sans compte externe, et
couvre les quatre exigences non-test du master prompt (couverture, analyse statique,
vérification OWASP, scan de dépendances). Rien n'empêche d'ajouter un vrai SonarCloud plus
tard — ce sont des outils complémentaires, pas mutuellement exclusifs.

## 6. Limites connues

- **Pas de vrai SonarQube/SonarCloud** — voir §5.
- **Le scan de dépendances Java ne se déclenche que sur les PR**, pas sur un simple push
  sur une branche — limite acceptée de `dependency-review-action` (a besoin d'une base de
  comparaison). `npm audit`/`pip-audit`, eux, tournent à chaque push.
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
