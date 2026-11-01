import Foundation

/// One subscription. Only a card nickname and its last four digits are stored, never a full card number.
/// Field names match the Android app and the backup format.
struct Subscription: Identifiable, Equatable {
    var id: Int64 = 0
    var name: String
    var nickname: String = ""
    var amountMinor: Int64
    var currency: String = "INR"
    var periodCount: Int = 1
    var periodUnit: String = PeriodUnit.MONTH.rawValue
    var firstPaymentEpochDay: Int
    var category: String = Category.OTHER.rawValue
    var cardNickname: String = ""
    var cardLast4: String = ""
    var remindDaysBefore: Int = 1
    var notes: String = ""
    var active: Bool = true
    var createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    var planType: String = PlanType.RECURRING.rawValue
    var endEpochDay: Int? = nil
    var includeInMonthly: Bool = true
    var splitCount: Int = 1
    var regularAmountMinor: Int64? = nil

    var type: PlanType { PlanType.from(planType) }
    var period: BillingPeriod { BillingPeriod.of(periodCount, periodUnit) }
    var categoryValue: Category { Category.from(category) }
    var firstPaymentDate: Day { Day(epochDay: firstPaymentEpochDay) }
    var endDate: Day? { endEpochDay.map { Day(epochDay: $0) } }

    var plan: Plan {
        Plan(
            type: type,
            amountMinor: amountMinor,
            period: period,
            start: firstPaymentDate,
            end: endDate,
            split: max(splitCount, 1),
            includeInMonthly: includeInMonthly,
            active: active,
            regularAmountMinor: regularAmountMinor
        )
    }

    func status(_ today: Day) -> Status { Plans.status(plan, today) }
    func nextCharge(_ today: Day) -> Day? { Plans.nextCharge(plan, today) }
    func previousCharge(_ today: Day) -> Day? { Plans.previousCharge(plan, today) }
    func paymentsMade(_ today: Day) -> Int { Plans.paymentsMade(plan, today) }
    var shareMinor: Int64 { Plans.shareMinor(plan) }
    func monthlyBillMinor(_ today: Day) -> Int64 { Plans.monthlyBillMinor(plan, today) }
    func yearlyMinor(_ today: Day) -> Int64 { Plans.yearlyMinor(plan, today) }

    /// "HDFC •••• 1234", "HDFC", "•••• 1234" or "".
    var cardLabel: String {
        if !cardNickname.isEmpty && !cardLast4.isEmpty { return "\(cardNickname) •••• \(cardLast4)" }
        if !cardNickname.isEmpty { return cardNickname }
        if !cardLast4.isEmpty { return "•••• \(cardLast4)" }
        return ""
    }
}
