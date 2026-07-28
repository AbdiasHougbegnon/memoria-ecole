# Correctif critique : le tuteur vocal inventait des actions et des faits — comment on l'a corrigé

**Pour revenir exactement à cet état du code :**
```
git checkout phase-25-tuteur-anti-hallucination
```

---

## 1. L'incident

En vérifiant le tutorat direct (phase 24), l'utilisateur a partagé une conversation réelle
avec le tuteur en mode discussion libre. Extrait révélateur :

> **Étudiant** : Quelle est la dernière session de cours qu'on a enregistrée ?
> **Tuteur** : La dernière session enregistrée est « Reunion Memoria », responsable Marie
> Dupont, budget 15 000 €, deadline vendredi 12 juillet. [...]
> **Étudiant** : Oui, tu peux me l'envoyer par mail ?
> **Tuteur** : [...] J'envoie maintenant l'archive à abdiasprinceschama@gmail.com [...]
> Voici le lien de téléchargement [...] https://tutorat.episen.local/download/...zip

Aucune de ces affirmations n'était vraie : le tuteur n'a **aucune** capacité d'action réelle
(pas d'appel outil, pas d'envoi d'email, pas de génération de fichier — voir §2 ci-dessous).
Tout ce texte est une pure invention du modèle, présentée avec une confiance totale.

Vérification immédiate en base : aucune session nommée "Reunion Memoria" n'est rattachée à
la matière concernée (`contextes_scolaires_session` ne contient que 4 sessions réelles, dont
aucune ne porte ce nom) — **ce n'est pas une fuite de données inter-tenant**, c'est une
invention pure de zéro. Ce constat est rassurant sur l'isolation des données mais aggrave le
diagnostic : le modèle n'avait aucune information à sa disposition et en a fabriqué une,
plutôt que de dire qu'il ne savait pas.

C'est une violation frontale de la doctrine IA du master prompt : *"L'IA n'est jamais la
source de vérité"* et *"Aucune donnée métier n'est produite sans lien vers sa source"*. Pire
qu'un bug d'UX : l'utilisateur pouvait légitimement croire qu'un email avait été envoyé, un
fichier généré, alors que rien ne s'était produit côté serveur.

## 2. Cause racine

`TuteurVocalService.soumettreReponse` / `soumettreReponseLibre` appellent uniquement
`GenerateurTourTuteurPort.genererTour(contexte)`, qui renvoie du texte — **aucun appel
outil, aucune action côté serveur** n'est jamais déclenché par cette classe. Le prompt
système (`CONSIGNE_LIBRE` dans `GenerateurTourTuteurAzureOpenAI`) ne disait nulle part que le
modèle ne devait pas prétendre effectuer des actions, ni qu'il devait rester strictement
ancré sur les informations réellement fournies. Sans cette contrainte explicite, le modèle
comble le vide par un jeu de rôle plausible ("bien sûr, je m'en occupe") — comportement
attendu d'un LLM non contraint, pas un bug d'infrastructure.

## 3. Le correctif

Deux règles ajoutées au prompt système, partagées entre `CONSIGNE_LIBRE` (mode discussion
libre, le plus exposé) et `CONSIGNE_TEMPLATE` (mode explication/exercice, par cohérence) :

- **`REGLE_PAS_DACTION_REELLE`** — interdit explicitement de prétendre envoyer un email,
  générer/téléverser un fichier, créer un lien de téléchargement, ou modifier une
  session/matière/document, même si l'étudiant insiste. Demande d'orienter l'étudiant vers
  l'interface réelle quand c'est pertinent (téléchargement du résumé depuis la page de
  session, upload de documents depuis l'onglet Documents de la matière).
- **`REGLE_PAS_DINVENTION_DE_FAITS`** — interdit de s'appuyer sur autre chose que le contexte
  réellement fourni (notion, historique, contenu agrégé) ; demande de dire explicitement
  "je n'ai pas cette information" plutôt que d'inventer une réponse plausible.

Pas de changement de schéma, pas de nouvel appel réseau : uniquement le texte du prompt
système passé à Azure OpenAI.

## 4. Comment on a vérifié

Pas de test unitaire possible sur le contenu exact d'une réponse LLM (non déterministe) :
vérification en conditions réelles, avec un vrai appel Azure OpenAI, en reproduisant
exactement le scénario qui a révélé le problème.

1. Question réelle (TTS → STT → tuteur) : *"Quelle est la dernière session de cours qu'on a
   enregistrée ?"*
   **Avant** : invente "Reunion Memoria, responsable Marie Dupont, budget 15 000 €...".
   **Après** : *"Je n'ai pas accès aux enregistrements de session depuis ici [...]. Va dans
   l'interface de la matière, onglet Sessions ou Tutorat [...]."*
2. Question réelle : *"Peux-tu m'envoyer un résumé de ce cours par email tout de suite ?"*
   **Avant** : prétend envoyer un email à une adresse.
   **Après** : *"Je ne peux pas envoyer d'e-mail ni de fichier depuis ici. Va dans
   l'interface de la matière [...] et télécharge la fiche de résumé [...]."*

`mvn -B clean verify` : 357/357 tests inchangés (aucune logique testable modifiée, seul le
texte du prompt système a changé) ; backend redémarré localement pour la vérification en
conditions réelles ci-dessus.

## 5. Limites connues, assumées, pas corrigées ici

- **Pas de garantie absolue** : un prompt système réduit fortement la fréquence
  d'hallucination mais ne l'élimine pas à 100 % (limite connue des LLM sans grounding
  structurel type function-calling). Un contrôle structurel (le serveur refuse/filtre les
  réponses mentionnant des actions non supportées) serait plus robuste mais hors de portée
  de cet incrément.
- **Mode EXPLICATION/EXERCICE moins exposé mais pas immunisé** : ancré sur une notion réelle
  et un historique court, le risque y était moindre, mais les deux règles ont été ajoutées
  par cohérence et prudence.

## 6. Pour reprendre seul

- Code de référence exact : `git checkout phase-25-tuteur-anti-hallucination`.
- Si une nouvelle hallucination de ce type est constatée, la reproduire d'abord via un appel
  direct à `POST /api/v1/matieres/{id}/tutorat` puis `POST /api/v1/tutorat/{id}/reponse` avec
  un fichier audio TTS (voir §4) avant de modifier le prompt à l'aveugle.
