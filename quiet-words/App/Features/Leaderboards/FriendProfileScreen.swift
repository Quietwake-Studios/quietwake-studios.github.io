import QuietUI
import QuietWordsCore
import SwiftUI

/// The kit's friend profile on this game's boards (demo launches show sample data).
struct FriendProfileScreen: View {
    let entry: GameCenterService.Entry

    @Environment(AppModel.self) private var app

    /// The boards of the language the player is on.
    static func boards(_ language: WordLanguage) -> FriendBoards {
        FriendBoards(daily: Leaderboard.daily(language).rawValue, best: Leaderboard.bestHard(language).rawValue,
                     streak: Leaderboard.longestStreak.rawValue)
    }

    var body: some View {
        FriendProfileView(entry: entry, gameCenter: app.gameCenter, boards: Self.boards(app.wordLanguage))
    }
}

extension View {
    /// Pushes a friend's profile while `friend` is set.
    func friendProfile(_ friend: Binding<GameCenterService.Entry?>) -> some View {
        navigationDestination(isPresented: Binding(get: { friend.wrappedValue != nil }, set: { if !$0 { friend.wrappedValue = nil } })) {
            if let entry = friend.wrappedValue { FriendProfileScreen(entry: entry) }
        }
    }
}
