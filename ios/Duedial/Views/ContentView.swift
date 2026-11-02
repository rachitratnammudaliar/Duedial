import SwiftUI

struct EditTarget: Identifiable {
    /// 0 means a new subscription.
    let id: Int64
}

struct ContentView: View {
    @EnvironmentObject private var store: Store
    @State private var today = Day.today()
    @State private var editing: EditTarget?
    @State private var showSettings = false
    @State private var filter: Category?

    private let timer = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    private var categories: [Category] {
        Array(Set(store.subscriptions.map(\.categoryValue))).sorted { a, b in
            (Category.allCases.firstIndex(of: a) ?? 0) < (Category.allCases.firstIndex(of: b) ?? 0)
        }
    }

    private var shown: [Subscription] {
        let far = Day(epochDay: Int.max / 4)
        let list = store.subscriptions.filter { filter == nil || $0.categoryValue == filter }
        return list.sorted { a, b in
            let ga = listGroup(a, today), gb = listGroup(b, today)
            if ga != gb { return ga < gb }
            switch store.sortMode {
            case .NEXT:
                let da = Plans.nextEvent(a.plan, today)?.date ?? far
                let db = Plans.nextEvent(b.plan, today)?.date ?? far
                if da != db { return da < db }
            case .PRICE:
                let pa = a.yearlyMinor(today) > 0 ? a.yearlyMinor(today) : a.shareMinor
                let pb = b.yearlyMinor(today) > 0 ? b.yearlyMinor(today) : b.shareMinor
                if pa != pb { return pa > pb }
            case .NAME:
                break
            }
            return a.name.lowercased() < b.name.lowercased()
        }
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    SummaryCard(all: store.subscriptions, today: today, mode: $store.totalMode)
                }
                if store.subscriptions.isEmpty {
                    Section {
                        VStack(spacing: 10) {
                            Text("No subscriptions yet").font(.headline)
                            Text("Add Netflix, a free trial, a student offer or a lifetime app. Everything stays on this iPhone.")
                                .font(.subheadline).foregroundColor(.secondary).multilineTextAlignment(.center)
                            Button("Add your first subscription") { editing = EditTarget(id: 0) }
                                .buttonStyle(.borderedProminent)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                    }
                } else {
                    Section {
                        Picker("Sort", selection: $store.sortMode) {
                            ForEach(SortMode.allCases) { Text($0.label).tag($0) }
                        }
                        .pickerStyle(.segmented)
                        if categories.count > 1 {
                            Picker("Category", selection: $filter) {
                                Text("All").tag(Category?.none)
                                ForEach(categories) { Text($0.label).tag(Category?.some($0)) }
                            }
                        }
                    }
                    let groups = Dictionary(grouping: shown) { listGroup($0, today) }
                    ForEach(groups.keys.sorted(), id: \.self) { group in
                        Section(header: Text(groupTitles[group] ?? "")) {
                            ForEach(groups[group] ?? []) { s in
                                NavigationLink(value: s.id) { SubscriptionRow(s: s, today: today) }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Duedial")
            .navigationDestination(for: Int64.self) { id in DetailView(id: id, today: today) }
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button { showSettings = true } label: { Image(systemName: "gearshape") }
                        .accessibilityLabel("Settings")
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { editing = EditTarget(id: 0) } label: { Image(systemName: "plus") }
                        .accessibilityLabel("Add subscription")
                }
            }
            .sheet(item: $editing) { target in
                EditView(existing: target.id == 0 ? nil : store.get(target.id), today: today)
            }
            .sheet(isPresented: $showSettings) { SettingsView() }
            .alert("Duedial", isPresented: Binding(get: { store.problem != nil }, set: { if !$0 { store.problem = nil } })) {
                Button("OK", role: .cancel) { store.problem = nil }
            } message: {
                Text(store.problem ?? "")
            }
        }
        .onReceive(timer) { _ in today = Day.today() }
    }
}

struct SummaryCard: View {
    let all: [Subscription]
    let today: Day
    @Binding var mode: TotalMode

    private func sorted(_ totals: [String: Int64]) -> [(String, Int64)] {
        totals.filter { $0.value > 0 }.sorted { a, b in
            if (a.key == "INR") != (b.key == "INR") { return a.key == "INR" }
            return a.key < b.key
        }
    }

    var body: some View {
        let start = today.firstOfMonth, end = today.lastOfMonth
        var totals: [String: Int64] = [:]
        for s in all {
            let value = mode == .MONTHLY_BILL ? s.monthlyBillMinor(today) : Plans.dueInTotalsMinor(s.plan, start, end)
            totals[s.currency, default: 0] += value
        }
        let running = all.filter { $0.status(today) == .upcoming || $0.status(today) == .ending }
        let countedList = all.filter { Plans.countsInMonthlyBill($0.plan, today) }
        var yearly: [String: Int64] = [:]
        for s in countedList { yearly[s.currency, default: 0] += s.yearlyMinor(today) }
        let trials = all.filter { $0.status(today) == .trial }.sorted { $0.firstPaymentDate < $1.firstPaymentDate }
        let next = all.compactMap { s in s.nextCharge(today).map { (s, $0) } }.min { $0.1 < $1.1 }

        return VStack(alignment: .leading, spacing: 4) {
            Text(mode == .THIS_MONTH ? "Due in \(today.monthName)" : "Monthly bill").font(.subheadline).foregroundColor(.secondary)
            let lines = sorted(totals)
            if lines.isEmpty {
                Text("Nothing to pay").font(.title2).fontWeight(.semibold)
            } else {
                ForEach(lines.map { $0.0 }, id: \.self) { currency in
                    Text(Money.format(totals[currency] ?? 0, currency)).font(.largeTitle).fontWeight(.semibold)
                }
            }
            if mode == .MONTHLY_BILL {
                let notCounted = running.count - countedList.count
                let y = sorted(yearly).map { Money.format($0.1, $0.0) }.joined(separator: " + ")
                Text("\(countedList.count) counted" + (notCounted > 0 ? " · \(notCounted) not counted" : "") + (y.isEmpty ? "" : " · \(y) a year"))
                    .font(.caption).foregroundColor(.secondary)
            } else {
                Text(mode.description).font(.caption).foregroundColor(.secondary)
            }
            if let n = next {
                Text("Next: \(n.0.name), \(Renewals.relativeLabel(Renewals.daysUntil(today, n.1)))").font(.subheadline).fontWeight(.medium)
            }
            if let t = trials.first {
                let end = t.firstPaymentDate
                Text("Trial: \(t.name) ends \(Renewals.relativeLabel(Renewals.daysUntil(today, end))). Cancel by \(end.plusDays(-1).format(.short))" + (trials.count > 1 ? " (+\(trials.count - 1) more)" : ""))
                    .font(.subheadline).fontWeight(.semibold).foregroundColor(.red)
            }
            Picker("Total", selection: $mode) {
                ForEach(TotalMode.allCases) { Text($0.label).tag($0) }
            }
            .pickerStyle(.segmented)
            .padding(.top, 6)
        }
        .padding(.vertical, 6)
    }
}

struct SubscriptionRow: View {
    let s: Subscription
    let today: Day

    var body: some View {
        let line = statusLine(s, today)
        let labels = priceLabels(s, today)
        let muted = s.status(today) == .ended || s.status(today) == .paused
        HStack(spacing: 12) {
            Badge(name: s.name)
            VStack(alignment: .leading, spacing: 2) {
                Text(s.name).font(.headline).lineLimit(1)
                Text(line.text).font(.subheadline).foregroundColor(toneColor(line.tone))
                    .fontWeight(line.tone == .warn ? .semibold : .regular).lineLimit(2)
                if !s.cardLabel.isEmpty {
                    Text(s.cardLabel).font(.caption).foregroundColor(.secondary).lineLimit(1)
                }
            }
            Spacer(minLength: 4)
            VStack(alignment: .trailing, spacing: 2) {
                Text(labels.0).font(.headline)
                if !labels.1.isEmpty { Text(labels.1).font(.caption).foregroundColor(.secondary) }
            }
        }
        .opacity(muted ? 0.6 : 1)
        .padding(.vertical, 2)
    }
}
