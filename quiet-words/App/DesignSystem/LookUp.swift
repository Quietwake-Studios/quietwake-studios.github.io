import QuietUI
import SwiftUI
import UIKit

/// A word to look up in the system dictionary (the same sheet as "Look Up" in Books).
struct LookUpTerm: Identifiable, Hashable {
    let id: String
}

/// The system dictionary. Local, no network, nothing collected; "Search Web" is the player's
/// own choice inside Apple's sheet. It only knows the dictionaries the player has downloaded,
/// so the action shows only where it has a definition.
@MainActor
enum SystemDictionary {
    private static var cache: [String: Bool] = [:]

    static func hasDefinition(_ term: String) -> Bool {
        #if DEBUG
        // `-forceLookUp`: the simulator has no dictionaries; show the action anyway (UI tests).
        if ProcessInfo.processInfo.arguments.contains("-forceLookUp") { return true }
        #endif
        if let known = cache[term] { return known }
        let found = UIReferenceLibraryViewController.dictionaryHasDefinition(forTerm: term)
        cache[term] = found
        return found
    }
}

/// `UIReferenceLibraryViewController` in a sheet.
struct LookUpSheet: UIViewControllerRepresentable {
    let term: String

    func makeUIViewController(context: Context) -> UIReferenceLibraryViewController {
        UIReferenceLibraryViewController(term: term)
    }

    func updateUIViewController(_ controller: UIReferenceLibraryViewController, context: Context) {}
}

/// A found word that opens its definition: a small book icon when the dictionary has one;
/// otherwise plain text (nothing to tap).
struct LookUpWord: View {
    /// As shown in the list, with accents ("León marino").
    let text: String
    @Binding var lookUp: LookUpTerm?

    @Environment(\.quietTheme) private var theme
    @State private var available = false

    var body: some View {
        Group {
            if available {
                Button { lookUp = LookUpTerm(id: text) } label: {
                    HStack(spacing: 4) {
                        Text(verbatim: text).foregroundStyle(theme.ink)
                        Image(systemName: "book.closed").font(.caption2).foregroundStyle(theme.primary)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityHint(Text("Shows the definition"))
                .accessibilityIdentifier("lookUp-\(text)")
                .contextMenu {
                    Button { lookUp = LookUpTerm(id: text) } label: { Label("Look Up", systemImage: "book.closed") }
                }
            } else {
                Text(verbatim: text).foregroundStyle(theme.ink)
            }
        }
        .font(.subheadline)
        .task(id: text) { available = SystemDictionary.hasDefinition(text) }
    }
}

extension View {
    /// Shows the dictionary sheet while `term` is set.
    func lookUpSheet(_ term: Binding<LookUpTerm?>) -> some View {
        sheet(item: term) { LookUpSheet(term: $0.id).ignoresSafeArea() }
    }
}
