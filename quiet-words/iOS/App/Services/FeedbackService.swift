import QuietUI
import UIKit

/// Haptics and sound for play, honoring the player's settings.
@MainActor
final class FeedbackService {
    enum Event {
        /// The stroke reached one more letter.
        case letter
        case found
        /// A guess that isn't the hidden word, a tap that isn't allowed.
        case invalid
        case hint
        case hiddenWord
        case won
    }

    static let shared = FeedbackService()

    private let haptics = HapticsService()
    private let tones = ToneService()

    func play(_ event: Event, step: Int = 0) {
        let defaults = UserDefaults.standard
        if defaults.object(forKey: PreferenceKey.hapticsEnabled) as? Bool ?? true {
            switch event {
            case .letter, .hint: haptics.play(.select)
            case .found: haptics.play(.success)
            case .hiddenWord, .won: haptics.play(.success)
            case .invalid: haptics.play(.invalid)
            }
        }
        if defaults.object(forKey: PreferenceKey.soundEnabled) as? Bool ?? true {
            // C major pentatonic, like the rest of the family. The stroke climbs a note per letter.
            let scale = [261.63, 293.66, 329.63, 392.0, 440.0, 523.25, 587.33, 659.25, 783.99, 880.0, 1046.5, 1174.66]
            switch event {
            case .letter: tones.play([scale[min(step, scale.count - 1)]])
            case .hint: tones.play([523.25])
            case .found: tones.play([659.25, 783.99])
            case .invalid: tones.play([220.0])
            case .hiddenWord: tones.play([523.25, 659.25, 783.99])
            case .won: tones.play([523.25, 659.25, 783.99, 1046.5])
            }
        }
    }
}
