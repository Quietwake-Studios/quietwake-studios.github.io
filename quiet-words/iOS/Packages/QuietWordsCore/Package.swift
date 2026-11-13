// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "QuietWordsCore",
    platforms: [.iOS(.v18), .macOS(.v15)],
    products: [
        .library(name: "QuietWordsCore", targets: ["QuietWordsCore"]),
    ],
    dependencies: [
        .package(name: "QuietKit", path: "../../../../QuietKit/iOS"),
    ],
    targets: [
        .target(
            name: "QuietWordsCore",
            dependencies: [.product(name: "QuietCore", package: "QuietKit")],
            // Every theme is one JSON file in Themes/; the app loads the whole folder.
            resources: [.copy("Resources/Themes"), .copy("Resources/Blocklist")]
        ),
        .testTarget(name: "QuietWordsCoreTests", dependencies: ["QuietWordsCore", .product(name: "QuietCore", package: "QuietKit")]),
    ]
)
