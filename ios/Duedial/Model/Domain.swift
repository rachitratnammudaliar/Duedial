import Foundation

// A Swift port of the Android app's domain logic (app/src/main/java/.../domain).
// Keep the two in step: the unit tests in DuedialTests mirror the Kotlin tests.

// MARK: - Day

/// A calendar date without a time, stored as days since 1970-01-01 (the same as Kotlin's epoch day).
struct Day: Hashable, Comparable, Codable {
    let epochDay: Int

    init(epochDay: Int) { self.epochDay = epochDay }

    private static let calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }()

    init(_ year: Int, _ month: Int, _ day: Int) {
        let date = Day.calendar.date(from: DateComponents(year: year, month: month, day: day))!
        self.epochDay = Int((date.timeIntervalSince1970 / 86_400).rounded(.down))
    }

    /// Today in the phone's own time zone.
    static func today() -> Day { Day(localDate: Date()) }

    /// The calendar day a moment falls on, in the phone's time zone.
    init(localDate: Date) {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: localDate)
        self.init(c.year!, c.month!, c.day!)
    }

    var date: Date { Date(timeIntervalSince1970: TimeInterval(epochDay) * 86_400) }

    /// Midnight of this day in the phone's time zone, for date pickers.
    var localDate: Date {
        let c = Day.calendar.dateComponents([.year, .month, .day], from: date)
        return Calendar.current.date(from: c) ?? date
    }

    var components: (year: Int, month: Int, day: Int) {
        let c = Day.calendar.dateComponents([.year, .month, .day], from: date)
        return (c.year!, c.month!, c.day!)
    }

    var lengthOfMonth: Int { Day.calendar.range(of: .day, in: .month, for: date)!.count }

    var firstOfMonth: Day { let c = components; return Day(c.year, c.month, 1) }

    var lastOfMonth: Day { let c = components; return Day(c.year, c.month, lengthOfMonth) }

    func plusDays(_ n: Int) -> Day { Day(epochDay: epochDay + n) }

    /// Adds months like java.time: the day is clamped to the month's length (31 Jan + 1 month = 28/29 Feb).
    func plusMonths(_ n: Int) -> Day {
        let c = components
        let total = c.year * 12 + (c.month - 1) + n
        let year = Int((Double(total) / 12).rounded(.down))
        let month = total - year * 12 + 1
        let length = Day(year, month, 1).lengthOfMonth
        return Day(year, month, min(c.day, length))
    }

    func plusYears(_ n: Int) -> Day { plusMonths(n * 12) }

    static func < (a: Day, b: Day) -> Bool { a.epochDay < b.epochDay }

    func format(_ style: DayStyle) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_GB")
        f.timeZone = TimeZone(identifier: "UTC")
        f.dateFormat = style == .long ? "d MMM yyyy" : "d MMM"
        return f.string(from: date)
    }

    var monthName: String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_GB")
        f.timeZone = TimeZone(identifier: "UTC")
        f.dateFormat = "MMMM"
        return f.string(from: date)
    }
}

enum DayStyle { case long, short }

// MARK: - Billing period

enum PeriodUnit: String, CaseIterable, Identifiable {
    case DAY, WEEK, MONTH, YEAR
    var id: String { rawValue }

    var singular: String { ["DAY": "day", "WEEK": "week", "MONTH": "month", "YEAR": "year"][rawValue]! }
    var plural: String { singular + "s" }
    var perYear: Int64 {
        switch self {
        case .DAY: return 365
        case .WEEK: return 52
        case .MONTH: return 12
        case .YEAR: return 1
        }
    }

    static func from(_ name: String) -> PeriodUnit { PeriodUnit(rawValue: name) ?? .MONTH }
}

struct BillingPeriod: Hashable {
    let count: Int
    let unit: PeriodUnit

    init(_ count: Int, _ unit: PeriodUnit) {
        self.count = min(max(count, 1), 999)
        self.unit = unit
    }

    var isMonthly: Bool { unit == .MONTH && count == 1 }

    var label: String {
        if count == 1 {
            switch unit {
            case .DAY: return "Daily"
            case .WEEK: return "Weekly"
            case .MONTH: return "Monthly"
            case .YEAR: return "Yearly"
            }
        }
        return "Every \(count) \(unit.plural)"
    }

    static let weekly = BillingPeriod(1, .WEEK)
    static let monthly = BillingPeriod(1, .MONTH)
    static let quarterly = BillingPeriod(3, .MONTH)
    static let halfYearly = BillingPeriod(6, .MONTH)
    static let yearly = BillingPeriod(1, .YEAR)
    static let presets = [weekly, monthly, quarterly, halfYearly, yearly]

    static func of(_ count: Int, _ unitName: String) -> BillingPeriod { BillingPeriod(count, PeriodUnit.from(unitName)) }

    static func fromLegacyCycle(_ name: String) -> BillingPeriod {
        switch name {
        case "WEEKLY": return weekly
        case "QUARTERLY": return quarterly
        case "HALF_YEARLY": return halfYearly
        case "YEARLY": return yearly
        default: return monthly
        }
    }
}

/// Rounds a / b half up, for non-negative a and positive b (like BigDecimal HALF_UP).
func divideRoundingHalfUp(_ a: Int64, _ b: Int64) -> Int64 {
    if a >= 0 { return (2 * a + b) / (2 * b) }
    return -((2 * -a + b) / (2 * b))
}

// MARK: - Renewals

/// Payment-date maths. Every date is computed from the first payment, never by adding to the
/// previous date, so month ends don't drift.
enum Renewals {
    static func nthDate(_ anchor: Day, _ period: BillingPeriod, _ n: Int) -> Day {
        switch period.unit {
        case .DAY: return anchor.plusDays(n * period.count)
        case .WEEK: return anchor.plusDays(n * period.count * 7)
        case .MONTH: return anchor.plusMonths(n * period.count)
        case .YEAR: return anchor.plusYears(n * period.count)
        }
    }

    /// A rough number of whole units between two days; corrected by the loops below.
    private static func approxUnits(_ from: Day, _ to: Day, _ unit: PeriodUnit) -> Int {
        let days = to.epochDay - from.epochDay
        switch unit {
        case .DAY: return days
        case .WEEK: return days / 7
        case .MONTH: return Int(Double(days) / 30.436875)
        case .YEAR: return Int(Double(days) / 365.2425)
        }
    }

    private static func firstIndexOnOrAfter(_ anchor: Day, _ period: BillingPeriod, _ day: Day) -> Int {
        if !(anchor < day) { return 0 }
        var n = max(approxUnits(anchor, day, period.unit) / period.count, 0)
        while n > 0 && !(nthDate(anchor, period, n - 1) < day) { n -= 1 }
        while nthDate(anchor, period, n) < day { n += 1 }
        return n
    }

    static func nextRenewal(_ anchor: Day, _ period: BillingPeriod, _ today: Day) -> Day {
        nthDate(anchor, period, firstIndexOnOrAfter(anchor, period, today))
    }

    static func previousPayment(_ anchor: Day, _ period: BillingPeriod, _ today: Day) -> Day? {
        let n = firstIndexOnOrAfter(anchor, period, today)
        return n == 0 ? nil : nthDate(anchor, period, n - 1)
    }

    static func paymentsMade(_ anchor: Day, _ period: BillingPeriod, _ today: Day) -> Int {
        if anchor > today { return 0 }
        let n = firstIndexOnOrAfter(anchor, period, today)
        return nthDate(anchor, period, n) == today ? n + 1 : n
    }

    static func chargesBetween(_ anchor: Day, _ period: BillingPeriod, _ start: Day, _ end: Day) -> Int {
        if end < start { return 0 }
        var n = firstIndexOnOrAfter(anchor, period, start)
        var count = 0
        while !(nthDate(anchor, period, n) > end) && count < 400 {
            count += 1
            n += 1
        }
        return count
    }

    static func daysUntil(_ today: Day, _ date: Day) -> Int { date.epochDay - today.epochDay }

    static func monthlyEquivalentMinor(_ amount: Int64, _ period: BillingPeriod) -> Int64 {
        divideRoundingHalfUp(amount * period.unit.perYear, 12 * Int64(period.count))
    }

    static func yearlyEquivalentMinor(_ amount: Int64, _ period: BillingPeriod) -> Int64 {
        divideRoundingHalfUp(amount * period.unit.perYear, Int64(period.count))
    }

    static func relativeLabel(_ days: Int) -> String {
        switch days {
        case ..<0: return "overdue"
        case 0: return "today"
        case 1: return "tomorrow"
        case 2..<7: return "in \(days) days"
        case 7..<14: return "in 1 week"
        case 14..<31: return "in \(days / 7) weeks"
        case 31..<60: return "in 1 month"
        case 60..<365: return "in \(days / 30) months"
        default: return "in \(days) days"
        }
    }
}

// MARK: - Money

/// Money is stored as whole minor units (paise, cents) to avoid rounding errors.
enum Money {
    static let currencies = ["INR", "EUR", "USD", "GBP"]

    /// "199", "199.5" or "199,50" to minor units. Nil if invalid, zero, negative or too large.
    static func parseToMinor(_ text: String) -> Int64? {
        let cleaned = text.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: ",", with: ".")
        if cleaned.isEmpty { return nil }
        let parts = cleaned.split(separator: ".", omittingEmptySubsequences: false)
        if parts.count > 2 { return nil }
        let whole = String(parts[0])
        let fraction = parts.count == 2 ? String(parts[1]) : ""
        if whole.isEmpty && fraction.isEmpty { return nil }
        if !(whole + fraction).allSatisfy({ $0.isASCII && $0.isNumber }) { return nil }
        if fraction.count > 2 || whole.count > 12 { return nil }
        let wholeValue = Int64(whole.isEmpty ? "0" : whole) ?? 0
        let fractionValue = Int64((fraction + "00").prefix(2)) ?? 0
        let minor = wholeValue * 100 + fractionValue
        if minor <= 0 || minor > 100_000_000 * 100 { return nil }
        return minor
    }

    /// 19950 -> "199.5", 64900 -> "649".
    static func minorToInput(_ minor: Int64) -> String {
        let whole = minor / 100
        let cents = minor % 100
        if cents == 0 { return "\(whole)" }
        if cents % 10 == 0 { return "\(whole).\(cents / 10)" }
        return String(format: "%lld.%02lld", whole, cents)
    }

    static func format(_ minor: Int64, _ currency: String) -> String {
        let f = NumberFormatter()
        f.numberStyle = .currency
        switch currency {
        case "INR": f.locale = Locale(identifier: "en_IN")
        case "EUR": f.locale = Locale(identifier: "en_IE")
        case "GBP": f.locale = Locale(identifier: "en_GB")
        default: f.locale = Locale(identifier: "en_US")
        }
        f.currencyCode = currency
        f.minimumFractionDigits = 2
        f.maximumFractionDigits = 2
        let value = NSDecimalNumber(mantissa: UInt64(abs(minor)), exponent: -2, isNegative: minor < 0)
        return f.string(from: value) ?? "\(currency) \(minorToInput(minor))"
    }

    static func isValidLast4(_ text: String) -> Bool {
        text.isEmpty || (text.count == 4 && text.allSatisfy { $0.isASCII && $0.isNumber })
    }
}

// MARK: - Plans

enum PlanType: String, CaseIterable, Identifiable {
    case RECURRING, TRIAL, FREE, LIFETIME
    var id: String { rawValue }

    var label: String {
        switch self {
        case .RECURRING: return "Subscription"
        case .TRIAL: return "Free trial"
        case .FREE: return "Free"
        case .LIFETIME: return "Lifetime"
        }
    }

    var description: String {
        switch self {
        case .RECURRING: return "Paid on a schedule"
        case .TRIAL: return "Free now, paid when the trial ends unless you cancel"
        case .FREE: return "Costs nothing, e.g. a student offer or free tier"
        case .LIFETIME: return "Paid once, yours to keep"
        }
    }

    static func from(_ name: String) -> PlanType { PlanType(rawValue: name) ?? .RECURRING }
}

enum Status { case upcoming, trial, trialCancelled, free, owned, ending, ended, paused }

enum EventKind { case charge, trialEnds, freeEnds, subscriptionEnds }

struct PlanEvent: Equatable {
    let date: Day
    let kind: EventKind
}

struct Plan {
    var type: PlanType
    var amountMinor: Int64
    var period: BillingPeriod
    /// First charge date. For a trial, the day it ends; for lifetime, the purchase; for free, when it started.
    var start: Day
    /// Last active day, or nil if it continues. No charges happen after it.
    var end: Day? = nil
    var split: Int = 1
    var includeInMonthly: Bool = true
    var active: Bool = true
    var regularAmountMinor: Int64? = nil
}

enum Plans {
    static func defaultIncludeInMonthly(_ type: PlanType, _ period: BillingPeriod) -> Bool {
        (type == .RECURRING || type == .TRIAL) && (period.unit == .DAY || period.unit == .WEEK || period.isMonthly)
    }

    static func shareMinor(_ p: Plan) -> Int64 { divideRoundingHalfUp(p.amountMinor, Int64(max(p.split, 1))) }

    static func inTrial(_ p: Plan, _ today: Day) -> Bool { p.type == .TRIAL && today < p.start }

    private static func trialCancelled(_ p: Plan) -> Bool {
        guard p.type == .TRIAL, let end = p.end else { return false }
        return end < p.start
    }

    private static func allowed(_ p: Plan, _ date: Day) -> Bool { p.end == nil || !(date > p.end!) }

    private static func limit(_ p: Plan, _ date: Day) -> Day {
        if let end = p.end, end < date { return end }
        return date
    }

    static func nextCharge(_ p: Plan, _ today: Day) -> Day? {
        if !p.active { return nil }
        let next: Day?
        switch p.type {
        case .FREE: next = nil
        case .LIFETIME: next = !(p.start < today) ? p.start : nil
        case .RECURRING, .TRIAL: next = Renewals.nextRenewal(p.start, p.period, today)
        }
        guard let n = next, allowed(p, n) else { return nil }
        return n
    }

    static func previousCharge(_ p: Plan, _ today: Day) -> Day? {
        let l = limit(p, today.plusDays(-1))
        switch p.type {
        case .FREE: return nil
        case .LIFETIME: return !(p.start > l) ? p.start : nil
        case .RECURRING, .TRIAL:
            let made = Renewals.paymentsMade(p.start, p.period, l)
            return made == 0 ? nil : Renewals.nthDate(p.start, p.period, made - 1)
        }
    }

    static func paymentsMade(_ p: Plan, _ today: Day) -> Int {
        let l = limit(p, today)
        switch p.type {
        case .FREE: return 0
        case .LIFETIME: return !(p.start > l) ? 1 : 0
        case .RECURRING, .TRIAL: return Renewals.paymentsMade(p.start, p.period, l)
        }
    }

    static func paidSoFarMinor(_ p: Plan, _ today: Day) -> Int64 { shareMinor(p) * Int64(paymentsMade(p, today)) }

    static func status(_ p: Plan, _ today: Day) -> Status {
        if !p.active { return .paused }
        if let end = p.end, today > end { return .ended }
        switch p.type {
        case .FREE: return .free
        case .LIFETIME: return .owned
        case .TRIAL, .RECURRING:
            if trialCancelled(p) && today < p.start { return .trialCancelled }
            if inTrial(p, today) { return .trial }
            if p.end != nil { return .ending }
            return .upcoming
        }
    }

    static func countsInMonthlyBill(_ p: Plan, _ today: Day) -> Bool {
        let s = status(p, today)
        return p.includeInMonthly && (s == .upcoming || s == .ending) && (p.type == .RECURRING || p.type == .TRIAL)
    }

    static func monthlyBillMinor(_ p: Plan, _ today: Day) -> Int64 {
        countsInMonthlyBill(p, today) ? Renewals.monthlyEquivalentMinor(shareMinor(p), p.period) : 0
    }

    static func yearlyMinor(_ p: Plan, _ today: Day) -> Int64 {
        let s = status(p, today)
        return (s == .upcoming || s == .ending) ? Renewals.yearlyEquivalentMinor(shareMinor(p), p.period) : 0
    }

    static func dueBetweenMinor(_ p: Plan, _ from: Day, _ to: Day) -> Int64 {
        if !p.active { return 0 }
        let last = limit(p, to)
        if last < from { return 0 }
        let count: Int
        switch p.type {
        case .FREE: count = 0
        case .LIFETIME: count = (!(p.start < from) && !(p.start > last)) ? 1 : 0
        case .RECURRING, .TRIAL: count = Renewals.chargesBetween(p.start, p.period, from, last)
        }
        return shareMinor(p) * Int64(count)
    }

    static func savingMinor(_ p: Plan) -> Int64? {
        guard let regular = p.regularAmountMinor else { return nil }
        let paid = p.type == .FREE ? 0 : p.amountMinor
        let saving = regular - paid
        return saving > 0 ? saving : nil
    }

    static func nextEvent(_ p: Plan, _ today: Day) -> PlanEvent? {
        switch status(p, today) {
        case .paused, .ended, .trialCancelled, .owned:
            if p.type == .LIFETIME, let n = nextCharge(p, today) { return PlanEvent(date: n, kind: .charge) }
            return nil
        case .trial: return PlanEvent(date: p.start, kind: .trialEnds)
        case .free: return p.end.map { PlanEvent(date: $0, kind: .freeEnds) }
        case .upcoming: return nextCharge(p, today).map { PlanEvent(date: $0, kind: .charge) }
        case .ending:
            if let n = nextCharge(p, today) { return PlanEvent(date: n, kind: .charge) }
            return p.end.map { PlanEvent(date: $0, kind: .subscriptionEnds) }
        }
    }

    static func reminderDue(_ p: Plan, _ remindDaysBefore: Int, _ today: Day) -> PlanEvent? {
        if remindDaysBefore < 0 { return nil }
        guard let event = nextEvent(p, today) else { return nil }
        let days = Renewals.daysUntil(today, event.date)
        let onDay = days == 0 && (event.kind == .charge || event.kind == .trialEnds)
        return (days == remindDaysBefore || onDay) ? event : nil
    }
}

// MARK: - Categories and brands

enum Category: String, CaseIterable, Identifiable {
    case ENTERTAINMENT, MUSIC, SOFTWARE, CLOUD, PHONE, FITNESS, NEWS, EDUCATION, SHOPPING, FINANCE, OTHER
    var id: String { rawValue }

    var label: String {
        switch self {
        case .ENTERTAINMENT: return "Entertainment"
        case .MUSIC: return "Music"
        case .SOFTWARE: return "Software & AI"
        case .CLOUD: return "Cloud storage"
        case .PHONE: return "Phone & internet"
        case .FITNESS: return "Health & fitness"
        case .NEWS: return "News & reading"
        case .EDUCATION: return "Education"
        case .SHOPPING: return "Shopping & food"
        case .FINANCE: return "Finance"
        case .OTHER: return "Other"
        }
    }

    static func from(_ name: String) -> Category { Category(rawValue: name) ?? .OTHER }
}

enum Brands {
    struct Brand {
        let color: UInt32
        let category: Category
    }

    private static let known: [([String], Brand)] = [
        (["netflix"], Brand(color: 0xE50914, category: .ENTERTAINMENT)),
        (["youtube"], Brand(color: 0xFF0000, category: .ENTERTAINMENT)),
        (["prime video", "amazon prime", "prime"], Brand(color: 0x00A8E1, category: .ENTERTAINMENT)),
        (["hotstar", "disney"], Brand(color: 0x1A2A6C, category: .ENTERTAINMENT)),
        (["jiocinema"], Brand(color: 0xD9008D, category: .ENTERTAINMENT)),
        (["sonyliv", "sony liv"], Brand(color: 0x2B2B2B, category: .ENTERTAINMENT)),
        (["zee5"], Brand(color: 0x8230C6, category: .ENTERTAINMENT)),
        (["crunchyroll"], Brand(color: 0xF47521, category: .ENTERTAINMENT)),
        (["apple tv"], Brand(color: 0x1C1C1E, category: .ENTERTAINMENT)),
        (["spotify"], Brand(color: 0x1DB954, category: .MUSIC)),
        (["apple music"], Brand(color: 0xFA2D48, category: .MUSIC)),
        (["jiosaavn", "saavn"], Brand(color: 0x2BC5B4, category: .MUSIC)),
        (["gaana"], Brand(color: 0xE72C30, category: .MUSIC)),
        (["chatgpt", "openai"], Brand(color: 0x10A37F, category: .SOFTWARE)),
        (["claude", "anthropic"], Brand(color: 0xC15F3C, category: .SOFTWARE)),
        (["gemini"], Brand(color: 0x4C7BF4, category: .SOFTWARE)),
        (["lovable"], Brand(color: 0xFF4F7A, category: .SOFTWARE)),
        (["github"], Brand(color: 0x24292F, category: .SOFTWARE)),
        (["adobe"], Brand(color: 0xFA0F00, category: .SOFTWARE)),
        (["microsoft", "office 365", "microsoft 365"], Brand(color: 0x0078D4, category: .SOFTWARE)),
        (["canva"], Brand(color: 0x00C4CC, category: .SOFTWARE)),
        (["notion"], Brand(color: 0x191919, category: .SOFTWARE)),
        (["figma"], Brand(color: 0xA259FF, category: .SOFTWARE)),
        (["icloud"], Brand(color: 0x3693F3, category: .CLOUD)),
        (["google one", "google drive"], Brand(color: 0x4285F4, category: .CLOUD)),
        (["dropbox"], Brand(color: 0x0061FF, category: .CLOUD)),
        (["airtel"], Brand(color: 0xE40000, category: .PHONE)),
        (["jio"], Brand(color: 0x0F3CC9, category: .PHONE)),
        (["vodafone", "vi "], Brand(color: 0xEE1C25, category: .PHONE)),
        (["telia", "tele2", "bite"], Brand(color: 0x990AE3, category: .PHONE)),
        (["linkedin"], Brand(color: 0x0A66C2, category: .EDUCATION)),
        (["duolingo"], Brand(color: 0x58CC02, category: .EDUCATION)),
        (["coursera"], Brand(color: 0x0056D2, category: .EDUCATION)),
        (["udemy"], Brand(color: 0xA435F0, category: .EDUCATION)),
        (["gym", "fitness", "cult"], Brand(color: 0xFF5A36, category: .FITNESS)),
        (["swiggy"], Brand(color: 0xFC8019, category: .SHOPPING)),
        (["zomato"], Brand(color: 0xE23744, category: .SHOPPING)),
        (["bolt"], Brand(color: 0x34D186, category: .SHOPPING)),
        (["wolt"], Brand(color: 0x009DE0, category: .SHOPPING)),
    ]

    static func match(_ name: String) -> Brand? {
        let n = " \(name.lowercased().trimmingCharacters(in: .whitespaces)) "
        return known.first { keys, _ in keys.contains { n.contains($0) } }?.1
    }

    private static let palette: [UInt32] = [0x0B5FA5, 0x7B4DFF, 0x00897B, 0xB4462B, 0x5D4037, 0x3949AB, 0xAD1457, 0x2E7D32]

    /// Java's String.hashCode, so a name gets the same colour on every platform.
    static func javaHash(_ s: String) -> Int32 {
        var h: Int32 = 0
        for unit in s.utf16 { h = h &* 31 &+ Int32(unit) }
        return h
    }

    static func colorFor(_ name: String) -> UInt32 {
        if let brand = match(name) { return brand.color }
        let h = Int(javaHash(name.trimmingCharacters(in: .whitespaces).lowercased()))
        let i = ((h % palette.count) + palette.count) % palette.count
        return palette[i]
    }

    static func initial(_ name: String) -> String {
        guard let c = name.trimmingCharacters(in: .whitespaces).first(where: { $0.isLetter || $0.isNumber }) else { return "?" }
        return String(c).uppercased()
    }
}
