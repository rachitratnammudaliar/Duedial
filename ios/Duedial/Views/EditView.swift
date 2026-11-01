import SwiftUI

/// Add or edit. The same fields and rules as the Android app.
struct EditView: View {
    @EnvironmentObject private var store: Store
    @Environment(\.dismiss) private var dismiss

    let existing: Subscription?
    let today: Day

    @State private var type: PlanType
    @State private var name: String
    @State private var nickname: String
    @State private var amount: String
    @State private var regular: String
    @State private var currency: String
    @State private var periodCount: Int
    @State private var periodUnit: PeriodUnit
    @State private var customPeriod: Bool
    @State private var firstDate: Date
    @State private var hasEnd: Bool
    @State private var endDate: Date
    @State private var category: Category
    @State private var categoryTouched: Bool
    @State private var cardNickname: String
    @State private var cardLast4: String
    @State private var remind: Int
    @State private var remindTouched: Bool
    @State private var split: Int
    @State private var include: Bool
    @State private var includeTouched: Bool
    @State private var notes: String
    @State private var active: Bool
    @State private var showErrors = false

    init(existing: Subscription?, today: Day) {
        self.existing = existing
        self.today = today
        let e = existing
        let t = e?.type ?? .RECURRING
        _type = State(initialValue: t)
        _name = State(initialValue: e?.name ?? "")
        _nickname = State(initialValue: e?.nickname ?? "")
        _amount = State(initialValue: (e != nil && t != .FREE && e!.amountMinor > 0) ? Money.minorToInput(e!.amountMinor) : "")
        _regular = State(initialValue: e?.regularAmountMinor.map { Money.minorToInput($0) } ?? "")
        _currency = State(initialValue: e?.currency ?? "INR")
        _periodCount = State(initialValue: e?.periodCount ?? 1)
        _periodUnit = State(initialValue: PeriodUnit.from(e?.periodUnit ?? "MONTH"))
        _customPeriod = State(initialValue: e.map { !BillingPeriod.presets.contains($0.period) } ?? false)
        _firstDate = State(initialValue: (e?.firstPaymentDate ?? today).localDate)
        _hasEnd = State(initialValue: e?.endEpochDay != nil)
        _endDate = State(initialValue: (e?.endDate ?? today.plusMonths(1)).localDate)
        _category = State(initialValue: e?.categoryValue ?? .OTHER)
        _categoryTouched = State(initialValue: e != nil)
        _cardNickname = State(initialValue: e?.cardNickname ?? "")
        _cardLast4 = State(initialValue: e?.cardLast4 ?? "")
        _remind = State(initialValue: e?.remindDaysBefore ?? 1)
        _remindTouched = State(initialValue: e != nil)
        _split = State(initialValue: min(max(e?.splitCount ?? 1, 1), 20))
        _include = State(initialValue: e?.includeInMonthly ?? true)
        _includeTouched = State(initialValue: e != nil)
        _notes = State(initialValue: e?.notes ?? "")
        _active = State(initialValue: e?.active ?? true)
    }

    private var isPaidPlan: Bool { type == .RECURRING || type == .TRIAL }
    private var period: BillingPeriod? { (1...999).contains(periodCount) ? BillingPeriod(periodCount, periodUnit) : nil }
    private var firstDay: Day { Day(localDate: firstDate) }
    private var endDay: Day { Day(localDate: endDate) }
    private var amountMinor: Int64? { Money.parseToMinor(amount) }
    private var regularMinor: Int64? { regular.isEmpty ? nil : Money.parseToMinor(regular) }

    private var nameError: Bool { name.trimmingCharacters(in: .whitespaces).isEmpty }
    private var amountError: Bool { type != .FREE && amountMinor == nil }
    private var regularError: Bool { !regular.isEmpty && regularMinor == nil }
    private var last4Error: Bool { !Money.isValidLast4(cardLast4) }
    private var endError: Bool { hasEnd && (type == .RECURRING || type == .FREE) && endDay < firstDay }
    private var invalid: Bool { nameError || amountError || regularError || (isPaidPlan && period == nil) || last4Error || endError }

    private func applyDefaults() {
        if !includeTouched, let p = period { include = Plans.defaultIncludeInMonthly(type, p) }
        if !remindTouched { remind = type == .TRIAL ? 2 : 1 }
    }

    private func save() {
        if invalid {
            showErrors = true
            return
        }
        let p = period ?? .monthly
        var endEpoch: Int?
        switch type {
        case .RECURRING, .FREE: endEpoch = hasEnd ? endDay.epochDay : nil
        case .TRIAL: endEpoch = hasEnd ? firstDay.epochDay - 1 : nil
        case .LIFETIME: endEpoch = nil
        }
        let s = Subscription(
            id: existing?.id ?? 0,
            name: name.trimmingCharacters(in: .whitespaces),
            nickname: nickname.trimmingCharacters(in: .whitespaces),
            amountMinor: type == .FREE ? 0 : (amountMinor ?? 0),
            currency: currency,
            periodCount: p.count,
            periodUnit: p.unit.rawValue,
            firstPaymentEpochDay: firstDay.epochDay,
            category: category.rawValue,
            cardNickname: type == .FREE ? "" : cardNickname.trimmingCharacters(in: .whitespaces),
            cardLast4: type == .FREE ? "" : cardLast4,
            remindDaysBefore: type == .LIFETIME ? -1 : remind,
            notes: notes.trimmingCharacters(in: .whitespacesAndNewlines),
            active: active,
            createdAt: existing?.createdAt ?? Int64(Date().timeIntervalSince1970 * 1000),
            planType: type.rawValue,
            endEpochDay: endEpoch,
            includeInMonthly: isPaidPlan && include,
            splitCount: isPaidPlan ? split : 1,
            regularAmountMinor: regularMinor
        )
        store.save(s)
        dismiss()
    }

    private func errorText(_ show: Bool, _ text: String) -> some View {
        Group {
            if show && showErrors { Text(text).font(.caption).foregroundColor(.red) }
        }
    }

    private var priceTitle: String {
        switch type {
        case .RECURRING: return "Price"
        case .TRIAL: return "Price after the trial"
        case .FREE: return "Normal price (optional)"
        case .LIFETIME: return "Price paid"
        }
    }

    private var firstDateTitle: String {
        switch type {
        case .RECURRING: return "First payment"
        case .TRIAL: return "Trial ends (first charge)"
        case .FREE: return "Free since"
        case .LIFETIME: return "Purchase date"
        }
    }

    private var includeNote: String {
        guard let a = amountMinor, let p = period else { return "Add it to the monthly total" }
        let share = divideRoundingHalfUp(a, Int64(split))
        var note = p.isMonthly
            ? "Adds \(Money.format(share, currency)) a month"
            : "Adds \(Money.format(Renewals.monthlyEquivalentMinor(share, p), currency)) a month (\(Money.format(share, currency)) \(p.label.lowercased()), spread out)"
        if type == .TRIAL { note += ". Starts counting after the trial." }
        return note
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Type", selection: $type) {
                        ForEach(PlanType.allCases) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    Text(type.description).font(.caption).foregroundColor(.secondary)
                }

                Section {
                    HStack(spacing: 12) {
                        Badge(name: name, size: 40)
                        TextField("Name, e.g. Netflix", text: $name)
                            .textInputAutocapitalization(.words)
                            .onChange(of: name) { value in
                                if value.count > 60 { name = String(value.prefix(60)) }
                                if existing == nil && !categoryTouched { category = Brands.match(name)?.category ?? .OTHER }
                            }
                    }
                    errorText(nameError, "Enter a name")
                    TextField(type == .FREE ? "Nickname, e.g. Student offer" : "Nickname, e.g. Family plan", text: $nickname)
                }

                Section(priceTitle) {
                    if type == .FREE {
                        TextField("Normally costs", text: $regular).keyboardType(.decimalPad)
                        errorText(regularError, "Enter an amount above zero, up to 2 decimals")
                    } else {
                        TextField("Amount", text: $amount).keyboardType(.decimalPad)
                        errorText(amountError, "Enter an amount above zero, up to 2 decimals")
                    }
                    Picker("Currency", selection: $currency) {
                        ForEach(Money.currencies, id: \.self) { Text($0).tag($0) }
                    }
                    .pickerStyle(.segmented)
                }

                if isPaidPlan {
                    Section("Billing period") {
                        Picker("Repeats", selection: Binding<BillingPeriod?>(
                            get: { customPeriod ? nil : BillingPeriod.presets.first { $0.count == periodCount && $0.unit == periodUnit } },
                            set: { value in
                                if let p = value {
                                    customPeriod = false
                                    periodCount = p.count
                                    periodUnit = p.unit
                                } else {
                                    customPeriod = true
                                }
                            }
                        )) {
                            ForEach(BillingPeriod.presets, id: \.self) { Text($0.label).tag(BillingPeriod?.some($0)) }
                            Text("Custom").tag(BillingPeriod?.none)
                        }
                        if customPeriod {
                            Stepper("Every \(periodCount) \(periodCount == 1 ? periodUnit.singular : periodUnit.plural)", value: $periodCount, in: 1...999)
                            Picker("Unit", selection: $periodUnit) {
                                ForEach(PeriodUnit.allCases) { Text($0.plural.capitalized).tag($0) }
                            }
                            .pickerStyle(.segmented)
                        }
                    }
                }

                Section("Dates") {
                    DatePicker(firstDateTitle, selection: $firstDate, displayedComponents: .date)
                    switch type {
                    case .RECURRING:
                        if let p = period {
                            let next = Renewals.nextRenewal(firstDay, p, today)
                            Text("\(p.label). Next payment: \(next.format(.long)), \(Renewals.relativeLabel(Renewals.daysUntil(today, next))).")
                                .font(.caption).foregroundColor(.secondary)
                        }
                        Toggle("Cancelled or fixed term", isOn: $hasEnd)
                        if hasEnd {
                            DatePicker("Active until", selection: $endDate, displayedComponents: .date)
                            errorText(endError, "The end date can't be before the first payment.")
                        }
                    case .TRIAL:
                        Text("Cancel by \(firstDay.plusDays(-1).format(.long)) to avoid being charged. You'll get a reminder before.")
                            .font(.caption).foregroundColor(.secondary)
                        Toggle("Already cancelled", isOn: $hasEnd)
                    case .FREE:
                        Toggle("Free until a date", isOn: $hasEnd)
                        if hasEnd {
                            DatePicker("Free until", selection: $endDate, displayedComponents: .date)
                            errorText(endError, "The end date can't be before the start.")
                        } else {
                            Text("No end date: free for as long as it lasts.").font(.caption).foregroundColor(.secondary)
                        }
                    case .LIFETIME:
                        Text("Paid once. No renewals and not part of the monthly bill.").font(.caption).foregroundColor(.secondary)
                    }
                }

                if isPaidPlan {
                    Section("Cost") {
                        Stepper(split == 1 ? "Just me" : "Split between \(split) people", value: $split, in: 1...6)
                        if split > 1, let a = amountMinor {
                            Text("Your share: \(Money.format(divideRoundingHalfUp(a, Int64(split)), currency)) per payment.")
                                .font(.caption).foregroundColor(.secondary)
                        }
                        TextField("Regular price (optional)", text: $regular).keyboardType(.decimalPad)
                        errorText(regularError, "Enter an amount above zero, up to 2 decimals")
                        Toggle(isOn: Binding(get: { include }, set: { include = $0; includeTouched = true })) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Count in monthly bill")
                                Text(includeNote).font(.caption).foregroundColor(.secondary)
                            }
                        }
                    }
                }

                Section("Category") {
                    Picker("Category", selection: Binding(get: { category }, set: { category = $0; categoryTouched = true })) {
                        ForEach(Category.allCases) { Text($0.label).tag($0) }
                    }
                }

                if type != .FREE {
                    Section {
                        TextField("Card or method, e.g. HDFC Visa, UPI", text: $cardNickname)
                        TextField("Last 4 digits (optional)", text: $cardLast4)
                            .keyboardType(.numberPad)
                            .onChange(of: cardLast4) { value in
                                let digits = String(value.filter { $0.isASCII && $0.isNumber }.prefix(4))
                                if digits != value { cardLast4 = digits }
                            }
                        errorText(last4Error, "Enter exactly 4 digits, or leave it empty.")
                    } header: {
                        Text("Payment method")
                    } footer: {
                        Text("Only the last 4 digits are stored, never the full card number.")
                    }
                }

                if type != .LIFETIME {
                    Section {
                        Picker(type == .TRIAL ? "Before the trial ends" : (type == .FREE ? "Before the free period ends" : "Remind me"),
                               selection: Binding(get: { remind }, set: { remind = $0; remindTouched = true })) {
                            ForEach(reminderOptions.map { $0.0 }, id: \.self) { days in Text(reminderLabel(days)).tag(days) }
                        }
                    } header: {
                        Text("Reminder")
                    }
                }

                Section {
                    TextField("Notes (optional)", text: $notes, axis: .vertical).lineLimit(2...6)
                    Toggle(isOn: $active) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Active")
                            Text("Turn off to pause: no reminders, not counted").font(.caption).foregroundColor(.secondary)
                        }
                    }
                }
            }
            .navigationTitle(existing == nil ? "New" : "Edit")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Save") { save() } }
            }
            .onChange(of: type) { _ in applyDefaults() }
            .onChange(of: periodCount) { _ in applyDefaults() }
            .onChange(of: periodUnit) { _ in applyDefaults() }
        }
    }
}
