// swift-tools-version:4.0
import PackageDescription

let package = Package(
    name: "rine",
    dependencies: [
        .package(url: "https://github.com/vapor/vapor.git", from: "3.1.0"),
        .package(url: "https://github.com/vapor/leaf.git", from: "3.0.0"),
        .package(url: "https://github.com/vapor/mysql.git", from: "3.0.0"),
    ],
    targets: [
        .target(name: "App", dependencies: ["MySQL", "Leaf", "Vapor"]),
        .target(name: "Run", dependencies: ["App"]),
        .testTarget(name: "AppTests", dependencies: ["App"])
    ]
)

