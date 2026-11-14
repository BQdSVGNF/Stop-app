# Auto Clicker (cible unique + cibles multiples)

Application Android en Kotlin, sans aucune bibliothèque externe. Android 7.0 minimum.

## Fonctionnalités
- **Mode cible unique** : un cercle déplaçable, avec un intervalle réglable.
- **Mode cibles multiples** : des clics (＋) et des glissements (↗) numérotés 1, 2, 3…, exécutés dans l'ordre puis en boucle.
- **Chaque cible est personnalisable** (on la touche pour l'ouvrir) : délai avant l'action suivante, durée de l'appui ou du glissement, nombre de répétitions.
- **Décimales avec virgule ou point**, dans l'unité **ms, s ou min** : `0,5 s`, `1,25 min`, `8 ms`, `300 min`…
- **Arrêt automatique** : jamais, après N cycles, ou après une durée (décimales acceptées aussi).
- **Configurations** : 💾 dans le panneau pour enregistrer, puis « Gérer les configurations » pour charger, renommer ou supprimer.
- Le panneau flottant se déplace avec ✥.

## Obtenir l'APK sans ordinateur (GitHub Actions)
1. Crée un dépôt sur GitHub et envoie-y tout le contenu de ce dossier (y compris `.github/`).
2. Ouvre l'onglet **Actions**, puis le build « Build APK ». Il se lance tout seul à chaque envoi.
3. Quand il est terminé, télécharge l'artefact **AutoClicker-apk** (un zip qui contient `app-debug.apk`).

## Avec Android Studio
Ouvre ce dossier, attends la synchronisation Gradle, puis lance **Build → Build APK(s)**.

## Première utilisation
1. Installe l'APK.
2. Dans l'appli, touche le bandeau rouge, puis Accessibilité → Auto Clicker → activer.
   Sur Android 13+, si l'option est grisée : Infos de l'appli → ⋮ → **Autoriser les paramètres restreints**.
3. Touche **DÉSACTIVÉ** sous le mode voulu pour l'activer. Le panneau apparaît par-dessus tes applis.
