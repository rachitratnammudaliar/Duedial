import Foundation
import UserNotifications

/// All subscriptions, saved on this iPhone only as a Duedial backup file after every change.
@MainActor
final class Store: ObservableObject {
    @Published private(set) var subscriptions: [Subscription] = []
    @Published var problem: String?
    @Published var totalMode: TotalMode {
        didSet { UserDefaults.standard.set(totalMode.rawValue, forKey: "total_mode") }
    }
    @Published var sortMode: SortMode {
        didSet { UserDefaults.standard.set(sortMode.rawValue, forKey: "sort_mode") }
    }

    private let fileURL: URL

    init(directory: URL? = nil) {
        let dir = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Duedial", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        fileURL = dir.appendingPathComponent("subscriptions.json")
        totalMode = TotalMode(rawValue: UserDefaults.standard.string(forKey: "total_mode") ?? "") ?? .MONTHLY_BILL
        sortMode = SortMode(rawValue: UserDefaults.standard.string(forKey: "sort_mode") ?? "") ?? .NEXT
        load()
    }

    func get(_ id: Int64) -> Subscription? { subscriptions.first { $0.id == id } }

    private func load() {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return }
        do {
            let data = try Data(contentsOf: fileURL)
            subscriptions = withIds(try Backup.decode(data))
        } catch {
            let kept = fileURL.deletingLastPathComponent()
                .appendingPathComponent("subscriptions-unreadable-\(Int(Date().timeIntervalSince1970)).json")
            try? FileManager.default.moveItem(at: fileURL, to: kept)
            problem = "Your saved data couldn't be read, so Duedial started empty. The old file was kept."
        }
    }

    private func withIds(_ list: [Subscription]) -> [Subscription] {
        list.enumerated().map { i, s in
            var copy = s
            copy.id = Int64(i + 1)
            return copy
        }
    }

    private func persist(_ list: [Subscription]) {
        subscriptions = list
        do {
            try Backup.encode(list).write(to: fileURL, options: [.atomic, .completeFileProtection])
        } catch {
            problem = "Couldn't save: \(error.localizedDescription)"
        }
        Notifications.reschedule(list)
    }

    @discardableResult
    func save(_ s: Subscription) -> Int64 {
        if s.id == 0 {
            var copy = s
            copy.id = (subscriptions.map(\.id).max() ?? 0) + 1
            persist(subscriptions + [copy])
            return copy.id
        }
        persist(subscriptions.map { $0.id == s.id ? s : $0 })
        return s.id
    }

    func delete(_ id: Int64) { persist(subscriptions.filter { $0.id != id }) }

    /// Writes a backup file to share or save, and returns its location.
    func exportFile() throws -> URL {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("duedial-backup-\(f.string(from: Date())).json")
        try Backup.encode(subscriptions).write(to: url, options: .atomic)
        return url
    }

    /// Adds everything from a backup made on any device. Returns how many were added.
    func importData(_ data: Data) throws -> Int {
        let incoming = try Backup.decode(data)
        var next = (subscriptions.map(\.id).max() ?? 0) + 1
        let added = incoming.map { s -> Subscription in
            var copy = s
            copy.id = next
            next += 1
            return copy
        }
        persist(subscriptions + added)
        return added.count
    }
}

/// Reminders are scheduled ahead as local notifications, so they arrive even when the app is closed.
enum Notifications {
    /// iOS keeps at most 64 pending notifications per app.
    private static let limit = 60
    private static let daysAhead = 90

    static func requestPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            if granted {
                Task { @MainActor in
                    // Schedule with whatever is saved now that we're allowed to.
                    reschedule(Store.shared?.subscriptions ?? [])
                }
            }
        }
    }

    /// The reminders that would be shown over the next [daysAhead] days, earliest first.
    static func upcoming(_ subscriptions: [Subscription], from start: Day) -> [(Day, Subscription, PlanEvent)] {
        var result: [(Day, Subscription, PlanEvent)] = []
        for s in subscriptions where s.remindDaysBefore >= 0 {
            for offset in 0..<daysAhead {
                let day = start.plusDays(offset)
                if let event = Plans.reminderDue(s.plan, s.remindDaysBefore, day) {
                    result.append((day, s, event))
                }
            }
        }
        return result.sorted { $0.0 < $1.0 }
    }

    private static let lateKey = "late_reminders"

    private static func shownLate(_ id: String, _ day: Day) -> Bool {
        let stored = UserDefaults.standard.stringArray(forKey: lateKey) ?? []
        return stored.first == "\(day.epochDay)" && stored.contains(id)
    }

    private static func markShownLate(_ id: String, _ day: Day) {
        var stored = UserDefaults.standard.stringArray(forKey: lateKey) ?? []
        if stored.first != "\(day.epochDay)" { stored = ["\(day.epochDay)"] }
        stored.append(id)
        UserDefaults.standard.set(stored, forKey: lateKey)
    }

    static func reschedule(_ subscriptions: [Subscription]) {
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()
        let now = Date()
        for (day, s, event) in upcoming(subscriptions, from: Day.today()).prefix(limit) {
            var when = Calendar.current.dateComponents([.year, .month, .day], from: day.localDate)
            when.hour = 9
            when.minute = 0
            let id = "\(s.name)-\(s.firstPaymentEpochDay)-\(day.epochDay)-\(event.kind)"
            // A reminder for today added after 9:00 is shown in a minute instead, once.
            if let fire = Calendar.current.date(from: when), fire < now {
                guard day == Day.today(), !shownLate(id, day) else { continue }
                markShownLate(id, day)
                when = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: now.addingTimeInterval(60))
            }
            let content = UNMutableNotificationContent()
            content.title = ReminderText.title(s)
            content.body = ReminderText.body(s, event, day)
            content.sound = .default
            let request = UNNotificationRequest(identifier: id, content: content, trigger: UNCalendarNotificationTrigger(dateMatching: when, repeats: false))
            center.add(request)
        }
    }
}

extension Store {
    /// The app's store, for rescheduling after permission is granted.
    static weak var shared: Store?
}
