# Observabilité (Prometheus + Grafana + OpenTelemetry) — comment on l'a construite

**Pour revenir exactement à cet état du code :**
```
git checkout phase-7-observabilite
```
Ce tag pointe sur le commit qui ajoute la stack d'observabilité, vérifiée end-to-end avec
de vrais conteneurs (trafic réel visible dans Prometheus et dans Tempo, dégradation
gracieuse testée en coupant Tempo puis Prometheus/Grafana).

---

## 1. Le besoin

Le master prompt le pose dans "Stack technique" (bullet "Infra & DevSecOps") : *"Prometheus
+ Grafana + OpenTelemetry (observabilité dès la conception)"*. La fiche technique de la
brique précédente (`docs/phases/phase-7-deploiement-docker-terraform.md`) concluait
explicitement : *"Prochaine direction retenue... observabilité minimale (Prometheus +
Grafana + OpenTelemetry), explicitement liée à Docker/Terraform dans le master prompt."*
`spring-boot-starter-actuator` avait déjà été ajouté lors de cette brique précédente comme
"premier pas minimal" (juste `/actuator/health`) — cette brique le complète réellement :
métriques exportées, traces distribuées, visualisation.

État avant cette brique : aucune dépendance micrometer/prometheus/opentelemetry ;
`application.properties` sans propriété `management.*` ; seule `/actuator/health` en
`permitAll()` ; `docker-compose.yml` à 4 services.

**Hors périmètre explicite** (limites assumées, pas construites ici) : métriques de coût
par tenant/service (nécessite du code métier custom — chantier séparé, "Maîtrise des coûts
Azure" du master prompt) ; alerting (Alertmanager) ; observabilité frontend (Sentry/RUM) ;
logs centralisés (Loki).

## 2. Les décisions de conception

### 2.1 — Micrometer + `/actuator/prometheus`, zéro code métier

`micrometer-registry-prometheus` instrumente automatiquement les requêtes HTTP Spring MVC,
la mémoire/GC JVM, le pool Hikari et Tomcat — aucun code applicatif à écrire.
`management.metrics.distribution.percentiles-histogram.http.server.requests=true` est
nécessaire pour calculer des p95/p99 côté Prometheus (`histogram_quantile()`) : sans cette
ligne, seuls count/sum/max sont exportés.

### 2.2 — Agent Java OpenTelemetry en auto-instrumentation, pas de code applicatif

Attaché via `-javaagent:` dans `backend/Dockerfile`, il instrumente par bytecode au
démarrage JVM (HTTP entrant, JDBC, Tomcat...). Version épinglée (`2.30.0`, jamais
`latest`) pour un build reproductible. `OTEL_METRICS_EXPORTER=none` évite une double
émission de métriques : Micrometer/Prometheus reste la seule source, l'agent ne sert
qu'aux traces.

### 2.3 — Grafana Tempo pour le stockage de traces, jamais exposé à l'hôte

Mode monolithique, stockage local (pas de dépendance à un backend objet externe pour ce
lot). Aucun `ports:` publié dans `docker-compose.yml` — même doctrine que
`speaker-service` (déjà documentée en phase-7-déploiement) : seul le backend (export OTLP)
et Grafana (lecture) l'atteignent via le réseau Docker interne.

### 2.4 — `/actuator/prometheus` suit exactement la doctrine de `/actuator/health`

`permitAll()` dans `SecurityConfig.java`, protégé par la frontière réseau (nginx ne relaie
que `/api/*`, jamais `/actuator/*`) et non par l'authentification applicative. Le
commentaire existant au-dessus de `/actuator/health`, qui affirmait à tort "les autres
endpoints actuator sont non exposés ici", est corrigé au passage.

### 2.5 — Grafana provisionné par fichiers, pas par clic-ouistiti

Datasources (Prometheus, Tempo) et dashboard ("Memoria Backend", 4 panels : taux de
requêtes HTTP, latence p95/p99, mémoire JVM heap, connexions actives Hikari) sont montés
en lecture seule depuis `infra/observability/grafana/` — reproductible, versionné, pas de
configuration manuelle perdue au prochain `docker compose down`.

### 2.6 — Dégradation gracieuse pour Tempo, doctrine déjà établie ailleurs

Même raisonnement que `EnvoyeurEmailSmtp` et les clients Azure : l'absence ou l'arrêt de
Tempo ne doit jamais faire planter le backend, seulement produire des warnings de log.
Vérifié concrètement (voir §5).

## 3. Les fichiers, un par un

### `backend/pom.xml`
Ajout de `micrometer-registry-prometheus` (pas de `<version>`, héritée du BOM Spring Boot).

### `backend/src/main/resources/application.properties`
```properties
management.endpoints.web.exposure.include=health,prometheus
management.endpoint.health.show-details=never
management.metrics.distribution.percentiles-histogram.http.server.requests=true
management.metrics.tags.application=${spring.application.name}
```

### `backend/src/main/java/com/memoria/core/auth/SecurityConfig.java`
```java
.requestMatchers("/actuator/health").permitAll()
.requestMatchers("/actuator/prometheus").permitAll()
```

### `backend/Dockerfile`
Agent Java OpenTelemetry téléchargé et attaché à l'`ENTRYPOINT` :
```dockerfile
ARG OTEL_JAVAAGENT_VERSION=2.30.0
RUN curl -fsSL -o /app/opentelemetry-javaagent.jar \
      "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVAAGENT_VERSION}/opentelemetry-javaagent.jar"
ENV OTEL_SERVICE_NAME=memoria-core \
    OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4317 \
    OTEL_EXPORTER_OTLP_PROTOCOL=grpc \
    OTEL_TRACES_EXPORTER=otlp \
    OTEL_METRICS_EXPORTER=none \
    OTEL_LOGS_EXPORTER=none \
    OTEL_TRACES_SAMPLER=parentbased_always_on
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
```
`OTEL_EXPORTER_OTLP_PROTOCOL=grpc` explicite — voir bug §5.1.

### `infra/observability/` (nouveau, miroir de `infra/terraform/`)
```
prometheus/prometheus.yml            # scrape /actuator/prometheus toutes les 15s
tempo/tempo.yaml                     # mode monolithique, stockage local, OTLP grpc+http
grafana/provisioning/datasources/    # Prometheus + Tempo, uid fixes
grafana/provisioning/dashboards/     # provider pointant vers grafana/dashboards/
grafana/dashboards/memoria-backend.json  # 4 panels fonctionnels
```

### `docker-compose.yml`
Ajoute `prometheus` (port `9090` publié, debug local), `tempo` (jamais publié),
`grafana` (port `3000` publié, mot de passe admin obligatoire via
`GF_SECURITY_ADMIN_PASSWORD:?...`). Trois nouveaux volumes nommés.

### `.env.example` + `docs/deploiement.md`
Variable `GF_SECURITY_ADMIN_PASSWORD` documentée ; nouvelle section "Observabilité" dans
le guide de déploiement (accès Grafana/Prometheus, doctrine Tempo, dégradation gracieuse).

### `frontend/nginx.conf` — corrigé pendant la vérification (hors périmètre initial)
Voir §5.2 et §5.3.

### `frontend/Dockerfile` — corrigé pendant la vérification (hors périmètre initial)
Voir §5.4.

## 4. Les tests

Aucun changement de code métier testable unitairement (config Spring Boot + infra pure).
`cd backend && mvn test` — **164/164 tests** passent, inchangé.

## 5. Comment on a vérifié en conditions réelles

Trois bugs ont été découverts pendant la vérification (pas anticipés par le plan initial),
et corrigés dans la même brique — même méthode que la brique précédente (numpy, tsc) : la
vérification par redémarrage réel de conteneurs, pas juste "ça compile", est ce qui les a
révélés.

### 5.1 — L'agent OTel ne déduit pas gRPC du port 4317

Malgré `OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4317` (le port gRPC de Tempo), l'agent
loggait `WARN ... OTLP exporter endpoint port is likely incorrect for protocol version
"http/protobuf"` puis `ERROR ... Failed to export spans`. Le SDK OTel Java (vérifié en
v2.30.0) ne déduit pas le protocole du numéro de port. **Corrigé** en fixant
`OTEL_EXPORTER_OTLP_PROTOCOL=grpc` explicitement. Vérifié : plus aucun warning après
rebuild, et de vraies traces confirmées dans Tempo (`POST /api/v1/auth/connexion` complet,
spans imbriqués Tomcat → Spring Data → Hibernate → JDBC).

### 5.2 — nginx met en cache l'IP Docker du backend indéfiniment

`proxy_pass http://backend:8080/api/;` (nom d'hôte statique) résout le DNS **une seule
fois**, au chargement de la config nginx. Après un `docker compose restart backend`
(nouvelle IP interne), nginx continuait de taper sur l'ancienne IP → `502 Host is
unreachable` jusqu'à son propre redémarrage. **Corrigé** avec `resolver 127.0.0.11
valid=10s;` (DNS embarqué de Docker) + une variable dans `proxy_pass`, qui force une
résolution périodique.

### 5.3 — Une variable dans `proxy_pass` casse la réécriture de chemin habituelle

Après le correctif §5.2, les requêtes à travers nginx retournaient `403` au lieu de `401`
pour de mauvais identifiants, alors que l'accès direct au backend restait correct. Cause :
avec une variable dans `proxy_pass`, nginx ne fait plus son remplacement de préfixe
habituel — concaténer un chemin littéral (`$backend_upstream/api/`) après la variable
produit un chemin ambigu, qui ne correspond plus à la règle `permitAll` attendue côté
Spring Security. **Corrigé** en utilisant `proxy_pass http://$backend_upstream:8080$request_uri;`
— `$request_uri` repasse l'URI d'origine telle quelle, sans ambiguïté de concaténation.
Vérifié robuste sur plusieurs redémarrages successifs du backend.

### 5.4 — Le HEALTHCHECK du frontend échoue en IPv6, alors que nginx répond bien

`docker ps` affichait `memoria-frontend-1` en `unhealthy` en continu, alors que le trafic
réel (curl à travers nginx) fonctionnait parfaitement. Cause : le `HEALTHCHECK` du
`frontend/Dockerfile` fait `wget http://localhost/`, et `localhost` résout d'abord en IPv6
(`::1`, présent avant `127.0.0.1` dans `/etc/hosts` du conteneur) — or nginx n'écoute que
sur `0.0.0.0:80` (IPv4), jamais `[::]:80` (l'entrypoint nginx n'active l'écoute IPv6 par
défaut que si sa config n'a pas été personnalisée, ce qui n'est pas le cas ici). Confirmé
par `wget http://127.0.0.1/` en direct dans le conteneur (succès immédiat) contre
`wget http://localhost/` (`Connection refused`). **Corrigé** en remplaçant `localhost` par
`127.0.0.1` dans le `CMD` du `HEALTHCHECK`. N'affecte aucun autre fichier (le proxy
`/api/` de `nginx.conf` n'est pas concerné, seul le health-check `/` racine l'était).

### Séquence de vérification réelle

- `docker compose up --build -d` → 7 conteneurs `Up`/`healthy` (postgres, backend,
  speaker-service, frontend, prometheus, tempo, grafana).
- Trafic réel (inscription, connexion) via `http://localhost`.
- Prometheus UI (`:9090` → Status → Targets) : cible `memoria-backend` = `UP`, `lastError`
  vide.
- Requête PromQL directe : `http_server_requests_seconds_count > 0` renvoie des séries
  réelles, taguées `application="memoria-core"`. `hikaricp_connections_active` également
  présent.
- Grafana Explore → datasource Tempo → recherche `service.name = memoria-core` : dizaines
  de traces réelles, dont une trace complète `POST /api/v1/auth/connexion` avec spans
  imbriqués corrects.
- **Test de dégradation gracieuse (Tempo)** : `docker compose stop tempo` puis
  `docker compose restart backend` → backend reste `healthy`, `curl` continue de
  fonctionner, seuls des warnings `GrpcExporter - Failed to export spans` apparaissent en
  boucle dans les logs — jamais de crash, jamais de redémarrage en boucle.
- **Test de dégradation gracieuse (Prometheus + Grafana)** : `docker compose stop
  prometheus grafana` puis `curl -X POST http://localhost/api/v1/auth/connexion` (`401`)
  et `curl -X POST http://localhost/api/v1/auth/inscription` (`201`) — flux critique
  frontend→backend→postgres intact, preuve que l'observabilité est un ajout, jamais une
  dépendance dure. Stack remise à 7 conteneurs ensuite.
- nginx testé résistant à des redémarrages **répétés** du backend (pas un coup de chance
  isolé) : `401` correct à chaque tentative après le correctif §5.3.
- Frontend confirmé `healthy` dans `docker ps` après le correctif §5.4, sans régression du
  flux réel.

## 6. Limites connues, assumées, pas corrigées ici

- **Pas de métriques de coût par tenant/service** — nécessiterait du code métier custom
  (comptage d'appels Azure par session/tenant), chantier séparé ("Maîtrise des coûts
  Azure" du master prompt).
- **Pas d'alerting** (Alertmanager) — dashboards de consultation uniquement, personne
  n'est notifié automatiquement d'une anomalie.
- **Pas d'observabilité frontend** (Sentry/RUM) — ce lot couvre uniquement le backend.
- **Pas de logs centralisés** (Loki) — seulement métriques + traces.
- **Ports 9090/3000 publiés localement pour le débogage** — en déploiement Azure réel,
  seul le NSG Terraform (règles 80/443/22) contrôle ce qui est joignable depuis Internet ;
  ces ports restent fermés au niveau réseau même "publiés" localement par Compose.
- **Authentification Grafana par API (curl) non fiable pendant cette session** — le
  serveur répondait `403` à des tentatives Basic Auth et cookie-session malgré des logs de
  provisioning propres (aucune erreur) et un mot de passe confirmé correct dans le
  conteneur ; probablement un contrôle CSRF/origin de Grafana 11.x pour les clients non-
  navigateur. Non résolu par manque de temps utile — vérification faite via les logs de
  provisioning (zéro erreur) plutôt que via l'API, une vérification manuelle au navigateur
  reste recommandée.

## 7. Pour reprendre seul

- Code de référence exact : `git checkout phase-7-observabilite`
- Grafana : `http://localhost:3000`, `admin` / `GF_SECURITY_ADMIN_PASSWORD` du `.env`,
  dashboard "Memoria Backend" préprovisionné.
- Prometheus : `http://localhost:9090` — debug local uniquement.
- Tempo : jamais exposé, consultable via Grafana Explore (datasource "Tempo",
  `service.name = memoria-core`).
- Prochaine direction possible : qualité (SonarQube/OWASP dependency-check) ou la
  fonctionnalité tuteur vocal École — à choisir explicitement, pas enchaînée
  automatiquement.
