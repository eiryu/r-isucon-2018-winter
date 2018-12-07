"use strict";

const { promisify } = require("util");

function getDB() {
  const { app } = require("../app");
  return app.get("db");
}

async function connect() {
  const db = getDB();
  const getConnection = promisify(db.getConnection.bind(db));
  const connection = await getConnection();
  const beginTransaction = promisify(connection.beginTransaction.bind(connection));
  const commit = promisify(connection.commit.bind(connection));
  const rollback = promisify(connection.rollback.bind(connection));
  const release = promisify(connection.release.bind(connection));
  const query = promisify(connection.query.bind(connection));
  return { connection: { beginTransaction, commit, rollback, release, }, query };
}

function query() {
  const db = getDB();
  return promisify(db.query.bind(db));
}

module.exports = {
  connect,
  query
};
