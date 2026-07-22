# Déploiement

Premier incrément de "déploiement mono-instance industrialisé" (master prompt, section
"Accès utilisateurs et déploiement") : une VM Azure unique faisant tourner la stack
complète via Docker Compose. Pas d'AKS dans ce lot — voir "Limites et migration future"
en bas de page.

## 1. Lancer la stack complète en local

Depuis la racine du repo :

```
cp .env.example .env
# renseigner .env : au minimum MEMORIA_JWT_SECRET en production ;
# les cles Azure sont optionnelles, le backend degrade gracieusement si vides.
docker compose up --build
```

Ça démarre 4 conteneurs : `postgres` (5433→5432, inchangé par rapport à avant),
`backend` (accessible uniquement via le réseau interne, jamais publié directement),
`speaker-service` (jamais publié — voir §3), `frontend` (nginx, publié sur `:80`,
sert le build React et fait suivre `/api/*` vers `backend`).

Tester : ouvrir `http://localhost/choix-module`, s'inscrire, enregistrer une session
courte, vérifier qu'un document PDF/photo persiste après `docker compose restart backend`
(preuve que les volumes `memoria_audio_data`/`memoria_documents_data` sont bien
persistants, pas liés au cycle de vie du conteneur).

## 2. Variables d'environnement

Voir `.env.example` pour la liste complète. Toutes les clés Azure sont optionnelles
(dégradation gracieuse déjà gérée par le code — log + fonctionnalité désactivée,
jamais de crash au démarrage). Seule `MEMORIA_JWT_SECRET` est obligatoire en
production (valeur aléatoire, 32 caractères minimum).

## 3. Sécurité : speaker-service jamais exposé

Le `speaker-service` (reconnaissance de voix) n'a aujourd'hui aucune authentification
entre lui et le backend Java (voir `speaker-service/README.md`). Dans `docker-compose.yml`,
il n'a délibérément **aucun port publié vers l'hôte** — seul le backend, via le réseau
Docker interne, peut l'atteindre. Ne jamais ajouter de mapping `ports:` dessus sans
avoir d'abord construit une authentification.

## 4. Provisionner une VM Azure (Terraform)

```
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
# renseigner terraform.tfvars : nom du client, domaine, cle SSH publique,
# CIDR autorise en SSH (jamais 0.0.0.0/0)
terraform init
terraform validate
terraform plan
```

**Ne jamais lancer `terraform apply` sans supervision directe et sans avoir
vérifié le plan** — ça crée de vraies ressources Azure facturées. Aucune
variable secrète applicative (JWT, clés Azure AI) ne transite par Terraform :
elles vivent uniquement dans un `.env` copié séparément sur la VM (le state
Terraform stocke tout en clair, ce n'est pas un endroit pour des secrets).

La VM provisionnée n'a que Docker + le plugin Compose installés (via
cloud-init) — le dépôt applicatif et le `.env` réel se déploient séparément
(scp manuel pour l'instant, étape CI/CD à construire plus tard).

## 5. Limites et migration future

- **Pas d'AKS dans ce lot.** Le master prompt nomme AKS, mais ça correspond à
  la Phase 4 "Plateforme/passage à l'échelle" — prématuré sans client réel
  branché dessus. Les mêmes conteneurs (mêmes Dockerfiles, même
  `docker-compose.yml` comme base) tourneraient tels quels sur AKS le jour où
  cette phase l'exige — aucune réécriture nécessaire, juste des manifests k8s
  en plus.
- **Pas de Kafka, Entra ID, Key Vault, ni migration vers Azure Blob Storage**
  dans ce lot — hors périmètre explicite, à traiter séparément.
- **Stockage sur disque local** (volumes Docker), pas Azure Blob — c'est déjà
  documenté dans le code (`StockageAudioPort`) comme "remplaçable... Azure Blob
  demain".
- **Pas de push d'image vers un registry ni de déploiement automatique** —
  la CI construit les images comme garde-fou (`docker build`), mais ne les
  pousse nulle part. À construire quand un vrai déploiement récurrent
  (plusieurs clients, mises à jour fréquentes) le justifiera.
