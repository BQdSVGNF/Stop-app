# Tempo pour Android : garde le rythme

La version Android de Tempo, avec les mêmes fonctions que la version iPhone :
- **Aujourd'hui** : ta journée, ta phrase d'encouragement, ta progression et ta série de jours 🔥
- **Tâches et routines** avec emoji, couleur, date, jours de la semaine et rappel à l'heure choisie
- **Pause ⏸** : note où tu t'es arrêté, choisis quand reprendre, et Tempo te le rappelle
- **Noter pour plus tard 💡** : une idée te vient ? Tu la notes en 2 secondes
- **Thèmes 🎨 sur toute l'app** : 7 décors (Verre liquide, Dégradé, Verre cannelé, Trame, Blueprint, Topographie, ASCII) × 7 palettes, mode clair ou sombre, et un effet 3D qui suit l'inclinaison du téléphone
- **Notifications** avec bouton « ✓ C'est fait », reprise automatique des pauses, et un bouton pour tester les rappels

**100 % gratuit.** Pas de Play Store, pas de compte développeur, et l'app n'expire jamais : contrairement à l'iPhone, il n'y a rien à re-signer chaque semaine. Une fois installée, elle reste active pour toujours, et elle reprogramme ses rappels toute seule (chaque nuit, après un redémarrage, après une mise à jour).

---

## Étape 1 : faire compiler l'app par GitHub

1. Sur **github.com**, crée un nouveau dépôt (par exemple `tempo-android`, en **Private**).
2. Clique sur **uploading an existing file**, glisse **tout le contenu** du dossier `Tempo-Android` (le dossier `.github` compris), puis clique sur **Commit changes**.
3. Ouvre l'onglet **Actions**. La compilation démarre toute seule et prend environ 5 minutes.
4. Quand la coche est verte, ouvre la compilation et télécharge **Tempo-apk** en bas de la page. Dézippe-le pour obtenir **`app-debug.apk`**.

> ❌ Si la compilation affiche une croix rouge, ouvre l'étape en rouge, copie le message d'erreur et envoie-le à Claude.

## Étape 2 : installer sur ton téléphone Android

1. Envoie `app-debug.apk` sur ton téléphone (Google Drive, e-mail à toi-même, câble USB…).
2. Ouvre le fichier sur le téléphone. Android te demande d'autoriser l'installation d'**applications inconnues** pour l'app depuis laquelle tu l'ouvres (Fichiers, Drive, Gmail…). Accepte.
3. Appuie sur **Installer**. Si Play Protect affiche un avertissement, choisis **Installer quand même** : c'est normal pour une app qui ne vient pas du Play Store.
4. Ouvre **Tempo** 🎉

## Étape 3 : régler les rappels (une fois)

Dans Tempo, va dans **Réglages → Fiabilité des rappels** et mets tout au vert :
- **Notifications** : « Activées ».
- **Rappels à l'heure exacte** : « Activés » (automatique sur Android 13 et plus).
- **Batterie sans restriction** : appuie sur **Autoriser**. Indispensable sur Samsung, Xiaomi, Huawei, Oppo…
- **Ne jamais mettre en veille** : désactive « Suspendre l'activité si l'app n'est pas utilisée ».

Puis appuie sur **Tester les rappels** : une notification arrive tout de suite, une autre 1 minute après (ferme l'app pour vérifier).

## Mettre à jour l'app

Modifie un fichier sur GitHub, puis valide. GitHub recompile tout seul. Installe le nouvel APK par-dessus l'ancien : **tes tâches sont conservées**. (L'app est signée avec une clé fixe, `app/tempo.keystore` : ne supprime pas ce fichier, sinon Android refusera les mises à jour.)

> ⚠️ Si tu avais installé une version d'avant la 1.1, désinstalle-la **une seule fois** avant d'installer la 1.1 (l'ancienne était signée avec une clé aléatoire). Ensuite, les mises à jour passeront toujours par-dessus.

---

### Contenu du projet
```
Tempo-Android/
├── .github/workflows/android.yml   → compilation automatique
├── app/build.gradle.kts            → configuration de l'app
└── app/src/main/java/com/tempo/app/
    ├── MainActivity.kt
    ├── data/    (Task, stockage, réglages, citations)
    ├── notif/   (notifications et alarmes)
    └── ui/      (écrans, thèmes, fenêtres)
```
Tu peux aussi ouvrir le dossier avec **Android Studio** (gratuit, fonctionne sous Windows) pour tester l'app sur un émulateur.
