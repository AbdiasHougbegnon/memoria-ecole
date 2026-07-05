# Phase 2 : le QR code multi-appareils — comment on l'a construit

**Pour revenir exactement à cet état du code :**
```
git checkout phase-2-qr-multi-device
```
Ce tag pointe sur le commit `859696d`, au moment précis où la fonctionnalité a été vérifiée avec un vrai téléphone.

Ce document explique en détail *comment* on a construit cette fonctionnalité, et surtout *pourquoi* elle a demandé beaucoup plus de mise au point réseau que de code — pour que tu puisses reprendre seul, et surtout pour que tu comprennes les pièges (navigateur, Android, Windows, Azure) qu'on a rencontrés, parce qu'ils reviendront probablement le jour où tu voudras déployer ou retester ça ailleurs.

---

## 1. Le besoin

`memoria-master-prompt.md` (section engine, capture visuelle légère) dit exactement :

> L'utilisateur envoie des photos ponctuelles du tableau (liées à la session active, par ex. via QR code multi-appareils) ou uploade des documents/slides PDF exploités par l'IA.

Le problème concret : une session tourne sur un ordinateur portable (posé sur la table, qui enregistre l'audio). Prendre en photo un tableau avec cet ordinateur est peu pratique — un téléphone est bien mieux placé pour ça. Mais le téléphone ne sait pas, tout seul, à quelle session rattacher la photo, et on ne veut pas lui faire naviguer/chercher la session à la main sur un petit écran.

La réponse : le PC affiche un QR code. Le téléphone le scanne, arrive directement sur une page qui sait déjà à quelle session il appartient, prend la photo, et c'est envoyé.

---

## 2. La décision de conception la plus importante : ne rien ajouter au backend

Avant d'écrire une ligne de code, la question était : est-ce qu'on a besoin d'un nouvel endpoint pour "générer un lien de session" ou "authentifier un appareil mobile" ?

Réponse : non. On a déjà, depuis la brique précédente (`phase-2-pdf-photos`), un endpoint `POST /sessions/{id}/documents` qui accepte n'importe quel upload de photo. Le seul vrai problème à résoudre est **l'aiguillage** : comment le téléphone arrive-t-il sur la bonne session sans taper l'UUID à la main ? Un QR code n'est qu'un raccourci visuel vers une URL — il n'a pas besoin d'un mécanisme serveur dédié.

Donc : le QR code encode simplement `http://<adresse-du-PC>/mobile/sessions/<id>`, généré et affiché **entièrement côté navigateur**, avec la librairie `qrcode` (aucun appel réseau pour le générer). Et cette URL pointe vers une nouvelle route front-end minimaliste, qui réutilise l'upload déjà existant. Zéro nouveau code Java pour cette fonctionnalité — tout le travail est côté `frontend/`.

C'est le principe du moteur partagé (`CLAUDE.md`) appliqué au niveau le plus simple : ne pas dupliquer un mécanisme qui existe déjà.

---

## 3. Les fichiers, un par un

Tous dans `frontend/`.

### `vite.config.ts` *(modifié)*

```ts
server: {
  host: true,
  proxy: { '/api': 'http://localhost:8080' },
},
```
Par défaut, le serveur de développement Vite n'écoute que sur `localhost` — injoignable depuis un autre appareil du réseau. `host: true` le fait écouter sur toutes les interfaces réseau de la machine (`0.0.0.0`), donc aussi sur son IP Wi-Fi. Le `proxy` vers le backend n'a pas besoin de changer : ce transfert `/api` → `localhost:8080` se fait *à l'intérieur* du process Vite, sur la même machine — peu importe par quelle IP le client (le téléphone) a atteint Vite en premier lieu.

### `App.tsx` *(modifié)*

```tsx
<Route path="/mobile/sessions/:id" element={<MobileUploadPage />} />
```
Une route de plus, au même niveau que `/sessions/:id`. Rien de spécial — c'est une page React normale.

### `MobileUploadPage.tsx` *(nouveau)*

La page que le téléphone affiche après avoir scanné. Volontairement minimaliste : pas de menu, pas de retour, pas de transcription ni de résumé — un titre de session et un seul bouton.

```tsx
useEffect(() => {
  if (!id) return
  obtenirSession(id).then(setSession).catch(() => setSessionIntrouvable(true))
  obtenirDocuments(id).then(setDocuments).catch(() => {})
}, [id])
```
Au chargement, on vérifie juste que la session existe (`obtenirSession`) — c'est la seule "authentification" qu'il y a. On ne demande pas de mot de passe : l'UUID de session sert déjà d'identifiant impossible à deviner, et l'authentification complète (Entra ID) est explicitement repoussée à la Phase 4 par `CLAUDE.md`. Pour une utilisation en réunion/classe où le lien QR n'est visible que quelques minutes sur un écran physique, c'est un compromis raisonnable — à revoir si le produit expose un jour ces sessions publiquement sur internet.

```tsx
<input
  type="file"
  accept="image/*"
  capture="environment"
  className="hidden"
  onChange={(e) => {
    void surPhotoCapturee(e.target.files?.[0])
    e.target.value = ''
  }}
/>
```
Le détail qui fait tout le travail ici, c'est l'attribut HTML **`capture="environment"`**. Sur un navigateur mobile, ça dit : "ouvre directement l'appareil photo arrière", au lieu de proposer la galerie de fichiers comme sur desktop. C'est une simple balise HTML standard — **pas** de `navigator.mediaDevices.getUserMedia()`, pas de flux vidéo en direct à gérer en JavaScript. Ça a son importance pour la section 5 plus bas.

`e.target.value = ''` après chaque envoi réinitialise le champ, pour pouvoir reprendre une photo juste après sans recharger la page — la liste de documents s'accumule dans `documents` (React state), elle n'est jamais écrasée.

### `SessionDetailPage.tsx` — la section QR *(modifiée, lignes 59-67 et 148-170 et 297-322)*

Deux morceaux à comprendre ensemble.

**a) Un champ IP séparé de l'origine de la page** (lignes 61-67) :
```tsx
// Le microphone n'est accessible que sur localhost ou en HTTPS (contexte
// securise impose par les navigateurs) : on reste donc sur localhost pour
// enregistrer, et on ne demande l'IP reseau du PC que pour construire le
// lien du QR code, sans changer l'origine de la page elle-meme.
const [adresseReseau, setAdresseReseau] = useState(() =>
  /^(localhost|127\.0\.0\.1)$/.test(window.location.hostname) ? '' : window.location.host,
)
```
La raison de ce détour est expliquée en détail section 5 — en résumé, la page principale (celle qui enregistre l'audio) doit rester sur `localhost` pour que le micro fonctionne, mais le QR, lui, doit pointer vers une adresse que le téléphone peut atteindre (une IP réseau). On ne peut pas utiliser `window.location.origin` pour les deux à la fois, donc on demande l'IP séparément, dans un simple champ texte.

**b) La génération, qui se met à jour en direct** (lignes 148-170) :
```tsx
async function regenererQrCode() {
  const hote = adresseReseau.trim() || window.location.host
  const url = `http://${hote}/mobile/sessions/${id}`
  setQrCodeUrl(url)
  setQrCodeImage(await QRCode.toDataURL(url, { width: 220, margin: 1 }))
}

useEffect(() => {
  if (qrCodeImage) void regenererQrCode()
}, [adresseReseau])
```
Le `useEffect` régénère l'image dès qu'on modifie le champ IP, si le QR est déjà affiché — utile en pratique parce que ton adresse réseau a changé plusieurs fois pendant les tests (changement de Wi-Fi, puis point d'accès mobile).

### `redimensionnerImage.ts` *(nouveau)*

```ts
const TAILLE_MAX_OCTETS = 3_500_000
const DIMENSION_MAX_PIXELS = 2000
const QUALITE_JPEG = 0.8

export async function redimensionnerImageSiNecessaire(fichier: File): Promise<File> {
  if (!fichier.type.startsWith('image/') || fichier.size <= TAILLE_MAX_OCTETS) {
    return fichier
  }
  const bitmap = await createImageBitmap(fichier)
  // ratio pour que le plus grand cote fasse <= 2000px, canvas invisible,
  // recompression en JPEG qualite 80%
  ...
}
```
Appelée juste avant l'upload, aussi bien dans `MobileUploadPage.tsx` que dans `SessionDetailPage.tsx` (le bouton desktop d'ajout de document). Le "pourquoi" de cette fonction est le cœur de la section 5.4 plus bas — retenir pour l'instant qu'elle tourne **entièrement dans le navigateur** via un `<canvas>` jamais affiché à l'écran : décoder l'image, la redessiner à une résolution réduite, la recompresser, en ressortir un nouveau `File`.

### `application.properties` *(backend, modifié)*

```properties
spring.servlet.multipart.max-file-size=25MB
spring.servlet.multipart.max-request-size=25MB
```
Sans ça, Spring Boot refuse (par défaut) tout upload de plus de 1 Mo — bien en dessous d'une vraie photo de téléphone non compressée. Ce n'est pas la vraie limite qui compte au final (voir 5.4), mais c'est la première qu'on a rencontrée, et il fallait la lever pour voir la suivante.

---

## 4. Il n'y a pas de "nouveau contrat API"

Contrairement aux fonctionnalités précédentes de la Phase 2, il n'y a ici ni nouvel endpoint, ni nouveau format de réponse à documenter — le QR réutilise `POST /sessions/{id}/documents` et `GET /sessions/{id}/documents`, déjà documentés dans le travail sur l'ingestion PDF/photos. La seule vraie nouveauté côté "contrat", c'est l'URL de la route front-end : `/mobile/sessions/:id`, qui n'appelle jamais le backend directement, seulement via les fonctions `api.ts` déjà existantes.

---

## 5. Le vrai du vrai : quatre problèmes réseau, dans l'ordre où on les a rencontrés

C'est la partie la plus utile de ce document. Chaque étape de la vérification en conditions réelles a révélé un problème différent, et aucun n'était un bug de code — chacun est une contrainte d'environnement qu'il faut connaître.

### 5.1 — `getUserMedia` (le micro) exige un "contexte sécurisé"

Premier réflexe (naïf) : faire pointer le PC directement sur son IP réseau (`http://192.168.x.x:5173`) pour que `window.location.origin` serve à la fois à enregistrer l'audio *et* à construire le QR. Résultat : **le micro a cessé de fonctionner**, avec une erreur "impossible d'accéder au microphone".

La cause : les navigateurs n'autorisent `navigator.mediaDevices.getUserMedia()` (utilisé par le composant `Recorder` pour l'audio) que dans un **contexte sécurisé** — HTTPS, ou l'exception spéciale `localhost`/`127.0.0.1`. Une IP LAN brute en HTTP (`http://192.168.x.x`) n'est ni l'un ni l'autre, donc bloquée, silencieusement pour l'utilisateur (juste un refus d'accès).

**Solution retenue** : la page principale reste sur `localhost` (le micro marche), et seul le QR code encode une IP différente — via le champ texte de la section 3b. Alternative qu'on n'a pas prise : configurer un certificat HTTPS auto-signé pour le serveur de dev (`@vitejs/plugin-basic-ssl` ou équivalent), ce qui aurait résolu le problème à la racine mais ajoute de la friction (avertissement de certificat non fiable à accepter sur chaque appareil). En production, ce problème disparaît de lui-même : le déploiement dédié par client (`CLAUDE.md`, domaine propre type `memoria.episen.fr`) sera en HTTPS, donc n'importe quel nom d'hôte fonctionnera pour le micro.

### 5.2 — Un téléphone ne peut (souvent) pas joindre son propre point d'accès

Deuxième essai : activer le partage de connexion sur le téléphone (il devient le point d'accès Wi-Fi), connecter le PC dessus, et scanner le QR avec ce même téléphone. Résultat : **la page ne se charge pas du tout** sur le téléphone, alors que le PC (branché sur ce même hotspot) atteignait bien le serveur.

La cause (connue, documentée comme limitation Android) : quand un téléphone Android héberge son propre point d'accès Wi-Fi, son navigateur utilise en général l'interface de données mobiles (4G/5G) pour son propre trafic, pas l'interface de tethering qu'il vient de créer pour les autres appareils. Autrement dit, le téléphone-hôte ne "voit" pas facilement les appareils connectés à son propre hotspot, y compris lui-même.

**Solution retenue** : inverser le sens. Le **PC** héberge le point d'accès (fonctionnalité native Windows *Point d'accès mobile*, Paramètres → Réseau et Internet), le téléphone s'y connecte comme client simple — c'est le sens normal et le mieux supporté, quel que soit le système d'exploitation du téléphone. L'IP du PC sur ce réseau est alors l'adresse ICS standard de Windows, `192.168.137.1`.

### 5.3 — Le pare-feu Windows bloque les connexions entrantes par défaut sur un réseau "Public"

Même en connectant le téléphone au point d'accès du PC, la page ne se chargeait toujours pas. Diagnostic (détaillé, avec les commandes PowerShell exactes utilisées, dans l'historique de la conversation) :
- `Get-NetConnectionProfile` : le réseau Wi-Fi est catégorisé **Public** par Windows.
- `Get-NetFirewallProfile` : le profil Public a `DefaultInboundAction = NotConfigured`, ce qui revient à **bloquer** tout le trafic entrant non explicitement autorisé.
- Une règle existante (`Node.js JavaScript Runtime`, créée automatiquement à un moment donné) autorisait déjà `node.exe` sur le profil Public — mais ça ne suffisait visiblement pas à couvrir le cas précis (interface virtuelle du point d'accès mobile, catégorisation différente, etc. — pas élucidé dans le détail, mais la solution ci-dessous a réglé le symptôme).

**Solution retenue** : une règle de pare-feu entrante explicite, ciblée sur les deux ports utilisés en développement :
```powershell
New-NetFirewallRule -DisplayName "Memoria Dev (5173 8080)" -Direction Inbound -Protocol TCP -LocalPort 5173,8080 -Action Allow -Profile Any
```
Nécessite des droits administrateur (a été fait manuellement par toi, pas par l'agent, qui n'avait pas les privilèges nécessaires). C'est une règle de développement local — à ne pas reproduire telle quelle sur un serveur de production exposé sur internet (là, le vrai firewall applicatif/cloud doit être pensé au moment du déploiement, Phase 4).

### 5.4 — Azure Document Intelligence refuse les images trop lourdes

Une fois le réseau enfin débloqué, la photo arrivait bien jusqu'au backend (`201 Created`), mais le document finissait en statut `ECHEC` peu après. Le log backend montrait :
```
com.memoria.core.document.ExtractionDocumentException: Azure Document Intelligence a repondu avec le statut 400
```
Un appel direct à l'API Azure avec le même fichier a donné le vrai message :
```json
{"error":{"code":"InvalidRequest","message":"Invalid request.",
  "innererror":{"code":"InvalidContentLength","message":"The input image is too large. ..."}}}
```
Le niveau gratuit d'Azure Document Intelligence a une limite basse (de l'ordre de 4 Mo pour une image envoyée directement en corps de requête) — largement en dessous de ce que produit un vrai appareil photo de smartphone moderne (souvent 5 à 10 Mo pour une photo haute résolution).

**Solution retenue** : compresser l'image **avant** de l'envoyer (`redimensionnerImage.ts`, section 3). Testé avec un fichier de test de 9,5 Mo : après passage dans la fonction, 1,77 Mo, extraction Azure réussie (`statut: REUSSI`). C'est aussi tout simplement une bonne pratique indépendamment de la limite Azure : envoyer une photo brute non compressée sur une connexion mobile est lent et gaspille de la bande passante pour un gain de qualité inutile (Azure fait de l'OCR, pas de la retouche photo).

---

## 6. Comment on a vérifié que ça marchait vraiment

Deux niveaux de vérification, comme d'habitude sur ce projet — jamais se contenter du premier qui compile.

**Automatisé (Playwright), avant même de sortir un vrai téléphone :**
- Un onglet simule le PC (`/sessions/{id}`), clique sur le bouton QR, lit le texte de l'URL affichée sous le QR (pas besoin de décoder l'image QR elle-même, l'URL est aussi affichée en texte brut pour le debug).
- Un second onglet, avec un viewport mobile (390×844), navigue directement vers cette URL — ça simule fidèlement ce qu'un scan ferait, sans dépendre d'un vrai lecteur QR.
- Upload d'une photo via `setInputFiles` sur l'input `capture="environment"` (Playwright peut remplir ce type de champ même s'il déclenche normalement l'appareil photo natif).
- Vérifié : le titre de session s'affiche correctement sur "mobile", la confirmation "Photo envoyée" apparaît, et en rechargeant l'onglet "PC", le document envoyé depuis "mobile" est bien visible.
- Refait à l'identique avec un fichier de 9,5 Mo générée exprès (image bruitée haute résolution) pour valider la compression, avant de demander à l'utilisateur de retester avec son vrai téléphone.

**Réel, avec un vrai téléphone :** chaque étape de la section 5 a été découverte *parce que* le test réel échouait alors que la version automatisée fonctionnait déjà — la simulation Playwright valide le code, mais seul un vrai appareil, sur un vrai réseau, révèle les contraintes d'environnement (sécurité navigateur, comportement Android, pare-feu Windows, limites Azure). Retenir cette leçon : passer les deux niveaux de test n'est pas redondant, ils attrapent des classes de problèmes différentes.

---

## 7. Limites connues, assumées, pas corrigées ici

- **Pas d'authentification sur le lien mobile.** L'UUID de session fait office de secret implicite. Suffisant pour un usage en présentiel où le QR n'est affiché que quelques minutes, mais à revoir si ces liens doivent un jour être partageables plus largement ou survivre longtemps.
- **L'adresse IP doit être saisie à la main** dans le champ dédié, et redemandée à chaque changement de réseau (observé plusieurs fois pendant les tests : changement de Wi-Fi, puis point d'accès mobile). Rien n'auto-détecte l'IP réseau de la machine depuis le JavaScript du navigateur (ce n'est pas possible de façon fiable sans un aller-retour serveur, qu'on n'a pas voulu ajouter pour rester dans le principe "zéro nouveau code backend").
- **Toute cette mise au point réseau (localhost pour le micro, hotspot, pare-feu) est une contrainte de développement local.** En production, avec un vrai domaine HTTPS dédié par client (modèle de déploiement du projet), la plupart de ces problèmes n'existeront plus : une seule origine HTTPS pour tout le monde, atteignable aussi bien par le PC que par le téléphone, sans jonglage d'IP ni de point d'accès.

---

## 8. Pour reprendre seul

- Le code de référence exact de cette étape : `git checkout phase-2-qr-multi-device`
- Si le micro ou le QR se comportent bizarrement après un changement de machine/réseau, relire la section 5 dans l'ordre : c'est presque toujours l'un de ces quatre problèmes, jamais un cinquième nouveau.
- Si tu déploies un jour cette fonctionnalité en dehors du réseau local (vraie instance en ligne), la plupart du code de cette brique reste valable tel quel — seul le champ `adresseReseau` devient inutile (le QR peut alors réutiliser directement `window.location.origin`, puisque tout tournera sur un vrai domaine HTTPS).
- Pour ajuster la limite de compression des photos, tout se passe dans `redimensionnerImage.ts` (`TAILLE_MAX_OCTETS`, `DIMENSION_MAX_PIXELS`, `QUALITE_JPEG`) — aucun autre fichier à toucher.
