"use strict";

const http = require("http");
const express = require("express");
const Session = require("express-session");
const mysql = require("mysql");
const path = require("path");
const bodyParser = require("body-parser");
const WebSocket = require("ws");
const session = Session({
  name: "RINE_SESSION",
  secret: "rine-secret-key",
  resave: false,
  saveUninitialized: false,
  cookie: {httpOnly: false}
});

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server, path: "/ws/notification" });
wss.broadcast = function broadcast(data) {
  wss.clients.forEach(function each(client) {
    if (client.readyState === WebSocket.OPEN) {
      client.send(data);
    }
  });
};
const db = mysql.createPool({
  host: process.env.RISUCON_DB_HOST || "localhost",
  port: process.env.RISUCON_DB_PORT || 3306,
  user: process.env.RISUCON_DB_USER || "isucon",
  password: process.env.RISUCON_DB_PASSWORD || "isucon",
  database: process.env.RISUCON_DB_NAME || "rine",
  connectionLimit: 100,
  multipleStatements: true,
  charset: "utf8mb4"
});

app.use(express.static(path.join(__dirname, "public")))

app.use(bodyParser.urlencoded({ extended: false }));
app.use(bodyParser.json());
app.set("views", path.join(__dirname, "views"));
app.set("view engine", "ejs");
app.set("db", db);
app.set("wss", wss);

app.use(session);

app.use("/initialize", require("./routes/initialize"));
app.use("/login", require("./routes/login"));
app.use("/logout", require("./routes/logout"));
app.use("/register", require("./routes/register"));
app.use("/groups", require("./routes/groups"));
app.get("/", (req, res) => res.redirect("/groups"));

module.exports = { app, server };
