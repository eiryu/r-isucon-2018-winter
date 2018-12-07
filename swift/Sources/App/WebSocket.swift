import Vapor

public func websocket(_ wss: NIOWebSocketServer) {
    wss.get("ws/notification") { ws, req in
        // TODO: add
    }
}
