import Vapor
import MySQL

extension MySQLDatabase.Connection {
    public func getUser(with username: String) -> EventLoopFuture<User?> {
        return raw("SELECT * FROM user WHERE username = ?")
            .bind(username)
            .first(decoding: User.self)
    }
}
