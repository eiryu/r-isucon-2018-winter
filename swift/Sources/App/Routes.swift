import Vapor
import MySQL

extension Request {
    public func currentUser() throws -> EventLoopFuture<User?> {
        guard let username = try session()["username"] else {
            return future(nil)
        }
        return withPooledConnection(to: .mysql) { (conn: MySQLDatabase.Connection) in
            return conn.getUser(with: username)
        }
    }
}

struct GroupParameter: Encodable {
    let user: User?
    let groups: [Group]
}

/// Register your application's routes here.
public func routes(_ router: Router) throws {
    router.get("/initialize") { req in
        // TODO: add
        return ""
    }

    router.get("/") { req in
        return req.redirect(to: "/groups")
    }

    router.get("/register") { req -> Future<AnyResponse> in
        return try req.currentUser().flatMap { user in
            if let _ = user {
                let res = req.redirect(to: "/")
                return Future.map(on: req) { AnyResponse(res) }
            }
            let res = try req.view().render("register")
            return Future.map(on: req) { AnyResponse(res) }
        }
    }

    router.post("/register") { req in
        // TODO: add
        return ""
    }

    router.get("/login") { req -> Future<AnyResponse> in
        if let _ = try req.session()["username"] {
            let res = req.redirect(to: "/")
            return Future.map(on: req) { AnyResponse(res) }
        }
        let res = try req.view().render("login")
        return Future.map(on: req) { AnyResponse(res) }
    }

    router.post("/login") { req -> String in
        // TODO: replace
        try req.session()["username"] = "kamome"
        return ""
    }
    
    router.get("/groups") { req -> Future<AnyResponse> in
        return try req.currentUser().flatMap { user in
            guard let user = user else {
                let res = req.redirect(to: "/login")
                return Future.map(on: req) { AnyResponse(res) }
            }
            let res = try req.view().render("groups", GroupParameter(user: user, groups: []))
            return Future.map(on: req) { AnyResponse(res) }
        }
    }
}
