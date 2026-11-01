import SwiftUI
import UniformTypeIdentifiers
import UserNotifications

struct SettingsView: View {
    @EnvironmentObject private var store: Store
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase

    @State private var notificationsOn = true
    @State private var importing = false
    @State private var exportURL: URL?
    @State private var message: String?

    private var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }

    private func refreshNotificationStatus() {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            let on = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
            DispatchQueue.main.async { notificationsOn = on }
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(notificationsOn
                         ? "On. Duedial reminds you at 9:00 before payments, when trials end and when free periods end, even when the app is closed."
                         : "Notifications are off, so reminders can't be shown.")
                    if !notificationsOn {
                        Button("Turn on notifications") {
                            if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                        }
                    }
                } header: {
                    Text("Reminders")
                }

                Section {
                    Text("Backups work across all versions of Duedial: export on Android and import here, or the other way round.")
                    if let url = exportURL {
                        ShareLink(item: url) { Label("Export backup", systemImage: "square.and.arrow.up") }
                    }
                    Button { importing = true } label: { Label("Import backup", systemImage: "square.and.arrow.down") }
                    if let message = message {
                        Text(message).font(.footnote).foregroundColor(.secondary)
                    }
                } header: {
                    Text("Backup")
                }

                Section {
                    Text("Duedial works completely offline. No account, no ads and no tracking. Your data is stored only on this iPhone.")
                } header: {
                    Text("Privacy")
                }

                Section {
                    Text("Duedial \(version)")
                    Link("github.com/rachitratnammudaliar/duedial", destination: URL(string: "https://github.com/rachitratnammudaliar/duedial")!)
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
            }
            .onAppear {
                refreshNotificationStatus()
                exportURL = try? store.exportFile()
            }
            .onChange(of: scenePhase) { phase in
                if phase == .active { refreshNotificationStatus() }
            }
            .onChange(of: store.subscriptions) { _ in
                exportURL = try? store.exportFile()
            }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.json, .plainText, .data]) { result in
                switch result {
                case .success(let url):
                    let scoped = url.startAccessingSecurityScopedResource()
                    defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                    do {
                        let data = try Data(contentsOf: url)
                        let count = try store.importData(data)
                        message = "Added \(count) subscriptions from the backup."
                    } catch {
                        message = error.localizedDescription
                    }
                case .failure(let error):
                    message = error.localizedDescription
                }
            }
        }
    }
}
