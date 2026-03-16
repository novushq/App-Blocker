package appblocker.appblocker.ui.screens

data class ResearchInsight(
    val headline: String,
    val detail: String
)

val onboardingResearchInsights = listOf(
    ResearchInsight(
        headline = "Attention residue is real",
        detail = "Task-switching research shows your brain keeps part of its focus on previous content, reducing deep-work performance for many minutes."
    ),
    ResearchInsight(
        headline = "Infinite feeds amplify habit loops",
        detail = "Behavioral design studies show variable rewards make short-form feeds harder to stop than fixed-end content."
    ),
    ResearchInsight(
        headline = "Sleep and mood both take a hit",
        detail = "Late-night doom scrolling is associated with shorter sleep duration, higher stress markers, and lower next-day cognitive control."
    ),
    ResearchInsight(
        headline = "Small sessions become big losses",
        detail = "Fifteen extra minutes per day compounds to roughly 91 hours per year: nearly four full days of waking life."
    )
)

val timeWasteQuotes = listOf(
    "You can always earn money back; you cannot earn yesterday back.",
    "A 10-minute scroll rarely stays 10 minutes.",
    "What you repeat daily becomes your identity.",
    "If it is not intentional, it is probably stealing your time.",
    "Your future is built from the minutes you protect today."
)

fun quoteForIndex(index: Int): String {
    if (timeWasteQuotes.isEmpty()) return "Protect your time before it disappears."
    val safe = index.mod(timeWasteQuotes.size)
    return timeWasteQuotes[safe]
}

