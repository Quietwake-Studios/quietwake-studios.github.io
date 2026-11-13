import QuietUI
import SwiftUI

@main
struct QuietWordsApp: App {
    @State private var app = AppModel(persistence: PersistenceService(store: Self.store))
    @State private var showsSplash = Self.wantsSplash
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ZStack {
                HomeView()
                    .modifier(AppearanceRoot())
                    .environment(app)
                    .task {
                        app.gameCenter.authenticate()
                        app.warmUpDaily()
                    }
                    .onChange(of: scenePhase) { _, phase in
                        if phase != .active { app.saveActiveGame() }
                    }
                    .dailyReminderTriggers(app.reminders)
                if showsSplash {
                    SplashView {
                        let animation: Animation? = UIAccessibility.isReduceMotionEnabled ? nil : .easeInOut(duration: 0.25)
                        withAnimation(animation) { showsSplash = false }
                    }
                    .transition(.opacity)
                    .zIndex(1)
                }
            }
        }
        .backgroundTask(.appRefresh(DailyReminders.friendsTaskID)) {
            await app.refreshFriendsReminder()
        }
    }

    /// Debug launches go straight in, so UI tests aren't held up.
    private static var wantsSplash: Bool {
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        return !arguments.contains("-startGame") && !arguments.contains("-uiTesting") && !arguments.contains("-open") && !arguments.contains("-demo")
        #else
        return true
        #endif
    }

    /// `-startGame` and `-demo` keep everything in memory; `-uiTesting` uses its own file (`-resetStore` wipes it).
    private static var store: PersistenceService.Store {
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        if arguments.contains("-startGame") || arguments.contains("-demo") { return .inMemory }
        if arguments.contains("-uiTesting") { return .named("UITests", reset: arguments.contains("-resetStore")) }
        #endif
        return .standard
    }
}
