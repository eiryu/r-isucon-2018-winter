"use strict";

const express = require("express");
const router = express.Router({});
const { promisify } = require("util");
const exec = promisify(require("child_process").exec);

router.get("/", async (req, res) => {
  try{
    await exec("bash ../sql/db_setup.sh");
    res.json({message: "initialized"});
  } catch (e) {
    res.status(500);
    res.json({message: e.message});
  }
});

module.exports = router;
