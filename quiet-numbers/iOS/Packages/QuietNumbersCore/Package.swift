// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "QuietNumbersCore",
    platforms: [.iOS(.v18), .macOS(.v15)],
    products: [
        .library(name: "QuietNumbersCore", targets: ["QuietNumbersCore"]),
    ],
    dependencies: [
        .package(name: "QuietKit", path: "../../../../QuietKit/iOS"),
    ],
    targets: [
        .target(name: "QuietNumbersCore", dependencies: [.product(name: "QuietCore", package: "QuietKit")]),
        .testTarget(name: "QuietNumbersCoreTests", dependencies: ["QuietNumbersCore", .product(name: "QuietCore", package: "QuietKit")]),
    ]
)
