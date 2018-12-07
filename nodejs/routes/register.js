"use strict";

const express = require("express");
const path = require("path");
const router = express.Router();
const multer = require("multer");
const mimes = require("mime-types");
const bcrypt = require("bcrypt");
const uuid = require("uuid/v4");
const {
  validateUsername,
  validatePassword
} = require("../utils/validator");

const storage = multer.diskStorage({
  destination(req, file, cb) {
    cb(null, path.resolve(__dirname, "..", "public", "images"));
  },
  filename(req, file, cb) {
    const ext = mimes.extension(file.mimetype);
    if (ext != "jpeg" && ext != "jpg" && ext != "png" && ext != "gif") {
      return cb(null, "default.png");
    }
    cb(
      null,
      `${req.body.username}-${Date.now()}.${uuid()}.${mimes.extension(file.mimetype)}`
    );
  }
});
const uploads = multer({ storage: storage });

router.get("/", async (req, res, next) => {
  if (req.session.user) {
    return res.redirect("/");
  }
  res.render("register");
});

router.post("/", uploads.single("icon"), async (req, res, next) => {
  const { connect } = require("./misc");
  const { query, connection } = await connect();
  try {
    const {
      username,
      password,
      firstname,
      lastname,
    } = req.body;

    const { filename } = req.file ? req.file : { filename: "default.png" };
    const invalid = await validation(query, res, {
      username,
      password,
    });
    if (invalid) {
      res.status(400);
      res.render("register", {
        message: invalid,
      });
      return;
    }
    await connection.beginTransaction();
    const [result] = await query("SELECT * from user WHERE username=?", [
      username
    ]);

    if (result) {
      res.status(403);
      res.render("register", {
        message: "既にユーザーが登録されています。",
      });
      return;
    }

    const salt = await bcrypt.genSalt();
    const hash = await bcrypt.hash(password, salt);
    await query(
      "INSERT INTO user (username, salt, hash, lastname, firstname, icon) VALUES (?, ?, ?, ?, ?, ?)",
      [username, salt, hash, lastname, firstname, filename]
    );
    const [general] = await query("SELECT * FROM groups WHERE name = ? and owner = ?", ["general", "root"]);
    await query(
      "INSERT INTO belongs_user_group (group_id, username) VALUES (?, ?)",
      [general.id, username]
    );
    await connection.commit();
    req.session.user = {
      username,
      lastname,
      firstname,
      icon: filename,
    };
    res.redirect("/");
  } catch (e) {
    console.error(e);
    await connection.rollback();
    res.status(500);
    res.render("register", {
      message: "エラーが発生しました。",
    });
  } finally {
    await connection.release();
  }
});

async function validation(query, res, obj) {
  const { username, password, first_name, last_name } = obj;
  let validationMessage = "";
  validationMessage = validateUsername(username);
  if (validationMessage) {
    return validationMessage;
  }
  validationMessage = validatePassword(password, username);
  if (validationMessage) {
    return validationMessage;
  }
  return validationMessage;
}

module.exports = router;
