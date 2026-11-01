import XCTest
@testable import Duedial

/// Mirrors the Kotlin tests (RenewalsTest, PlansTest, BackupTest) so every platform calculates the same.
final class DuedialTests: XCTestCase {
    private let today = Day(2026, 11, 1)
    private func d(_ m: Int, _ day: Int, _ y: Int = 2026) -> Day { Day(y, m, day) }

    private func plan(
        type: PlanType = .RECURRING, amount: Int64 = 60000, period: BillingPeriod = .monthly,
        start: Day? = nil, end: Day? = nil, split: Int = 1, include: Bool? = nil, active: Bool = true, regular: Int64? = nil
    ) -> Plan {
        Plan(type: type, amountMinor: amount, period: period, start: start ?? d(1, 15), end: end, split: split,
             includeInMonthly: include ?? Plans.defaultIncludeInMonthly(type, period), active: active, regularAmountMinor: regular)
    }

    // MARK: Renewals

    func testEpochDayMatchesJavaTime() {
        XCTAssertEqual(Day(1970, 1, 1).epochDay, 0)
        XCTAssertEqual(Day(2026, 11, 1).epochDay, 20758)
    }

    func testMonthEndsDoNotDrift() {
        let anchor = d(1, 31)
        XCTAssertEqual(Renewals.nthDate(anchor, .monthly, 1), d(2, 28))
        XCTAssertEqual(Renewals.nthDate(anchor, .monthly, 2), d(3, 31))
        XCTAssertEqual(Renewals.nthDate(Day(2024, 2, 29), .yearly, 1), Day(2025, 2, 28))
        XCTAssertEqual(Renewals.nthDate(Day(2024, 2, 29), .yearly, 4), Day(2028, 2, 29))
    }

    func testNextAndPreviousPayment() {
        XCTAssertEqual(Renewals.nextRenewal(d(1, 15), .monthly, today), d(11, 15))
        XCTAssertEqual(Renewals.previousPayment(d(1, 15), .monthly, today), d(10, 15))
        XCTAssertEqual(Renewals.nextRenewal(d(1, 15), .quarterly, today), d(1, 15, 2027))
        XCTAssertEqual(Renewals.nextRenewal(d(11, 1), .weekly, today), d(11, 1))
        XCTAssertNil(Renewals.previousPayment(d(12, 1), .monthly, today))
        XCTAssertEqual(Renewals.paymentsMade(d(1, 15), .monthly, today), 10)
        XCTAssertEqual(Renewals.chargesBetween(d(1, 1), .weekly, d(11, 1), d(11, 30)), 4)
    }

    func testEquivalents() {
        XCTAssertEqual(Renewals.monthlyEquivalentMinor(149900, .yearly), 12492)
        XCTAssertEqual(Renewals.monthlyEquivalentMinor(90000, .quarterly), 30000)
        XCTAssertEqual(Renewals.yearlyEquivalentMinor(64900, .monthly), 778800)
    }

    func testMoney() {
        XCTAssertEqual(Money.parseToMinor("649"), 64900)
        XCTAssertEqual(Money.parseToMinor("199,5"), 19950)
        XCTAssertEqual(Money.parseToMinor(".5"), 50)
        XCTAssertNil(Money.parseToMinor("0"))
        XCTAssertNil(Money.parseToMinor("1.234"))
        XCTAssertNil(Money.parseToMinor("-5"))
        XCTAssertNil(Money.parseToMinor("abc"))
        XCTAssertEqual(Money.minorToInput(19950), "199.5")
        XCTAssertEqual(Money.minorToInput(64900), "649")
        XCTAssertEqual(Money.minorToInput(1299), "12.99")
        XCTAssertEqual(Money.minorToInput(1205), "12.05")
        XCTAssertTrue(Money.isValidLast4(""))
        XCTAssertTrue(Money.isValidLast4("4821"))
        XCTAssertFalse(Money.isValidLast4("482"))
    }

    func testBrandsMatchAndColoursAreStable() {
        XCTAssertEqual(Brands.match("Netflix Premium")?.category, .ENTERTAINMENT)
        XCTAssertEqual(Brands.colorFor("Netflix"), 0xE50914)
        XCTAssertEqual(Brands.javaHash("hello"), 99162322)
        XCTAssertEqual(Brands.initial("  netflix"), "N")
    }

    // MARK: Plans

    func testLongerPlansAreSpreadWhenIncluded() {
        XCTAssertEqual(Plans.monthlyBillMinor(plan(amount: 90000, period: .quarterly, include: true), today), 30000)
        XCTAssertEqual(Plans.monthlyBillMinor(plan(amount: 120000, period: .yearly, include: true), today), 10000)
        XCTAssertEqual(Plans.monthlyBillMinor(plan(amount: 120000, period: .yearly, include: false), today), 0)
    }

    func testSplitUsesYourShare() {
        let p = plan(amount: 23900, split: 2)
        XCTAssertEqual(Plans.shareMinor(p), 11950)
        XCTAssertEqual(Plans.monthlyBillMinor(p, today), 11950)
    }

    func testTrial() {
        let t = plan(type: .TRIAL, amount: 29900, start: d(11, 3))
        XCTAssertEqual(Plans.status(t, today), .trial)
        XCTAssertEqual(Plans.monthlyBillMinor(t, today), 0)
        XCTAssertEqual(Plans.reminderDue(t, 2, today), PlanEvent(date: d(11, 3), kind: .trialEnds))
        XCTAssertNil(Plans.reminderDue(t, 1, today))
        XCTAssertEqual(Plans.status(t, d(11, 10)), .upcoming)
        XCTAssertEqual(Plans.paymentsMade(t, d(11, 10)), 1)

        let cancelled = plan(type: .TRIAL, start: d(11, 3), end: d(11, 2))
        XCTAssertEqual(Plans.status(cancelled, today), .trialCancelled)
        XCTAssertNil(Plans.nextCharge(cancelled, today))
        XCTAssertEqual(Plans.status(cancelled, d(11, 5)), .ended)
        XCTAssertEqual(Plans.paymentsMade(cancelled, d(12, 31)), 0)
    }

    func testFreeAndLifetime() {
        let free = plan(type: .FREE, amount: 0, regular: 1000)
        XCTAssertEqual(Plans.status(free, today), .free)
        XCTAssertNil(Plans.nextCharge(free, today))
        XCTAssertEqual(Plans.savingMinor(free), 1000)
        let student = plan(type: .FREE, amount: 0, end: d(11, 8))
        XCTAssertEqual(Plans.reminderDue(student, 7, today), PlanEvent(date: d(11, 8), kind: .freeEnds))

        let lifetime = plan(type: .LIFETIME, amount: 129900, start: d(3, 1))
        XCTAssertEqual(Plans.status(lifetime, today), .owned)
        XCTAssertEqual(Plans.paidSoFarMinor(lifetime, today), 129900)
        XCTAssertEqual(Plans.monthlyBillMinor(lifetime, today), 0)
        XCTAssertEqual(Plans.dueBetweenMinor(lifetime, d(3, 1), d(3, 31)), 129900)
    }

    func testCancelledRunsUntilEnd() {
        let p = plan(start: d(1, 15), end: d(11, 14))
        XCTAssertEqual(Plans.status(p, today), .ending)
        XCTAssertNil(Plans.nextCharge(p, today))
        XCTAssertEqual(Plans.nextEvent(p, today), PlanEvent(date: d(11, 14), kind: .subscriptionEnds))
        XCTAssertEqual(Plans.paymentsMade(p, d(12, 31)), 10)
        XCTAssertEqual(Plans.status(p, d(11, 15)), .ended)
    }

    func testDueThisMonthIgnoresToggle() {
        let yearly = plan(amount: 149900, period: .yearly, start: d(11, 20, 2025), include: false)
        XCTAssertEqual(Plans.monthlyBillMinor(yearly, today), 0)
        XCTAssertEqual(Plans.dueBetweenMinor(yearly, d(11, 1), d(11, 30)), 149900)
    }

    func testPausedCountsNothing() {
        let p = plan(active: false)
        XCTAssertEqual(Plans.status(p, today), .paused)
        XCTAssertNil(Plans.reminderDue(p, 0, d(11, 15)))
        XCTAssertEqual(Plans.dueBetweenMinor(p, d(11, 1), d(11, 30)), 0)
    }

    // MARK: Backup

    func testBackupRoundTripAndCompatibility() throws {
        let s = Subscription(name: "Spotify", amountMinor: 23900, firstPaymentEpochDay: 20000, cardNickname: "HDFC", cardLast4: "4821",
                             planType: "RECURRING", endEpochDay: 20300, includeInMonthly: true, splitCount: 2, regularAmountMinor: 30000)
        let back = try Backup.decode(Backup.encode([s]))
        XCTAssertEqual(back.count, 1)
        XCTAssertEqual(back[0].name, "Spotify")
        XCTAssertEqual(back[0].splitCount, 2)
        XCTAssertEqual(back[0].endEpochDay, 20300)
        XCTAssertEqual(back[0].regularAmountMinor, 30000)

        let android = """
        {"format":"renewly-backup","version":2,"subscriptions":[
          {"name":"Netflix","amountMinor":64900,"periodCount":1,"periodUnit":"MONTH","firstPaymentEpochDay":20000},
          {"name":"Prime","amountMinor":149900,"periodCount":1,"periodUnit":"YEAR","firstPaymentEpochDay":20000},
          {"name":"","amountMinor":100,"firstPaymentEpochDay":1},
          {"name":"Gym","amountMinor":9000,"cycle":"QUARTERLY","firstPaymentEpochDay":20000}
        ]}
        """
        let list = try Backup.decode(Data(android.utf8))
        XCTAssertEqual(list.map(\.name), ["Netflix", "Prime", "Gym"])
        XCTAssertTrue(list[0].includeInMonthly)
        XCTAssertFalse(list[1].includeInMonthly)
        XCTAssertEqual(list[2].periodCount, 3)
        XCTAssertThrowsError(try Backup.decode(Data("not json".utf8)))
    }
}
