"use strict";

const express = require("express");
const router = express.Router({});

router.post("/", async (req, res) => {
  req.session.user = null;
  res.redirect("/");
});

module.exports = router;
