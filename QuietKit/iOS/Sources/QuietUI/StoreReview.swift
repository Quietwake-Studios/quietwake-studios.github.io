#if canImport(UIKit)
import QuietCore
import StoreKit
import SwiftUI

/// Store reviews, the same in every game (2026-11-13). StoreKit is used for this only.
///
/// - The system prompt once per install, when the summary of the third finished game is
///   closed (`ReviewPrompt`). The app calls `summaryClosed(startsNewGame:)` from every button
///   that closes a summary, and `returnedToMenu()` whenever Home or the Daily Challenge shows;
///   the prompt comes after the sheet is gone, never over it, mid-game or on a fresh board.
/// - `RateAppRow` in Settings opens the App Store's "Write a Review" page.
///
/// The count and the "asked" flag live in UserDefaults under `storeReview.*`; "Reset
/// progress" doesn't clear them. Off with the debug launches (`-uiTesting`, `-startGame`,
/// `-demo`), so tests and screenshots never see the system dialog.
@MainActor
public final class StoreReview {
    public let appStoreID: String
    private let defaults: UserDefaults
    private let isEnabled: Bool

    static let finishedGamesKey = "storeReview.finishedGames"
    static let askedKey = "storeReview.asked"

    public init(appStoreID: String, defaults: UserDefaults = .standard, isEnabled: Bool? = nil) {
        self.appStoreID = appStoreID
        self.defaults = defaults
        let arguments = ProcessInfo.processInfo.arguments
        self.isEnabled = isEnabled ?? !["-uiTesting", "-startGame", "-demo"].contains(where: arguments.contains)
    }

    /// The App Store page to write a review.
    public var writeReviewURL: URL {
        URL(string: "https://apps.apple.com/app/id\(appStoreID)?action=write-review")!
    }

    /// A finished game's summary was closed. `startsNewGame`: with "New Game" (the prompt then
    /// waits for `returnedToMenu()`, never over the fresh board).
    public func summaryClosed(startsNewGame: Bool = false) {
        update { $0.summaryClosed(startsNewGame: startsNewGame) }
    }

    /// Home or the Daily Challenge is on screen (the app calls it on every navigation there).
    public func returnedToMenu() {
        update { $0.returnedToMenu() }
    }

    private func update(_ change: (inout ReviewPrompt) -> Bool) {
        guard isEnabled else { return }
        var prompt = ReviewPrompt(finishedGames: defaults.integer(forKey: Self.finishedGamesKey),
                                  asked: defaults.bool(forKey: Self.askedKey))
        let asks = change(&prompt)
        defaults.set(prompt.finishedGames, forKey: Self.finishedGamesKey)
        defaults.set(prompt.asked, forKey: Self.askedKey)
        guard asks else { return }
        Task { @MainActor in
            // Let the sheet slide away and the next screen settle first.
            try? await Task.sleep(for: .seconds(0.8))
            let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            guard let scene = scenes.first(where: { $0.activationState == .foregroundActive }) else { return }
            AppStore.requestReview(in: scene)
        }
    }
}

/// Settings row: "Rate Quiet Words", opening the App Store's review page. The app gives its
/// name (not translated) and puts the row in its "About" section, above "Version".
public struct RateAppRow: View {
    let appName: String
    let review: StoreReview

    @Environment(\.quietTheme) private var theme
    @Environment(\.openURL) private var openURL

    public init(appName: String, review: StoreReview) {
        self.appName = appName
        self.review = review
    }

    public var body: some View {
        Button { openURL(review.writeReviewURL) } label: {
            HStack(spacing: 12) {
                Text("Rate \(appName)", bundle: .main)
                    .foregroundStyle(theme.ink)
                Spacer()
                Image(systemName: "arrow.up.forward")
                    .font(.footnote.weight(.bold))
                    .foregroundStyle(theme.muted)
                    .accessibilityHidden(true)
            }
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(.isLink)
        .accessibilityIdentifier("rateApp")
    }
}
#endif
