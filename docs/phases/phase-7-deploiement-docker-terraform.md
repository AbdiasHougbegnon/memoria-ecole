# Déploiement mono-instance industrialisé (Docker + Terraform) — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout phase-7-deploiement-docker-terraform
```
Ce tag pointe sur le commit `fdf2d3b`, vérifié end-to-end avec de vrais conteneurs
(inscription, session, upload de document persistant après redémarrage) et un vrai
`terraform validate`.

---

## 1. Le besoin

Le master prompt pose ceci comme non-négociable, pas une amélioration facultative : *"une
instance dédiée par client... déploiement mono-instance industrialisé (rapide, automatisé
via Docker + Terraform), pas le cloisonnement interne"* (section "Accès utilisateurs et
déploiement"), et : *"Docker + Terraform + CI/CD ne sont pas décoratifs : ils rendent le
modèle économique (vente en instance dédiée par client) possible et l'entreprise
scalable"* (section "Contraintes de conception"). Sans ça, le modèle "une instance par
client" n'est pas industrialisable — chaque déploiement resterait un bricolage manuel.

État avant cette brique : `speaker-service/Dockerfile` existait déjà seul ;
`docker-compose.yml` racine ne contenait que Postgres ; aucun Dockerfile backend/frontend ;
aucun Terraform nulle part ; la CI ne faisait que tester, jamais construire d'image.

## 2. Les décisions de conception

### 2.1 — Une VM Azure unique + Docker Compose, pas AKS

Le master prompt nomme AKS explicitement dans sa liste d'infra, mais AKS correspond à la
Phase 4 du roadmap ("Plateforme/passage à l'échelle : milliers d'utilisateurs
simultanés") — prématuré pour un premier déploiement sans client réel branché dessus.
Les mêmes conteneurs (mêmes Dockerfiles, même `docker-compose.yml` comme base)
tourneraient tels quels sur AKS le jour où cette phase l'exige, sans réécriture.

### 2.2 — Terraform construit et validé, jamais appliqué dans cette session

Aucun identifiant Azure de production n'était disponible. `terraform apply` crée de
vraies ressources facturées — ça exige une confirmation explicite et les identifiants de
l'utilisateur. Le plan s'est arrêté à `init`/`validate`/`plan`.

### 2.3 — Aucun secret applicatif ne transite par Terraform

Le state Terraform stocke tout en clair. Les secrets (JWT, clés Azure AI) vivent
uniquement dans un `.env` copié séparément sur la VM (scp manuel pour l'instant), jamais
dans une variable Terraform ni dans le cloud-init — la VM provisionnée n'a que Docker et
le plugin Compose installés, rien d'applicatif.

### 2.4 — speaker-service jamais exposé au-delà du réseau Docker interne

Le README du service documente explicitement l'absence d'authentification entre lui et
le backend Java. Mitigation immédiate sans construire l'authentification manquante :
aucun `ports:` publié vers l'hôte dans `docker-compose.yml`, seul le backend peut
l'atteindre via le réseau Docker.

### 2.5 — Ajout de `spring-boot-starter-actuator` pour un healthcheck réel

Le projet n'avait aucun endpoint de santé. Sans ça, `depends_on: condition:
service_healthy` (utilisé par `backend` sur `postgres`) n'a pas de sens, et Docker ne
peut pas savoir si le conteneur backend est réellement prêt. Dépendance standard,
non-opinionée — sert aussi l'exigence d'observabilité du master prompt ("Prometheus +
Grafana + OpenTelemetry... dès la conception"), un premier pas minimal.

### 2.6 — Build multi-stage Maven-in-Docker plutôt que jar pré-construit

Garde `docker build` autonome et reproductible sur n'importe quelle machine (y compris la
VM elle-même si besoin), cohérent avec "rapide, automatisé, reproductible" — pas de
dépendance à un artefact CI externe.

## 3. Les fichiers, un par un

### `backend/Dockerfile` (nouveau)
Multi-stage : `maven:3.9-eclipse-temurin-21` (build) → `eclipse-temurin:21-jre-alpine`
(runtime). `HEALTHCHECK` via `curl -f http://localhost:8080/actuator/health`.

### `backend/src/main/java/com/memoria/core/auth/SecurityConfig.java`
Ajoute `.requestMatchers("/actuator/health").permitAll()` — sans ça, le mur de sécurité
existant (`anyRequest().authenticated()`) aurait bloqué la sonde de santé Docker avec un
401, rendant le `HEALTHCHECK` inutilisable.

### `frontend/Dockerfile` + `frontend/nginx.conf` (nouveaux)
Multi-stage : `node:22-alpine` (build) → `nginx:alpine` (sert `dist/`). `nginx.conf` fait
suivre `/api/*` vers `http://backend:8080/api/` (nom du service Compose, résolu par son
DNS interne) et retombe sur `index.html` pour les routes react-router côté client
(`try_files ... /index.html`).

### `docker-compose.yml` (étendu)
Postgres inchangé (port 5433, credentials `memoria`/`memoria`). Ajoute `backend`
(dépend de `postgres` en état `service_healthy`), `speaker-service` (sans port publié,
voir §2.4), `frontend` (seul service publié, port 80). Volumes persistants :
`memoria_audio_data`, `memoria_documents_data`, `memoria_speaker_profiles`.

### `backend/src/main/resources/application.properties`
Le datasource devient env-configurable :
```properties
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5433/memoria}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:memoria}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:memoria}
```
Noms de variables Spring Boot standard (pas un nom custom `MEMORIA_DB_*`) : cohérent
avec le style `${VAR:default}` déjà utilisé pour chaque autre propriété du fichier,
tout en restant les noms que Spring Boot reconnaît nativement.

### `infra/terraform/` (nouveau)
`versions.tf` (provider `azurerm ~> 3.0`), `main.tf` (resource group, vnet/subnet, NSG
limitant 80/443 à tous et 22 à `var.ssh_allowed_cidr` uniquement, IP publique, VM Linux
`Standard_B2s` avec cloud-init installant Docker), `variables.tf` (`client_name`,
`domain_name` par défaut `memoria.episen.fr` — l'exemple cité tel quel dans le master
prompt), `outputs.tf`, `terraform.tfvars.example`.

### `.github/workflows/ci.yml`
Nouveau job `docker` : construit les deux images (`docker build`), ne push rien, ne
déploie rien — garde-fou pour détecter si un Dockerfile casse.

### `docs/deploiement.md` (nouveau)
Guide opérationnel : lancer la stack (`docker compose up --build`), appliquer Terraform,
limites explicites.

## 4. Les tests

Aucun test dédié ajouté (rien de testable unitairement dans des Dockerfiles/Terraform).
`cd backend && mvn test` — **164/164 tests** passent, inchangé (l'ajout d'actuator et la
règle `SecurityConfig` n'affectent aucun test existant).

## 5. Comment on a vérifié en conditions réelles

Deux bugs préexistants ont bloqué la première tentative de `docker compose build` — corrigés
en route, hors du périmètre initial de cette brique mais nécessaires pour que la
vérification passe réellement :

1. **`speaker-service/requirements.txt`** épinglait `numpy==2.5.1`, qui exige Python
   ≥3.12, incompatible avec `python:3.11-slim` (le Dockerfile existant, non modifié par
   ce lot). Corrigé en `numpy==2.2.6`.
2. **`frontend/src/api.ts`** utilisait une propriété de constructeur raccourcie
   (`constructor(public status: number, ...)`), interdite par `erasableSyntaxOnly: true`
   dans `tsconfig.app.json`. Ce bug faisait déjà échouer `tsc -b --noEmit` **depuis le
   début de la session** — systématiquement classé à tort comme "pré-existant, sans
   rapport" à chaque vérification frontend antérieure, parce que le cache incrémental de
   `tsc` local masquait l'échec. Un build Docker, toujours propre (aucun
   `.tsbuildinfo`), l'a fait échouer pour de vrai. Corrigé.

Séquence de vérification réelle après correction :
- `docker compose build` — les 4 images se construisent (Postgres est une image
  officielle).
- `docker compose up -d` — `backend` passe `healthy` après `postgres` (healthchecks
  respectés dans l'ordre).
- Flux réel via `http://localhost` (nginx → backend conteneurisé) : inscription
  (`POST /api/v1/auth/inscription`), création de session, upload d'un document PDF.
- **Persistance des volumes prouvée, pas supposée** : après `docker compose restart
  backend`, la ligne en base **et** le fichier réel sur le volume (`docker exec
  memoria-backend-1 find /app/data`) sont tous les deux retrouvés intacts.
- `terraform init && terraform validate` — réussi. `terraform plan` — échoue proprement
  à l'authentification Azure (`az login` requis), confirmant que le code est correct
  sans qu'aucune ressource réelle n'ait été créée.
- Après `docker compose down` (qui a aussi arrêté le conteneur `memoria-postgres`
  préexistant, car géré par le même projet Compose), vérifié que le volume nommé
  `memoria_memoria_postgres_data` avait bien survécu (les volumes nommés ne sont
  jamais supprimés par `down` sans `-v`) et que les 42 comptes / 95 sessions
  accumulés avant cette brique étaient intacts après recréation du conteneur.

## 6. Limites connues, assumées, pas corrigées ici

- **Pas d'AKS** — VM unique + Docker Compose seulement (voir §2.1).
- **Pas de Kafka, Entra ID, Key Vault, ni migration vers Azure Blob Storage** — hors
  périmètre explicite, à traiter séparément.
- **Stockage sur disque local** (volumes Docker), pas Azure Blob — déjà documenté dans
  le code (`StockageAudioPort`) comme "remplaçable... Azure Blob demain".
- **Pas de push d'image vers un registry ni de déploiement automatique** — la CI
  construit les images comme garde-fou, ne les pousse nulle part.
- **`terraform apply` jamais exécuté** — aucune VM Azure réelle n'existe à ce jour, le
  code n'a été validé que syntaxiquement/sémantiquement.
- **Déploiement du `.env` réel sur la VM entièrement manuel** (scp) — pas d'étape
  CI/CD pour ça.

## 7. Pour reprendre seul

- Code de référence exact : `git checkout phase-7-deploiement-docker-terraform`
- Chemin de bout en bout local : `docker compose up --build` → `frontend` (nginx, `:80`)
  → proxy `/api/*` → `backend` (`:8080` interne) → `postgres` (`:5432` interne) /
  `speaker-service` (`:8090` interne, jamais publié).
- Chemin de bout en bout cloud (non exécuté, code prêt) : `cd infra/terraform &&
  terraform apply` (avec de vrais identifiants Azure) → VM provisionnée avec Docker →
  déployer manuellement le dépôt + `.env` réel → `docker compose up -d` sur la VM.
- Prochaine direction retenue après cette brique : observabilité minimale (Prometheus +
  Grafana + OpenTelemetry), explicitement liée à Docker/Terraform dans le master prompt
  ("dès la conception").
