package com.tempo.app.data

import java.time.LocalDate

/** Phrases d'encouragement originales, une par jour. */
object Quotes {
    val all = listOf(
        "Un petit pas aujourd'hui vaut mieux qu'un grand plan jamais commencé.",
        "Tu n'as pas besoin d'être parfait, juste d'avancer.",
        "Commence par la tâche la plus simple. L'élan fera le reste.",
        "Chaque case cochée est une promesse tenue envers toi-même.",
        "Ta journée t'appartient. Décide de ce qui compte.",
        "Le désordre d'hier ne décide pas de ta réussite d'aujourd'hui.",
        "Cinq minutes suffisent pour démarrer. Lance-toi.",
        "Ce que tu fais chaque jour compte plus que ce que tu fais de temps en temps.",
        "Respire. Une chose à la fois.",
        "Tu es plus organisé que tu ne le crois : tu as ouvert cette app.",
        "Le meilleur moment pour commencer, c'est maintenant.",
        "Avance doucement, mais avance.",
        "Fais-le pour la personne que tu seras ce soir.",
        "Les grandes choses sont faites de petites habitudes.",
        "Aujourd'hui, choisis le progrès plutôt que la perfection.",
        "Une liste claire, un esprit léger.",
        "Tu as déjà surmonté des journées plus difficiles que celle-ci.",
        "Termine une chose. Puis savoure-la.",
        "Ton futur toi te remercie déjà.",
        "La motivation vient en agissant, pas en attendant.",
        "Sois fier de chaque effort, même minuscule.",
        "Ce n'est pas grave d'aller lentement. Ce qui compte, c'est de ne pas s'arrêter.",
        "Range une chose, et c'est déjà un peu plus de calme.",
        "Une bonne journée se construit tâche après tâche.",
        "Tu mérites la tranquillité d'une journée maîtrisée.",
        "Décider, c'est déjà avancer.",
        "Le plus dur, c'est de commencer. Tu es plus fort que le plus dur.",
        "Garde le cap. Les résultats arrivent toujours après la régularité.",
        "Aujourd'hui est une page blanche. Écris-y quelque chose de bien.",
        "Fais simple. Fais maintenant.",
        "Ton énergie est précieuse : mets-la dans ce qui compte.",
        "Les petites victoires méritent aussi d'être célébrées.",
        "Tu n'es pas en retard. Tu es exactement là où tu commences.",
        "Un esprit organisé laisse plus de place aux rêves.",
        "Chaque jour est une nouvelle chance de prendre de bonnes habitudes.",
        "Tu as le droit de faire une pause. Pas d'abandonner.",
        "La discipline, c'est se souvenir de ce que l'on veut vraiment.",
        "Ce soir, tu seras content d'avoir commencé ce matin.",
        "Concentre-toi sur la prochaine étape, pas sur tout l'escalier.",
        "Tu construis quelque chose, même quand ça ne se voit pas encore.",
        "Une tâche faite, c'est un souci en moins.",
        "Crois en ta capacité à t'améliorer chaque jour.",
        "Le calme vient quand chaque chose trouve sa place.",
        "Ose commencer imparfaitement.",
        "Ta régularité est ton super-pouvoir.",
        "Avance avec bienveillance envers toi-même.",
        "Il n'y a pas de petite progression.",
        "Fais aujourd'hui ce qui rendra demain plus facile.",
        "Tu es capable de bien plus que ta liste ne le dit.",
        "Le succès, c'est la somme de petits efforts répétés.",
        "Allume l'élan : une tâche, tout de suite.",
        "Une journée réussie commence par une intention claire.",
        "Ne compte pas les jours, fais que les jours comptent.",
        "Tu progresses, même les jours où tu en doutes.",
        "Donne-toi la fierté d'avoir tenu parole.",
        "Le chaos recule à chaque case cochée.",
        "Prends soin de tes objectifs, ils prendront soin de toi.",
        "Aujourd'hui, sois la version de toi qui s'organise.",
        "Une habitude à la fois, une vie qui change.",
        "Bravo d'être là. Maintenant, à toi de jouer."
    )

    fun forDate(date: LocalDate): String {
        val index = (date.dayOfYear + date.year * 7) % all.size
        return all[index]
    }

    val today: String get() = forDate(LocalDate.now())
}
