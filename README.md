# Tempo pour Android : garde le rythme

La version Android de Tempo, avec les mêmes fonctions que la version iPhone :
- **Aujourd'hui** : ta journée, ta phrase d'encouragement, ta progression et ta série de jours 🔥
- **Tâches et routines** avec emoji, couleur, date, jours de la semaine et rappel à l'heure choisie
- **Pause ⏸** : note où tu t'es arrêté, choisis quand reprendre, et Tempo te le rappelle
- **Noter pour plus tard 💡** : une idée te vient ? Tu la notes en 2 secondes
- **Thèmes 🎨** : 6 styles (Dégradé, Verre cannelé, Trame, Blueprint, Topographie, ASCII) × 7 palettes, plus le mode clair ou sombre

**100 % gratuit.** Pas de Play Store, pas de compte développeur, et l'app n'expire jamais.

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

Dans Tempo, va dans **Réglages → Système** :
- **Notifications** : elles doivent être « Activées ».
- **Rappels à l'heure exacte** : appuie sur **Autoriser**, pour que tes rappels arrivent pile à l'heure.

💡 Sur certains téléphones (Samsung, Xiaomi, Huawei…), l'économie de batterie peut retarder les rappels. Dans les Réglages du téléphone, va dans **Applis → Tempo → Batterie** et choisis **Non restreinte**.

## Mettre à jour l'app

Modifie un fichier sur GitHub, puis valide. GitHub recompile tout seul. Installe le nouvel APK par-dessus l'ancien : **tes tâches sont conservées**.

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
