import Foundation

/// The same JSON backup format as the Android app, so data moves freely between them.
enum Backup {
    static let format = "duedial-backup"
    static let legacyFormat = "renewly-backup"
    static let version = 3

    enum BackupError: LocalizedError {
        case notABackup
        var errorDescription: String? { "This file isn't a Duedial backup." }
    }

    static func encode(_ subscriptions: [Subscription]) -> Data {
        let items: [[String: Any]] = subscriptions.map { s in
            [
                "name": s.name,
                "nickname": s.nickname,
                "amountMinor": s.amountMinor,
                "currency": s.currency,
                "periodCount": s.periodCount,
                "periodUnit": s.periodUnit,
                "firstPaymentEpochDay": s.firstPaymentEpochDay,
                "category": s.category,
                "cardNickname": s.cardNickname,
                "cardLast4": s.cardLast4,
                "remindDaysBefore": s.remindDaysBefore,
                "notes": s.notes,
                "active": s.active,
                "createdAt": s.createdAt,
                "planType": s.planType,
                "endEpochDay": s.endEpochDay.map { $0 as Any } ?? NSNull(),
                "includeInMonthly": s.includeInMonthly,
                "splitCount": s.splitCount,
                "regularAmountMinor": s.regularAmountMinor.map { $0 as Any } ?? NSNull(),
            ]
        }
        let root: [String: Any] = ["format": format, "version": version, "subscriptions": items]
        return (try? JSONSerialization.data(withJSONObject: root, options: [.prettyPrinted, .sortedKeys])) ?? Data()
    }

    // Lenient readers, like org.json's opt* methods.
    private static func string(_ o: [String: Any], _ key: String, _ fallback: String = "") -> String {
        if let s = o[key] as? String { return s }
        if let n = o[key] as? NSNumber { return n.stringValue }
        return fallback
    }

    private static func int64(_ o: [String: Any], _ key: String) -> Int64? {
        if let n = o[key] as? NSNumber {
            return CFGetTypeID(n) == CFBooleanGetTypeID() ? nil : n.int64Value
        }
        if let s = o[key] as? String { return Int64(s) }
        return nil
    }

    private static func bool(_ o: [String: Any], _ key: String, _ fallback: Bool) -> Bool {
        if let n = o[key] as? NSNumber { return n.boolValue }
        if let s = o[key] as? String { return s.lowercased() == "true" ? true : (s.lowercased() == "false" ? false : fallback) }
        return fallback
    }

    /// Throws BackupError.notABackup if the data isn't a Duedial (or Renewly) backup. Broken entries are skipped.
    static func decode(_ data: Data) throws -> [Subscription] {
        guard let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { throw BackupError.notABackup }
        let fmt = root["format"] as? String
        guard fmt == format || fmt == legacyFormat else { throw BackupError.notABackup }
        let items = root["subscriptions"] as? [Any] ?? []
        return items.compactMap { item -> Subscription? in
            guard let o = item as? [String: Any] else { return nil }
            let name = string(o, "name").trimmingCharacters(in: .whitespacesAndNewlines)
            let amount = int64(o, "amountMinor") ?? 0
            let type = PlanType.from(string(o, "planType", PlanType.RECURRING.rawValue))
            let amountOk = type == .FREE ? amount >= 0 : amount > 0
            guard !name.isEmpty, amountOk, let first = int64(o, "firstPaymentEpochDay") else { return nil }

            let period = o["periodUnit"] != nil
                ? BillingPeriod.of(Int(int64(o, "periodCount") ?? 1), string(o, "periodUnit"))
                : BillingPeriod.fromLegacyCycle(string(o, "cycle"))
            let last4 = String(string(o, "cardLast4").filter { $0.isASCII && $0.isNumber }.prefix(4))
            let currency = string(o, "currency", "INR")
            let defaultInclude = period.isMonthly || period.unit == .DAY || period.unit == .WEEK
            let regular = int64(o, "regularAmountMinor").flatMap { $0 > 0 ? $0 : nil }

            return Subscription(
                name: String(name.prefix(60)),
                nickname: String(string(o, "nickname").prefix(60)),
                amountMinor: amount,
                currency: currency.isEmpty ? "INR" : currency,
                periodCount: period.count,
                periodUnit: period.unit.rawValue,
                firstPaymentEpochDay: Int(first),
                category: Category.from(string(o, "category")).rawValue,
                cardNickname: String(string(o, "cardNickname").prefix(30)),
                cardLast4: last4.count == 4 ? last4 : "",
                remindDaysBefore: min(max(Int(int64(o, "remindDaysBefore") ?? 1), -1), 30),
                notes: String(string(o, "notes").prefix(500)),
                active: bool(o, "active", true),
                createdAt: int64(o, "createdAt") ?? Int64(Date().timeIntervalSince1970 * 1000),
                planType: type.rawValue,
                endEpochDay: int64(o, "endEpochDay").map { Int($0) },
                includeInMonthly: bool(o, "includeInMonthly", defaultInclude),
                splitCount: min(max(Int(int64(o, "splitCount") ?? 1), 1), 20),
                regularAmountMinor: regular
            )
        }
    }
}
