import SwiftUI

@main
struct DuedialApp: App {
    @StateObject private var store = Store()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .tint(Color(rgb: 0x0B5FA5))
                .onAppear {
                    Store.shared = store
                    Notifications.requestPermission()
                    Notifications.reschedule(store.subscriptions)
                }
                .onChange(of: scenePhase) { phase in
                    if phase == .active { Notifications.reschedule(store.subscriptions) }
                }
        }
    }
}

extension Color {
    init(rgb: UInt32) {
        self.init(
            red: Double((rgb >> 16) & 0xFF) / 255,
            green: Double((rgb >> 8) & 0xFF) / 255,
            blue: Double(rgb & 0xFF) / 255
        )
    }
}

/// Brand colour and first letter, like the Android app's default icon.
struct Badge: View {
    let name: String
    var size: CGFloat = 44

    var body: some View {
        ZStack {
            Circle().fill(Color(rgb: Brands.colorFor(name.isEmpty ? "?" : name)))
            Text(Brands.initial(name))
                .font(.system(size: size * 0.42, weight: .bold))
                .foregroundColor(.white)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

func toneColor(_ tone: Tone) -> Color {
    switch tone {
    case .warn: return .red
    case .muted: return .secondary.opacity(0.75)
    case .normal: return .secondary
    }
}

/// A label on the left and a value (with an optional note) on the right.
struct InfoRow: View {
    let label: String
    let value: String
    var note: String = ""

    var body: some View {
        HStack(alignment: .top) {
            Text(label).foregroundColor(.secondary)
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(value).fontWeight(.medium).multilineTextAlignment(.trailing)
                if !note.isEmpty {
                    Text(note).font(.caption).foregroundColor(.secondary).multilineTextAlignment(.trailing)
                }
            }
        }
    }
}
