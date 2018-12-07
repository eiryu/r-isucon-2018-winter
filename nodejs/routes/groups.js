const express = require("express");
const router = express.Router();
const { validateGroupname } = require("../utils/validator");

const getGroups = async (user) => {
  const { query } = require("./misc");
  const q = query();
  const gs = await q("SELECT * FROM belongs_user_group WHERE username = ? ORDER BY group_id", [user.username]);
  const groups = [];
  for (const g of gs) {
    const [group] = await q("SELECT * FROM groups WHERE id = ?", [g.group_id]);
    const [userCount] = await q("SELECT COUNT(*) as cnt FROM belongs_user_group WHERE group_id = ?", [g.group_id]);
    const [chatCount] = await q("SELECT COUNT(*) as cnt FROM belongs_chat_group WHERE group_id = ?", [g.group_id]);
    group.userCount = userCount.cnt;
    group.chatCount = chatCount.cnt;
    groups.push(group);
  }
  return groups;
}

router.get("/", async (req, res, next) => {
  if (!req.session.user) {
    return res.redirect("/login");
  }
  try {
    const user = req.session.user;
    const groups = await getGroups(user);
    res.render("groups", { groups, user });
  } catch (e) {
    next(e);
  }
});

router.post("/", async (req, res, next) => {
  if (!req.session.user) {
    res.redirect("/login");
  }
  const { connect } = require("./misc");
  const { query, connection } = await connect();
  const { groupname, usernames } = req.body;
  const user = req.session.user;
  const groups = await getGroups(user);

  const invalid = validateGroupname(groupname);
  if (invalid) {
    res.status(400);
    res.render("groups", { message: invalid, groups, user });
    return;
  }

  try {
    await connection.beginTransaction();
    await query("INSERT INTO groups (name, owner) VALUES (?, ?) ON DUPLICATE KEY UPDATE name = ?, owner = ?", [groupname, user.username, groupname, user.username]);
    const [{id}] = await query("SELECT id FROM groups WHERE name = ? AND owner = ?", [groupname, user.username]);
    const target_usernames = [...usernames.split(",").map(u => u.trim()).filter(u => u !== ""), user.username];
    for (const username of target_usernames) {
      const [user] = await query("SELECT * FROM user WHERE username = ?", [username]);
      if (!user) {
        const err = new Error("存在しないユーザが指定されています");
        err.status = 400;
        throw err
      }
      await query("INSERT INTO belongs_user_group (group_id, username) VALUES (?, ?) ON DUPLICATE KEY UPDATE group_id = ?, username = ?", [id, username, id, username])
    }
    await connection.commit();
    res.redirect("/groups")
  } catch (e) {
    await connection.rollback();
    res.status(e.status || 500);
    res.render("groups", { message: e.message, groups, user });
  } finally {
    await connection.release();
  }
});

router.get("/:owner/:groupname", async (req, res, next) => {
  const { connect } = require("./misc");
  const { query, connection } = await connect();
  try {
    if (!req.session.user) {
      return res.redirect("/login")
    }
    const user = req.session.user;
    const owner = req.params.owner;
    const groupname = req.params.groupname;
    await connection.beginTransaction();
    const [group] = await query("SELECT * FROM groups WHERE name = ? and owner = ?", [groupname, owner]);
    if (!group) {
      res.status(404);
      await connection.rollback();
      return res.render("chat", {message: "not found group", chats: [], user, group: {name: ""}});
    }
    const [belongs] = await query("SELECT * FROM belongs_user_group WHERE group_id = ? AND username = ?", [group.id, user.username]);
    if (!belongs) {
      res.status(404);
      await connection.rollback();
      return res.render("chat", {message: "not found group", chats: [], user,  group: {name: ""}});
    }
    const chatIds = await query("SELECT chat_id FROM belongs_chat_group WHERE group_id = ? ORDER BY chat_id DESC LIMIT 100", [group.id]);

    if (chatIds.length === 0) {
      await connection.commit();
      return res.render("chat", { group, chats: [], user });
    }

    const placeHolders = chatIds.map((id) => "?").join(",");
    const cids = chatIds.map((c) => c.chat_id);
    const cs = await query(`SELECT * FROM chat WHERE id IN (${placeHolders}) ORDER BY comment_at`, [...cids])
    const chats = [];
    for (const c of cs) {
      const [commentUser] = await query("SELECT * FROM user WHERE username = ?", [c.comment_by])
      await query("INSERT INTO read_chat (chat_id, username) VALUES (?, ?) ON DUPLICATE KEY UPDATE chat_id = ?, username = ?",
      [c.id, user.username, c.id, user.username])
      try {
        const [cnt] = await query("SELECT COUNT(*) as cnt FROM read_chat WHERE chat_id = ?", [c.id])
        const { app } = require("../app");
        const wss = app.get("wss");
        wss.broadcast(JSON.stringify({ eventName: "read", id: c.id, username: user.username, count: cnt.cnt}));
      } catch (e) {
        // ignore
      }
      const [cnt] = await query("SELECT COUNT(*) as cnt FROM read_chat WHERE chat_id = ?", [c.id])
      chats.push({...c, commentUser, count: cnt.cnt})
    }
    await connection.commit();
    return res.render("chat", { group, chats, user });
  } catch (e) {
    await connection.rollback();
    next(e);
  } finally {
    await connection.release();
  }
});

router.post("/:owner/:groupname", async (req, res, next) => {
  const { connect } = require("./misc");
  const { query, connection } = await connect();
  try {
    if (!req.session.user) {
      res.status(401);
      return res.json({message: "unauthorized"});
    }
    const user = req.session.user;
    const owner = req.params.owner;
    const groupname = req.params.groupname;
    const { value } = req.body;
    await connection.beginTransaction();
    const [group] = await query("SELECT * FROM groups WHERE name = ? and owner = ?", [groupname, owner]);
    if (!group) {
      res.status(404);
      await connection.rollback();
      return res.json({message: "not found group"});
    }
    const [belongs] = await query("SELECT * FROM belongs_user_group WHERE group_id = ? AND username = ?", [group.id, user.username]);
    if (!belongs) {
      res.status(404);
      await connection.rollback();
      return res.json({message: "not found group"});
    }

    await query("INSERT INTO chat (comment, comment_by, comment_at) VALUES (?, ?, ?)",
      [value, user.username, new Date()]);

    const [last] = await query("SELECT LAST_INSERT_ID() as id FROM chat");

    await query("INSERT INTO belongs_chat_group (chat_id, group_id) VALUES (?, ?)", [last.id, group.id])
    await connection.commit();

    const { app } = require("../app");
    const wss = app.get("wss")
    wss.broadcast(JSON.stringify({eventName: "message", comment: value, commentBy: {username: user.username, icon: user.icon}, groupname: groupname, chatId: last.id, count: 0 }));
    return res.json({message: "ok", chatId: last.id});
  } catch (e) {
    console.error(e);
    await connection.rollback();
    next(e);
  } finally {
    await connection.release();
  }
});

router.post("/:owner/:groupname/:chatid", async (req, res, next) => {
  const { connect } = require("./misc");
  const { query, connection } = await connect();
  try {
    if (!req.session.user) {
      res.status(401);
      return res.json({message: "unauthorized"});
    }
    const user = req.session.user;
    const owner = req.params.owner;
    const groupname = req.params.groupname;
    const chatId = parseInt(req.params.chatid);
    await connection.beginTransaction();
    const [group] = await query("SELECT * FROM groups WHERE name = ? and owner = ?", [groupname, owner]);
    if (!group) {
      res.status(404);
      await connection.rollback();
      return res.json({message: "not found chat"});
    }
    const [belongs] = await query("SELECT * FROM belongs_user_group WHERE group_id = ? AND username = ?", [group.id, user.username]);
    if (!belongs) {
      res.status(404);
      await connection.rollback();
      return res.json({message: "not found chat"});
    }
    const [cid] = await query("SELECT chat_id FROM belongs_chat_group WHERE group_id = ? AND chat_id = ?", [group.id, chatId]);
    if (!cid) {
      res.status(404);
      await connection.rollback();
      return res.json({message: "not found chat"});
    }

    await query("INSERT INTO read_chat (chat_id, username) VALUES (?, ?) ON DUPLICATE KEY UPDATE chat_id = ?, username = ?",
      [chatId, user.username, chatId, user.username]);
    await connection.commit();
    const [cnt] = await query("SELECT COUNT(*) as cnt FROM read_chat WHERE chat_id = ?", [chatId])
    const { app } = require("../app");
    const wss = app.get("wss")
    wss.broadcast(JSON.stringify({ eventName: "read", id: chatId, username: user.username, count: cnt.cnt }));
    return res.json({message: "ok", id: chatId});
  } catch (e) {
    console.error(e);
    await connection.rollback();
    next(e);
  } finally {
    await connection.release();
  }
});
module.exports = router;
