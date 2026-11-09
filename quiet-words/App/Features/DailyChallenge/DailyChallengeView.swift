import QuietCore
import QuietUI
import QuietWordsCore
import SwiftUI

/// Today's challenge in either language, the streak (one for both) and the calendar.
struct DailyChallengeView: View {
    let onPlay: (String, WordLanguage) -> Void

    @Environment(AppModel.self) private var app
    @Environment(\.quietTheme) private var theme
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var month = AppClock.now
    @State private var friendsRank: Int?
    @State private var friendsToday: [GameCenterService.Entry]?
    @State private var friend: GameCenterService.Entry?
    /// A day left halfway: continue it or start over.
    @State private var unfinished: String?

    var body: some View {
        @Bindable var app = app
        let today = app.todayKey
        let done = Set(WordLanguage.allCases.filter { app.isCompleted(today, $0) })
        ScrollView {
            VStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 10) {
                    Text(DayKeyFormat.weekdayString(today))
                        .font(.subheadline)
                        .opacity(0.85)
                    Text("\(app.theme(forDay: today)?.localizedName ?? "") · \(Text(DailyChallenge.level(forDayKey: today).title))")
                        .font(.system(.title2, design: .rounded, weight: .heavy))
                    HStack(spacing: 10) {
                        LanguagePill(language: $app.wordLanguage, done: done, onPrimary: true)
                        Text(detail(today))
                            .font(.footnote)
                            .opacity(0.85)
                    }
                    Button { play(today) } label: {
                        Text(playTitle(today))
                            .font(.headline)
                            .foregroundStyle(theme.primary)
                            .frame(maxWidth: .infinity, minHeight: 46)
                            .background(theme.onPrimary, in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .disabled(app.isPreparing)
                    .accessibilityIdentifier("playToday")
                    HStack {
                        Label { Text("\(app.currentStreak) streak") } icon: { Image(systemName: "flame.fill") }
                        Spacer()
                        Text("Best \(app.longestStreak)")
                        Spacer()
                        if let friendsRank { Text("#\(friendsRank) among friends") }
                    }
                    .font(.subheadline)
                    status(today).font(.footnote).opacity(0.85)
                }
                .foregroundStyle(theme.onPrimary)
                .padding(18)
                .background(theme.primary, in: RoundedRectangle(cornerRadius: 26, style: .continuous))

                if let friendsToday {
                    FriendsTodayCard(entries: friendsToday) { friend = $0 }
                }

                VStack(spacing: 6) {
                    CalendarView(month: $month, todayKey: today, completed: app.persistence.completedDays,
                                 late: app.progress.lateDays, inProgress: app.persistence.daysInProgress, onSelect: play)
                    Text("Next challenge in \(Text(DailyKey.nextRollover(after: AppClock.now), style: .relative))")
                        .font(.subheadline)
                        .foregroundStyle(theme.muted)
                        .padding(.top, 4)
                    Text("Play today in both languages: each one has its leaderboard, and the streak counts the day once. Past days don't go to the leaderboard, but finishing one counts toward your streak.")
                        .font(.footnote)
                        .foregroundStyle(theme.muted)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, 12)
                .padding(.top, 14)
                .padding(.bottom, 10)
                .background { SurfaceShape(shape: RoundedRectangle(cornerRadius: 26, style: .continuous)) }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .frame(maxWidth: Layout.calendar(sizeClass) + 40)
            .frame(maxWidth: .infinity)
        }
        .screen(Text("Daily Challenge"))
        .task(id: "\(app.gameCenter.isAuthenticated)-\(app.wordLanguage)") { await loadFriends() }
        .friendProfile($friend)
        .alert("Continue this challenge?",
               isPresented: Binding(get: { unfinished != nil }, set: { if !$0 { unfinished = nil } }),
               presenting: unfinished) { dayKey in
            Button("Continue") { onPlay(dayKey, app.wordLanguage) }
            Button("Start over") {
                app.startOver(dayKey, language: app.wordLanguage)
                onPlay(dayKey, app.wordLanguage)
            }
            Button("Cancel", role: .cancel) {}
        } message: { dayKey in
            Text("You left \(DayKeyFormat.string(dayKey)) halfway.")
        }
    }

    /// A day in progress (in the current language) asks first.
    private func play(_ dayKey: String) {
        if app.isInProgress(dayKey, app.wordLanguage) {
            unfinished = dayKey
        } else {
            onPlay(dayKey, app.wordLanguage)
        }
    }

    private func loadFriends() async {
        guard app.gameCenter.isAuthenticated else {
            friendsToday = nil
            friendsRank = nil
            return
        }
        let result = try? await app.gameCenter.dailyEntries(for: Leaderboard.daily(app.wordLanguage).rawValue, friendsOnly: true)
        friendsToday = result?.top
        friendsRank = result?.local?.rank
    }

    private func detail(_ dayKey: String) -> LocalizedStringResource {
        let side = DailyChallenge.level(forDayKey: dayKey).side
        return DailyChallenge.hasLongHidden(dayKey) ? "\(side)×\(side) · long hidden word" : "\(side)×\(side)"
    }

    private func playTitle(_ dayKey: String) -> LocalizedStringKey {
        if app.isInProgress(dayKey, app.wordLanguage) { return "Continue" }
        return app.isCompleted(dayKey, app.wordLanguage) ? "Play again" : "Play"
    }

    private func status(_ dayKey: String) -> Text {
        guard let record = app.persistence.record(DailyID(dayKey, app.wordLanguage)), record.isCompleted else {
            return Text("\(app.wordLanguage.wordsTitle) · not played yet")
        }
        return Text("\(app.wordLanguage.wordsTitle) · best \(record.score)")
    }
}
