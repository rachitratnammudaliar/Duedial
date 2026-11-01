import SwiftUI

struct DetailView: View {
    @EnvironmentObject private var store: Store
    @Environment(\.dismiss) private var dismiss
    let id: Int64
    let today: Day

    @State private var editing: EditTarget?
    @State private var confirmDelete = false
    @State private var cancelling = false
    @State private var cancelEnd = Date()

    var body: some View {
        if let s = store.get(id) {
            content(s)
        } else {
            Text("This subscription was deleted.").foregroundColor(.secondary)
        }
    }

    private func dateRows(_ s: Subscription) -> [(String, String, String)] {
        let status = s.status(today)
        let end = s.endDate
        let share = Money.format(s.shareMinor, s.currency)
        var rows: [(String, String, String)] = []
        switch status {
        case .trial:
            let trialEnd = s.firstPaymentDate
            rows.append(("Trial ends", trialEnd.format(.long), Renewals.relativeLabel(Renewals.daysUntil(today, trialEnd))))
            rows.append(("Cancel by", trialEnd.plusDays(-1).format(.long), "to avoid being charged"))
            rows.append(("Then costs", share, s.period.label))
        case .trialCancelled:
            rows.append(("Trial", "Cancelled", "no charge will happen"))
            rows.append(("Free until", end?.format(.long) ?? "", ""))
        case .free:
            rows.append(("Free until", end?.format(.long) ?? "No end date", end.map { Renewals.relativeLabel(Renewals.daysUntil(today, $0)) } ?? ""))
            rows.append(("Free since", s.firstPaymentDate.format(.long), ""))
        case .owned:
            let date = s.firstPaymentDate
            rows.append((date > today ? "Purchase on" : "Bought", date.format(.long), ""))
            rows.append(("Paid", share, "once"))
        default:
            let next = s.nextCharge(today)
            if status != .ended && status != .paused {
                rows.append((
                    "Next payment",
                    next?.format(.long) ?? "None",
                    next.map { Renewals.relativeLabel(Renewals.daysUntil(today, $0)) } ?? "no more payments"
                ))
            }
            if let end = end { rows.append((status == .ended ? "Ended" : "Active until", end.format(.long), "")) }
            rows.append(("Last payment", s.previousCharge(today)?.format(.long) ?? "Not yet", ""))
            rows.append(("First payment", s.firstPaymentDate.format(.long), ""))
            let paid = s.paymentsMade(today)
            rows.append((
                "Paid so far",
                paid == 0 ? "Nothing yet" : Money.format(Plans.paidSoFarMinor(s.plan, today), s.currency),
                paid == 0 ? "" : "\(paid) payment\(paid == 1 ? "" : "s"), at today's price"
            ))
        }
        return rows
    }

    private func includeNote(_ s: Subscription) -> String {
        let bill = s.monthlyBillMinor(today)
        if !s.includeInMonthly { return "Not counted" }
        if bill == 0 { return "Counted when it's running" }
        if s.period.isMonthly { return "Adds \(Money.format(bill, s.currency))" }
        return "Adds \(Money.format(bill, s.currency)) a month (\(s.period.label.lowercased()), spread out)"
    }

    @ViewBuilder
    private func content(_ s: Subscription) -> some View {
        let status = s.status(today)
        let type = s.type
        let labels = priceLabels(s, today)
        let line = statusLine(s, today)
        let paidPlan = type == .RECURRING || type == .TRIAL

        List {
            Section {
                HStack(spacing: 16) {
                    Badge(name: s.name, size: 60)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(labels.0).font(.title).fontWeight(.semibold)
                        if !labels.1.isEmpty { Text(labels.1).foregroundColor(.secondary) }
                        Text([type.label, s.nickname, s.categoryValue.label].filter { !$0.isEmpty }.joined(separator: " · "))
                            .font(.subheadline).foregroundColor(.secondary)
                    }
                }
                Text(line.text).foregroundColor(toneColor(line.tone)).fontWeight(.medium)
            }

            Section {
                ForEach(Array(dateRows(s).enumerated()), id: \.offset) { item in
                    InfoRow(label: item.element.0, value: item.element.1, note: item.element.2)
                }
            }

            if paidPlan || s.regularAmountMinor != nil || s.splitCount > 1 {
                Section("Cost") {
                    if s.splitCount > 1 {
                        InfoRow(label: "Your share", value: Money.format(s.shareMinor, s.currency),
                                note: "\(Money.format(s.amountMinor, s.currency)) split \(s.splitCount) ways")
                    }
                    if paidPlan {
                        InfoRow(label: "Per month", value: Money.format(Renewals.monthlyEquivalentMinor(s.shareMinor, s.period), s.currency),
                                note: s.period.isMonthly ? "" : "average")
                        InfoRow(label: "Per year", value: Money.format(Renewals.yearlyEquivalentMinor(s.shareMinor, s.period), s.currency))
                        Toggle(isOn: Binding(
                            get: { s.includeInMonthly },
                            set: { value in
                                var copy = s
                                copy.includeInMonthly = value
                                store.save(copy)
                            }
                        )) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Count in monthly bill")
                                Text(includeNote(s)).font(.caption).foregroundColor(.secondary)
                            }
                        }
                    }
                    if let saving = Plans.savingMinor(s.plan) {
                        InfoRow(label: "You save", value: Money.format(saving, s.currency),
                                note: "per payment vs \(Money.format(s.regularAmountMinor ?? 0, s.currency)) regular price")
                    }
                }
            }

            Section {
                InfoRow(label: "Payment method", value: s.cardLabel.isEmpty ? "Not set" : s.cardLabel)
                if type != .LIFETIME { InfoRow(label: "Reminder", value: reminderLabel(s.remindDaysBefore)) }
            }

            if !s.notes.isEmpty {
                Section("Notes") { Text(s.notes) }
            }

            Section {
                if paidPlan && s.endEpochDay == nil && s.active {
                    Button(status == .trial ? "I cancelled the trial" : "Mark as cancelled") {
                        let next = s.nextCharge(today)
                        var end = next?.plusDays(-1) ?? today
                        if end < today { end = today }
                        cancelEnd = end.localDate
                        cancelling = true
                    }
                }
                if paidPlan && s.endEpochDay != nil {
                    Button(status == .ended ? "Resume subscription" : "Undo cancel") {
                        var copy = s
                        copy.endEpochDay = nil
                        store.save(copy)
                    }
                }
                Button(s.active ? "Pause" : "Unpause") {
                    var copy = s
                    copy.active.toggle()
                    store.save(copy)
                }
                Button("Delete", role: .destructive) { confirmDelete = true }
            }
        }
        .navigationTitle(s.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button("Edit") { editing = EditTarget(id: s.id) }
            }
        }
        .sheet(item: $editing) { target in
            EditView(existing: store.get(target.id), today: today)
        }
        .sheet(isPresented: $cancelling) {
            NavigationStack {
                Form {
                    Text(status == .trial
                         ? "Duedial will stop reminding you and won't count a charge. You can keep using it until:"
                         : "No payments will be counted after this date. Usually it's the last day of the period you've paid for:")
                    DatePicker("Active until", selection: $cancelEnd, displayedComponents: .date)
                }
                .navigationTitle(status == .trial ? "Trial cancelled" : "Mark as cancelled")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Back") { cancelling = false } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Mark cancelled") {
                            var copy = s
                            copy.endEpochDay = Day(localDate: cancelEnd).epochDay
                            store.save(copy)
                            cancelling = false
                        }
                    }
                }
            }
            .presentationDetents([.medium])
        }
        .alert("Delete \(s.name)?", isPresented: $confirmDelete) {
            Button("Delete", role: .destructive) {
                store.delete(s.id)
                dismiss()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes it and its history from Duedial. It can't be undone. To stop tracking without losing history, mark it as cancelled instead.")
        }
    }
}
