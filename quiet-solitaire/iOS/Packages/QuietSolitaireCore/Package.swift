// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "QuietSolitaireCore",
    platforms: [.iOS(.v18), .macOS(.v15)],
    products: [
        .library(name: "QuietSolitaireCore", targets: ["QuietSolitaireCore"]),
    ],
    dependencies: [
        .package(name: "QuietKit", path: "../../../../QuietKit/iOS"),
    ],
    targets: [
        .target(name: "QuietSolitaireCore", dependencies: [.product(name: "QuietCore", package: "QuietKit")]),
        .testTarget(name: "QuietSolitaireCoreTests", dependencies: ["QuietSolitaireCore", .product(name: "QuietCore", package: "QuietKit")]),
    ]
)
