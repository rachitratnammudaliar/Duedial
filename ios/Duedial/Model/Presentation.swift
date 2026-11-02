import Foundation

// Words and numbers shown for a subscription, the same as the Android app's ui/Presentation.kt.

let reminderOptions: [(Int, String)] = [
    (-1, "Off"), (0, "On the day"), (1, "1 day before"), (2, "2 days before"), (3, "3 days before"), (7, "1 week before"),
]

func reminderLabel(_ days: Int) -> String {
    reminderOptions.first { $0.0 == days }?.1 ?? "\(days) days before"
}

enum Tone { case normal, warn, muted }

struct Line {
    let text: String
    var tone: Tone = .normal
}

func statusLine(_ s: Subscription, _ today: Day) -> Line {
    let end = s.endDate
    switch s.status(today) {
    case .upcoming:
        guard let next = s.nextCharge(today) else { return Line(text: "No upcoming payment", tone: .muted) }
        let days = Renewals.daysUntil(today, next)
        return Line(text: "\(next.format(.long)) · \(Renewals.relativeLabel(days))", tone: days <= 3 ? .warn : .normal)
    case .ending:
        if let next = s.nextCharge(today) {
            return Line(text: "\(next.format(.short)) · last payment, ends \(end?.format(.short) ?? "")")
        }
        return Line(text: "Cancelled · active until \(end?.format(.long) ?? "")", tone: .muted)
    case .trial:
        let trialEnd = s.firstPaymentDate
        let days = Renewals.daysUntil(today, trialEnd)
        return Line(
            text: "Trial ends \(Renewals.relativeLabel(days)) · cancel by \(trialEnd.plusDays(-1).format(.short))",
            tone: days <= 3 ? .warn : .normal
        )
    case .trialCancelled:
        return Line(text: "Trial cancelled · free until \(end?.format(.short) ?? "")", tone: .muted)
    case .free:
        if let end = end {
            let days = Renewals.daysUntil(today, end)
            return Line(text: "Free until \(end.format(.long))", tone: days <= 7 ? .warn : .normal)
        }
        return Line(text: "Free · no end date")
    case .owned:
        let date = s.firstPaymentDate
        return date > today ? Line(text: "Purchase on \(date.format(.long))") : Line(text: "Bought \(date.format(.long))", tone: .muted)
    case .ended:
        return Line(text: "Ended \(end?.format(.long) ?? "")".trimmingCharacters(in: .whitespaces), tone: .muted)
    case .paused:
        return Line(text: "Paused", tone: .muted)
    }
}

/// The big number on the right of a list row, and the small line under it.
func priceLabels(_ s: Subscription, _ today: Day) -> (String, String) {
    let share = Money.format(s.shareMinor, s.currency)
    let st = s.status(today)
    let notCounted = !s.includeInMonthly && (st == .upcoming || st == .ending || st == .trial) ? " · not counted" : ""
    let split = (s.splitCount > 1 ? " · your share" : "") + notCounted
    switch s.type {
    case .FREE: return ("Free", s.regularAmountMinor.map { "normally \(Money.format($0, s.currency))" } ?? "")
    case .LIFETIME: return (share, "once")
    case .TRIAL:
        if st == .trial || st == .trialCancelled { return ("Free", "then \(share)" + (s.includeInMonthly ? "" : " · not counted")) }
        return (share, s.period.label + split)
    case .RECURRING: return (share, s.period.label + split)
    }
}

/// 0: upcoming, ending, trial. 1: free, lifetime, cancelled trial. 2: ended, paused.
func listGroup(_ s: Subscription, _ today: Day) -> Int {
    switch s.status(today) {
    case .upcoming, .ending, .trial: return 0
    case .free, .owned, .trialCancelled: return 1
    case .ended, .paused: return 2
    }
}

let groupTitles: [Int: String] = [0: "Upcoming", 1: "Free & lifetime", 2: "Ended & paused"]

enum TotalMode: String, CaseIterable, Identifiable {
    case MONTHLY_BILL, THIS_MONTH
    var id: String { rawValue }
    var label: String { self == .MONTHLY_BILL ? "Monthly bill" : "Due this month" }
    var description: String {
        self == .MONTHLY_BILL
            ? "Subscriptions you count in your bill; longer plans spread across months"
            : "Charged this calendar month, from subscriptions you count"
    }
}

enum SortMode: String, CaseIterable, Identifiable {
    case NEXT, PRICE, NAME
    var id: String { rawValue }
    var label: String {
        switch self {
        case .NEXT: return "Next payment"
        case .PRICE: return "Price"
        case .NAME: return "Name"
        }
    }
}

enum ReminderText {
    static func title(_ s: Subscription) -> String { s.nickname.isEmpty ? s.name : "\(s.name) (\(s.nickname))" }

    /// [today] is the day the reminder is shown.
    static func body(_ s: Subscription, _ event: PlanEvent, _ today: Day) -> String {
        let days = Renewals.daysUntil(today, event.date)
        let date = event.date.format(.short)
        let price = Money.format(s.shareMinor, s.currency)
        switch event.kind {
        case .charge:
            return days == 0 ? "\(price) is due today" : "Renews \(Renewals.relativeLabel(days)), \(date) · \(price)"
        case .trialEnds:
            return days == 0
                ? "Free trial ended today. You'll be charged \(price) unless you've cancelled."
                : "Free trial ends \(date). Cancel by \(event.date.plusDays(-1).format(.short)) to avoid paying \(price)."
        case .freeEnds:
            return "Free period ends \(Renewals.relativeLabel(days)), \(date)"
        case .subscriptionEnds:
            return "Ends \(Renewals.relativeLabel(days)), \(date)"
        }
    }
}
