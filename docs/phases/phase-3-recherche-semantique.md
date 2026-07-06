# Phase 3 : la recherche sémantique — comment on l'a construite

**Pour revenir exactement à cet état du code :**
```
git checkout phase-3-recherche-semantique
```

Ce document est plus long que les précédents (Phase 2) parce que cette brique a touché à des choses qu'on n'avait jamais faites avant sur ce projet : provisionner une nouvelle ressource Azure de zéro (pas juste utiliser une clé déjà là), gérer des quotas et des limites de débit réels, et comprendre ce qu'est réellement une "recherche vectorielle". L'objectif de ce fichier est que tu puisses non seulement reproduire cette brique, mais comprendre *chaque concept* utilisé, même ceux qui dépassent le simple "code Java/TypeScript" (Azure, React, tests).

---

## 1. Le besoin

`memoria-master-prompt.md` (section engine) demande une recherche sémantique qui retrouve "le passage exact, avec son timestamp", sur **tout l'historique** des sessions — pas juste dans une session à la fois. C'est différent d'un `Ctrl+F` : on veut pouvoir taper *"c'est quand qu'on a parlé du budget ?"* et retrouver le passage même si personne n'a jamais prononcé le mot "budget" tel quel, parce qu'on cherche une **signification**, pas une chaîne de caractères.

C'est la première brique de la Phase 3 ("Mémoire & recherche"), et le premier morceau du projet qui utilise **Azure AI Search** (jusque-là, on utilisait Azure Speech, Azure OpenAI et Azure Document Intelligence, mais jamais de base de données vectorielle).

---

## 2. Les concepts, avant le code

Si tu ne connais pas encore ces trois notions, tout le reste du document est plus clair une fois qu'elles sont posées.

### 2.1 — Qu'est-ce qu'un "embedding" (vecteur) ?

Un embedding est une liste de nombres (ici, 1536 nombres décimaux) qui représente le *sens* d'un texte. Deux phrases qui veulent dire la même chose, même écrites très différemment ("le projet est repoussé" / "on lance le produit plus tard que prévu"), ont des listes de nombres *proches* mathématiquement. Un modèle d'IA spécialisé (ici `text-embedding-ada-002` d'Azure OpenAI) transforme n'importe quel texte en cette liste de nombres — on appelle ça "vectoriser" un texte.

### 2.2 — Qu'est-ce qu'une "recherche vectorielle" ?

Une fois que chaque segment de transcription est transformé en vecteur et stocké, chercher revient à : vectoriser la question tapée par l'utilisateur (même modèle, même transformation), puis demander à la base de données "quels sont les vecteurs stockés les plus proches mathématiquement de ce vecteur-là ?". C'est ce qu'on appelle une recherche **KNN** (k-nearest-neighbors, "les k plus proches voisins"). Azure AI Search sait faire ça nativement, avec un algorithme appelé **HNSW** (une structure de données qui évite de comparer le vecteur de la requête à *tous* les vecteurs stockés un par un, ce qui serait trop lent dès qu'il y a beaucoup de documents).

Point important à retenir pour la suite (section 10.3) : une recherche KNN renvoie **toujours** les k résultats les plus proches, même si aucun n'est vraiment pertinent. Il n'existe pas de "aucun résultat" tant que l'index contient au moins un document — juste des résultats plus ou moins bons.

### 2.3 — Ports et adaptateurs (rappel d'architecture)

Comme pour Azure Speech/OpenAI/Document Intelligence dans les phases précédentes, on ne fait jamais appeler directement "Azure AI Search" depuis le code métier. Le motif est toujours le même :
- Une **interface Java** ("port") qui décrit *ce dont on a besoin* sans dire comment (`RecherchePort`, `GenerateurEmbeddingPort`).
- Une **classe qui implémente cette interface** ("adaptateur") qui sait parler à Azure en HTTP (`RechercheAzureAiSearch`, `GenerateurEmbeddingAzureOpenAI`).

Pourquoi cette fois-ci il y a **deux** ports séparés au lieu d'un seul "port de recherche" qui ferait tout ? Parce que ce sont deux ressources Azure complètement différentes (Azure OpenAI pour transformer du texte en vecteur, Azure AI Search pour stocker/interroger des vecteurs), qui pourraient un jour changer indépendamment l'une de l'autre (par exemple remplacer Azure AI Search par une autre base vectorielle, en gardant le même modèle d'embeddings). Les séparer en deux ports, c'est appliquer la même règle de `CLAUDE.md` ("le domaine ne dépend d'aucune techno précise") au niveau le plus fin possible.

---

## 3. Les décisions de conception, avant le code

### 3.1 — Quelle granularité indexer ?

Trois choix possibles : indexer toute une session en un bloc, indexer par chunk audio de 30s (`Transcription`), ou indexer par prise de parole (`SegmentLocuteur`, une phrase ou un groupe de phrases dites par la même personne sans interruption).

On a choisi le niveau **segment**, parce que c'est le seul qui satisfait l'exigence de `CLAUDE.md` : *"chaque résumé doit être traçable jusqu'au passage exact de la transcription qui le justifie"*. Un `SegmentLocuteur` a déjà un `offsetMillisecondes` précis (depuis la Phase 2, diarization) — c'est exactement le "timestamp exact" que le master-prompt demande. Indexer au niveau du chunk de 30s aurait donné un timestamp bien moins précis (le début du chunk, pas le début de la phrase).

Conséquence en termes de coût : une session ordinaire produit 2 à 10x plus de segments que de chunks. On mitige ça en faisant **un seul appel** à l'API d'embeddings par session (avec tous les segments de la session envoyés d'un coup dans une liste), jamais un appel par segment — voir section 6.3.

### 3.2 — Quand déclencher l'indexation ?

Même dilemme que pour le résumé (Phase 2) : indexer à la fin de chaque chunk (dès qu'il arrive) ou attendre la fin de la session ? On a choisi **la fin de session**, pour deux raisons : (1) le besoin exprimé est une recherche *historique*, pas du temps réel — inutile de payer pour indexer une session encore en cours ; (2) ça permet de réutiliser exactement le même mécanisme d'événements que `ResumeService` (voir section 6.4), pas de nouveau concept à inventer.

### 3.3 — Où stocker les vecteurs ? (décision de l'utilisateur)

Deux options envisagées : `pgvector` (extension PostgreSQL qui ajoute un type de colonne vecteur — gratuit, pas de nouvelle ressource Azure) ou Azure AI Search (service managé dédié à la recherche, payant séparément mais avec plus de fonctionnalités : reranking, recherche hybride texte+vecteur, mise à l'échelle indépendante). Le choix par défaut recommandé était `pgvector` (plus simple, cohérent avec l'approche "monolithe d'abord" du projet), mais l'utilisateur a choisi **Azure AI Search** explicitement — c'est ce choix qui a entraîné toute la section 4 (provisionnement d'une ressource entièrement nouvelle).

---

## 4. Provisionner les ressources Azure — le vrai parcours (avec les galères)

C'est la partie la plus longue à raconter, et la plus utile si tu dois un jour recréer ou modifier ces ressources Azure toi-même.

### 4.1 — Ce qu'il fallait, concrètement

Deux choses, sur deux ressources Azure différentes :
1. Un **déploiement de modèle d'embeddings** sur la ressource Azure OpenAI existante (`memoria-openai`) — distinct du déploiement de chat déjà utilisé pour les résumés/comptes rendus, parce qu'un modèle d'embeddings et un modèle de chat sont deux familles de modèles différentes.
2. Une **ressource Azure AI Search** entièrement nouvelle (`memoria-search-pj`), avec son propre endpoint et sa propre clé — ça, contrairement au reste, existait déjà comme variables d'environnement (`AZURE_SEARCH_ENDPOINT`, `AZURE_SEARCH_KEY`) au moment où on a commencé, donc pas de provisionnement à faire pour celle-là.

### 4.2 — Se connecter à Azure en ligne de commande : plus dur que prévu

Pour créer un déploiement de modèle, il faut soit passer par le portail web Azure AI Foundry, soit par la CLI Azure (`az`). Le portail web posait un problème concret : il insistait pour créer un nouveau "projet" Azure AI Foundry au lieu de réutiliser la ressource `memoria-openai` existante — pas pratique.

On est donc passé par la CLI (`az`), mais la connexion (`az login`) a posé plusieurs problèmes en cascade, dans cet ordre :

1. **`az login` classique (ouverture de navigateur automatique)** : n'a jamais ouvert de fenêtre de navigateur dans cet environnement (pas d'explication trouvée — probablement une limitation de l'environnement d'exécution de l'agent). Résultat : la commande restait bloquée en attente, sans jamais aboutir.
2. **`az login --use-device-code`** (méthode alternative : affiche un code à taper manuellement sur `https://microsoft.com/devicelogin`) : a fini par afficher un code, mais après connexion le profil Azure local (`~/.azure/azureProfile.json`) restait avec `"subscriptions": []` — vide. Le token d'authentification était bien obtenu (le cache MSAL se mettait à jour), mais aucune souscription Azure n'était visible avec ce compte dans ce tenant par défaut.
3. **`az login --use-device-code --allow-no-subscriptions`** : toujours pareil, `az account show` répondait "Please run az login" même après un login apparemment réussi.

**Cause profonde jamais totalement élucidée**, mais contournement trouvé : utiliser **Azure Cloud Shell** (le terminal intégré au portail Azure, accessible depuis n'importe quel navigateur, déjà authentifié automatiquement avec le compte connecté au portail). Toutes les commandes `az` de ce document ont finalement été exécutées **dans le Cloud Shell**, pas dans un terminal local — c'est important si tu dois refaire cette manipulation : ne perds pas de temps à déboguer `az login` en local, ouvre directement le Cloud Shell.

**Piège rencontré dans le Cloud Shell** : le Cloud Shell peut démarrer en session **PowerShell**, où le caractère `\` en fin de ligne (continuation de commande en Bash) provoque une erreur de syntaxe. Il faut soit écrire la commande sur une seule ligne, soit utiliser la syntaxe PowerShell (`` ` `` en fin de ligne). Toutes les commandes ci-dessous sont donc données sur une seule ligne.

### 4.3 — Trouver le nom exact des ressources

```
az cognitiveservices account list --query "[].{name:name, resourceGroup:resourceGroup, kind:kind}" -o table
```
Résultat (pertinent pour la suite) :
```
Name                ResourceGroup    Kind
------------------  ---------------  --------------
memoria-openai      memoria-rg       AIServices
```
`memoria-rg` (groupe de ressources) et `memoria-openai` (nom du compte) sont les deux informations dont on a besoin pour créer le déploiement.

### 4.4 — Choisir un modèle d'embeddings disponible dans la région

```
az cognitiveservices account list-models --name memoria-openai --resource-group memoria-rg -o table
```
Ça liste tous les modèles que la ressource peut déployer (des dizaines, y compris des modèles de chat qu'on n'utilise pas). Parmi eux :
```
text-embedding-ada-002    (version 2, GenerallyAvailable)
text-embedding-3-small    (version 1, GenerallyAvailable)
text-embedding-3-large    (version 1, GenerallyAvailable)
```
Le choix initial (recommandé par l'agent) était `text-embedding-3-small` — plus récent, moins cher, 1536 dimensions comme `ada-002` mais meilleure qualité en théorie. **Ça n'a pas marché**, voir section 5.1.

### 4.5 — Premier essai de création de déploiement, et le premier échec (SKU incompatible)

```
az cognitiveservices account deployment create --name memoria-openai --resource-group memoria-rg --deployment-name text-embedding-3-small --model-name text-embedding-3-small --model-version "1" --model-format OpenAI --sku-name "Standard" --sku-capacity 1
```
Erreur :
```
(InvalidResourceProperties) The specified SKU 'Standard' for model 'text-embedding-3-small 1' is not supported in this region 'francecentral'.
```
Deuxième essai avec `--sku-name "GlobalStandard"` à la place de `"Standard"` : le SKU a été accepté, mais nouvelle erreur :
```
(InsufficientQuota) This operation require 1 new capacity in quota Tokens Per Minute (thousands) - text-embedding-3-small, which is bigger than the current available capacity 0. The current quota usage is 0 and the quota limit is 0...
```
**Diagnostic** : chaque modèle, dans chaque région, sur chaque SKU (`Standard`, `GlobalStandard`, `DataZoneStandard`), a un quota différent — et parfois ce quota est **zéro**, ce qui bloque tout déploiement même à la capacité minimale. Pour vérifier quel modèle/SKU a vraiment du quota disponible, la commande utile est :
```
az cognitiveservices usage list --location francecentral --query "[?contains(name.value, 'embedding')]" -o json
```
Résultat (extrait, ce qui comptait) :
```json
{"name": {"value": "OpenAI.Standard.text-embedding-ada-002"}, "limit": 240.0}
{"name": {"value": "OpenAI.Standard.text-embedding-3-large"}, "limit": 350.0}
{"name": {"value": "OpenAI.GlobalStandard.text-embedding-3-small"}, "limit": 0.0}
```
`text-embedding-3-small` n'avait **aucun quota disponible** sur ce compte Azure for Students, sur aucun SKU. **Décision (validée avec l'utilisateur, via une question explicite)** : basculer sur `text-embedding-ada-002` (SKU `Standard`, quota 240K disponible) — plus ancien, mais avec du quota réel, moins cher, et largement suffisant pour ce projet.

### 4.6 — Le déploiement qui a marché

```
az cognitiveservices account deployment create --name memoria-openai --resource-group memoria-rg --deployment-name text-embedding-ada-002 --model-name text-embedding-ada-002 --model-version "2" --model-format OpenAI --sku-name "Standard" --sku-capacity 1
```
Réponse : `"deploymentState": "Running"`, `"provisioningState": "Succeeded"`. Ce déploiement est **utilisé par son nom** (`text-embedding-ada-002`) dans la variable d'environnement `AZURE_OPENAI_EMBEDDING_DEPLOYMENT`, exactement comme `AZURE_OPENAI_DEPLOYMENT` pointe vers le déploiement de chat.

### 4.7 — Deuxième limite de capacité, découverte plus tard (section 5.2)

`--sku-capacity 1` veut dire "1 unité de capacité", où une unité = 1000 tokens par minute. C'est très bas — juste assez pour un test unitaire, pas pour une vraie session. On l'a augmenté à `10` (10 000 tokens/minute) une fois le problème détecté (voir section 5.2) :
```
az cognitiveservices account deployment create --name memoria-openai --resource-group memoria-rg --deployment-name text-embedding-ada-002 --model-name text-embedding-ada-002 --model-version "2" --model-format OpenAI --sku-name "Standard" --sku-capacity 10
```
Cette commande peut être relancée à tout moment pour changer uniquement la capacité (elle recrée/met à jour le déploiement existant, ne le supprime pas).

---

## 5. Les erreurs rencontrées côté code, dans l'ordre

### 5.1 — Conflit de schéma d'index ("Existing field 'id' cannot be changed")

En testant l'API Azure AI Search directement au tout début (avant d'écrire une ligne de Java), on a créé un index de test à la main avec `curl` pour vérifier le format exact d'une requête de création d'index et d'upload de document. Une fois le code Java (`RechercheAzureAiSearch.assurerIndexExiste()`) écrit avec une définition de champs légèrement différente de ce test manuel, le démarrage du backend affichait :
```
Impossible de creer/mettre a jour l'index Azure AI Search 'memoria-segments' : statut 400 - {"error":{"code":"OperationNotAllowed","message":"Existing field 'id' cannot be changed."}}
```
**Cause** : Azure AI Search autorise d'*ajouter* des champs à un index existant, mais pas de changer les propriétés (`searchable`, `filterable`, `sortable`...) d'un champ déjà créé — même si la différence semble mineure. **Correctif** : supprimer l'index de test (`DELETE /indexes/memoria-segments`) et laisser l'application le recréer proprement au prochain démarrage, avec le schéma définitif du code. **Leçon** : si tu modifies un jour la définition des champs dans `RechercheAzureAiSearch.assurerIndexExiste()`, il faudra probablement supprimer l'index existant à la main avant de relancer l'appli (perte du contenu déjà indexé — à réindexer ensuite avec `POST /api/v1/recherche/reindexation`, voir section 9).

### 5.2 — Limite de débit dépassée (`RateLimitReached`) en réindexant une vraie session

En testant la réindexation sur une vraie session (30 segments, transcription d'une histoire), la génération d'embeddings a échoué :
```
{"error":{"code":"RateLimitReached","message":"Your requests ... have exceeded the call rate limit for your current AIServices S0 pricing tier ... Please retry after 60 seconds."}}
```
**Diagnostic** : la capacité du déploiement (`--sku-capacity 1` = 1000 tokens/minute) était trop basse — les 30 segments de cette session représentaient environ 1239 tokens estimés (calcul approximatif : nombre de caractères ÷ 4), au-dessus de la limite en un seul appel. **Correctif** : augmenter la capacité à 10 (section 4.7). **Leçon générale, utile pour la suite du projet** : une vraie réunion de 30 minutes produira largement plus de 1000 tokens de segments — ne jamais laisser une capacité de déploiement au minimum "juste pour tester", ça casse silencieusement dès qu'on utilise de vraies données.

### 5.3 — Précision médiocre de la recherche vectorielle pure (score trop resserré)

Après avoir tout fait fonctionner, l'utilisateur a testé lui-même et remarqué : *"c'est pas beaucoup précis"*. En comparant les scores renvoyés pour une question pertinente et une question totalement hors-sujet :
```
Question pertinente ("comment le couple a trouve sa fortune")   -> meilleur score 0.887
Question hors-sujet ("xyzxyz ... rien du tout")                  -> meilleur score 0.831
```
Un écart de seulement 0.05 entre "ça correspond vraiment" et "ça ne correspond à rien" — trop faible pour distinguer les deux de façon fiable. **Cause** : c'est une propriété connue de `text-embedding-ada-002` (un modèle plus ancien) — les scores de similarité cosinus qu'il produit ont tendance à rester dans une bande étroite et élevée (souvent 0.8-0.9), quelle que soit la pertinence réelle, ce qui rend un seuil fixe ("n'affiche rien en dessous de 0.85") peu fiable.

**Correctif retenu : recherche hybride.** Azure AI Search permet de combiner, en une seule requête, la recherche vectorielle *et* une recherche par mots-clés classique (BM25, l'algorithme de pertinence textuelle traditionnel) sur le champ `texte` — les deux classements sont ensuite fusionnés automatiquement par Azure via une méthode appelée **RRF** (*Reciprocal Rank Fusion* : au lieu de comparer des scores de nature différente, RRF combine les *rangs* — la position n°1, n°2, n°3... de chaque méthode — dans un score final unique). Testé sur les deux mêmes questions après ce changement :
```
Question pertinente   -> meilleur score 0.0325, puis 0.0307, 0.0306 (3 resultats groupes), puis chute a 0.0255
Question hors-sujet    -> un seul score a 0.0293, puis chute immediate a 0.0167
```
L'échelle des scores change complètement (RRF produit des scores petits, pas des similarités cosinus), mais l'écart relatif est bien plus net : trois bons résultats consécutifs pour la question pertinente, contre un seul résultat "limite" puis une chute abrupte pour le bruit. **Coût de ce changement : zéro** — aucune réindexation nécessaire, c'est un changement uniquement côté requête de recherche (voir `RechercheAzureAiSearch.rechercher()`, section 6.2).

**Limite qui reste, assumée** : la recherche KNN renvoie toujours des résultats (section 2.2) — il n'y a toujours pas de vrai "aucun résultat" tant que l'index n'est pas vide. Une amélioration possible mais pas faite ici : le **reranking sémantique** d'Azure AI Search, une fonctionnalité payante séparée qui utilise un modèle dédié pour reclasser les résultats avec un jugement de pertinence bien plus fin qu'un score de distance — non activée dans ce projet (coût supplémentaire, à évaluer si la précision doit encore progresser).

---

## 6. Les fichiers backend, un par un

Tous dans `backend/src/main/java/com/memoria/core/recherche/` (nouveau package — remarque `core`, pas `entreprise` ni `ecole` : la recherche sémantique est une fonctionnalité du moteur partagé, utile aux deux produits, conformément à `CLAUDE.md`).

### 6.1 — Les types de données (records et enum)

```java
public record SegmentARecherche(int numeroSequence, int locuteur, String texte, long offsetMillisecondes, long dureeMillisecondes) {}
public record ResultatRecherche(UUID sessionId, String titreSession, Instant dateSession, String texte, int locuteur, long offsetMillisecondes, long dureeMillisecondes, int numeroSequence, double score) {}
```
Ce sont des **records Java** (une syntaxe qui génère automatiquement le constructeur, les méthodes d'accès type `texte()`, `equals()`, `hashCode()` et `toString()` — pratique pour des objets qui ne font que transporter des données, sans logique). `SegmentARecherche` est ce qu'on envoie *vers* Azure AI Search (un segment à indexer), `ResultatRecherche` est ce qu'on reçoit *depuis* Azure AI Search (un résultat de recherche). Ce sont des DTO "internes" (entre le service et les ports), différents des DTO "API" (`RechercheResultatResponse`, section 6.6) qui eux sont ce que le backend renvoie au navigateur.

`StatutIndexRecherche` (enum `REUSSI` / `ECHEC`) suit le même principe que `ResumeStatut`/`StatutCompteRendu` des phases précédentes : marquer si l'indexation d'une session a réussi ou échoué, sans jamais réessayer automatiquement (pour ne pas rappeler Azure OpenAI en boucle sur une session qui échoue systématiquement).

### 6.2 — Les deux ports et leurs adaptateurs

`GenerateurEmbeddingPort` (interface) :
```java
public interface GenerateurEmbeddingPort {
    List<float[]> genererEmbeddings(List<String> textes);
}
```
Une méthode, qui prend une **liste** de textes et renvoie une liste de vecteurs (un `float[]` par texte, dans le même ordre) — jamais un texte à la fois, précisément pour pouvoir tout envoyer en un seul appel HTTP (discipline de coûts, `CLAUDE.md`).

Son implémentation, `GenerateurEmbeddingAzureOpenAI`, construit une requête vers `https://memoria-openai.services.ai.azure.com/openai/deployments/text-embedding-ada-002/embeddings?api-version=2023-05-15` avec un corps `{"input": ["texte 1", "texte 2", ...]}`, et récupère `data[].embedding` dans la réponse. Un détail important : Azure ne garantit pas que l'ordre des vecteurs dans la réponse corresponde exactement à l'ordre des textes envoyés — chaque élément de la réponse a un champ `index`, et le code **trie explicitement** sur ce champ avant de renvoyer les vecteurs (`extraireVecteurs()`), pour être sûr que `vecteurs.get(i)` correspond bien à `textes.get(i)`.

`RecherchePort` (interface) :
```java
public interface RecherchePort {
    void indexerSegments(UUID sessionId, String titreSession, Instant dateSession, List<SegmentARecherche> segments, List<float[]> vecteurs);
    List<ResultatRecherche> rechercher(String texteRequete, float[] vecteurRequete, int limite);
}
```
Son implémentation, `RechercheAzureAiSearch`, fait trois choses :
1. **`assurerIndexExiste()`** (annotée `@PostConstruct`, donc exécutée automatiquement une fois au démarrage de l'application) : envoie un `PUT` vers `/indexes/memoria-segments` avec la définition complète des champs. Ce `PUT` est **idempotent** — s'il n'existe pas, Azure le crée (statut `201`) ; s'il existe déjà à l'identique, Azure le met à jour sans rien casser (statut `204`). C'est ce qui permet à l'application de "s'auto-provisionner" son propre index à chaque démarrage, sans étape manuelle (cohérent avec l'objectif `CLAUDE.md` de déploiement reproductible).
2. **`indexerSegments()`** : construit un tableau de documents et les envoie en un seul `POST` vers `/indexes/memoria-segments/docs/index`, avec `"@search.action": "mergeOrUpload"` sur chaque document — cette action crée le document s'il n'existe pas, ou le remplace s'il existe déjà avec le même `id` (donc rappeler cette méthode deux fois sur les mêmes segments ne duplique rien). L'`id` de chaque document est construit comme `{sessionId}_{numeroSequence}_{offsetMillisecondes}` — une combinaison forcément unique (deux segments différents ne peuvent pas commencer exactement à la même milliseconde dans le même chunk).
3. **`rechercher()`** : envoie un `POST` vers `/indexes/memoria-segments/docs/search`, avec à la fois `vectorQueries` (le vecteur de la question) et `search` (le texte brut de la question, pour la recherche hybride, voir section 5.3).

### 6.3 — `RechercheService`, le chef d'orchestre

C'est la classe qui relie tout : elle ne parle jamais directement à Azure (ça, c'est le travail des adaptateurs), elle décide *quand* et *avec quelles données* appeler les ports.

**`indexerSiPossible(UUID sessionId)`** (privée) est le cœur de la logique d'indexation, avec cette structure en 4 étapes, qu'on retrouve identique dans `ResumeService` et `CompteRenduService` des phases précédentes :
1. **Garde d'idempotence** : si un `IndexRecherche` existe déjà pour cette session, on ne fait rien (`return` immédiat). Ça protège contre un double déclenchement (voir point suivant) *et* ça rend `reindexerHistorique()` totalement sûr à relancer plusieurs fois.
2. **Filtrage des transcriptions réussies** : on ignore les chunks dont la transcription a échoué (`TranscriptionStatut.ECHEC`).
3. **Aplatissement des segments** (`flatMap`) : chaque `Transcription` réussie a une liste de `SegmentLocuteur` ; on les met tous à plat dans une seule liste `List<SegmentARecherche>`, en filtrant au passage les segments dont le texte est vide ou ne contient que des espaces (`isBlank()`) — utile pour les tout premiers segments d'un chunk, parfois vides selon la façon dont Azure Speech découpe l'audio.
4. **Appel aux ports, avec gestion d'échec** : un seul appel à `genererEmbeddings()` pour tous les segments d'un coup, puis un seul appel à `indexerSegments()`. Si n'importe quelle étape lève une exception (Azure OpenAI ou Azure AI Search en panne, quota dépassé...), elle est attrapée et la session est marquée `ECHEC` plutôt que de faire planter le thread — exactement le même principe de résilience que pour les résumés (`CLAUDE.md`, section résilience : *"l'analyse peut rattraper son retard plus tard"*, pas de session perdue si l'IA est indisponible).

**Pourquoi deux méthodes publiques déclenchent la même logique privée ?**
```java
@Async @EventListener
public void surSessionTerminee(SessionTermineeEvent evenement) { indexerSiPossible(evenement.sessionId()); }

@Async @EventListener
public void surToutesTranscriptionsTerminees(ToutesTranscriptionsTermineesEvent evenement) { indexerSiPossible(evenement.sessionId()); }
```
`SessionTermineeEvent` est publié quand l'utilisateur clique sur "Terminer la session". `ToutesTranscriptionsTermineesEvent` est publié quand le *dernier* chunk audio fini d'être transcrit. Le problème : ces deux événements peuvent arriver dans **n'importe quel ordre** — la session peut être marquée terminée avant que la transcription du dernier chunk (qui prend du temps, appel à Azure Speech) soit revenue, ou l'inverse. C'est une "course" (*race condition*) connue et déjà rencontrée pour les résumés en Phase 2. La solution n'est pas d'éliminer la course (impossible ici, ce sont deux processus asynchrones indépendants), mais de la rendre **sans danger** : les deux méthodes appellent la même logique idempotente, donc peu importe laquelle arrive en premier ou si les deux arrivent presque en même temps, le résultat final est le même (une seule indexation, jamais deux).

`@Async` (Spring) veut dire : cette méthode s'exécute sur un thread séparé, pas sur celui qui a publié l'événement — sinon, terminer une session bloquerait le navigateur de l'utilisateur pendant tout le temps de l'appel à Azure OpenAI + Azure AI Search.

### 6.4 — `IndexRecherche`, l'entité de garde

```java
@Entity @Table(name = "index_recherche")
public class IndexRecherche {
    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;
    private int nombreSegments;
    private StatutIndexRecherche statut; // REUSSI ou ECHEC
    private Instant dateCreation;
}
```
Une ligne par session, jamais plus (contrainte `unique = true` sur `sessionId`, appliquée par Hibernate à la création de la table). Point important à comprendre : **cette table ne contient aucun vecteur, aucun texte de segment** — le vrai contenu cherchable vit entièrement dans Azure AI Search. `IndexRecherche` n'est qu'un **marqueur** ("cette session a déjà été traitée, REUSSI ou ECHEC") pour la garde d'idempotence de la section précédente. C'est une différence importante avec `Resume` ou `CompteRendu` (qui, eux, stockent le contenu généré directement en base) : ici, PostgreSQL et Azure AI Search ont chacun un rôle différent et ne dupliquent jamais la même donnée.

### 6.5 — `RechercheController`

```java
@GetMapping
public List<RechercheResultatResponse> rechercher(@RequestParam("q") String requete, @RequestParam(name = "limite", defaultValue = "10") int limite) {
    if (requete == null || requete.isBlank()) return List.of();
    return rechercheService.rechercher(requete, limite).stream().map(RechercheResultatResponse::depuis).toList();
}

@PostMapping("/reindexation")
public ResponseEntity<Void> reindexerHistorique() {
    rechercheService.reindexerHistorique();
    return ResponseEntity.accepted().build();
}
```
Remarque d'architecture : `RechercheController` est mappé sur `/api/v1/recherche`, **pas** sur `/api/v1/sessions/{sessionId}/...` comme tous les contrôleurs précédents (`ResumeController`, `CompteRenduController`...). C'est volontaire et reflète directement le besoin métier : la recherche porte sur *tout l'historique*, elle n'appartient à aucune session en particulier.

Le `POST /reindexation` renvoie `202 Accepted` (pas `200 OK`) — un code HTTP qui signifie "j'ai bien reçu la demande, le travail se fait en arrière-plan, je ne te dis pas quand il finira". C'est cohérent avec le fait que `reindexerHistorique()` est elle-même `@Async` côté service : le navigateur n'attend pas que toutes les sessions soient réindexées pour recevoir une réponse.

### 6.6 — `application.properties`, les nouvelles clés

```properties
azure.openai.embedding.deployment=${AZURE_OPENAI_EMBEDDING_DEPLOYMENT:}
azure.search.endpoint=${AZURE_SEARCH_ENDPOINT:}
azure.search.key=${AZURE_SEARCH_KEY:}
azure.search.index=memoria-segments
```
Le `:` après le nom de la variable d'environnement (`${AZURE_SEARCH_ENDPOINT:}`) donne une valeur par défaut vide si la variable n'existe pas — sans ça, Spring refuserait de démarrer du tout en l'absence de la variable. C'est ce qui permet à l'application de démarrer même sans ces clés (avec un simple `WARN` dans les logs, voir le constructeur de `RechercheAzureAiSearch`), plutôt que de planter au démarrage.

### 6.7 — Les fichiers "petits" qu'on n'a pas encore détaillés

Trois catégories de fichiers restent, volontairement mises à part parce qu'elles suivent chacune un patron mécanique déjà vu ailleurs dans le projet — mais chacune mérite une explication précise, pas juste "c'est trivial".

**`IndexRechercheRepository`** :
```java
public interface IndexRechercheRepository extends JpaRepository<IndexRecherche, UUID> {
    Optional<IndexRecherche> findBySessionId(UUID sessionId);
}
```
Une interface, sans aucune implémentation écrite nulle part dans le code — et pourtant elle fonctionne. C'est **Spring Data JPA** qui génère automatiquement le code réel derrière cette interface, au démarrage de l'application, à partir du nom de la méthode. `findBySessionId(UUID sessionId)` est décodé littéralement : "trouve par le champ `sessionId`" — Spring construit tout seul la requête SQL équivalente (`SELECT * FROM index_recherche WHERE session_id = ?`). `extends JpaRepository<IndexRecherche, UUID>` donne aussi gratuitement des méthodes déjà prêtes qu'on n'a pas eu besoin de nommer : `save(...)`, `findById(...)`, `findAll()`, etc. — c'est pour ça qu'on ne voit jamais de `save()` "réécrit à la main" nulle part dans ce projet.

**`GenerationEmbeddingException` et `RechercheException`** :
```java
public class RechercheException extends RuntimeException {
    public RechercheException(String message) { super(message); }
    public RechercheException(String message, Throwable cause) { super(message, cause); }
}
```
Deux classes qui ne font quasiment rien de plus qu'une `RuntimeException` standard — et c'est précisément le but. Pourquoi ne pas simplement lancer `new RuntimeException("...")` directement dans `RechercheAzureAiSearch` ? Parce qu'une exception **typée** (une classe à part) permet, ailleurs dans le code (ou plus tard, si besoin), d'écrire `catch (RechercheException e)` pour réagir *seulement* aux erreurs venant d'Azure AI Search, sans intercepter par erreur une tout autre exception qui n'a rien à voir. Dans `RechercheService.indexerSiPossible()`, le `catch (Exception e)` générique attrape en fait aussi bien `RechercheException` que `GenerationEmbeddingException` — les deux existent surtout pour que le **message d'erreur** soit clair dans les logs (`"Azure AI Search a repondu avec le statut ..."` plutôt qu'une exception anonyme), et pour documenter, rien qu'en lisant la signature d'une méthode, quel genre d'échec elle peut produire.

**`RechercheResultatResponse`** :
```java
public record RechercheResultatResponse(UUID sessionId, String titreSession, Instant dateSession, String texte, ...) {
    public static RechercheResultatResponse depuis(ResultatRecherche resultat) { ... }
}
```
C'est le DTO ("Data Transfer Object") exposé par `RechercheController` — la règle du projet, déjà expliquée en Phase 2 (diarization), est de **ne jamais renvoyer un objet interne directement en JSON**, toujours passer par un DTO dédié à l'API. Ici, il se trouve que `RechercheResultatResponse` a exactement les mêmes champs que `ResultatRecherche` (le record interne utilisé entre `RechercheService` et `RecherchePort`) — ce qui peut sembler être une duplication inutile. Ce n'en est pas une : `ResultatRecherche` appartient à la couche "domaine/port" (elle pourrait exister même si l'API HTTP n'existait pas), alors que `RechercheResultatResponse` appartient à la couche "web" (son seul rôle est d'être sérialisée en JSON). Si un jour l'un des deux doit changer sans l'autre (par exemple, cacher le score de pertinence dans l'API publique tout en le gardant en interne pour du débogage), les deux classes séparées permettent ce changement sans rien casser ailleurs.

---

## 7. Les tests unitaires, expliqués

Fichier : `backend/src/test/java/com/memoria/core/recherche/RechercheServiceTest.java` (8 tests). Si tu ne connais pas encore Mockito : c'est une bibliothèque qui permet de créer de "faux" objets (`@Mock`) qui imitent le comportement d'une vraie classe sans l'exécuter réellement — utile ici pour tester `RechercheService` sans jamais appeler le vrai Azure OpenAI ou Azure AI Search (ce serait lent, coûterait de l'argent à chaque lancement des tests, et échouerait sans connexion internet).

- **`@Mock`** déclare un faux objet (ex. `@Mock GenerateurEmbeddingPort generateurEmbedding`).
- **`when(...).thenReturn(...)`** programme ce que le faux objet doit répondre à un appel précis (ex. "quand on appelle `genererEmbeddings(List.of("Bonjour."))`, réponds avec ce vecteur précis").
- **`verify(...)`** vérifie, après coup, qu'une méthode a bien (ou n'a pas) été appelée — utile pour prouver qu'un appel coûteux (comme `genererEmbeddings`) n'a **pas** eu lieu quand il ne devrait pas.

Les 8 tests, et ce que chacun prouve :
1. `surSessionTerminee_indexe_les_segments_des_transcriptions_reussies` — le cas nominal : deux segments valides sont bien transformés en `SegmentARecherche`, les bons textes sont envoyés à l'embedding, et un `IndexRecherche` avec statut `REUSSI` est sauvegardé.
2. `surSessionTerminee_ne_fait_rien_si_aucune_transcription_na_reussi` — si toutes les transcriptions ont échoué, on ne doit **jamais** appeler `genererEmbeddings` (ça coûterait de l'argent pour rien) ni sauvegarder quoi que ce soit.
3. `surSessionTerminee_ne_fait_rien_si_les_transcriptions_reussies_nont_aucun_segment_exploitable` — un texte de segment vide ou avec juste des espaces (`"  "`) ne doit produire aucun appel à Azure — et surtout, on vérifie qu'on ne va **même pas chercher la session** (`sessionService.obtenirSession`) dans ce cas, pour prouver qu'on sort tôt, avant tout travail inutile.
4. `surSessionTerminee_marque_echec_quand_lembedding_echoue` — si Azure OpenAI lève une exception, la session doit être marquée `ECHEC` (pas laissée sans trace, pas de crash de l'application), et `indexerSegments` (Azure AI Search) ne doit **jamais** être appelé après un échec d'embedding.
5. `surToutesTranscriptionsTerminees_indexe_si_rien_nexiste_encore` — le deuxième déclencheur (section 6.3) fonctionne aussi tout seul.
6. `indexerSiPossible_ne_fait_rien_si_la_session_est_deja_indexee` — la garde d'idempotence : si un `IndexRecherche` existe déjà, ni `surSessionTerminee` ni `surToutesTranscriptionsTerminees` ne doivent aller chercher les transcriptions ou appeler l'embedding — la "course" entre les deux événements (section 6.3) ne doit jamais causer une double indexation.
7. `reindexerHistorique_indexe_les_sessions_terminees_pas_encore_indexees_et_ignore_les_autres` — construit trois sessions (une terminée sans index existant, une terminée mais sans transcription réussie, une encore en cours) et vérifie que seule la première déclenche vraiment une indexation, et que la session "en cours" n'est même pas interrogée (`verify(transcriptionRepository, never())`).
8. `rechercher_embed_la_requete_et_delegue_au_port_de_recherche` — la question tapée par l'utilisateur est bien vectorisée, puis le texte brut *et* le vecteur sont bien transmis ensemble au port de recherche (nécessaire depuis l'ajout de la recherche hybride, section 5.3).

Pour les lancer : `cd backend && mvn test` (comme toujours sur ce projet — aucune configuration spécifique à ce module).

---

## 8. Le frontend, et les bases de React utilisées ici

Fichier : `frontend/src/pages/RecherchePage.tsx` (nouveau composant). Si React est encore nouveau pour toi, voici les notions utilisées dans ce fichier précis, expliquées à partir du code réel :

### 8.1 — `useState` : la mémoire d'un composant

```tsx
const [requete, setRequete] = useState('')
const [resultats, setResultats] = useState<RechercheResultat[]>([])
const [chargement, setChargement] = useState(false)
```
`useState('')` crée une "case mémoire" qui vaut au départ une chaîne vide, et renvoie deux choses : la valeur actuelle (`requete`) et une fonction pour la changer (`setRequete`). **Point essentiel à comprendre** : appeler `setRequete(...)` ne modifie pas juste une variable — ça dit à React "réaffiche ce composant, quelque chose a changé". C'est ce mécanisme qui fait que taper dans le champ de recherche met à jour l'écran en temps réel. `RecherchePage` a 5 de ces "cases mémoire" (`requete`, `resultats`, `chargement`, `aRecherche`, `reindexationLancee`) — chacune correspond à un bout d'état que l'interface doit retenir entre deux affichages.

### 8.2 — Un champ de formulaire "contrôlé"

```tsx
<input
  type="text"
  value={requete}
  onChange={(e) => setRequete(e.target.value)}
/>
```
C'est ce qu'on appelle un "composant contrôlé" : la valeur affichée dans le champ (`value={requete}`) vient **entièrement** de l'état React, jamais du navigateur tout seul. Chaque frappe au clavier déclenche `onChange`, qui met à jour `requete` via `setRequete`, ce qui fait réafficher le champ avec la nouvelle valeur. Ça semble faire un détour inutile (pourquoi ne pas laisser le champ HTML gérer sa propre valeur ?), mais ça permet à React de toujours savoir "que contient ce champ en ce moment", utilisable ailleurs dans le composant (ici, dans `lancerRecherche`).

### 8.3 — Gérer la soumission d'un formulaire, en async

```tsx
async function lancerRecherche(e: React.FormEvent) {
  e.preventDefault()
  if (!requete.trim()) return
  setChargement(true)
  setARecherche(true)
  try {
    setResultats(await rechercher(requete.trim()))
  } finally {
    setChargement(false)
  }
}
```
`e.preventDefault()` empêche le comportement par défaut du navigateur (recharger toute la page à la soumission d'un `<form>`) — sans ça, chaque recherche rechargerait l'application entière. `await rechercher(...)` attend la réponse du backend (un appel réseau, donc pas instantané) — pendant ce temps, `chargement` reste `true`, ce qui désactive le bouton (`disabled={chargement}`) et change son texte ("Recherche..."). Le `finally` garantit que `chargement` repasse à `false` **même si la recherche a échoué** (erreur réseau) — sinon le bouton resterait bloqué indéfiniment.

### 8.4 — Affichage conditionnel

```tsx
{aRecherche && !chargement && resultats.length === 0 && (
  <p className="mb-4 text-sm text-slate-500">Aucun resultat.</p>
)}
```
En JSX (la syntaxe qui mélange JavaScript et HTML utilisée par React), `condition && <element>` est une façon courante d'afficher quelque chose *seulement si* la condition est vraie — si `condition` vaut `false`, React n'affiche rien du tout à cet endroit. Ici, le message "Aucun résultat" ne s'affiche que si une recherche a déjà été lancée (`aRecherche`), qu'elle n'est plus en cours (`!chargement`), et qu'elle n'a rien trouvé (`resultats.length === 0`) — trois conditions combinées avec `&&`, qui doivent toutes être vraies.

### 8.5 — Afficher une liste (`.map`)

```tsx
{resultats.map((resultat, index) => (
  <li key={index} className="...">
    ...
  </li>
))}
```
`.map()` transforme chaque élément du tableau `resultats` en un morceau de JSX — c'est la façon standard d'afficher une liste en React (pas de boucle `for` dans le JSX). Le `key={index}` est une exigence de React : chaque élément d'une liste affichée doit avoir un identifiant unique, pour que React sache lequel a changé si la liste se met à jour (ici, `index` suffit parce que la liste entière est remplacée à chaque nouvelle recherche, jamais modifiée élément par élément).

### 8.6 — Le lien de "drill-down" (retour vers la session source)

```tsx
<Link to={`/sessions/${resultat.sessionId}`} className="text-sm font-medium text-slate-900 hover:underline">
  {resultat.titreSession}
</Link>
```
`<Link>` (de `react-router-dom`) est l'équivalent React d'un `<a href="...">`, mais qui change de page **sans recharger toute l'application** (contrairement à un lien HTML classique). Cliquer sur le titre d'une session dans un résultat de recherche amène directement sur `SessionDetailPage`, où on peut voir la transcription complète — c'est la traçabilité exigée par `CLAUDE.md` (*"l'utilisateur peut toujours redescendre du résumé vers la transcription puis l'audio"*) : ici, on descend du résultat de recherche vers la session complète.

### 8.7 — `api.ts`, le seul endroit qui parle au backend

```typescript
export async function rechercher(requete: string, limite = 10): Promise<RechercheResultat[]> {
  const parametres = new URLSearchParams({ q: requete, limite: String(limite) })
  const reponse = await verifierReponse(await fetch(`/api/v1/recherche?${parametres}`))
  return reponse.json()
}

export async function reindexerHistorique(): Promise<void> {
  await verifierReponse(await fetch('/api/v1/recherche/reindexation', { method: 'POST' }))
}
```
Comme pour toutes les autres fonctionnalités du projet, `RecherchePage.tsx` n'appelle jamais `fetch` directement — tout passe par `api.ts`, qui centralise la construction des URLs et la vérification des réponses (`verifierReponse`, définie une seule fois en haut du fichier, réutilisée partout). `URLSearchParams` construit proprement une chaîne de requête (`?q=...&limite=...`) en gérant automatiquement l'encodage des caractères spéciaux (espaces, accents, points d'interrogation dans la question elle-même) — écrire ça à la main avec de la concaténation de chaînes serait source d'erreurs (une question contenant un `&` casserait l'URL, par exemple).

Rappel important (déjà noté en Phase 2, section diarization) : **`RechercheResultat` (TypeScript) et `RechercheResultatResponse` (Java) sont deux déclarations séparées**, à synchroniser à la main. Aucun outil ne vérifie automatiquement qu'elles correspondent — seul un test manuel (ou une réponse qui ne "matche" pas silencieusement, avec des champs `undefined`) le révélerait.

---

## 9. La réindexation de l'historique

### 9.1 — Pourquoi c'était nécessaire

Cette fonctionnalité n'était pas prévue dans la conception initiale — elle est apparue en testant en conditions réelles : toutes les sessions créées *avant* l'existence de cette brique n'ont jamais déclenché `surSessionTerminee`/`surToutesTranscriptionsTerminees` (ces événements ont été publiés en leur temps, avant que `RechercheService` existe pour les écouter) — elles ne seraient donc **jamais** cherchables sans un mécanisme de rattrapage.

### 9.2 — Comment ça marche

`RechercheService.reindexerHistorique()` (section 6.3) réutilise **exactement** la même méthode privée `indexerSiPossible()` que les déclencheurs automatiques — aucune nouvelle logique d'indexation écrite, juste une nouvelle façon de la déclencher : parcourir *toutes* les sessions `TERMINEE` et appeler `indexerSiPossible` sur chacune. La garde d'idempotence (section 6.3, étape 1) fait le travail de filtrage tout seule : les sessions déjà indexées sont ignorées instantanément, donc relancer cette méthode plusieurs fois (par exemple après une coupure) ne fait jamais de travail en double ni ne consomme de quota Azure inutilement.

### 9.3 — Limite découverte en testant sur les vraies données

Sur les sessions réelles du projet (52 sessions terminées), seules 11 avaient réellement du contenu indexable — les 27 autres candidates (qui avaient bien des transcriptions réussies) avaient des transcriptions **sans aucun `SegmentLocuteur`** (des sessions créées avant la Phase 2, avant que la diarization existe — elles n'ont qu'un champ `texte` brut, jamais découpé par locuteur). Comme l'indexation se fait au niveau segment (section 3.1), une session sans segment ne produit aucun document à indexer, et `indexerSiPossible` s'arrête tôt sans même créer de ligne `IndexRecherche` (ni `REUSSI` ni `ECHEC` — juste "rien à faire"). **Décision (validée avec l'utilisateur)** : accepter que ces très anciennes sessions (surtout des sessions de test avec des titres courts type "nn", "bb") restent non cherchables, plutôt que d'ajouter un repli d'indexation au niveau transcription entière — inutile pour de vraies données puisque toute nouvelle session a des segments par construction.

### 9.4 — Le bouton dans l'interface

Un lien texte "Réindexer l'historique", volontairement affiché **en permanence** sous la barre de recherche (pas conditionné à "aucun résultat trouvé") — voir section 5.3 pour comprendre pourquoi : une recherche vectorielle KNN renvoie presque toujours des résultats, même mauvais, donc un bouton qui n'apparaîtrait que si `resultats.length === 0` ne se serait quasiment jamais affiché en pratique. C'est une correction de design faite après un premier essai raté (vérifié avec Playwright, voir section 10).

---

## 10. Comment on a vérifié que ça marchait vraiment

Toujours la même règle du projet : ne jamais déclarer un succès sans l'avoir observé en conditions réelles, avec de vraies données.

**Étape 1 — vérification API brute, avant tout code Java.** Avant d'écrire `RechercheAzureAiSearch`, on a testé à la main avec `curl` : créer un index de test, y indexer un document avec un vrai vecteur (obtenu via un vrai appel à Azure OpenAI), puis interroger cet index avec une question reformulée différemment du texte source ("à quelle date le produit a-t-il été repoussé ?" pour retrouver "nous avons décidé de reporter le lancement du produit"). Résultat : le bon document est ressorti en premier (score 0.855) — ça a validé le concept avant d'écrire la moindre ligne de Java.

**Étape 2 — test end-to-end avec des données insérées directement en base.** Plutôt que de re-tester tout le pipeline de transcription (déjà validé en Phase 2), on a créé une session via l'API, puis inséré directement en PostgreSQL une transcription réaliste avec ses segments (`INSERT INTO transcriptions...`, `INSERT INTO transcription_segments_locuteur...`), avant d'appeler `POST /sessions/{id}/terminer` pour déclencher l'indexation réelle (vrais appels à Azure OpenAI et Azure AI Search). Une recherche avec une question reformulée a bien retrouvé le bon passage en premier — preuve que le pipeline complet (événement → service → deux ports → deux vraies ressources Azure) fonctionne, sans avoir eu besoin de ré-enregistrer un vrai micro.

**Étape 3 — vérification de l'interface avec Playwright.** Script qui ouvre `/recherche`, tape une question, clique sur "Rechercher", et vérifie que le bon texte apparaît dans le HTML final, sans erreur dans la console du navigateur. Complété par une capture d'écran, inspectée visuellement (résultats classés par pertinence, lien vers la session cliquable et fonctionnel — testé en cliquant réellement dessus et en vérifiant l'URL obtenue).

**Étape 4 — test sur une vraie session existante du projet**, pas une donnée fabriquée pour l'occasion : la session "vv" (transcription d'une histoire dramatique, utilisée par ailleurs comme cas de test pour d'autres fonctionnalités du projet) a été réindexée manuellement (avant que le bouton de réindexation existe), et une question sur le contenu ("comment le couple a-t-il trouvé sa fortune ?") a bien retrouvé le passage sur le trésor découvert dans la grotte — alors que ce mot ("fortune") n'apparaît nulle part tel quel dans la transcription réelle (qui dit "trillionnaires").

**Étape 5 — vérification de la réindexation en masse**, en conditions réelles sur toute la base de données existante (pas des données de test) : déclenchée via `POST /api/v1/recherche/reindexation`, suivie en interrogeant directement PostgreSQL (`SELECT statut, count(*) FROM index_recherche GROUP BY statut`) jusqu'à stabilisation du compteur — ce qui a permis de découvrir la limite de la section 9.3 (27 sessions sans segments, jamais indexées, par conception).

---

## 11. Limites connues, assumées, pas corrigées ici

- **La recherche KNN renvoie toujours des résultats**, même hors-sujet (section 2.2) — améliorée par la recherche hybride (section 5.3), mais pas résolue à la racine. Un vrai seuil de pertinence fiable demanderait soit le reranking sémantique payant d'Azure AI Search, soit un modèle d'embeddings plus récent (`text-embedding-3-large`, qui a du quota disponible sur ce compte mais nécessiterait de réindexer tout le contenu existant avec de nouveaux vecteurs de dimension différente : 3072 au lieu de 1536).
- **27 sessions historiques (d'avant la diarization) ne sont pas cherchables**, faute de segments (section 9.3) — décision assumée, ce cas ne se reproduira plus pour les nouvelles sessions.
- **Pas de suivi de progression de la réindexation en masse** — `POST /reindexation` renvoie `202` immédiatement, sans moyen de savoir depuis l'interface si le travail est fini ou combien de sessions restent à traiter. Pour vérifier manuellement, il faut interroger PostgreSQL directement (`SELECT count(*) FROM index_recherche`) ou attendre et retenter une recherche.
- **La capacité Azure OpenAI pour les embeddings (10K tokens/minute) est un choix pragmatique**, pas dimensionné pour un usage massif multi-utilisateurs — à revoir si le nombre de sessions traitées simultanément augmente significativement (Phase 4, multi-tenant).

---

## 12. Pour reprendre seul

- Le code de référence exact de cette étape : `git checkout phase-3-recherche-semantique`
- Si tu dois recréer les ressources Azure de zéro, suis la section 4 dans l'ordre — en particulier, utilise le **Cloud Shell** plutôt que `az login` en local, ça évite toute la galère de la section 4.2.
- Si tu modifies un jour la définition des champs de l'index (`RechercheAzureAiSearch.assurerIndexExiste()`), il faudra supprimer l'index existant à la main (`DELETE /indexes/memoria-segments`) avant de relancer l'application — sinon Azure refusera la mise à jour (section 5.1). Pense à réindexer ensuite (`POST /api/v1/recherche/reindexation`).
- Pour changer de modèle d'embeddings, il faut : (1) créer un nouveau déploiement Azure OpenAI (section 4), (2) mettre à jour `AZURE_OPENAI_EMBEDDING_DEPLOYMENT`, (3) mettre à jour `DIMENSIONS_VECTEUR` dans `RechercheAzureAiSearch.java` si la nouvelle dimension diffère de 1536, (4) supprimer et laisser recréer l'index (les vecteurs existants ont une dimension figée, incompatible avec un nouveau modèle), (5) relancer une réindexation complète.
- L'ordre des couches, du clic à l'affichage, reste le même principe que dans les phases précédentes : navigateur → `RecherchePage.tsx` → `api.ts` (`rechercher`) → `RechercheController` → `RechercheService` → `GenerateurEmbeddingPort` + `RecherchePort` → Azure OpenAI + Azure AI Search, puis retour en DTO dans l'autre sens.
