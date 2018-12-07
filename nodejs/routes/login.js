"use strict";

const express = require("express");
const bcrypt = require("bcrypt");

const router = express.Router({});

router.get("/", (req, res) => {
  if (req.session.user) {
    return res.redirect("/");
  }
  res.render("login", { message: null });
});

router.post("/", async (req, res) => {

  try {
    const { username, password } = req.body;
    const { query } = require("./misc");
    const q = query();
    const [user] = await q("SELECT * FROM user WHERE username=?", [username]);
    if (!user) {
      res.status(400);
      res.render("login", {
        message: "ユーザ名もしくはパスワードが間違っています。"
      });
      return;
    }
    const passwordHash = user.hash;
    const hash = await bcrypt.hash(password, user.salt);
    if (hash !== passwordHash) {
      res.status(400);
      res.render("login", {
        message: "ユーザ名もしくはパスワードが間違っています。"
      });
      return;
    }
    req.session.user = user;
    res.redirect("/");
  } catch (e) {
    console.error("failed to login:", e);
    res.status(500);
    res.render("login", { message: "サーバでエラーが発生しました。" });
  }
});

module.exports = router;
