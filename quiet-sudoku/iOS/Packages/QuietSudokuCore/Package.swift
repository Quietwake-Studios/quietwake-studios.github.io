// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "QuietSudokuCore",
    platforms: [.iOS(.v18), .macOS(.v15)],
    products: [
        .library(name: "QuietSudokuCore", targets: ["QuietSudokuCore"]),
    ],
    dependencies: [
        .package(name: "QuietKit", path: "../../../../QuietKit/iOS"),
    ],
    targets: [
        .target(name: "QuietSudokuCore", dependencies: [.product(name: "QuietCore", package: "QuietKit")]),
        .testTarget(name: "QuietSudokuCoreTests", dependencies: ["QuietSudokuCore", .product(name: "QuietCore", package: "QuietKit")]),
    ]
)
