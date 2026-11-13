/// The family's color themes, by role, as hex values (from the "Quiet Numbers · Diseño"
/// canvas, page 6). `QuietUI` turns them into colors; `PaletteTests` checks text contrast.
public enum Palette: String, CaseIterable, Codable, Sendable {
    case tealCoral, sageSand, indigoPeach, sageCoral, highContrast

    public enum Role: String, CaseIterable, Sendable {
        case background, surface, surface2, ink, muted, primary, onPrimary, accent, ghost, separator
        /// Red suits on cards (Quiet Solitaire). The accent, except where it doesn't read as red.
        case suitRed
    }

    /// Surfaces get a border, so nothing depends on color alone.
    public var isHighContrast: Bool { self == .highContrast }

    /// (light, dark) hex. Every palette has both modes (2026-11-08): the player picks the theme,
    /// and light or dark follows the system unless pinned in Settings › Appearance.
    public func hex(_ role: Role) -> (light: String, dark: String) {
        Self.values[self]![role]!
    }

    private static let values: [Palette: [Role: (String, String)]] = [
        .tealCoral: [
            .background: ("#F3F7F8", "#0F1A1D"), .surface: ("#FFFFFF", "#172629"), .surface2: ("#E3EEF0", "#1F3236"),
            .ink: ("#16201F", "#E8F1F2"), .muted: ("#5E6F72", "#8A9DA0"), .primary: ("#0E6B78", "#5BC2CF"),
            .onPrimary: ("#FFFFFF", "#062327"), .accent: ("#B9472F", "#FF9A85"), .ghost: ("#CFD8DA", "#2C3A3D"),
            .separator: ("#E3EAEC", "#263A3E"), .suitRed: ("#B9472F", "#FF9A85"),
        ],
        .sageSand: [
            .background: ("#F6F4EE", "#161A17"), .surface: ("#FFFFFF", "#1F2420"), .surface2: ("#ECE9E0", "#2A302B"),
            .ink: ("#1F2622", "#ECEFE8"), .muted: ("#5F665F", "#9AA39A"), .primary: ("#387454", "#7FC79E"),
            .onPrimary: ("#FFFFFF", "#0F2418"), .accent: ("#9A5B16", "#E0A45C"), .ghost: ("#D5D3CB", "#343A35"),
            .separator: ("#E6E2D8", "#2C322D"),
            // The amber accent doesn't read as a red suit.
            .suitRed: ("#B4503A", "#F08C74"),
        ],
        .indigoPeach: [
            .background: ("#F5F4FB", "#13121F"), .surface: ("#FFFFFF", "#1C1B2C"), .surface2: ("#E9E7F5", "#27253A"),
            .ink: ("#1A1930", "#ECEBF7"), .muted: ("#5E5C78", "#9593AE"), .primary: ("#4B48C4", "#9A98FF"),
            .onPrimary: ("#FFFFFF", "#15133A"), .accent: ("#B4521F", "#FFAE82"), .ghost: ("#D3D1E4", "#302E44"),
            .separator: ("#E4E2F0", "#2A2840"), .suitRed: ("#B4452A", "#FFAE82"),
        ],
        .sageCoral: [
            .background: ("#F7F3EC", "#171A17"), .surface: ("#FFFFFF", "#202420"), .surface2: ("#EFEAE0", "#2B302B"),
            .ink: ("#1E2420", "#EDF0EA"), .muted: ("#646A64", "#9CA49C"), .primary: ("#4D7157", "#8DBF9A"),
            .onPrimary: ("#FFFFFF", "#13241A"), .accent: ("#B4503A", "#F0927A"), .ghost: ("#D9D4CA", "#353A35"),
            .separator: ("#E8E2D6", "#2D322D"), .suitRed: ("#B4503A", "#F0927A"),
        ],
        .highContrast: [
            .background: ("#FFFFFF", "#000000"), .surface: ("#FFFFFF", "#000000"), .surface2: ("#E6EEF9", "#14233A"),
            .ink: ("#000000", "#FFFFFF"), .muted: ("#3A3A3C", "#D1D1D6"), .primary: ("#003F91", "#6FB1FF"),
            .onPrimary: ("#FFFFFF", "#000000"), .accent: ("#B85500", "#FF9F45"), .ghost: ("#B0B0B5", "#48484A"),
            .separator: ("#000000", "#FFFFFF"),
            // A true red: the orange accent is too close to the black suits' contrast story.
            .suitRed: ("#B3001B", "#FF6B6B"),
        ],
    ]
}

/// WCAG contrast between two `#RRGGBB` colors.
public enum Contrast {
    public static func ratio(_ a: String, _ b: String) -> Double {
        let (la, lb) = (luminance(a), luminance(b))
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /// Red, green, blue in 0...1.
    public static func components(_ hex: String) -> (Double, Double, Double) {
        let value = UInt32(hex.dropFirst(), radix: 16) ?? 0
        return (Double((value >> 16) & 0xFF) / 255, Double((value >> 8) & 0xFF) / 255, Double(value & 0xFF) / 255)
    }

    private static func luminance(_ hex: String) -> Double {
        func channel(_ c: Double) -> Double { c <= 0.03928 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
        let (r, g, b) = components(hex)
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    private static func pow(_ x: Double, _ y: Double) -> Double { exp(y * log(x)) }
}

import Foundation
