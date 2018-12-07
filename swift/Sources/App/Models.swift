import Vapor

public struct User: Codable {
    let username:   String
    let salt:       String
    let hash:       String
    let icon:       String
    let lastname:  String
    let firstname: String
}

public struct Group: Codable {
    let id:    Int
    let name:  String
    let owner: String
}

public struct Chat: Codable {
    let id:         Int
    let comment:    String
    let comment_by: String
    let comment_at: Date
}

public struct BelongsUserGroup: Codable {
    let group_id: Int
    let username: String
}

public struct BelongsChatGroup: Codable {
    let chat_id:  Int
    let group_id: Int
}

public struct ReadChat: Codable {
    let chat_id:  Int
    let username: String
}
