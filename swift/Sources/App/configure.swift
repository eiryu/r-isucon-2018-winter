import Vapor
import Leaf
import MySQL

/// Called before your application initializes.
public func configure(_ config: inout Config, _ env: inout Environment, _ services: inout Services) throws {
    /// Register providers first
    try services.register(LeafProvider())
    try services.register(MySQLProvider())

    let mysqlConfig = MySQLDatabaseConfig(
                        hostname: Environment.get("RISUCON_DB_HOST") ?? "localhost",
                        port: Int(Environment.get("RISUCON_DB_PORT") ?? "") ?? 3306,
                        username: Environment.get("RISUCON_DB_USER") ?? "root",
                        password: Environment.get("RISUCON_DB_PASSWORD"),
                        database: "rine")
    services.register(mysqlConfig)

    let port = Int(Environment.get("PORT") ?? "") ?? 3000
    let serverConfiure = NIOServerConfig.default(hostname: "0.0.0.0", port: port)
    services.register(serverConfiure)

    /// Register routes to the router
    let router = EngineRouter.default()
    try routes(router)
    services.register(router, as: Router.self)
    
    /// Use Leaf for rendering views
    config.prefer(LeafRenderer.self, for: ViewRenderer.self)

    /// Register middleware
    var middlewares = MiddlewareConfig() // Create _empty_ middleware config
    middlewares.use(FileMiddleware.self) // Serves files from `Public/` directory
    middlewares.use(ErrorMiddleware.self) // Catches errors and converts to HTTP response
    let sessionsConfig = SessionsConfig(cookieName: "RINE_SESSION", cookieFactory: SessionsConfig.default().cookieFactory)
    let sessions = SessionsMiddleware(sessions: MemorySessions(), config: sessionsConfig)
    middlewares.use(sessions)
    services.register(middlewares)

    let wss = NIOWebSocketServer.default()
    websocket(wss)
    services.register(wss, as: WebSocketServer.self)
}
